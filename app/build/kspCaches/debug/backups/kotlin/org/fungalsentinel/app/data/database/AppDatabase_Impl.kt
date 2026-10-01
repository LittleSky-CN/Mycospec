package org.fungalsentinel.app.`data`.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass
import org.fungalsentinel.app.`data`.dao.ProjectDao
import org.fungalsentinel.app.`data`.dao.ProjectDao_Impl

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _projectDao: Lazy<ProjectDao> = lazy {
    ProjectDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(2, "f90b18ab18aed108c34edc697371e7ef", "21bf0016fbaaf66111fc8e88b5f9db86") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `fluorophore` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `finishedAt` INTEGER, `status` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `captures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `step` TEXT NOT NULL, `standardGroupId` INTEGER, `uri` TEXT NOT NULL, `exposureTimeNs` INTEGER NOT NULL, `iso` INTEGER NOT NULL, `focusDistance` REAL NOT NULL, `whiteBalance` INTEGER, `sensorWidth` INTEGER NOT NULL, `sensorHeight` INTEGER NOT NULL, `saturationRatio` REAL NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`standardGroupId`) REFERENCES `standard_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_projectId` ON `captures` (`projectId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_standardGroupId` ON `captures` (`standardGroupId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `calibrations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `step` TEXT NOT NULL, `quality` TEXT NOT NULL, `gValidationErrorNm` REAL, `mappingFormula` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_calibrations_projectId` ON `calibrations` (`projectId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `standard_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `concentration` REAL NOT NULL, `unit` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_standard_groups_projectId` ON `standard_groups` (`projectId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `regressionFormula` TEXT NOT NULL, `rSquared` REAL NOT NULL, `predictedConcentration` REAL, `slope` REAL NOT NULL, `intercept` REAL NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_results_projectId` ON `results` (`projectId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f90b18ab18aed108c34edc697371e7ef')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `projects`")
        connection.execSQL("DROP TABLE IF EXISTS `captures`")
        connection.execSQL("DROP TABLE IF EXISTS `calibrations`")
        connection.execSQL("DROP TABLE IF EXISTS `standard_groups`")
        connection.execSQL("DROP TABLE IF EXISTS `results`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsProjects: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProjects.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("fluorophore", TableInfo.Column("fluorophore", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("finishedAt", TableInfo.Column("finishedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProjects: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProjects: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoProjects: TableInfo = TableInfo("projects", _columnsProjects, _foreignKeysProjects, _indicesProjects)
        val _existingProjects: TableInfo = read(connection, "projects")
        if (!_infoProjects.equals(_existingProjects)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |projects(org.fungalsentinel.app.data.model.ProjectEntity).
              | Expected:
              |""".trimMargin() + _infoProjects + """
              |
              | Found:
              |""".trimMargin() + _existingProjects)
        }
        val _columnsCaptures: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCaptures.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("projectId", TableInfo.Column("projectId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("step", TableInfo.Column("step", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("standardGroupId", TableInfo.Column("standardGroupId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("uri", TableInfo.Column("uri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("exposureTimeNs", TableInfo.Column("exposureTimeNs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("iso", TableInfo.Column("iso", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("focusDistance", TableInfo.Column("focusDistance", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("whiteBalance", TableInfo.Column("whiteBalance", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("sensorWidth", TableInfo.Column("sensorWidth", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("sensorHeight", TableInfo.Column("sensorHeight", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("saturationRatio", TableInfo.Column("saturationRatio", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCaptures.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCaptures: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysCaptures.add(TableInfo.ForeignKey("projects", "CASCADE", "NO ACTION", listOf("projectId"), listOf("id")))
        _foreignKeysCaptures.add(TableInfo.ForeignKey("standard_groups", "SET NULL", "NO ACTION", listOf("standardGroupId"), listOf("id")))
        val _indicesCaptures: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesCaptures.add(TableInfo.Index("index_captures_projectId", false, listOf("projectId"), listOf("ASC")))
        _indicesCaptures.add(TableInfo.Index("index_captures_standardGroupId", false, listOf("standardGroupId"), listOf("ASC")))
        val _infoCaptures: TableInfo = TableInfo("captures", _columnsCaptures, _foreignKeysCaptures, _indicesCaptures)
        val _existingCaptures: TableInfo = read(connection, "captures")
        if (!_infoCaptures.equals(_existingCaptures)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |captures(org.fungalsentinel.app.data.model.CaptureEntity).
              | Expected:
              |""".trimMargin() + _infoCaptures + """
              |
              | Found:
              |""".trimMargin() + _existingCaptures)
        }
        val _columnsCalibrations: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCalibrations.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCalibrations.put("projectId", TableInfo.Column("projectId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCalibrations.put("step", TableInfo.Column("step", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCalibrations.put("quality", TableInfo.Column("quality", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCalibrations.put("gValidationErrorNm", TableInfo.Column("gValidationErrorNm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCalibrations.put("mappingFormula", TableInfo.Column("mappingFormula", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCalibrations.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCalibrations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysCalibrations.add(TableInfo.ForeignKey("projects", "CASCADE", "NO ACTION", listOf("projectId"), listOf("id")))
        val _indicesCalibrations: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesCalibrations.add(TableInfo.Index("index_calibrations_projectId", false, listOf("projectId"), listOf("ASC")))
        val _infoCalibrations: TableInfo = TableInfo("calibrations", _columnsCalibrations, _foreignKeysCalibrations, _indicesCalibrations)
        val _existingCalibrations: TableInfo = read(connection, "calibrations")
        if (!_infoCalibrations.equals(_existingCalibrations)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |calibrations(org.fungalsentinel.app.data.model.CalibrationEntity).
              | Expected:
              |""".trimMargin() + _infoCalibrations + """
              |
              | Found:
              |""".trimMargin() + _existingCalibrations)
        }
        val _columnsStandardGroups: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsStandardGroups.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStandardGroups.put("projectId", TableInfo.Column("projectId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStandardGroups.put("concentration", TableInfo.Column("concentration", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStandardGroups.put("unit", TableInfo.Column("unit", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStandardGroups.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysStandardGroups: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysStandardGroups.add(TableInfo.ForeignKey("projects", "CASCADE", "NO ACTION", listOf("projectId"), listOf("id")))
        val _indicesStandardGroups: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesStandardGroups.add(TableInfo.Index("index_standard_groups_projectId", false, listOf("projectId"), listOf("ASC")))
        val _infoStandardGroups: TableInfo = TableInfo("standard_groups", _columnsStandardGroups, _foreignKeysStandardGroups, _indicesStandardGroups)
        val _existingStandardGroups: TableInfo = read(connection, "standard_groups")
        if (!_infoStandardGroups.equals(_existingStandardGroups)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |standard_groups(org.fungalsentinel.app.data.model.StandardGroupEntity).
              | Expected:
              |""".trimMargin() + _infoStandardGroups + """
              |
              | Found:
              |""".trimMargin() + _existingStandardGroups)
        }
        val _columnsResults: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsResults.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResults.put("projectId", TableInfo.Column("projectId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResults.put("regressionFormula", TableInfo.Column("regressionFormula", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResults.put("rSquared", TableInfo.Column("rSquared", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResults.put("predictedConcentration", TableInfo.Column("predictedConcentration", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResults.put("slope", TableInfo.Column("slope", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResults.put("intercept", TableInfo.Column("intercept", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResults.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysResults: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysResults.add(TableInfo.ForeignKey("projects", "CASCADE", "NO ACTION", listOf("projectId"), listOf("id")))
        val _indicesResults: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesResults.add(TableInfo.Index("index_results_projectId", false, listOf("projectId"), listOf("ASC")))
        val _infoResults: TableInfo = TableInfo("results", _columnsResults, _foreignKeysResults, _indicesResults)
        val _existingResults: TableInfo = read(connection, "results")
        if (!_infoResults.equals(_existingResults)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |results(org.fungalsentinel.app.data.model.ResultEntity).
              | Expected:
              |""".trimMargin() + _infoResults + """
              |
              | Found:
              |""".trimMargin() + _existingResults)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "projects", "captures", "calibrations", "standard_groups", "results")
  }

  public override fun clearAllTables() {
    super.performClear(true, "projects", "captures", "calibrations", "standard_groups", "results")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ProjectDao::class, ProjectDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun projectDao(): ProjectDao = _projectDao.value
}
