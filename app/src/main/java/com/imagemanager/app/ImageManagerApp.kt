package com.imagemanager.app

import android.app.Application
import com.imagemanager.app.data.LastPositionStore
import com.imagemanager.app.data.MediaStoreRepository
import com.imagemanager.app.data.SharedPrefsLastPositionStore
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.domain.BitmapPhotoHasher
import com.imagemanager.app.domain.DuplicateScanner

class ImageManagerApp : Application() {
    lateinit var mediaStoreRepository: MediaStoreRepository
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var duplicateScanner: DuplicateScanner
        private set
    lateinit var lastPositionStore: LastPositionStore
        private set

    override fun onCreate() {
        super.onCreate()
        mediaStoreRepository = MediaStoreRepository(this)
        database = AppDatabase.getInstance(this)
        duplicateScanner = DuplicateScanner(
            photoSource = mediaStoreRepository::listAllPhotos,
            hasher = BitmapPhotoHasher(this),
            hashDao = database.photoHashDao(),
            groupDao = database.duplicateGroupDao(),
        )
        lastPositionStore = SharedPrefsLastPositionStore(this)
    }
}
