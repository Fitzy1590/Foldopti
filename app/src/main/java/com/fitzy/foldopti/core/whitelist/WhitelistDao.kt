package com.fitzy.foldopti.core.whitelist

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun toSource(v: String): ProtectedSource = ProtectedSource.valueOf(v)
    @TypeConverter fun fromSource(v: ProtectedSource): String = v.name
}

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM protected_app ORDER BY pkg")
    fun observeAll(): Flow<List<ProtectedApp>>

    @Query("SELECT COUNT(*) > 0 FROM protected_app WHERE pkg = :pkg")
    suspend fun contains(pkg: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: ProtectedApp)

    @Delete
    suspend fun delete(app: ProtectedApp)
}

@Database(entities = [ProtectedApp::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FoldOptiDatabase : RoomDatabase() {
    abstract fun whitelistDao(): WhitelistDao
}
