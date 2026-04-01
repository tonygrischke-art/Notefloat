package com.notefloat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String = "",
    val color: String = "#FFFFFF",
    val icon: String = "edit",
    val positionX: Int = 100,
    val positionY: Int = 300,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val isLocked: Boolean = false,
    val transparency: Float = 1.0f,
    val isChecklist: Boolean = false,
    val checklistItems: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val scheduledTime: Long? = null,
    val reminderTime: Long? = null
)

data class ChecklistItem(
    val text: String,
    val isChecked: Boolean = false
)
