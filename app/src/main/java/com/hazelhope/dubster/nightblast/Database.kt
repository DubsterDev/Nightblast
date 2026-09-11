package com.hazelhope.dubster.nightblast

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "public_keys")
data class Key(
    @PrimaryKey @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "public_key") val publicKey: String
)

@Entity(tableName = "alert_history")
data class AlertHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    val priority: Int,
    val message: String,
    val time: Long
)

@Dao
interface KeyDao {
    @Query("SELECT * FROM public_keys")
    suspend fun getAll(): List<Key>

    @Query(
        """
        SELECT * FROM public_keys
        WHERE phone_number = :phoneNumber LIMIT 1
        """
    )
    suspend fun findByNumber(phoneNumber: String): Key?

    @Upsert
    fun upsertKey(key: Key)

    @Delete
    suspend fun delete(key: Key)
}

@Dao
interface AlertHistoryDao {
    @Query("SELECT * FROM alert_history ORDER BY time DESC")
    suspend fun getAll(): List<AlertHistory>

    @Insert
    fun insert(alert: AlertHistory)

    @Delete
    suspend fun delete(alert: AlertHistory)
}

@Database(entities = [Key::class, AlertHistory::class], version = 1)
abstract class NightblastDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
    abstract fun alertHistoryDao(): AlertHistoryDao
}