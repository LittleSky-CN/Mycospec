package org.fungalsentinel.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.fungalsentinel.app.data.model.CalibrationEntity
import org.fungalsentinel.app.data.model.CaptureEntity
import org.fungalsentinel.app.data.model.ProjectEntity
import org.fungalsentinel.app.data.model.ResultEntity
import org.fungalsentinel.app.data.model.StandardGroupEntity

@Dao
interface ProjectDao {

    // ─────────────────────────────────────────────
    // Projects
    // ─────────────────────────────────────────────
    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getInProgressProject(): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    // ─────────────────────────────────────────────
    // Captures
    // ─────────────────────────────────────────────
    @Insert
    suspend fun insertCapture(capture: CaptureEntity): Long

    @Insert
    suspend fun insertCaptures(captures: List<CaptureEntity>): List<Long>

    @Query("SELECT * FROM captures WHERE projectId = :projectId AND step = :step ORDER BY createdAt DESC")
    fun getCapturesByStep(projectId: Long, step: String): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE projectId = :projectId AND step = :step AND standardGroupId = :groupId ORDER BY createdAt DESC")
    fun getCapturesByStepAndGroup(projectId: Long, step: String, groupId: Long): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getAllCapturesByProject(projectId: Long): Flow<List<CaptureEntity>>

    @Query("DELETE FROM captures WHERE projectId = :projectId")
    suspend fun deleteCapturesByProject(projectId: Long)

    @Query("DELETE FROM captures WHERE projectId = :projectId AND step = :step")
    suspend fun deleteCapturesByStep(projectId: Long, step: String)

    @Query("DELETE FROM captures WHERE projectId = :projectId AND standardGroupId = :groupId")
    suspend fun deleteCapturesByGroup(projectId: Long, groupId: Long)

    // ─────────────────────────────────────────────
    // Calibrations
    // ─────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(calibration: CalibrationEntity): Long

    @Query("SELECT * FROM calibrations WHERE projectId = :projectId AND step = :step ORDER BY createdAt DESC LIMIT 1")
    suspend fun getCalibration(projectId: Long, step: String): CalibrationEntity?

    @Query("DELETE FROM calibrations WHERE projectId = :projectId")
    suspend fun deleteCalibrationsByProject(projectId: Long)

    // ─────────────────────────────────────────────
    // Standard Groups
    // ─────────────────────────────────────────────
    @Insert
    suspend fun insertStandardGroup(group: StandardGroupEntity): Long

    @Query("SELECT * FROM standard_groups WHERE projectId = :projectId ORDER BY concentration ASC")
    fun getStandardGroups(projectId: Long): Flow<List<StandardGroupEntity>>

    @Query("SELECT * FROM standard_groups WHERE id = :groupId")
    suspend fun getStandardGroupById(groupId: Long): StandardGroupEntity?

    @Delete
    suspend fun deleteStandardGroup(group: StandardGroupEntity)

    @Query("DELETE FROM standard_groups WHERE projectId = :projectId")
    suspend fun deleteStandardGroupsByProject(projectId: Long)

    // ─────────────────────────────────────────────
    // Results
    // ─────────────────────────────────────────────
    @Insert
    suspend fun insertResult(result: ResultEntity): Long

    @Query("SELECT * FROM results WHERE projectId = :projectId LIMIT 1")
    suspend fun getResultByProject(projectId: Long): ResultEntity?

    @Query("DELETE FROM results WHERE projectId = :projectId")
    suspend fun deleteResultByProject(projectId: Long)

    // ─────────────────────────────────────────────
    // 批量操作（用于 Reset）
    // ─────────────────────────────────────────────
    @Transaction
    suspend fun resetProject(projectId: Long) {
        deleteCapturesByProject(projectId)
        deleteCalibrationsByProject(projectId)
        deleteStandardGroupsByProject(projectId)
        deleteResultByProject(projectId)
        deleteProjectById(projectId)
    }
}