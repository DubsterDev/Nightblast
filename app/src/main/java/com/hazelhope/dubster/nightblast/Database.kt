package com.hazelhope.dubster.nightblast

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "public_keys")
data class Key(
    @PrimaryKey @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "public_key") val publicKey: String
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

@Database(entities = [Key::class], version = 1)
abstract class NightblastDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
}