package cn.huacheng.safebaiyun.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockBizTest {

    private val dummyKey = "0011223344556677"

    @Test
    fun generatedHandshakeHasExpectedPublicFraming() {
        val command = LockBiz.encryptData(
            ByteUtil.hexToBytes("01020304"),
            LockBiz.hexToByteArray("AA:BB:CC:DD:EE:FF"),
            dummyKey
        )

        assertEquals(20, command.size)
        assertEquals(0xA5, command[0].toInt() and 0xFF)
        assertEquals(0x05, command[2].toInt() and 0xFF)
        assertEquals(0xCC, command[3].toInt() and 0xFF)
        assertEquals(0xDD, command[4].toInt() and 0xFF)
        assertEquals(0xEE, command[5].toInt() and 0xFF)
        assertEquals(0xFF, command[6].toInt() and 0xFF)
        assertEquals(0x5A, command.last().toInt() and 0xFF)
        assertTrue(hasValidChecksum(command))
    }

    @Test
    fun validSyntheticLockReplyIsAccepted() {
        val response = syntheticReply(dummyKey)
        assertTrue(LockBiz.isUnlockSuccess(response, dummyKey))
    }

    @Test
    fun corruptedSyntheticReplyIsRejected() {
        val response = syntheticReply(dummyKey)
        response[12] = (response[12].toInt() xor 0x01).toByte()
        assertFalse(LockBiz.isUnlockSuccess(response, dummyKey))
    }

    private fun syntheticReply(key: String): ByteArray {
        val payload = byteArrayOf(0x6E, 0x03, 0, 0, 0, 0, 0, 0)
        val encrypted = FDes.encryptData(payload, ByteUtil.hexToBytes(key))
        val response = ByteArray(20)
        response[0] = 0xA5.toByte()
        response[1] = 20
        response[2] = 0x04
        response[3] = 0xFF.toByte()
        response[4] = 0xFF.toByte()
        response[5] = 0xFF.toByte()
        response[6] = 0xFF.toByte()
        response[9] = 0x87.toByte()
        encrypted.copyInto(response, destinationOffset = 10)
        response[19] = 0x5A
        response[18] = checksum(response).toByte()
        return response
    }

    private fun hasValidChecksum(data: ByteArray): Boolean {
        return checksum(data) == (data[data.lastIndex - 1].toInt() and 0xFF)
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        data.forEachIndexed { index, byte ->
            if (index != data.lastIndex - 1) sum += byte.toInt() and 0xFF
        }
        return sum.inv() and 0xFF
    }
}
