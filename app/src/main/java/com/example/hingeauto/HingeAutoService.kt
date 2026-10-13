package com.example.hingeauto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
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
    private var attempts = 0
    private var noHeart = 0
    private var count = 0

    companion object {
        const val HINGE_PKG = "co.hinge.app"
        const val MAX_LIKES = 8
        const val NEXT_PROFILE_DELAY = 2000L
        // Fallback tap position of "Send like" (fraction of screen)
        const val SEND_X = 0.59f
        const val SEND_Y = 0.725f
    }

    override fun onServiceConnected() {
        running = false; halted = false; heartClicked = false
        attempts = 0; noHeart = 0; count = 0
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != HINGE_PKG) return
        if (!running && !halted && count < MAX_LIKES) {
            running = true
            handler.postDelayed({ step() }, 1000)
        }
    }

    private fun allRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        try { for (w in windows) w.root?.let { list.add(it) } } catch (e: Exception) { }
        rootInActiveWindow?.let { list.add(it) }
        return list
    }

    private fun step() {
        if (!running) return
        val roots = allRoots()
        if (roots.none { it.packageName == HINGE_PKG } || count >= MAX_LIKES) {
            running = false
            return
        }

        val sendBtn = roots.firstNotNullOfOrNull { findSend(it) }
        if (sendBtn != null) {
            clickNode(sendBtn)
            finishLike()
        } else if (heartClicked) {
            attempts++
            if (attempts >= 2) {
                // Send button not visible to the service: tap its known position
                val dm = resources.displayMetrics
                tap(dm.widthPixels * SEND_X, dm.heightPixels * SEND_Y)
                finishLike()
            } else {
                handler.postDelayed({ step() }, 1000)
            }
        } else {
            val heart = roots.firstNotNullOfOrNull { findHeart(it) }
            if (heart != null) {
                clickNode(heart)
                heartClicked = true
                attempts = 0
                noHeart = 0
            } else {
                noHeart++
                if (noHeart >= 3) {
                    dumpScreen(roots)
                    halted = true
                    running = false
                    return
                }
            }
            handler.postDelayed({ step() }, 1500)
        }
    }

    private fun finishLike() {
        count++
        heartClicked = false
        attempts = 0
        noHeart = 0
        handler.postDelayed({ step() }, NEXT_PROFILE_DELAY)
    }

    private fun label(n: AccessibilityNodeInfo): String =
        ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).lowercase()

    // Finds the "Send like" button. Never matches the rose button.
    private fun findSend(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val l = label(node)
        if (l.contains("send") && l.contains("like") && !l.contains("rose")) return node
        for (i in 0 until node.childCount) findSend(node.getChild(i))?.let { return it }
        return null
    }

    private fun findHeart(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val l = label(node)
        if (l.contains("like") && !l.contains("likes") && !l.contains("send") && !l.contains("rose")) return node
        for (i in 0 until node.childCount) findHeart(node.getChild(i))?.let { return it }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        val ok = n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        if (!ok) {
            val r = Rect()
            node.getBoundsInScreen(r)
            tap(r.exactCenterX(), r.exactCenterY())
        }
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun dumpScreen(roots: List<AccessibilityNodeInfo>) {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || sb.length > 9000) return
            if (n.text != null || n.contentDescription != null || n.isClickable) {
                sb.append("  ".repeat(d)).append(n.className)
                    .append(" | text=").append(n.text)
                    .append(" | desc=").append(n.contentDescription)
                    .append(" | click=").append(n.isClickable).append("\n")
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        roots.forEachIndexed { i, r -> sb.append("=== WINDOW $i ===\n"); walk(r, 0) }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("hinge-dump", sb.toString()))
        Toast.makeText(this, "Screen info copied. Paste it to Claude.", Toast.LENGTH_LONG).show()
    }

    override fun onInterrupt() { running = false }

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }
}
