package il.ac.technion.cs.softwaredesign.storage.datastructures

import com.google.inject.BindingAnnotation
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.utils.ConversionUtils
import il.ac.technion.cs.softwaredesign.internals.ISequenceGenerator
import il.ac.technion.cs.softwaredesign.storage.utils.TREE_CONST
import il.ac.technion.cs.softwaredesign.storage.utils.TREE_CONST.LAST_GENERATED_ID
import javax.inject.Singleton

@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
@BindingAnnotation
annotation class GeneratorStorage

@Singleton
class SecureSequenceGenerator
 constructor(private val secureStorage: SecureStorage, idInByteArray: ByteArray) : ISequenceGenerator {
    private var lastGeneratedKey : ByteArray = idInByteArray + LAST_GENERATED_ID.toByteArray()
    override fun next(): Long {
        val currentIndexInByteArray = secureStorage.read(lastGeneratedKey) ?: ConversionUtils.longToBytes(TREE_CONST.ROOT_INIT_INDEX)
        val nextValue:Long= ConversionUtils.bytesToLong(currentIndexInByteArray)+1L
        secureStorage.write(lastGeneratedKey, ConversionUtils.longToBytes(nextValue))
        return nextValue
    }

}