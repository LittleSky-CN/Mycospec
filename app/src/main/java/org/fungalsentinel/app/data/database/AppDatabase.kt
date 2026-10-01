package org.fungalsentinel.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fungalsentinel.app.data.dao.ProjectDao
import org.fungalsentinel.app.data.model.CalibrationEntity
import org.fungalsentinel.app.data.model.CaptureEntity
import org.fungalsentinel.app.data.model.ProjectEntity
import org.fungalsentinel.app.data.model.ResultEntity
import org.fungalsentinel.app.data.model.StandardGroupEntity

@Database(
    entities = [
        ProjectEntity::class,
        CaptureEntity::class,
        CalibrationEntity::class,
        StandardGroupEntity::class,
        ResultEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 删除旧表（开发阶段简化处理）
                db.execSQL("DROP TABLE IF EXISTS captures")
                db.execSQL("DROP TABLE IF EXISTS results")
                db.execSQL("DROP TABLE IF EXISTS standard_groups")
                db.execSQL("DROP TABLE IF EXISTS projects")

                // 创建新表
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `projects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `fluorophore` TEXT NOT NULL DEFAULT 'EGFP',
                        `createdAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `status` TEXT NOT NULL DEFAULT 'IN_PROGRESS'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `captures` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `step` TEXT NOT NULL,
                        `standardGroupId` INTEGER,
                        `uri` TEXT NOT NULL,
                        `exposureTimeNs` INTEGER NOT NULL DEFAULT 0,
                        `iso` INTEGER NOT NULL DEFAULT 0,
                        `focusDistance` REAL NOT NULL DEFAULT 0,
                        `whiteBalance` INTEGER,
                        `sensorWidth` INTEGER NOT NULL DEFAULT 0,
                        `sensorHeight` INTEGER NOT NULL DEFAULT 0,
                        `saturationRatio` REAL NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`standardGroupId`) REFERENCES `standard_groups`(`id`) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_projectId` ON `captures` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_standardGroupId` ON `captures` (`standardGroupId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calibrations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `step` TEXT NOT NULL,
                        `quality` TEXT NOT NULL,
                        `gValidationErrorNm` REAL,
                        `mappingFormula` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibrations_projectId` ON `calibrations` (`projectId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `standard_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `concentration` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_standard_groups_projectId` ON `standard_groups` (`projectId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `results` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `regressionFormula` TEXT NOT NULL,
                        `rSquared` REAL NOT NULL,
                        `predictedConcentration` REAL,
                        `slope` REAL NOT NULL,
                        `intercept` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_results_projectId` ON `results` (`projectId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fssa_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}