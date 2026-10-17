package org.fungalsentinel.app.data.preset

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

object PresetTypes {
    const val WAVELENGTH = "WAVELENGTH"
    const val SPD = "SPD"
    const val CURVE = "CURVE"
}

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val deviceModel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val slope: Double? = null, val intercept: Double? = null, val gErrorNm: Double? = null,
    val quality: String? = null, val roiStart: Int? = null, val roiEnd: Int? = null,
    val sensorWidth: Int? = null, val sensorHeight: Int? = null,
    val peakR: Int? = null, val peakG: Int? = null, val peakB: Int? = null,
    val wlR: Double? = null, val wlG: Double? = null, val wlB: Double? = null,
    val exposureSeconds: Double? = null,
    /** 【v2.2.6】R/B 通道自动交换结论（DEF-A01）。Room 映射为 INTEGER，DB version 3 起存在。 */
    val rbSwapped: Boolean? = null,
    val coeffR: Double? = null, val coeffB: Double? = null,
    val frames: Int? = null, val spdSource: String? = null,
    val payloadJson: String? = null      // Full serialized SPD calibration (v1.4)
)

@Dao
interface PresetDao {
    @Insert suspend fun insert(p: PresetEntity): Long
    @Update suspend fun update(p: PresetEntity)
    @Query("DELETE FROM presets WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM presets WHERE type = :type ORDER BY updatedAt DESC")
    fun observeByType(type: String): Flow<List<PresetEntity>>
}

@Database(entities = [PresetEntity::class], version = 3, exportSchema = false)
abstract class PresetDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile private var INSTANCE: PresetDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE presets ADD COLUMN payloadJson TEXT DEFAULT NULL")
            }
        }

        /** 【v2.2.6】DEF-A01：WAVELENGTH preset 必须持久化 rbSwapped，否则重载后恒为 false。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE presets ADD COLUMN rbSwapped INTEGER DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): PresetDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, PresetDatabase::class.java, "fssa_presets")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}