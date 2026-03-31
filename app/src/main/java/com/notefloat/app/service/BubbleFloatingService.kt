package com.notefloat.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.notefloat.app.MainActivity
import com.notefloat.app.R
import com.notefloat.app.ui.theme.Pink80
import kotlin.math.abs

class BubbleFloatingService : Service() {

    companion object {
        const val CHANNEL_ID = "NoteFloatChannel"
        const val NOTIFICATION_ID = 1
        
        private var bubbleView: View? = null
        private var expandedView: View? = null
        private var isExpanded = false
        private var isDragging = false
        
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        
        private var currentNoteText = ""
    }

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        showBubble()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NoteFloat Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Floating notepad service"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NoteFloat")
            .setContentText("Tap to open notepad")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showBubble() {
        if (bubbleView != null) return

        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.layout_bubble, null)
        
        val closeBtn = bubbleView?.findViewById<ImageButton>(R.id.btnClose)
        closeBtn?.setOnClickListener {
            stopSelf()
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        bubbleView?.let { view ->
            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                            isDragging = true
                        }
                        
                        if (isDragging) {
                            layoutParams.x = (initialX + deltaX).toInt()
                            layoutParams.y = (initialY + deltaY).toInt()
                            windowManager.updateViewLayout(view, layoutParams)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            showExpandedNotepad()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, layoutParams)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showExpandedNotepad() {
        if (expandedView != null) return
        
        isExpanded = true
        
        val inflater = LayoutInflater.from(this)
        expandedView = inflater.inflate(R.layout.layout_notepad, null)
        
        val editText = expandedView?.findViewById<android.widget.EditText>(R.id.editNote)
        val wordCount = expandedView?.findViewById<TextView>(R.id.tvWordCount)
        val charCount = expandedView?.findViewById<TextView>(R.id.tvCharCount)
        val btnClear = expandedView?.findViewById<ImageButton>(R.id.btnClear)
        val btnMinimize = expandedView?.findViewById<ImageButton>(R.id.btnMinimize)
        
        editText?.setText(currentNoteText)
        
        editText?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentNoteText = s?.toString() ?: ""
                updateCounts(s?.toString() ?: "", wordCount, charCount)
                saveNote(s?.toString() ?: "")
            }
        })
        
        updateCounts(currentNoteText, wordCount, charCount)
        
        btnClear?.setOnClickListener {
            editText?.text?.clear()
            currentNoteText = ""
        }
        
        btnMinimize?.setOnClickListener {
            hideExpandedNotepad()
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            width = (320 * resources.displayMetrics.density).toInt()
        }

        expandedView?.let { view ->
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hideExpandedNotepad()
                    true
                } else {
                    false
                }
            }
            windowManager.addView(view, layoutParams)
        }
    }

    private fun hideExpandedNotepad() {
        expandedView?.let {
            windowManager.removeView(it)
            expandedView = null
        }
        isExpanded = false
    }

    private fun updateCounts(text: String, wordCount: TextView?, charCount: TextView?) {
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        wordCount?.text = "Words: $words"
        charCount?.text = "Chars: ${text.length}"
    }

    private fun saveNote(text: String) {
        getSharedPreferences("NoteFloatPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("saved_note", text)
            .apply()
    }

    private fun loadNote(): String {
        return getSharedPreferences("NoteFloatPrefs", Context.MODE_PRIVATE)
            .getString("saved_note", "") ?: ""
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let {
            windowManager.removeView(it)
            bubbleView = null
        }
        expandedView?.let {
            windowManager.removeView(it)
            expandedView = null
        }
    }
}
