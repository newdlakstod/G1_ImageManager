package com.imagemanager.app.hashing

object DifferenceHash {

    fun compute(luminance: IntArray, width: Int = 9, height: Int = 8): Long {
        require(luminance.size == width * height) {
            "Expected $width x $height = ${width * height} pixels, got ${luminance.size}"
        }
        require(width >= 2) { "width must be at least 2 to compare adjacent pixels" }

        var hash = 0L
        var bitIndex = 0
        for (y in 0 until height) {
            for (x in 0 until width - 1) {
                val left = luminance[y * width + x]
                val right = luminance[y * width + x + 1]
                if (left < right) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        return hash
    }
}
