package com.geodensidad.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ActivityWithProject(
    val id: String, val name: String, val status: String, val projectId: String, val projectName: String,
    val lastOpenedAt: Long, val sampleCount: Int, val draftCount: Int,
)

data class StatusCount(val status: String, val n: Int)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE status != 'ARCHIVED' OR :all ORDER BY updated_at DESC")
    fun observe(all: Boolean = false): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") fun observeOne(id: String): Flow<ProjectEntity?>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: String): ProjectEntity?
    @Insert suspend fun insert(p: ProjectEntity)
    @Update suspend fun update(p: ProjectEntity)
    @Query("SELECT COUNT(*) FROM projects") suspend fun count(): Int
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE project_id = :projectId ORDER BY created_at")
    fun observeByProject(projectId: String): Flow<List<ActivityEntity>>
    @Query("SELECT * FROM activities WHERE id = :id") fun observeOne(id: String): Flow<ActivityEntity?>
    @Query("SELECT * FROM activities WHERE id = :id") suspend fun get(id: String): ActivityEntity?
    @Query(
        """SELECT a.id, a.name, a.status, a.project_id AS projectId, p.name AS projectName, a.last_opened_at AS lastOpenedAt,
           (SELECT COUNT(*) FROM samples s WHERE s.activity_id = a.id) AS sampleCount,
           (SELECT COUNT(*) FROM samples s WHERE s.activity_id = a.id AND s.status = 'DRAFT') AS draftCount
           FROM activities a JOIN projects p ON p.id = a.project_id
           WHERE a.status IN ('OPEN','PAUSED') ORDER BY a.last_opened_at DESC"""
    )
    fun observeOpen(): Flow<List<ActivityWithProject>>
    @Query("SELECT id FROM activities WHERE status IN ('OPEN','PAUSED') ORDER BY created_at") suspend fun listOpenIds(): List<String>
    @Insert suspend fun insert(a: ActivityEntity)
    @Update suspend fun update(a: ActivityEntity)
    @Query("UPDATE activities SET last_opened_at = :t WHERE id = :id") suspend fun touch(id: String, t: Long)
}

@Dao
interface JourneyDao {
    @Query("SELECT * FROM journeys WHERE activity_id = :activityId ORDER BY work_date DESC, created_at DESC")
    fun observeByActivity(activityId: String): Flow<List<JourneyEntity>>
    @Query("SELECT * FROM journeys WHERE activity_id = :activityId ORDER BY work_date DESC, created_at DESC")
    suspend fun listByActivity(activityId: String): List<JourneyEntity>
    @Query("SELECT * FROM journeys WHERE id = :id") suspend fun get(id: String): JourneyEntity?
    @Insert suspend fun insert(j: JourneyEntity)
    @Update suspend fun update(j: JourneyEntity)
}

@Dao
interface LotDao {
    @Query("SELECT * FROM lots WHERE activity_id = :activityId ORDER BY code") fun observeByActivity(activityId: String): Flow<List<LotEntity>>
    @Query("SELECT * FROM lots WHERE activity_id = :activityId ORDER BY code") suspend fun listByActivity(activityId: String): List<LotEntity>
    @Query("SELECT * FROM lots WHERE id = :id") fun observeOne(id: String): Flow<LotEntity?>
    @Query("SELECT * FROM lots WHERE id = :id") suspend fun get(id: String): LotEntity?
    @Insert suspend fun insert(l: LotEntity)
    @Update suspend fun update(l: LotEntity)
}

@Dao
interface SampleDao {
    @Query("SELECT * FROM samples WHERE activity_id = :activityId ORDER BY seq") fun observeByActivity(activityId: String): Flow<List<SampleEntity>>
    @Query("SELECT * FROM samples WHERE activity_id = :activityId ORDER BY seq") suspend fun listByActivity(activityId: String): List<SampleEntity>
    @Query("SELECT * FROM samples WHERE lot_id = :lotId ORDER BY seq") fun observeByLot(lotId: String): Flow<List<SampleEntity>>
    @Query("SELECT * FROM samples WHERE lot_id = :lotId ORDER BY seq") suspend fun listByLot(lotId: String): List<SampleEntity>
    @Query("SELECT * FROM samples WHERE id = :id") fun observeOne(id: String): Flow<SampleEntity?>
    @Query("SELECT * FROM samples WHERE id = :id") suspend fun get(id: String): SampleEntity?
    @Query("SELECT COALESCE(MAX(seq), 0) FROM samples WHERE activity_id = :activityId") suspend fun maxSeq(activityId: String): Int
    @Query("SELECT COUNT(*) FROM samples WHERE activity_id = :activityId AND code = :code") suspend fun countCode(activityId: String, code: String): Int
    @Query("SELECT status, COUNT(*) AS n FROM samples WHERE activity_id = :activityId GROUP BY status") fun observeStatusCounts(activityId: String): Flow<List<StatusCount>>
    @Query("SELECT s.status AS status, COUNT(*) AS n FROM samples s JOIN activities a ON a.id = s.activity_id WHERE a.project_id = :projectId GROUP BY s.status")
    fun observeProjectStatusCounts(projectId: String): Flow<List<StatusCount>>
    @Insert suspend fun insert(s: SampleEntity)
    @Update suspend fun update(s: SampleEntity)
    @Query("SELECT COUNT(*) FROM samples") suspend fun count(): Int
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM sample_revisions WHERE id = :id") suspend fun get(id: String): SampleRevisionEntity?
    @Query("SELECT * FROM sample_revisions WHERE id = :id") fun observeOne(id: String): Flow<SampleRevisionEntity?>
    @Query("SELECT * FROM sample_revisions WHERE sample_id = :sampleId ORDER BY number DESC") fun observeBySample(sampleId: String): Flow<List<SampleRevisionEntity>>
    @Query("SELECT * FROM sample_revisions WHERE sample_id = :sampleId ORDER BY number DESC") suspend fun listBySample(sampleId: String): List<SampleRevisionEntity>
    @Insert suspend fun insert(r: SampleRevisionEntity)
    @Update suspend fun update(r: SampleRevisionEntity)
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM materials ORDER BY archived, name") fun materials(): Flow<List<MaterialEntity>>
    @Query("SELECT * FROM materials WHERE id = :id") suspend fun material(id: String): MaterialEntity?
    @Upsert suspend fun upsertMaterial(m: MaterialEntity)
    @Query("SELECT * FROM equipment ORDER BY archived, name") fun equipment(): Flow<List<EquipmentEntity>>
    @Query("SELECT * FROM equipment WHERE id = :id") suspend fun equipmentOne(id: String): EquipmentEntity?
    @Upsert suspend fun upsertEquipment(e: EquipmentEntity)
    @Query("SELECT * FROM calibrations ORDER BY archived, date DESC") fun calibrations(): Flow<List<CalibrationEntity>>
    @Query("SELECT * FROM calibrations WHERE id = :id") suspend fun calibration(id: String): CalibrationEntity?
    @Upsert suspend fun upsertCalibration(c: CalibrationEntity)
    @Query("SELECT * FROM lab_references ORDER BY archived, code") fun labRefs(): Flow<List<LabReferenceEntity>>
    @Query("SELECT * FROM lab_references WHERE id = :id") suspend fun labRef(id: String): LabReferenceEntity?
    @Upsert suspend fun upsertLabRef(l: LabReferenceEntity)
    @Query("SELECT * FROM people ORDER BY archived, name") fun people(): Flow<List<PersonEntity>>
    @Upsert suspend fun upsertPerson(p: PersonEntity)
    @Query("SELECT * FROM criteria ORDER BY archived, code, version") fun criteria(): Flow<List<CriterionEntity>>
    @Query("SELECT * FROM criteria WHERE id = :id") suspend fun criterion(id: String): CriterionEntity?
    @Upsert suspend fun upsertCriterion(c: CriterionEntity)
    @Query("SELECT COUNT(*) FROM criteria") suspend fun criteriaCount(): Int
    @Query("SELECT COUNT(*) FROM samples WHERE calibration_id = :id") suspend fun calibrationUse(id: String): Int
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM template_versions WHERE active = 1 LIMIT 1") suspend fun active(): TemplateVersionEntity?
    @Query("SELECT * FROM template_versions WHERE active = 1 LIMIT 1") fun observeActive(): Flow<TemplateVersionEntity?>
    @Query("SELECT * FROM template_versions ORDER BY created_at DESC") fun observeAll(): Flow<List<TemplateVersionEntity>>
    @Query("UPDATE template_versions SET active = 0") suspend fun deactivateAll()
    @Insert suspend fun insert(t: TemplateVersionEntity)
    @Query("SELECT COUNT(*) FROM template_versions") suspend fun count(): Int
}

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields ORDER BY archived, entity, sort_order") fun observeAll(): Flow<List<CustomFieldEntity>>
    @Query("SELECT * FROM custom_fields WHERE entity = :entity AND archived = 0 ORDER BY sort_order") fun observeActive(entity: String): Flow<List<CustomFieldEntity>>
    @Query("SELECT * FROM custom_fields WHERE entity = :entity ORDER BY sort_order") suspend fun listAll(entity: String): List<CustomFieldEntity>
    @Query("SELECT * FROM custom_fields WHERE field_key = :key") suspend fun byKey(key: String): CustomFieldEntity?
    @Upsert suspend fun upsert(f: CustomFieldEntity)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE sample_id = :sampleId AND archived = 0 ORDER BY sort_order, created_at") fun observeBySample(sampleId: String): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos WHERE sample_id = :sampleId AND archived = 0 ORDER BY sort_order, created_at") suspend fun listBySample(sampleId: String): List<PhotoEntity>
    @Query("SELECT * FROM photos WHERE journey_id = :journeyId AND sample_id IS NULL AND archived = 0 ORDER BY sort_order, created_at") fun observeByJourney(journeyId: String): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos WHERE activity_id = :activityId AND archived = 0 ORDER BY sort_order, created_at") suspend fun listByActivity(activityId: String): List<PhotoEntity>
    @Query("SELECT * FROM photos WHERE id = :id") fun observeOne(id: String): Flow<PhotoEntity?>
    @Query("SELECT * FROM photos WHERE id = :id") suspend fun get(id: String): PhotoEntity?
    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM photos WHERE sample_id = :sampleId") suspend fun maxOrder(sampleId: String): Int
    @Insert suspend fun insert(p: PhotoEntity)
    @Update suspend fun update(p: PhotoEntity)
    @Query("SELECT COUNT(*) FROM photos") suspend fun count(): Int
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports WHERE activity_id = :activityId ORDER BY issued_at DESC") fun observeByActivity(activityId: String): Flow<List<ReportEntity>>
    @Query("SELECT * FROM reports WHERE sample_id = :sampleId ORDER BY issued_at DESC") fun observeBySample(sampleId: String): Flow<List<ReportEntity>>
    @Query("SELECT * FROM reports WHERE id = :id") suspend fun get(id: String): ReportEntity?
    @Query("SELECT COUNT(*) FROM reports WHERE activity_id = :activityId") suspend fun countByActivity(activityId: String): Int
    @Insert suspend fun insert(r: ReportEntity)
    @Query("SELECT COUNT(*) FROM reports") suspend fun count(): Int
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: AuditEntity)
    @Query("SELECT * FROM audit_events WHERE entity_id = :entityId ORDER BY at DESC") fun observeFor(entityId: String): Flow<List<AuditEntity>>
    @Query("SELECT * FROM audit_events ORDER BY at DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<AuditEntity>>
}
