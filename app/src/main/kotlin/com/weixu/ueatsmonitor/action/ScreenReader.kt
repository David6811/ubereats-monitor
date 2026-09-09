package com.weixu.ueatsmonitor.action

import android.view.accessibility.AccessibilityNodeInfo

/** Calculation. An accessibility node tree in, the visible strings out, top to bottom. */
object ScreenReader {

    fun readAll(root: AccessibilityNodeInfo?): List<String> {
        val collected = mutableListOf<String>()
        collect(root, collected, depth = 0)
        return collected
    }

    private fun collect(node: AccessibilityNodeInfo?, into: MutableList<String>, depth: Int) {
        if (node == null || depth > MAX_DEPTH) return
        val own = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        into += own
        for (index in 0 until node.childCount) {
            collect(node.getChild(index), into, depth + 1)
        }
    }

    private const val MAX_DEPTH = 40
}
