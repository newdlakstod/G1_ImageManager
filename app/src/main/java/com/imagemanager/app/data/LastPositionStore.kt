package com.imagemanager.app.data

interface LastPositionStore {
    fun save(mediaId: Long)
    fun load(): Long?
}
