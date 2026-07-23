package com.rameshta.splitframe.domain

/** Groups the intermediate updates from one pointer gesture into one undo entry. */
class TransformUndoSession {
    private var activeCellIndex: Int? = null

    /** Returns true only when the current project snapshot should be added to undo history. */
    fun onTransform(cellIndex: Int, gestureFinished: Boolean): Boolean {
        val shouldTrack = activeCellIndex != cellIndex
        activeCellIndex = if (gestureFinished) null else cellIndex
        return shouldTrack
    }

    fun expire(cellIndex: Int) {
        if (activeCellIndex == cellIndex) activeCellIndex = null
    }

    fun clear() {
        activeCellIndex = null
    }
}
