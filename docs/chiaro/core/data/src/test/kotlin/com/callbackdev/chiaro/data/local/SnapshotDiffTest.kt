package com.callbackdev.chiaro.data.local

import com.callbackdev.chiaro.data.local.SnapshotDiff.Line
import com.callbackdev.chiaro.data.local.SnapshotDiff.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotDiffTest {

    @Test
    fun `changed key emits old value as removed then new as added`() {
        val diff = SnapshotDiff.compute(
            previous = mapOf("temp" to "18.2", "status" to "Clear ☀️"),
            current = mapOf("temp" to "19.5", "status" to "Clear ☀️")
        )
        assertEquals(
            listOf(
                Line(Type.REMOVED, "temp", "18.2"),
                Line(Type.ADDED, "temp", "19.5"),
                Line(Type.CONTEXT, "status", "Clear ☀️")
            ),
            diff
        )
    }

    @Test
    fun `no previous snapshot marks every line added`() {
        val diff = SnapshotDiff.compute(
            previous = null,
            current = mapOf("a" to "1", "b" to "2")
        )
        assertEquals(listOf(Line(Type.ADDED, "a", "1"), Line(Type.ADDED, "b", "2")), diff)
    }

    @Test
    fun `new key is added and vanished key trails as removed`() {
        val diff = SnapshotDiff.compute(
            previous = mapOf("kept" to "x", "dropped" to "old"),
            current = mapOf("kept" to "x", "fresh" to "new")
        )
        assertEquals(
            listOf(
                Line(Type.CONTEXT, "kept", "x"),
                Line(Type.ADDED, "fresh", "new"),
                Line(Type.REMOVED, "dropped", "old")
            ),
            diff
        )
    }

    @Test
    fun `identical snapshots are all context`() {
        val snapshot = mapOf("a" to "1")
        assertEquals(
            listOf(Line(Type.CONTEXT, "a", "1")),
            SnapshotDiff.compute(snapshot, snapshot)
        )
    }
}
