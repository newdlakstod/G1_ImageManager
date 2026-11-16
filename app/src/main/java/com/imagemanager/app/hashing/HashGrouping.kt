package com.imagemanager.app.hashing

data class HashedPhoto(val mediaId: Long, val hash: Long)

object HashGrouping {

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun group(
        photos: List<HashedPhoto>,
        threshold: Int = 10,
        bucketBits: Int = 8,
    ): List<List<Long>> {
        if (photos.size < 2) return emptyList()

        val buckets = HashMap<Long, MutableList<Int>>()
        photos.forEachIndexed { index, photo ->
            val bucketKey = photo.hash ushr (64 - bucketBits)
            buckets.getOrPut(bucketKey) { mutableListOf() }.add(index)
        }

        val parent = IntArray(photos.size) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootA] = rootB
        }

        buckets.values.forEach { indices ->
            for (i in indices.indices) {
                for (j in i + 1 until indices.size) {
                    val a = indices[i]
                    val b = indices[j]
                    if (hammingDistance(photos[a].hash, photos[b].hash) <= threshold) {
                        union(a, b)
                    }
                }
            }
        }

        val groupsByRoot = HashMap<Int, MutableList<Long>>()
        photos.indices.forEach { index ->
            val root = find(index)
            groupsByRoot.getOrPut(root) { mutableListOf() }.add(photos[index].mediaId)
        }

        return groupsByRoot.values.filter { it.size > 1 }
    }
}
