package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<RecordItem>>

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): RecordItem?

    @Query("SELECT * FROM records ORDER BY createdAt DESC LIMIT 1")
    fun getLatestRecord(): Flow<RecordItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<RecordItem>)

    @Delete
    suspend fun deleteRecord(record: RecordItem)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteRecordById(id: String)

    @Query("DELETE FROM records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM records")
    suspend fun getCount(): Int
}
