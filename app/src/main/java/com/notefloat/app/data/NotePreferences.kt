package com.notefloat.app.data

import android.content.Context
import android.content.SharedPreferences

class NotePreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "NoteFloatPrefs"
        private const val KEY_NOTE_TEXT = "note_text"
        private const val KEY_NOTE_COLOR = "note_color"
        private const val KEY_NOTE_ICON = "note_icon"
        private const val KEY_TRANSPARENCY = "transparency"
        private const val KEY_CHECKLIST_MODE = "checklist_mode"
        private const val KEY_NOTE_POSITION_X = "position_x"
        private const val KEY_NOTE_POSITION_Y = "position_y"
    }
    
    fun getNoteText(): String = prefs.getString(KEY_NOTE_TEXT, "") ?: ""
    
    fun saveNoteText(text: String) {
        prefs.edit().putString(KEY_NOTE_TEXT, text).apply()
    }
    
    fun getNoteColor(): String = prefs.getString(KEY_NOTE_COLOR, "#FFFFFF") ?: "#FFFFFF"
    
    fun saveNoteColor(color: String) {
        prefs.edit().putString(KEY_NOTE_COLOR, color).apply()
    }
    
    fun getNoteIcon(): String = prefs.getString(KEY_NOTE_ICON, "edit") ?: "edit"
    
    fun saveNoteIcon(icon: String) {
        prefs.edit().putString(KEY_NOTE_ICON, icon).apply()
    }
    
    fun getTransparency(): Float = prefs.getFloat(KEY_TRANSPARENCY, 1.0f)
    
    fun saveTransparency(transparency: Float) {
        prefs.edit().putFloat(KEY_TRANSPARENCY, transparency).apply()
    }
    
    fun isChecklistMode(): Boolean = prefs.getBoolean(KEY_CHECKLIST_MODE, false)
    
    fun saveChecklistMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHECKLIST_MODE, enabled).apply()
    }
    
    fun getPositionX(): Int = prefs.getInt(KEY_NOTE_POSITION_X, 100)
    
    fun getPositionY(): Int = prefs.getInt(KEY_NOTE_POSITION_Y, 300)
    
    fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_NOTE_POSITION_X, x)
            .putInt(KEY_NOTE_POSITION_Y, y)
            .apply()
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
