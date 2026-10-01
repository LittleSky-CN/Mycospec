package org.fungalsentinel.app.data.preset

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object PresetTypes {
    const val WAVELENGTH = "WAVELENGTH"
    const val SPD = "SPD"
}

/**
 * Reusable calibration preset.
 * Stored in a SEPARATE database so the main experiment DB needs no migration.
 */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,                 // PresetTypes.WAVELENGTH | PresetTypes.SPD
    val deviceModel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Wavelength fields
    val slope: Double? = null,
    val intercept: Double? = null,
    val gErrorNm: Double? = null,
    val quality: String? = null,
    val roiStart: Int? = null,
    val roiEnd: Int? = null,
    val sensorWidth: Int? = null,
    val sensorHeight: Int? = null,
    val peakR: Int? = null,
    val peakG: Int? = null,
    val peakB: Int? = null,
    val wlR: Double? = null,
    val wlG: Double? = null,
    val wlB: Double? = null,
    val exposureSeconds: Double? = null,
    // SPD fields
    val coeffR: Double? = null,
    val coeffB: Double? = null,
    val frames: Int? = null,
    val spdSource: String? = null
)

@Dao
interface PresetDao {
    @Insert
    suspend fun insert(p: PresetEntity): Long

    @Update
    suspend fun update(p: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM presets WHERE type = :type ORDER BY updatedAt DESC")
    fun observeByType(type: String): Flow<List<PresetEntity>>
}

@Database(entities = [PresetEntity::class], version = 1, exportSchema = false)
abstract class PresetDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile
        private var INSTANCE: PresetDatabase? = null

        fun getInstance(context: Context): PresetDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PresetDatabase::class.java,
                    "fssa_presets"
                ).build().also { INSTANCE = it }
            }
    }
}