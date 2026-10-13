package com.example.hingeauto

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

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
    private var halted = false
    private var heartClicked = false
    private var count = 0

    companion object {
        const val HINGE_PKG = "co.hinge.app"
        const val COMMENT = "Heyy.. let's connect and talk"
        const val MAX_LIKES = 8
        const val NEXT_PROFILE_DELAY = 2000L
    }

    override fun onServiceConnected() {
        halted = false
        running = false
        count = 0
        heartClicked = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != HINGE_PKG) return
        if (!running && !halted && count < MAX_LIKES) {
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

        val sendBtn = find(root, "Send")
        if (sendBtn != null) {
            // Focus the comment field first
            val field = findEditText(root)
            field?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            field?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            handler.postDelayed({
                val r = rootInActiveWindow
                val f = findEditText(r)
                if (f != null) {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, COMMENT
                        )
                    }
                    f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
                handler.postDelayed({
                    val r2 = rootInActiveWindow
                    if (r2 != null) {
                        find(r2, "Send")?.let { click(it) }
                    }
                    count++
                    heartClicked = false
                    handler.postDelayed({ step() }, NEXT_PROFILE_DELAY)
                }, 700)
            }, 500)
        } else if (heartClicked) {
            // Heart was tapped but no Send button found: dump the screen for debugging
            dumpScreen(root)
            halted = true
            running = false
        } else {
            val heart = find(root, "Like")
            if (heart != null) {
                click(heart)
                heartClicked = true
            }
            handler.postDelayed({ step() }, 1500)
        }
    }

    private fun dumpScreen(root: AccessibilityNodeInfo) {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || sb.length > 6000) return
            sb.append("  ".repeat(depth))
                .append(n.className).append(" | text=").append(n.text)
                .append(" | desc=").append(n.contentDescription)
                .append(" | id=").append(n.viewIdResourceName)
                .append(" | click=").append(n.isClickable)
                .append(" | edit=").append(n.isEditable)
                .append("\n")
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("hinge-dump", sb.toString()))
        Toast.makeText(this, "Screen info copied. Paste it to Claude.", Toast.LENGTH_LONG).show()
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
        if (node.isEditable || node.className?.toString()?.contains("EditText") == true) return node
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
