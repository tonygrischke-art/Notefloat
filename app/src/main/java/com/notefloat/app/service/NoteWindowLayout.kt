package com.notefloat.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.notefloat.app.R
import com.notefloat.app.data.Note
import com.notefloat.app.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteWindowLayout(
    context: Context,
    private var note: Note,
    private val repository: NoteRepository,
    private val windowManager: WindowManager,
    private val onDelete: (Long) -> Unit
) : FrameLayout(context) {

    private val editText: EditText
    private val tvWordCount: TextView
    private val btnClose: ImageButton
    private val btnMenu: ImageButton
    private val colorBar: LinearLayout
    private val iconBar: LinearLayout
    private val cardView: CardView
    
    private var isDragging = false
    private var isMenuOpen = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    private val colors = listOf("#FFFFFF", "#FFE4E1", "#FFDAB9", "#FFFACD", "#98FB98", "#87CEEB", "#DDA0DD", "#F0E68C", "#FFB6C1", "#FF4081")
    private val icons = mapOf(
        "edit" to android.R.drawable.ic_menu_edit,
        "agenda" to android.R.drawable.ic_menu_agenda,
        "today" to android.R.drawable.ic_menu_today,
        "calendar" to android.R.drawable.ic_menu_my_calendar,
        "add" to android.R.drawable.ic_menu_add,
        "send" to android.R.drawable.ic_menu_send,
        "share" to android.R.drawable.ic_menu_share,
        "info" to android.R.drawable.ic_menu_info_details
    )
    
    private var currentColorIndex = colors.indexOf(note.color).coerceAtLeast(0)
    private var currentIcon = note.icon

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_note_window, this, true)
        
        cardView = findViewById(R.id.cardNote)
        editText = findViewById(R.id.editNote)
        tvWordCount = findViewById(R.id.tvWordCount)
        btnClose = findViewById(R.id.btnClose)
        btnMenu = findViewById(R.id.btnMenu)
        colorBar = findViewById(R.id.colorBar)
        iconBar = findViewById(R.id.iconBar)
        
        setupNote()
        setupTouchListener()
        setupButtons()
        setupColorPicker()
        setupIconPicker()
    }

    private fun setupNote() {
        editText.setText(note.text)
        updateColor()
        updateCounts()
        
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                updateCounts()
                saveNote(text)
            }
        })
    }

    private fun setupTouchListener() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = layoutParams as WindowManager.LayoutParams
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (abs(deltaX) > 5 || abs(deltaY) > 5) {
                        isDragging = true
                        val params = layoutParams as WindowManager.LayoutParams
                        params.x = (initialX + deltaX).toInt()
                        params.y = (initialY + deltaY).toInt()
                        windowManager.updateViewLayout(this, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val params = layoutParams as WindowManager.LayoutParams
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.updatePosition(note.id, params.x, params.y)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        btnClose.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                repository.trashNote(note.id)
            }
            onDelete(note.id)
        }
        
        btnMenu.setOnClickListener {
            toggleMenu()
        }
        
        btnMenu.setOnLongClickListener {
            showIconPicker()
            true
        }
    }

    private fun toggleMenu() {
        isMenuOpen = !isMenuOpen
        colorBar.visibility = if (isMenuOpen) View.VISIBLE else View.GONE
        iconBar.visibility = View.GONE
    }

    private fun showColorPicker() {
        isMenuOpen = true
        colorBar.visibility = View.VISIBLE
        iconBar.visibility = View.GONE
    }

    private fun showIconPicker() {
        isMenuOpen = true
        iconBar.visibility = View.VISIBLE
        colorBar.visibility = View.GONE
    }

    private fun setupColorPicker() {
        colorBar.removeAllViews()
        colors.forEachIndexed { index, color ->
            val colorView = View(context).apply {
                setBackgroundColor(Color.parseColor(color))
                setOnClickListener {
                    currentColorIndex = index
                    updateColor()
                    saveNote(editText.text.toString())
                    Toast.makeText(context, "Color: ${index + 1}", Toast.LENGTH_SHORT).show()
                }
            }
            colorView.layoutParams = LayoutParams(48, 48).apply {
                marginEnd = 8
            }
            colorBar.addView(colorView)
        }
    }

    private fun setupIconPicker() {
        iconBar.removeAllViews()
        icons.forEach { (name, iconRes) ->
            val iconView = ImageView(context).apply {
                setImageResource(iconRes)
                setOnClickListener {
                    currentIcon = name
                    updateIcon()
                    saveNote(editText.text.toString())
                    Toast.makeText(context, "Icon: $name", Toast.LENGTH_SHORT).show()
                }
            }
            iconView.layoutParams = LayoutParams(64, 64).apply {
                marginEnd = 8
            }
            iconBar.addView(iconView)
        }
    }

    private fun updateColor() {
        try {
            cardView.setCardBackgroundColor(Color.parseColor(colors[currentColorIndex]))
        } catch (e: Exception) {
            cardView.setCardBackgroundColor(Color.WHITE)
        }
    }

    private fun updateIcon() {
        // Icon update is visual only in bubble
    }

    private fun updateCounts() {
        val text = editText.text.toString()
        val words = text.trim().split("\\s+").filter { it.isNotEmpty() }.size
        tvWordCount.text = "$words words • ${text.length} chars"
    }

    private fun saveNote(text: String) {
        note = note.copy(
            text = text,
            color = colors.getOrElse(currentColorIndex) { "#FFFFFF" },
            icon = currentIcon,
            modifiedAt = System.currentTimeMillis()
        )
        CoroutineScope(Dispatchers.IO).launch {
            repository.updateNote(note)
        }
    }

    fun setTransparency(alpha: Float) {
        this.alpha = alpha
    }
}
