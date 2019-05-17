package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.datastructures.GeneratorStorage
import il.ac.technion.cs.softwaredesign.storage.impl.SecureStorageImpl


class LibraryMoudle : KotlinModule() {
    override fun configure() {
        bind<SecureStorage>().annotatedWith<GeneratorStorage>().to<SecureStorageImpl>()

    }
}