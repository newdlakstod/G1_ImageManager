package com.imagemanager.app.ui.compare

/**
 * Rules of the "tournament" comparison: one [main] item is compared against one [challenger]
 * at a time. For the challenger you either [delete] it, [hold] it, or [promote] it to be the new
 * main (the old main is then held). Immutable: every action returns a new session, and [undo]
 * returns the previous one. Nothing here touches the device; deletion happens later, for [deleted].
 */
class CompareSession<T> private constructor(
    val main: T,
    val queue: List<T>,
    val deleted: List<T>,
    val held: List<T>,
    val total: Int,
    private val previous: CompareSession<T>?,
) {
    val challenger: T? get() = queue.firstOrNull()
    val isFinished: Boolean get() = queue.isEmpty()
    val canUndo: Boolean get() = previous != null

    /** 1-based index of the current challenger (equals [total] + 1 once finished). */
    val position: Int get() = total - queue.size + 1

    fun delete(): CompareSession<T> = next(deleted = deleted + currentChallenger())

    fun hold(): CompareSession<T> = next(held = held + currentChallenger())

    fun promote(): CompareSession<T> = next(main = currentChallenger(), held = held + main)

    fun undo(): CompareSession<T> = checkNotNull(previous) { "nothing to undo" }

    private fun currentChallenger(): T = checkNotNull(challenger) { "comparison already finished" }

    private fun next(
        main: T = this.main,
        deleted: List<T> = this.deleted,
        held: List<T> = this.held,
    ): CompareSession<T> = CompareSession(main, queue.drop(1), deleted, held, total, previous = this)

    companion object {
        /** The first item becomes the main one; the rest are challengers in order. */
        fun <T> start(items: List<T>): CompareSession<T> {
            require(items.size >= 2) { "need at least 2 items to compare" }
            return CompareSession(
                main = items.first(),
                queue = items.drop(1),
                deleted = emptyList(),
                held = emptyList(),
                total = items.size - 1,
                previous = null,
            )
        }
    }
}
