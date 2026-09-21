package cn.huacheng.safebaiyun.util


/**
 *
 *@description:
 *@author: guangzhou
 *@create: 2024-03-04
 */
object LockBiz {

    fun encryptData(inputData: ByteArray, headerData: ByteArray, keyString: String): ByteArray {
        if (inputData.size < 4 || headerData.size < 6 || !keyString.matches(Regex("^[0-9a-fA-F]{16}$"))) {
            return byteArrayOf()
        }
        val keyBytes = ByteUtil.hexToBytes(keyString)
        val headerBytesSubset = ByteArray(4)
        val encryptedBlock = ByteArray(8)
        for (i in headerBytesSubset.indices) {
            headerBytesSubset[i] = headerData[i + 2]
        }
        println(headerData.contentToString())
        println(headerBytesSubset.contentToString())
        var sum = 0
        for (byte in inputData) {
            sum += Integer.parseInt(ByteUtil.byteToHex(byte), 16)
        }
        for (byte in keyBytes) {
            sum += Integer.parseInt(ByteUtil.byteToHex(byte), 16)
        }
        val sumBytes = byteArrayOf((sum and 255).toByte(), (sum shr 8 and 255).toByte())
        var paddedLength = sumBytes.size + inputData.size
        if (paddedLength % 8 != 0) {
            paddedLength = (paddedLength / 8 + 1) * 8
        }
        val paddedData = ByteArray(paddedLength)
        System.arraycopy(sumBytes, 0, paddedData, 0, sumBytes.size)
        System.arraycopy(inputData, 0, paddedData, sumBytes.size, inputData.size)
        for (i in sumBytes.size + inputData.size until paddedLength) {
            paddedData[i] = 0
        }
        System.arraycopy(FDes.encryptData(paddedData, keyBytes), 0, encryptedBlock, 0, 8)
        println("Sum: $sum")
        println("Before encryption: " + ByteUtil.bytesToHex(paddedData))
        println("After encryption: " + ByteUtil.bytesToHex(encryptedBlock))
        val finalDataLength = (encryptedBlock.size + 12).toByte()
        val finalData = ByteArray(encryptedBlock.size + 12)
        finalData[0] = -91
        finalData[1] = finalDataLength
        finalData[2] = 5
        finalData[3] = headerBytesSubset[0]
        finalData[4] = headerBytesSubset[1]
        finalData[5] = headerBytesSubset[2]
        finalData[6] = headerBytesSubset[3]
        finalData[7] = 0
        finalData[8] = 1
        finalData[9] = 7
        System.arraycopy(encryptedBlock, 0, finalData, 10, encryptedBlock.size)
        finalData[finalData.size - 2] = 0
        finalData[finalData.size - 1] = 90
        var checksum = 0
        for (byte in finalData) {
            checksum += Integer.parseInt(ByteUtil.byteToHex(byte), 16)
        }
        finalData[finalData.size - 2] = (checksum.inv() and 255).toByte()
        return finalData
    }

    /**
     * A write callback only confirms that Android handed the command to the lock.  The lock's
     * encrypted 0x04 notification is the actual acknowledgement.  Validate its framing,
     * checksum and encrypted status byte before reporting success.
     */
    fun isUnlockSuccess(response: ByteArray, keyString: String): Boolean {
        if (response.size != 20 || response[0] != 0xA5.toByte() ||
            response[1].toInt() and 0xFF != response.size || response[2] != 0x04.toByte() ||
            response.last() != 0x5A.toByte() || !hasValidChecksum(response) ||
            !keyString.matches(Regex("^[0-9a-fA-F]{16}$"))
        ) {
            return false
        }

        val encryptedPayload = response.copyOfRange(10, 18)
        val payload = FDes.decryptData(encryptedPayload, ByteUtil.hexToBytes(keyString))
        return payload.size == 8 && payload[2] == 0.toByte()
    }

    private fun hasValidChecksum(data: ByteArray): Boolean {
        var sum = 0
        data.forEachIndexed { index, byte ->
            if (index != data.lastIndex - 1) {
                sum += byte.toInt() and 0xFF
            }
        }
        val expected = sum.inv() and 0xFF
        return expected == (data[data.lastIndex - 1].toInt() and 0xFF)
    }

    fun hexToByteArray(hexString: String): ByteArray {
        val resultBytes = ByteArray(6)
        val hexPairs = hexString.split(":")
        for (i in hexPairs.indices) {
            resultBytes[i] = hexPairs[i].toInt(16).toByte()
        }
        return resultBytes
    }

}
