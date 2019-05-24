package il.ac.technion.cs.softwaredesign.storage.datastructures

import il.ac.technion.cs.softwaredesign.storage.api.ISecureStorageKey
import il.ac.technion.cs.softwaredesign.storage.utils.ConversionUtils
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS

class IdOperatorKey(private var id: Long = MANAGERS_CONSTS.CHANNEL_INVALID_ID, private var isOperator: Boolean = false)
    : ISecureStorageKey<IdOperatorKey> {
    override fun compareTo(other: IdOperatorKey): Int {
        val compare = id.compareTo(other.id)
        if (compare != 0) return compare
        return isOperator.compareTo(other.isOperator)
    }

    // form: <id><bool>
    override fun toByteArray(): ByteArray {
        val idByteArray = ConversionUtils.longToBytes(id)
        val isOperatorByteArray = if (isOperator) '1'.toByte() else '0'.toByte()
        return idByteArray + isOperatorByteArray
    }

    override fun fromByteArray(value: ByteArray) {
        var start = 0
        var end = Long.SIZE_BYTES - 1
        id = ConversionUtils.bytesToLong(value.sliceArray(IntRange(start,end)))
        start += Long.SIZE_BYTES
        end += Long.SIZE_BYTES
        isOperator = value[Long.SIZE_BYTES].toChar() == '1'
    }

    fun getId() : Long = id
}
