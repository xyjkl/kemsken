package com.example

import android.app.Application
import com.example.core.database.KemSkenDatabase
import com.example.core.storage.FileManager
import com.example.data.repository.DocumentRepository

class KemSkenApplication : Application() {

    lateinit var database: KemSkenDatabase
        private set

    lateinit var fileManager: FileManager
        private set

    lateinit var repository: DocumentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = KemSkenDatabase.getInstance(this)
        fileManager = FileManager(this)
        repository = DocumentRepository(database, fileManager, this)
    }

    companion object {
        lateinit var instance: KemSkenApplication
            private set
    }
}
