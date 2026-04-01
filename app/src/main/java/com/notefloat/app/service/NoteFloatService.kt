package com.notefloat.app.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.notefloat.app.MainActivity
import com.notefloat.app.R
import com.notefloat.app.data.Note
import com.notefloat.app.data.NoteDatabase
import com.notefloat.app.data.NoteRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class NoteFloatService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "NoteFloatChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_ADD_NOTE = "com.notefloat.ACTION_ADD_NOTE"
        const val ACTION_TOGGLE_VISIBILITY = "com.notefloat.ACTION_TOGGLE_VISIBILITY"
        const val ACTION_HIDE_ALL = "com.notefloat.ACTION_HIDE_ALL"
        
        var isRunning = false
            private set
        
        fun start(context: Context) {
            val intent = Intent(context, NoteFloatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, NoteFloatService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private val floatingNotes = mutableMapOf<Long, View>()
    private val noteWindows = mutableMapOf<Long, NoteWindowLayout>()
    
    private lateinit var repository: NoteRepository
    private var isNotesVisible = true
    private var bubbleView: View? = null
    private var menuView: View? = null
    private var isMenuOpen = false
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val db = NoteDatabase.getDatabase(this)
        repository = NoteRepository(db.noteDao())
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification())
        
        when (intent?.action) {
            ACTION_ADD_NOTE -> addNewNote()
            ACTION_TOGGLE_VISIBILITY -> toggleVisibility()
            ACTION_HIDE_ALL -> hideAllNotes()
            else -> {
                showBubble()
                loadNotes()
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NoteFloat Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Floating notes service"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val addIntent = Intent(this, NoteFloatService::class.java).apply {
            action = ACTION_ADD_NOTE
        }
        val pendingAddIntent = PendingIntent.getService(
            this, 1, addIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val toggleIntent = Intent(this, NoteFloatService::class.java).apply {
            action = ACTION_TOGGLE_VISIBILITY
        }
        val pendingToggleIntent = PendingIntent.getService(
            this, 2, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, NoteFloatService::class.java)
        val pendingStopIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NoteFloat")
            .setContentText("${floatingNotes.size} notes visible")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingMainIntent)
            .addAction(android.R.drawable.ic_menu_add, "Add", pendingAddIntent)
            .addAction(android.R.drawable.ic_menu_view, "Toggle", pendingToggleIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showBubble() {
        if (bubbleView != null) return

        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_bubble, null)
        
        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = 100
                    initialY = 300
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
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        showMenu()
                    }
                    true
                }
                else -> false
            }
        }

        val params = WindowManager.LayoutParams(
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

        bubbleView?.let {
            windowManager.addView(it, params)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showMenu() {
        if (menuView != null) {
            hideMenu()
            return
        }
        
        isMenuOpen = true
        
        menuView = LayoutInflater.from(this).inflate(R.layout.layout_menu, null)
        
        menuView?.findViewById<Button>(R.id.btnAddNote)?.setOnClickListener {
            addNewNote()
            hideMenu()
        }
        
        menuView?.findViewById<Button>(R.id.btnToggle)?.setOnClickListener {
            toggleVisibility()
            hideMenu()
        }
        
        menuView?.findViewById<Button>(R.id.btnHideAll)?.setOnClickListener {
            hideAllNotes()
            hideMenu()
        }
        
        menuView?.findViewById<Button>(R.id.btnStop)?.setOnClickListener {
            stopSelf()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        menuView?.let {
            it.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hideMenu()
                    true
                } else false
            }
            windowManager.addView(it, params)
        }
    }

    private fun hideMenu() {
        isMenuOpen = false
        menuView?.let {
            windowManager.removeView(it)
            menuView = null
        }
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            repository.allNotes.collectLatest { notes ->
                notes.forEach { note ->
                    if (!floatingNotes.containsKey(note.id)) {
                        createNoteView(note)
                    }
                }
                floatingNotes.keys.filter { id -> notes.none { it.id == id } }.forEach { id ->
                    removeNoteView(id)
                }
                updateNotification()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createNoteView(note: Note) {
        if (!isNotesVisible) return
        
        val noteWindow = NoteWindowLayout(this, note, repository, windowManager) { id ->
            removeNoteView(id)
        }
        
        noteWindows[note.id] = noteWindow
        floatingNotes[note.id] = noteWindow

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = note.positionX
            y = note.positionY
            alpha = note.transparency
        }

        windowManager.addView(noteWindow, params)
    }

    private fun removeNoteView(noteId: Long) {
        floatingNotes[noteId]?.let {
            windowManager.removeView(it)
        }
        floatingNotes.remove(noteId)
        noteWindows.remove(noteId)
        updateNotification()
    }

    private fun addNewNote() {
        lifecycleScope.launch {
            val newNote = Note(
                text = "",
                color = "#FFFFFF",
                icon = "edit",
                positionX = (100..500).random(),
                positionY = (100..800).random()
            )
            val id = repository.insertNote(newNote)
            val savedNote = repository.getNoteById(id)
            if (savedNote != null) {
                createNoteView(savedNote)
            }
        }
    }

    private fun toggleVisibility() {
        isNotesVisible = !isNotesVisible
        floatingNotes.values.forEach { view ->
            view.visibility = if (isNotesVisible) View.VISIBLE else View.GONE
        }
    }

    private fun hideAllNotes() {
        floatingNotes.values.forEach { view ->
            view.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        hideMenu()
        floatingNotes.values.forEach { windowManager.removeView(it) }
        floatingNotes.clear()
        noteWindows.clear()
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }
}
