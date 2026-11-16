package com.imagemanager.app.hashing

import android.graphics.Bitmap
import android.graphics.Color

fun Bitmap.computeDHash(): Long {
    val resized = Bitmap.createScaledBitmap(this, 9, 8, true)
    val luminance = IntArray(9 * 8)
    for (y in 0 until 8) {
        for (x in 0 until 9) {
            val pixel = resized.getPixel(x, y)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            luminance[y * 9 + x] = (r + g + b) / 3
        }
    }
    if (resized !== this) resized.recycle()
    return DifferenceHash.compute(luminance, width = 9, height = 8)
}
