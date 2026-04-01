package com.notefloat.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.notefloat.app.MainActivity
import com.notefloat.app.R
import com.notefloat.app.data.NotePreferences
import kotlin.math.abs

class BubbleFloatingService : Service() {

    companion object {
        const val CHANNEL_ID = "NoteFloatChannel"
        const val NOTIFICATION_ID = 1
        
        private var bubbleView: View? = null
        private var expandedView: View? = null
        private var isExpanded = false
        private var isDragging = false
        private var showColorPicker = false
        private var showIconPicker = false
        
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        
        private var currentNoteText = ""
        private var currentColor = "#FFFFFF"
        private var currentIcon = "edit"
        private var isChecklistMode = false
        private var transparency = 1.0f
    }

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: NotePreferences

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        prefs = NotePreferences(this)
        createNotificationChannel()
        loadSettings()
    }

    private fun loadSettings() {
        currentNoteText = prefs.getNoteText()
        currentColor = prefs.getNoteColor()
        currentIcon = prefs.getNoteIcon()
        transparency = prefs.getTransparency()
        isChecklistMode = prefs.isChecklistMode()
    }

    private fun saveSettings() {
        prefs.saveNoteText(currentNoteText)
        prefs.saveNoteColor(currentColor)
        prefs.saveNoteIcon(currentIcon)
        prefs.saveTransparency(transparency)
        prefs.saveChecklistMode(isChecklistMode)
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
        
        updateBubbleIcon()

        val closeBtn = bubbleView?.findViewById<ImageButton>(R.id.btnClose)
        closeBtn?.setOnClickListener {
            saveSettings()
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

    private fun updateBubbleIcon() {
        val iconView = bubbleView?.findViewById<ImageView>(R.id.ivBubbleIcon)
        val drawable = when (currentIcon) {
            "edit" -> android.R.drawable.ic_menu_edit
            "agenda" -> android.R.drawable.ic_menu_agenda
            "today" -> android.R.drawable.ic_menu_today
            "calendar" -> android.R.drawable.ic_menu_my_calendar
            "add" -> android.R.drawable.ic_menu_add
            "send" -> android.R.drawable.ic_menu_send
            "share" -> android.R.drawable.ic_menu_share
            "info" -> android.R.drawable.ic_menu_info_details
            else -> android.R.drawable.ic_menu_edit
        }
        iconView?.setImageResource(drawable)
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams", "SetTextI18n")
    private fun showExpandedNotepad() {
        if (expandedView != null) return
        
        isExpanded = true
        showColorPicker = false
        showIconPicker = false
        
        val inflater = LayoutInflater.from(this)
        expandedView = inflater.inflate(R.layout.layout_notepad, null)
        
        val card = expandedView?.findViewById<View>(R.id.cardNote)
        val editText = expandedView?.findViewById<EditText>(R.id.editNote)
        val wordCount = expandedView?.findViewById<TextView>(R.id.tvWordCount)
        val charCount = expandedView?.findViewById<TextView>(R.id.tvCharCount)
        val tvTransparency = expandedView?.findViewById<TextView>(R.id.tvTransparency)
        val seekTransparency = expandedView?.findViewById<SeekBar>(R.id.seekTransparency)
        val btnClose = expandedView?.findViewById<ImageButton>(R.id.btnClose)
        val btnMinimize = expandedView?.findViewById<ImageButton>(R.id.btnMinimize)
        val btnMenu = expandedView?.findViewById<ImageButton>(R.id.btnMenu)
        val ivIcon = expandedView?.findViewById<ImageView>(R.id.ivIcon)
        val colorPicker = expandedView?.findViewById<View>(R.id.colorPicker)
        val iconPicker = expandedView?.findViewById<View>(R.id.iconPicker)
        val cbChecklist = expandedView?.findViewById<CheckBox>(R.id.cbChecklist)
        
        editText?.setText(currentNoteText)
        
        if (currentColor.isNotEmpty()) {
            try {
                card?.setBackgroundColor(Color.parseColor(currentColor))
            } catch (e: Exception) { }
        }
        
        ivIcon?.setImageResource(when (currentIcon) {
            "edit" -> android.R.drawable.ic_menu_edit
            "agenda" -> android.R.drawable.ic_menu_agenda
            "today" -> android.R.drawable.ic_menu_today
            "calendar" -> android.R.drawable.ic_menu_my_calendar
            else -> android.R.drawable.ic_menu_edit
        })
        
        tvTransparency?.text = "${(transparency * 100).toInt()}%"
        seekTransparency?.progress = (transparency * 100).toInt()
        
        cbChecklist?.isChecked = isChecklistMode
        cbChecklist?.setOnCheckedChangeListener { _, isChecked ->
            isChecklistMode = isChecked
            editText?.inputType = if (isChecked) {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        }
        
        setupColorPicker(colorPicker, card, editText)
        setupIconPicker(iconPicker, ivIcon)
        
        btnMenu?.setOnClickListener {
            showColorPicker = !showColorPicker
            showIconPicker = false
            colorPicker?.visibility = if (showColorPicker) View.VISIBLE else View.GONE
            iconPicker?.visibility = View.GONE
        }
        
        btnMenu?.setOnLongClickListener {
            showIconPicker = !showIconPicker
            showColorPicker = false
            iconPicker?.visibility = if (showIconPicker) View.VISIBLE else View.GONE
            colorPicker?.visibility = View.GONE
            true
        }
        
        seekTransparency?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                transparency = progress / 100f
                tvTransparency?.text = "$progress%"
                card?.alpha = transparency
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                expandedView?.alpha = transparency
            }
        })
        
        editText?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentNoteText = s?.toString() ?: ""
                updateCounts(currentNoteText, wordCount, charCount)
                saveNote(currentNoteText)
            }
        })
        
        updateCounts(currentNoteText, wordCount, charCount)
        
        btnClose?.setOnClickListener {
            saveSettings()
            stopSelf()
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
            alpha = transparency
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

    private fun setupColorPicker(colorPicker: View?, card: View?, editText: EditText?) {
        colorPicker?.let { picker ->
            for (i in 0 until picker.childCount) {
                val colorView = picker.getChildAt(i)
                colorView.setOnClickListener {
                    val color = colorView.tag as? String ?: "#FFFFFF"
                    currentColor = color
                    try {
                        card?.setBackgroundColor(Color.parseColor(color))
                    } catch (e: Exception) { }
                    saveNote(currentNoteText)
                    Toast.makeText(this, "Color changed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupIconPicker(iconPicker: View?, ivIcon: ImageView?) {
        iconPicker?.let { picker ->
            for (i in 0 until picker.childCount) {
                val iconView = picker.getChildAt(i) as? ImageView
                iconView?.setOnClickListener {
                    val icon = iconView.tag as? String ?: "edit"
                    currentIcon = icon
                    ivIcon?.setImageResource(when (icon) {
                        "edit" -> android.R.drawable.ic_menu_edit
                        "agenda" -> android.R.drawable.ic_menu_agenda
                        "today" -> android.R.drawable.ic_menu_today
                        "calendar" -> android.R.drawable.ic_menu_my_calendar
                        "add" -> android.R.drawable.ic_menu_add
                        "send" -> android.R.drawable.ic_menu_send
                        "share" -> android.R.drawable.ic_menu_share
                        "info" -> android.R.drawable.ic_menu_info_details
                        else -> android.R.drawable.ic_menu_edit
                    })
                    updateBubbleIcon()
                    saveNote(currentNoteText)
                    Toast.makeText(this, "Icon changed", Toast.LENGTH_SHORT).show()
                }
            }
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
        prefs.saveNoteText(text)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
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
