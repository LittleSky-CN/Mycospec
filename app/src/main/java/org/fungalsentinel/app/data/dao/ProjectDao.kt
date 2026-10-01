package org.fungalsentinel.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.fungalsentinel.app.data.model.CaptureEntity
import org.fungalsentinel.app.data.model.ProjectEntity
import org.fungalsentinel.app.data.model.ResultEntity
import org.fungalsentinel.app.data.model.StandardGroupEntity

@Dao
interface ProjectDao {
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

    @Insert
    suspend fun insertCapture(capture: CaptureEntity): Long

    @Query("SELECT * FROM captures WHERE projectId = :projectId AND step = :step ORDER BY createdAt DESC")
    fun getCapturesByStep(projectId: Long, step: String): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE projectId = :projectId AND step = :step ORDER BY id ASC")
    suspend fun getCapturesByStepOnce(projectId: Long, step: String): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE projectId = :projectId AND step = :step AND standardGroupId = :groupId ORDER BY id ASC")
    suspend fun getCapturesByStepAndGroupOnce(projectId: Long, step: String, groupId: Long): List<CaptureEntity>

    @Query("DELETE FROM captures WHERE projectId = :projectId")
    suspend fun deleteCapturesByProject(projectId: Long)

    @Insert
    suspend fun insertStandardGroup(group: StandardGroupEntity): Long

    @Query("SELECT * FROM standard_groups WHERE projectId = :projectId ORDER BY concentration ASC")
    fun getStandardGroups(projectId: Long): Flow<List<StandardGroupEntity>>

    @Query("SELECT * FROM standard_groups WHERE projectId = :projectId ORDER BY concentration ASC")
    suspend fun getStandardGroupsOnce(projectId: Long): List<StandardGroupEntity>

    @Query("DELETE FROM standard_groups WHERE projectId = :projectId")
    suspend fun deleteStandardGroupsByProject(projectId: Long)

    @Insert
    suspend fun insertResult(result: ResultEntity): Long

    @Query("SELECT * FROM results WHERE projectId = :projectId LIMIT 1")
    suspend fun getResultByProject(projectId: Long): ResultEntity?
}