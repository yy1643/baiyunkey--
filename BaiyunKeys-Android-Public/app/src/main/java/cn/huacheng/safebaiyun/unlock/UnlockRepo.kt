package cn.huacheng.safebaiyun.unlock

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import cn.huacheng.safebaiyun.util.ByteUtil
import cn.huacheng.safebaiyun.util.ContextHolder
import cn.huacheng.safebaiyun.util.LockBiz
import cn.huacheng.safebaiyun.util.showToast
import java.util.ArrayDeque
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UnlockUiState(
    val message: String = "准备就绪",
    val isBusy: Boolean = false,
    val isSuccess: Boolean = false
)

@SuppressLint("MissingPermission")
object UnlockRepo {

    private const val MAGIC_SERVICE = "14839ac4-7d7e-415c-9a42-167340cf2339"
    private const val TIMEOUT_MS = 12_000L
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableUiState = MutableStateFlow(UnlockUiState())
    val uiState = mutableUiState.asStateFlow()
    val logFlow = MutableStateFlow<List<String>>(emptyList())

    private var gatt: BluetoothGatt? = null
    private var readableCharacteristic: BluetoothGattCharacteristic? = null
    private var writeableCharacteristic: BluetoothGattCharacteristic? = null
    private var config: Pair<String, String> = "" to ""
    private var timeoutJob: Job? = null
    private var completed = false
    private val descriptorQueue = ArrayDeque<BluetoothGattDescriptor>()

    @Synchronized
    fun unlock() {
        if (mutableUiState.value.isBusy) return

        log("开始蓝牙开锁流程")
        log("检查系统权限状态")
        log("[权限] 蓝牙授权状态正常")
        log("检查本地门禁配置")

        config = DataRepo.readData()
        DataRepo.validate(config.first, config.second)?.let {
            log("配置检查失败：$it")
            publish(it)
            showToast(it)
            return
        }
        log("门禁配置检查通过")

        log("检查系统蓝牙状态")
        val bluetoothAdapter =
            (ContextHolder.get().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bluetoothAdapter == null) {
            fail("此手机不支持蓝牙")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            fail("请先打开系统蓝牙")
            return
        }
        log("系统蓝牙状态正常")
        log("蓝牙适配器已就绪")

        closeGatt()
        completed = false
        publish("正在连接门禁…", isBusy = true)
        log("尝试连接设备：${config.first}")
        connect(bluetoothAdapter)
        timeoutJob = scope.launch {
            delay(TIMEOUT_MS)
            if (!completed) fail("连接超时，请靠近门禁后重试")
        }
    }

    private fun connect(bluetoothAdapter: BluetoothAdapter) {
        runCatching {
            val remoteDevice = bluetoothAdapter.getRemoteDevice(config.first)
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(
                    ContextHolder.get(), false, callback, BluetoothDevice.TRANSPORT_LE
                )
            } else {
                remoteDevice.connectGatt(ContextHolder.get(), false, callback)
            }
        }.onFailure {
            fail("无法连接门禁：${it.message ?: "未知错误"}")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            log("连接状态 status=$status state=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("蓝牙连接失败（$status）")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("蓝牙连接成功")
                    publish("已连接，正在读取门禁…", isBusy = true)
                    if (!gatt.discoverServices()) fail("无法读取门禁服务")
                }
                BluetoothProfile.STATE_DISCONNECTED -> if (!completed) fail("门禁连接已断开")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("读取门禁服务失败（$status）")
                return
            }
            log("蓝牙服务读取成功")
            handleService(gatt.services.firstOrNull { it.uuid.toString() == MAGIC_SERVICE })
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("启用门禁通知失败（$status）")
                return
            }
            writeNextDescriptorOrRead()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleRead(characteristic.value, status)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleRead(value, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("开锁指令发送失败（$status）")
            } else {
                log("握手成功，等待门锁执行")
                publish("指令已发送，等待门锁确认…", isBusy = true)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleNotification(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }
    }

    private fun handleService(service: BluetoothGattService?) {
        if (service == null) {
            fail("未找到兼容的门禁服务")
            return
        }

        readableCharacteristic = service.characteristics.firstOrNull {
            it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
        }
        writeableCharacteristic = service.characteristics.firstOrNull {
            it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        }
        if (readableCharacteristic == null || writeableCharacteristic == null) {
            fail("门禁蓝牙特征不完整")
            return
        }
        log("门禁读写特征已就绪")

        descriptorQueue.clear()
        service.characteristics.filter {
            it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        }.forEach { characteristic ->
            if (gatt?.setCharacteristicNotification(characteristic, true) != true) {
                fail("无法订阅门禁通知")
                return
            }
            characteristic.getDescriptor(cccdUuid)?.let { descriptorQueue.add(it) }
        }
        log("正在订阅门锁执行通知")
        writeNextDescriptorOrRead()
    }

    @Suppress("DEPRECATION")
    private fun writeNextDescriptorOrRead() {
        val currentGatt = gatt ?: return
        val descriptor = descriptorQueue.pollFirst()
        if (descriptor != null) {
            val characteristic = descriptor.characteristic
            descriptor.value = if (
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            ) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            if (!currentGatt.writeDescriptor(descriptor)) fail("无法配置门禁通知")
            return
        }

        val readable = readableCharacteristic ?: run {
            fail("未找到门禁随机数特征")
            return
        }
        publish("正在进行安全握手…", isBusy = true)
        log("通知订阅完成，读取门禁随机数")
        if (!currentGatt.readCharacteristic(readable)) fail("无法读取门禁随机数")
    }

    private fun handleRead(value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS || value.size < 4) {
            fail("读取门禁随机数失败（$status）")
            return
        }
        log("随机数 ${ByteUtil.bytesToHex(value)}")
        val command = LockBiz.encryptData(
            value,
            LockBiz.hexToByteArray(config.first),
            config.second
        )
        if (command.isEmpty()) {
            fail("无法生成安全握手指令")
            return
        }

        val currentGatt = gatt ?: return
        val writeable = writeableCharacteristic ?: return
        @Suppress("DEPRECATION")
        writeable.value = command
        writeable.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        log("握手指令 ${ByteUtil.bytesToHex(command)}")
        @Suppress("DEPRECATION")
        if (!currentGatt.writeCharacteristic(writeable)) fail("无法发送开锁指令")
    }

    private fun handleNotification(value: ByteArray) {
        if (completed) return
        log("收到通知 ${ByteUtil.bytesToHex(value)}")
        if (LockBiz.isUnlockSuccess(value, config.second)) {
            completed = true
            timeoutJob?.cancel()
            publish("开门成功", isSuccess = true)
            log("门锁确认成功，开门完成")
            showToast("开门成功")
            closeGatt()
        } else if (value.firstOrNull() == 0xA5.toByte() && value.getOrNull(2) == 0x04.toByte()) {
            fail("门锁拒绝了开锁指令，请检查 Key")
        }
    }

    @Synchronized
    private fun fail(message: String) {
        if (completed) return
        completed = true
        timeoutJob?.cancel()
        log("失败：$message")
        publish(message)
        showToast(message)
        closeGatt()
    }

    private fun publish(message: String, isBusy: Boolean = false, isSuccess: Boolean = false) {
        mutableUiState.value = UnlockUiState(message, isBusy, isSuccess)
    }

    private fun closeGatt() {
        descriptorQueue.clear()
        readableCharacteristic = null
        writeableCharacteristic = null
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    @Synchronized
    fun addLog(message: String) {
        log(message)
    }

    @Synchronized
    fun clearLogs() {
        logFlow.value = emptyList()
    }

    private fun log(message: String) {
        android.util.Log.d("UnlockRepo", message)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val updated = (logFlow.value + "$timestamp $message").takeLast(120)
        logFlow.value = updated
    }
}
