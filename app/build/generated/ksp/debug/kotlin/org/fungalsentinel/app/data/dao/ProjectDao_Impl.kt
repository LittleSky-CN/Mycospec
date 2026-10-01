package org.fungalsentinel.app.`data`.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import org.fungalsentinel.app.`data`.model.CalibrationEntity
import org.fungalsentinel.app.`data`.model.CaptureEntity
import org.fungalsentinel.app.`data`.model.ProjectEntity
import org.fungalsentinel.app.`data`.model.ResultEntity
import org.fungalsentinel.app.`data`.model.StandardGroupEntity

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ProjectDao_Impl(
  __db: RoomDatabase,
) : ProjectDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfProjectEntity: EntityInsertAdapter<ProjectEntity>

  private val __insertAdapterOfCaptureEntity: EntityInsertAdapter<CaptureEntity>

  private val __insertAdapterOfCalibrationEntity: EntityInsertAdapter<CalibrationEntity>

  private val __insertAdapterOfStandardGroupEntity: EntityInsertAdapter<StandardGroupEntity>

  private val __insertAdapterOfResultEntity: EntityInsertAdapter<ResultEntity>

  private val __deleteAdapterOfProjectEntity: EntityDeleteOrUpdateAdapter<ProjectEntity>

  private val __deleteAdapterOfStandardGroupEntity: EntityDeleteOrUpdateAdapter<StandardGroupEntity>

  private val __updateAdapterOfProjectEntity: EntityDeleteOrUpdateAdapter<ProjectEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfProjectEntity = object : EntityInsertAdapter<ProjectEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `projects` (`id`,`name`,`fluorophore`,`createdAt`,`finishedAt`,`status`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ProjectEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.fluorophore)
        statement.bindLong(4, entity.createdAt)
        val _tmpFinishedAt: Long? = entity.finishedAt
        if (_tmpFinishedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpFinishedAt)
        }
        statement.bindText(6, entity.status)
      }
    }
    this.__insertAdapterOfCaptureEntity = object : EntityInsertAdapter<CaptureEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `captures` (`id`,`projectId`,`step`,`standardGroupId`,`uri`,`exposureTimeNs`,`iso`,`focusDistance`,`whiteBalance`,`sensorWidth`,`sensorHeight`,`saturationRatio`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CaptureEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.projectId)
        statement.bindText(3, entity.step)
        val _tmpStandardGroupId: Long? = entity.standardGroupId
        if (_tmpStandardGroupId == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmpStandardGroupId)
        }
        statement.bindText(5, entity.uri)
        statement.bindLong(6, entity.exposureTimeNs)
        statement.bindLong(7, entity.iso.toLong())
        statement.bindDouble(8, entity.focusDistance.toDouble())
        val _tmpWhiteBalance: Int? = entity.whiteBalance
        if (_tmpWhiteBalance == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpWhiteBalance.toLong())
        }
        statement.bindLong(10, entity.sensorWidth.toLong())
        statement.bindLong(11, entity.sensorHeight.toLong())
        statement.bindDouble(12, entity.saturationRatio.toDouble())
        statement.bindLong(13, entity.createdAt)
      }
    }
    this.__insertAdapterOfCalibrationEntity = object : EntityInsertAdapter<CalibrationEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `calibrations` (`id`,`projectId`,`step`,`quality`,`gValidationErrorNm`,`mappingFormula`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CalibrationEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.projectId)
        statement.bindText(3, entity.step)
        statement.bindText(4, entity.quality)
        val _tmpGValidationErrorNm: Double? = entity.gValidationErrorNm
        if (_tmpGValidationErrorNm == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpGValidationErrorNm)
        }
        val _tmpMappingFormula: String? = entity.mappingFormula
        if (_tmpMappingFormula == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpMappingFormula)
        }
        statement.bindLong(7, entity.createdAt)
      }
    }
    this.__insertAdapterOfStandardGroupEntity = object : EntityInsertAdapter<StandardGroupEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `standard_groups` (`id`,`projectId`,`concentration`,`unit`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: StandardGroupEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.projectId)
        statement.bindDouble(3, entity.concentration)
        statement.bindText(4, entity.unit)
        statement.bindLong(5, entity.createdAt)
      }
    }
    this.__insertAdapterOfResultEntity = object : EntityInsertAdapter<ResultEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `results` (`id`,`projectId`,`regressionFormula`,`rSquared`,`predictedConcentration`,`slope`,`intercept`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ResultEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.projectId)
        statement.bindText(3, entity.regressionFormula)
        statement.bindDouble(4, entity.rSquared)
        val _tmpPredictedConcentration: Double? = entity.predictedConcentration
        if (_tmpPredictedConcentration == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpPredictedConcentration)
        }
        statement.bindDouble(6, entity.slope)
        statement.bindDouble(7, entity.intercept)
        statement.bindLong(8, entity.createdAt)
      }
    }
    this.__deleteAdapterOfProjectEntity = object : EntityDeleteOrUpdateAdapter<ProjectEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `projects` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ProjectEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__deleteAdapterOfStandardGroupEntity = object : EntityDeleteOrUpdateAdapter<StandardGroupEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `standard_groups` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: StandardGroupEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfProjectEntity = object : EntityDeleteOrUpdateAdapter<ProjectEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `projects` SET `id` = ?,`name` = ?,`fluorophore` = ?,`createdAt` = ?,`finishedAt` = ?,`status` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ProjectEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.fluorophore)
        statement.bindLong(4, entity.createdAt)
        val _tmpFinishedAt: Long? = entity.finishedAt
        if (_tmpFinishedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpFinishedAt)
        }
        statement.bindText(6, entity.status)
        statement.bindLong(7, entity.id)
      }
    }
  }

  public override suspend fun insertProject(project: ProjectEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfProjectEntity.insertAndReturnId(_connection, project)
    _result
  }

  public override suspend fun insertCapture(capture: CaptureEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfCaptureEntity.insertAndReturnId(_connection, capture)
    _result
  }

  public override suspend fun insertCaptures(captures: List<CaptureEntity>): List<Long> = performSuspending(__db, false, true) { _connection ->
    val _result: List<Long> = __insertAdapterOfCaptureEntity.insertAndReturnIdsList(_connection, captures)
    _result
  }

  public override suspend fun insertCalibration(calibration: CalibrationEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfCalibrationEntity.insertAndReturnId(_connection, calibration)
    _result
  }

  public override suspend fun insertStandardGroup(group: StandardGroupEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfStandardGroupEntity.insertAndReturnId(_connection, group)
    _result
  }

  public override suspend fun insertResult(result: ResultEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfResultEntity.insertAndReturnId(_connection, result)
    _result
  }

  public override suspend fun deleteProject(project: ProjectEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfProjectEntity.handle(_connection, project)
  }

  public override suspend fun deleteStandardGroup(group: StandardGroupEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfStandardGroupEntity.handle(_connection, group)
  }

  public override suspend fun updateProject(project: ProjectEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfProjectEntity.handle(_connection, project)
  }

  public override suspend fun resetProject(projectId: Long): Unit = performInTransactionSuspending(__db) {
    super@ProjectDao_Impl.resetProject(projectId)
  }

  public override fun getAllProjects(): Flow<List<ProjectEntity>> {
    val _sql: String = "SELECT * FROM projects ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("projects")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFluorophore: Int = getColumnIndexOrThrow(_stmt, "fluorophore")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _result: MutableList<ProjectEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ProjectEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFluorophore: String
          _tmpFluorophore = _stmt.getText(_columnIndexOfFluorophore)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpFinishedAt: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmpFinishedAt = null
          } else {
            _tmpFinishedAt = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          _item = ProjectEntity(_tmpId,_tmpName,_tmpFluorophore,_tmpCreatedAt,_tmpFinishedAt,_tmpStatus)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getInProgressProject(): ProjectEntity? {
    val _sql: String = "SELECT * FROM projects WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFluorophore: Int = getColumnIndexOrThrow(_stmt, "fluorophore")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _result: ProjectEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFluorophore: String
          _tmpFluorophore = _stmt.getText(_columnIndexOfFluorophore)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpFinishedAt: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmpFinishedAt = null
          } else {
            _tmpFinishedAt = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          _result = ProjectEntity(_tmpId,_tmpName,_tmpFluorophore,_tmpCreatedAt,_tmpFinishedAt,_tmpStatus)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getProjectById(projectId: Long): ProjectEntity? {
    val _sql: String = "SELECT * FROM projects WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFluorophore: Int = getColumnIndexOrThrow(_stmt, "fluorophore")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _result: ProjectEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFluorophore: String
          _tmpFluorophore = _stmt.getText(_columnIndexOfFluorophore)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpFinishedAt: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmpFinishedAt = null
          } else {
            _tmpFinishedAt = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          _result = ProjectEntity(_tmpId,_tmpName,_tmpFluorophore,_tmpCreatedAt,_tmpFinishedAt,_tmpStatus)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getCapturesByStep(projectId: Long, step: String): Flow<List<CaptureEntity>> {
    val _sql: String = "SELECT * FROM captures WHERE projectId = ? AND step = ? ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("captures")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _argIndex = 2
        _stmt.bindText(_argIndex, step)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfStep: Int = getColumnIndexOrThrow(_stmt, "step")
        val _columnIndexOfStandardGroupId: Int = getColumnIndexOrThrow(_stmt, "standardGroupId")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfExposureTimeNs: Int = getColumnIndexOrThrow(_stmt, "exposureTimeNs")
        val _columnIndexOfIso: Int = getColumnIndexOrThrow(_stmt, "iso")
        val _columnIndexOfFocusDistance: Int = getColumnIndexOrThrow(_stmt, "focusDistance")
        val _columnIndexOfWhiteBalance: Int = getColumnIndexOrThrow(_stmt, "whiteBalance")
        val _columnIndexOfSensorWidth: Int = getColumnIndexOrThrow(_stmt, "sensorWidth")
        val _columnIndexOfSensorHeight: Int = getColumnIndexOrThrow(_stmt, "sensorHeight")
        val _columnIndexOfSaturationRatio: Int = getColumnIndexOrThrow(_stmt, "saturationRatio")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<CaptureEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CaptureEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpStep: String
          _tmpStep = _stmt.getText(_columnIndexOfStep)
          val _tmpStandardGroupId: Long?
          if (_stmt.isNull(_columnIndexOfStandardGroupId)) {
            _tmpStandardGroupId = null
          } else {
            _tmpStandardGroupId = _stmt.getLong(_columnIndexOfStandardGroupId)
          }
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpExposureTimeNs: Long
          _tmpExposureTimeNs = _stmt.getLong(_columnIndexOfExposureTimeNs)
          val _tmpIso: Int
          _tmpIso = _stmt.getLong(_columnIndexOfIso).toInt()
          val _tmpFocusDistance: Float
          _tmpFocusDistance = _stmt.getDouble(_columnIndexOfFocusDistance).toFloat()
          val _tmpWhiteBalance: Int?
          if (_stmt.isNull(_columnIndexOfWhiteBalance)) {
            _tmpWhiteBalance = null
          } else {
            _tmpWhiteBalance = _stmt.getLong(_columnIndexOfWhiteBalance).toInt()
          }
          val _tmpSensorWidth: Int
          _tmpSensorWidth = _stmt.getLong(_columnIndexOfSensorWidth).toInt()
          val _tmpSensorHeight: Int
          _tmpSensorHeight = _stmt.getLong(_columnIndexOfSensorHeight).toInt()
          val _tmpSaturationRatio: Float
          _tmpSaturationRatio = _stmt.getDouble(_columnIndexOfSaturationRatio).toFloat()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = CaptureEntity(_tmpId,_tmpProjectId,_tmpStep,_tmpStandardGroupId,_tmpUri,_tmpExposureTimeNs,_tmpIso,_tmpFocusDistance,_tmpWhiteBalance,_tmpSensorWidth,_tmpSensorHeight,_tmpSaturationRatio,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getCapturesByStepAndGroup(
    projectId: Long,
    step: String,
    groupId: Long,
  ): Flow<List<CaptureEntity>> {
    val _sql: String = "SELECT * FROM captures WHERE projectId = ? AND step = ? AND standardGroupId = ? ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("captures")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _argIndex = 2
        _stmt.bindText(_argIndex, step)
        _argIndex = 3
        _stmt.bindLong(_argIndex, groupId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfStep: Int = getColumnIndexOrThrow(_stmt, "step")
        val _columnIndexOfStandardGroupId: Int = getColumnIndexOrThrow(_stmt, "standardGroupId")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfExposureTimeNs: Int = getColumnIndexOrThrow(_stmt, "exposureTimeNs")
        val _columnIndexOfIso: Int = getColumnIndexOrThrow(_stmt, "iso")
        val _columnIndexOfFocusDistance: Int = getColumnIndexOrThrow(_stmt, "focusDistance")
        val _columnIndexOfWhiteBalance: Int = getColumnIndexOrThrow(_stmt, "whiteBalance")
        val _columnIndexOfSensorWidth: Int = getColumnIndexOrThrow(_stmt, "sensorWidth")
        val _columnIndexOfSensorHeight: Int = getColumnIndexOrThrow(_stmt, "sensorHeight")
        val _columnIndexOfSaturationRatio: Int = getColumnIndexOrThrow(_stmt, "saturationRatio")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<CaptureEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CaptureEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpStep: String
          _tmpStep = _stmt.getText(_columnIndexOfStep)
          val _tmpStandardGroupId: Long?
          if (_stmt.isNull(_columnIndexOfStandardGroupId)) {
            _tmpStandardGroupId = null
          } else {
            _tmpStandardGroupId = _stmt.getLong(_columnIndexOfStandardGroupId)
          }
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpExposureTimeNs: Long
          _tmpExposureTimeNs = _stmt.getLong(_columnIndexOfExposureTimeNs)
          val _tmpIso: Int
          _tmpIso = _stmt.getLong(_columnIndexOfIso).toInt()
          val _tmpFocusDistance: Float
          _tmpFocusDistance = _stmt.getDouble(_columnIndexOfFocusDistance).toFloat()
          val _tmpWhiteBalance: Int?
          if (_stmt.isNull(_columnIndexOfWhiteBalance)) {
            _tmpWhiteBalance = null
          } else {
            _tmpWhiteBalance = _stmt.getLong(_columnIndexOfWhiteBalance).toInt()
          }
          val _tmpSensorWidth: Int
          _tmpSensorWidth = _stmt.getLong(_columnIndexOfSensorWidth).toInt()
          val _tmpSensorHeight: Int
          _tmpSensorHeight = _stmt.getLong(_columnIndexOfSensorHeight).toInt()
          val _tmpSaturationRatio: Float
          _tmpSaturationRatio = _stmt.getDouble(_columnIndexOfSaturationRatio).toFloat()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = CaptureEntity(_tmpId,_tmpProjectId,_tmpStep,_tmpStandardGroupId,_tmpUri,_tmpExposureTimeNs,_tmpIso,_tmpFocusDistance,_tmpWhiteBalance,_tmpSensorWidth,_tmpSensorHeight,_tmpSaturationRatio,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllCapturesByProject(projectId: Long): Flow<List<CaptureEntity>> {
    val _sql: String = "SELECT * FROM captures WHERE projectId = ? ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("captures")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfStep: Int = getColumnIndexOrThrow(_stmt, "step")
        val _columnIndexOfStandardGroupId: Int = getColumnIndexOrThrow(_stmt, "standardGroupId")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfExposureTimeNs: Int = getColumnIndexOrThrow(_stmt, "exposureTimeNs")
        val _columnIndexOfIso: Int = getColumnIndexOrThrow(_stmt, "iso")
        val _columnIndexOfFocusDistance: Int = getColumnIndexOrThrow(_stmt, "focusDistance")
        val _columnIndexOfWhiteBalance: Int = getColumnIndexOrThrow(_stmt, "whiteBalance")
        val _columnIndexOfSensorWidth: Int = getColumnIndexOrThrow(_stmt, "sensorWidth")
        val _columnIndexOfSensorHeight: Int = getColumnIndexOrThrow(_stmt, "sensorHeight")
        val _columnIndexOfSaturationRatio: Int = getColumnIndexOrThrow(_stmt, "saturationRatio")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<CaptureEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CaptureEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpStep: String
          _tmpStep = _stmt.getText(_columnIndexOfStep)
          val _tmpStandardGroupId: Long?
          if (_stmt.isNull(_columnIndexOfStandardGroupId)) {
            _tmpStandardGroupId = null
          } else {
            _tmpStandardGroupId = _stmt.getLong(_columnIndexOfStandardGroupId)
          }
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpExposureTimeNs: Long
          _tmpExposureTimeNs = _stmt.getLong(_columnIndexOfExposureTimeNs)
          val _tmpIso: Int
          _tmpIso = _stmt.getLong(_columnIndexOfIso).toInt()
          val _tmpFocusDistance: Float
          _tmpFocusDistance = _stmt.getDouble(_columnIndexOfFocusDistance).toFloat()
          val _tmpWhiteBalance: Int?
          if (_stmt.isNull(_columnIndexOfWhiteBalance)) {
            _tmpWhiteBalance = null
          } else {
            _tmpWhiteBalance = _stmt.getLong(_columnIndexOfWhiteBalance).toInt()
          }
          val _tmpSensorWidth: Int
          _tmpSensorWidth = _stmt.getLong(_columnIndexOfSensorWidth).toInt()
          val _tmpSensorHeight: Int
          _tmpSensorHeight = _stmt.getLong(_columnIndexOfSensorHeight).toInt()
          val _tmpSaturationRatio: Float
          _tmpSaturationRatio = _stmt.getDouble(_columnIndexOfSaturationRatio).toFloat()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = CaptureEntity(_tmpId,_tmpProjectId,_tmpStep,_tmpStandardGroupId,_tmpUri,_tmpExposureTimeNs,_tmpIso,_tmpFocusDistance,_tmpWhiteBalance,_tmpSensorWidth,_tmpSensorHeight,_tmpSaturationRatio,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCalibration(projectId: Long, step: String): CalibrationEntity? {
    val _sql: String = "SELECT * FROM calibrations WHERE projectId = ? AND step = ? ORDER BY createdAt DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _argIndex = 2
        _stmt.bindText(_argIndex, step)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfStep: Int = getColumnIndexOrThrow(_stmt, "step")
        val _columnIndexOfQuality: Int = getColumnIndexOrThrow(_stmt, "quality")
        val _columnIndexOfGValidationErrorNm: Int = getColumnIndexOrThrow(_stmt, "gValidationErrorNm")
        val _columnIndexOfMappingFormula: Int = getColumnIndexOrThrow(_stmt, "mappingFormula")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: CalibrationEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpStep: String
          _tmpStep = _stmt.getText(_columnIndexOfStep)
          val _tmpQuality: String
          _tmpQuality = _stmt.getText(_columnIndexOfQuality)
          val _tmpGValidationErrorNm: Double?
          if (_stmt.isNull(_columnIndexOfGValidationErrorNm)) {
            _tmpGValidationErrorNm = null
          } else {
            _tmpGValidationErrorNm = _stmt.getDouble(_columnIndexOfGValidationErrorNm)
          }
          val _tmpMappingFormula: String?
          if (_stmt.isNull(_columnIndexOfMappingFormula)) {
            _tmpMappingFormula = null
          } else {
            _tmpMappingFormula = _stmt.getText(_columnIndexOfMappingFormula)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = CalibrationEntity(_tmpId,_tmpProjectId,_tmpStep,_tmpQuality,_tmpGValidationErrorNm,_tmpMappingFormula,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getStandardGroups(projectId: Long): Flow<List<StandardGroupEntity>> {
    val _sql: String = "SELECT * FROM standard_groups WHERE projectId = ? ORDER BY concentration ASC"
    return createFlow(__db, false, arrayOf("standard_groups")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfConcentration: Int = getColumnIndexOrThrow(_stmt, "concentration")
        val _columnIndexOfUnit: Int = getColumnIndexOrThrow(_stmt, "unit")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<StandardGroupEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: StandardGroupEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpConcentration: Double
          _tmpConcentration = _stmt.getDouble(_columnIndexOfConcentration)
          val _tmpUnit: String
          _tmpUnit = _stmt.getText(_columnIndexOfUnit)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = StandardGroupEntity(_tmpId,_tmpProjectId,_tmpConcentration,_tmpUnit,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getStandardGroupById(groupId: Long): StandardGroupEntity? {
    val _sql: String = "SELECT * FROM standard_groups WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, groupId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfConcentration: Int = getColumnIndexOrThrow(_stmt, "concentration")
        val _columnIndexOfUnit: Int = getColumnIndexOrThrow(_stmt, "unit")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: StandardGroupEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpConcentration: Double
          _tmpConcentration = _stmt.getDouble(_columnIndexOfConcentration)
          val _tmpUnit: String
          _tmpUnit = _stmt.getText(_columnIndexOfUnit)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = StandardGroupEntity(_tmpId,_tmpProjectId,_tmpConcentration,_tmpUnit,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getResultByProject(projectId: Long): ResultEntity? {
    val _sql: String = "SELECT * FROM results WHERE projectId = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfRegressionFormula: Int = getColumnIndexOrThrow(_stmt, "regressionFormula")
        val _columnIndexOfRSquared: Int = getColumnIndexOrThrow(_stmt, "rSquared")
        val _columnIndexOfPredictedConcentration: Int = getColumnIndexOrThrow(_stmt, "predictedConcentration")
        val _columnIndexOfSlope: Int = getColumnIndexOrThrow(_stmt, "slope")
        val _columnIndexOfIntercept: Int = getColumnIndexOrThrow(_stmt, "intercept")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: ResultEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpRegressionFormula: String
          _tmpRegressionFormula = _stmt.getText(_columnIndexOfRegressionFormula)
          val _tmpRSquared: Double
          _tmpRSquared = _stmt.getDouble(_columnIndexOfRSquared)
          val _tmpPredictedConcentration: Double?
          if (_stmt.isNull(_columnIndexOfPredictedConcentration)) {
            _tmpPredictedConcentration = null
          } else {
            _tmpPredictedConcentration = _stmt.getDouble(_columnIndexOfPredictedConcentration)
          }
          val _tmpSlope: Double
          _tmpSlope = _stmt.getDouble(_columnIndexOfSlope)
          val _tmpIntercept: Double
          _tmpIntercept = _stmt.getDouble(_columnIndexOfIntercept)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = ResultEntity(_tmpId,_tmpProjectId,_tmpRegressionFormula,_tmpRSquared,_tmpPredictedConcentration,_tmpSlope,_tmpIntercept,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteProjectById(projectId: Long) {
    val _sql: String = "DELETE FROM projects WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteCapturesByProject(projectId: Long) {
    val _sql: String = "DELETE FROM captures WHERE projectId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteCapturesByStep(projectId: Long, step: String) {
    val _sql: String = "DELETE FROM captures WHERE projectId = ? AND step = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _argIndex = 2
        _stmt.bindText(_argIndex, step)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteCapturesByGroup(projectId: Long, groupId: Long) {
    val _sql: String = "DELETE FROM captures WHERE projectId = ? AND standardGroupId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, groupId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteCalibrationsByProject(projectId: Long) {
    val _sql: String = "DELETE FROM calibrations WHERE projectId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteStandardGroupsByProject(projectId: Long) {
    val _sql: String = "DELETE FROM standard_groups WHERE projectId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteResultByProject(projectId: Long) {
    val _sql: String = "DELETE FROM results WHERE projectId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
