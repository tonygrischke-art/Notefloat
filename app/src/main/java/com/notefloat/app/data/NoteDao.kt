package com.notefloat.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isTrashed = 0 ORDER BY modifiedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY modifiedAt DESC")
    fun getArchivedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY modifiedAt DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("UPDATE notes SET isArchived = :archived, modifiedAt = :time WHERE id = :id")
    suspend fun archiveNote(id: Long, archived: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isTrashed = :trashed, modifiedAt = :time WHERE id = :id")
    suspend fun trashNote(id: Long, trashed: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET positionX = :x, positionY = :y WHERE id = :id")
    suspend fun updatePosition(id: Long, x: Int, y: Int)

    @Query("SELECT * FROM notes WHERE scheduledTime IS NOT NULL AND scheduledTime <= :currentTime")
    suspend fun getScheduledNotes(currentTime: Long): List<Note>
}
