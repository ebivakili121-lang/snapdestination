package com.example.snapscanner

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SnapAccessibilityService : AccessibilityService() {

    private val captured = mutableSetOf<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        // فقط هنگام تغییر صفحه یا اسکرول بررسی می‌کنیم
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val root = rootInActiveWindow ?: return

        val barcodes = mutableListOf<Pair<Int, String>>()

        readNodes(root, barcodes)

        // مرتب‌سازی از بالای صفحه به پایین
        val sortedBarcodes = barcodes
            .distinctBy { it.second }
            .sortedBy { it.first }

        for ((_, barcode) in sortedBarcodes) {
            if (captured.add(barcode)) {
                BarcodeStore.assign(barcode)
            }
        }
    }

    private fun readNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<Pair<Int, String>>
    ) {
        val text = node.text?.toString()?.trim()

        if (!text.isNullOrEmpty()) {

            val normalized = BarcodeStore.normalize(text)

            if (normalized.length in 6..20 &&
                normalized.all { it.isDigit() }
            ) {
                val position = IntArray(2)
                node.getBoundsInScreen(
                    android.graphics.Rect().also {
                        node.getBoundsInScreen(it)
                        position[1] = it.top
                    }
                )

                result.add(position[1] to normalized)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            readNodes(child, result)
            child.recycle()
        }
    }

    override fun onInterrupt() {
        // هنگام قطع سرویس کاری انجام نمی‌دهیم
    }
}