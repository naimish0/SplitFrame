package com.rameshta.splitframe.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformUndoSessionTest {
    @Test
    fun `continuous gesture records one undo entry`() {
        val session = TransformUndoSession()

        assertTrue(session.onTransform(cellIndex = 2, gestureFinished = false))
        assertFalse(session.onTransform(cellIndex = 2, gestureFinished = false))
        assertFalse(session.onTransform(cellIndex = 2, gestureFinished = true))
        assertTrue(session.onTransform(cellIndex = 2, gestureFinished = false))
    }

    @Test
    fun `cell change and expiry start a new undo entry`() {
        val session = TransformUndoSession()

        assertTrue(session.onTransform(cellIndex = 0, gestureFinished = false))
        assertTrue(session.onTransform(cellIndex = 1, gestureFinished = false))
        session.expire(cellIndex = 1)
        assertTrue(session.onTransform(cellIndex = 1, gestureFinished = false))
    }
}
