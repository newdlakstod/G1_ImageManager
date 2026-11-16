package com.imagemanager.app.ui.piles

object PilePaths {
    fun relativePathFor(name: String): String = "Pictures/Piles/${name.trim()}/"
}
