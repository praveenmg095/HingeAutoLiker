package com.example.hingeauto

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        finish()
    }
}

class HingeAutoService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var count = 0

    companion object {
        const val HINGE_PKG = "co.hinge.app"
        const val COMMENT = "Heyy.. let's connect and talk"
        const val MAX_LIKES = 8
        const val NEXT_PROFILE_DELAY = 2000L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != HINGE_PKG) return
        if (!running && count < MAX_LIKES) {
            running = true
            handler.postDelayed({ step() }, 1000)
        }
    }

    private fun step() {
        if (!running) return
        val root = rootInActiveWindow
        if (root == null || root.packageName != HINGE_PKG || count >= MAX_LIKES) {
            running = false
            return
        }
        val sendBtn = find(root, "Send Like")
        if (sendBtn != null) {
            findEditText(root)?.let {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, COMMENT
                    )
                }
                it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            handler.postDelayed({
                rootInActiveWindow?.let { r -> find(r, "Send Like")?.let { click(it) } }
                count++
                handler.postDelayed({ step() }, NEXT_PROFILE_DELAY)
            }, 600)
        } else {
            val heart = find(root, "Like")
            if (heart != null) click(heart)
            handler.postDelayed({ step() }, 1000)
        }
    }

    private fun find(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByText(label)?.firstOrNull()?.let { return it }
        return searchDesc(root, label)
    }

    private fun searchDesc(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.contains(label, true) == true) return node
        for (i in 0 until node.childCount) {
            searchDesc(node.getChild(i), label)?.let { return it }
        }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            findEditText(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() { running = false }
    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }
}
