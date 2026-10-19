package org.fungalsentinel.app.viewmodel

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield
import org.fungalsentinel.app.data.model.ProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * [v2.2.8-D1] lost-update race when writing the project row (projects table).
 *
 * The same row carries both the **report snapshot** (written every time Step 4 produces a result) and the **flow state** (Finish writes
 * `FINISHED` / `finishedAt`), and the two paths each run in a fire-and-forget coroutine. If each writes back its own
 * `copy()`, the later write wipes out the earlier result for the whole row:
 *
 * ```
 * persistReportSnapshot : reads  (status=IN_PROGRESS, finishedAt=null)   <- stale
 * finishProject         : writes (status=FINISHED,    finishedAt=ts)
 * persistReportSnapshot : writes (status=IN_PROGRESS, finishedAt=null)   <- pollutes
 * ```
 *
 * The symptom is **silent**: `shouldRestoreProvenance()` requires `status == "FINISHED"`, and once it is pushed back to
 * `IN_PROGRESS`, reopening from History no longer restores the provenance of Steps 1/2/4 -- meaning the provenance loss
 * we just fixed would reproduce verbatim through a different trigger path.
 *
 * ## Why this is enough, and why no mockk / Robolectric
 *
 * `ProjectViewModel` depends on `Application` + two Room databases and cannot be instantiated in a JVM unit test (see
 * the class KDoc of `ReopenProvenanceTest`). The fix therefore extracted both writes into top-level suspend functions
 * -- [`finishProjectLocked()`] / [`persistReportSnapshotLocked()`] -- **the production path in the ViewModel
 * calls exactly this code**, and here we drive it with fake reader / writer doubles instead of copying an equivalent implementation.
 * If someone bypasses them in the ViewModel and calls `dao.updateProject(...)` directly, neither fake can catch it, but such a change
 * (`projectMetadataMutex` disappearing from the three write sites) is obvious at review time.
 *
 * ## Why **deterministic** cases are possible here
 *
 * Both barriers use rendezvous `Channel`s, never `sleep` / `delay`:
 *
 * - the slow coroutine notifies the examiner and waits "**after reading, before writing**" -- that point sits exactly in the middle of the critical section,
 *   so "it holds a stale copy" happens 100% of the time;
 * - before releasing, `yield()` several times to give up every scheduling slot: with a lock the opponent coroutine queues on the lock (it cannot proceed,
 *   so no amount of `yield` helps), without a lock it finishes right at this step. The same script can therefore distinguish both implementations.
 *
 * The verdict ignores intermediate states (the intermediate states of a race are random anyway) and only looks at **the whole row finally persisted**: that is exactly
 * the only thing `openProject()` will ever read.
 */
class FinishProjectRaceTest {

    /** Pretends to be that single row of the projects table: overwrites the whole row and records every write. */
    private class FakeProjectRow(initial: ProjectEntity) {
        var row: ProjectEntity = initial
            private set
        val writes = mutableListOf<ProjectEntity>()

        fun write(fresh: ProjectEntity) {
            row = fresh
            writes += fresh
        }
    }

    private val step4Snapshot = "SNAPSHOT_FROM_STEP4"
    private val finishSnapshot = "SNAPSHOT_FROM_FINISH"

    private fun unfinishedRow() = ProjectEntity(id = 1, name = "demo", status = "IN_PROGRESS")

    // ─────────────────────────────────────────────────────────────────────
    // 1. the snapshot write takes off first: the stale row it read must not wipe out the later Finish
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun flyingSnapshotWrite_cannotClobberFinish() = runBlocking {
        val mutex = Mutex()
        val row = FakeProjectRow(unfinishedRow())
        val readDone = Channel<Unit>(Channel.RENDEZVOUS)
        val allowWrite = Channel<Unit>(Channel.RENDEZVOUS)

        // (1) simulate the persistReportSnapshot() triggered by a Step 4 result: take the lock -> read the stale row -> block
        val flying = launch {
            persistReportSnapshotLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = {
                    readDone.send(Unit)     // holding the stale copy now, waiting for you to release
                    allowWrite.receive()
                    step4Snapshot
                },
                writeProject = { row.write(it) }
            )
        }
        readDone.receive()

        // (2) at this blocked moment the user taps Finish
        val finishing = launch {
            finishProjectLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = { finishSnapshot },
                writeProject = { row.write(it) }
            )
        }
        // give up every scheduling slot: without a lock, finish completes right at this step.
        repeat(5) { yield() }

        allowWrite.send(Unit)   // (3) release that stale write-back
        joinAll(flying, finishing)

        assertEquals(
            "the row finally persisted must be FINISHED: if an in-flight snapshot write can write back the stale row it read, it will push status to IN_PROGRESS and the provenance is lost",
            "FINISHED", row.row.status
        )
        assertNotNull(
            "finishedAt must not be pushed back to null",
            row.row.finishedAt
        )
        assertEquals(
            "the snapshot must be the one written by Finish (the last write wins)",
            finishSnapshot, row.row.reportDataJson
        )
        assertEquals("both writes must happen", 2, row.writes.size)
        assertEquals(
            "with the same lock, Finish must complete after the stale write-back",
            listOf("IN_PROGRESS", "FINISHED"), row.writes.map { it.status }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. the opposite order: Finish takes the lock first, so the snapshot write must wait until it finishes before reading,
    //    and therefore already reads a FINISHED row -- even if it overwrites, no state is lost.
    //    (this specifically stops an implementation that "only fixed one direction" from sneaking through)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun finishInFlight_snapshotWriteMustNotReadStaleRow() = runBlocking {
        val mutex = Mutex()
        val row = FakeProjectRow(unfinishedRow())
        val readDone = Channel<Unit>(Channel.RENDEZVOUS)
        val allowWrite = Channel<Unit>(Channel.RENDEZVOUS)

        // (1) Finish takes off first and blocks after reading
        val finishing = launch {
            finishProjectLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = {
                    readDone.send(Unit)
                    allowWrite.receive()
                    finishSnapshot
                },
                writeProject = { row.write(it) }
            )
        }
        readDone.receive()

        // (2) in the meantime the Step 4 snapshot write is triggered
        val flying = launch {
            persistReportSnapshotLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = { step4Snapshot },
                writeProject = { row.write(it) }
            )
        }
        repeat(5) { yield() }

        allowWrite.send(Unit)
        joinAll(finishing, flying)

        assertEquals(
            "the snapshot write must not slip in behind the locked coroutine before Finish and write arbitrarily: the final state must be FINISHED",
            "FINISHED", row.row.status
        )
        assertNotNull("finishedAt must still be there", row.row.finishedAt)
        assertEquals(
            "with the lock the snapshot write is ordered after Finish, so it already reads a FINISHED row and must not clobber the phases",
            listOf("FINISHED", "FINISHED"), row.writes.map { it.status }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. contract: when no project is in progress, write not a single byte and change no state at all.
    //    (this decides whether finishProject() in the ViewModel should keep going on to clear uiState)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun finishProjectLocked_withoutCurrentProject_writesNothing() = runBlocking {
        val row = FakeProjectRow(unfinishedRow())

        val written = finishProjectLocked(
            mutex = Mutex(),
            readProject = { null },
            serialize = { finishSnapshot },
            writeProject = { row.write(it) }
        )

        assertFalse("must return false when there is no current project", written)
        assertEquals("not a single updateProject may happen", 0, row.writes.size)
    }

    @Test
    fun persistReportSnapshotLocked_withoutCurrentProject_writesNothing() = runBlocking {
        val row = FakeProjectRow(unfinishedRow())

        val written = persistReportSnapshotLocked(
            mutex = Mutex(),
            readProject = { null },
            serialize = { step4Snapshot },
            writeProject = { row.write(it) }
        )

        assertFalse("must return false when there is no current project", written)
        assertEquals("not a single updateProject may happen", 0, row.writes.size)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. each path on its own: the fields written by the snapshot write and by the Finish write are correct
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun snapshotWrite_onlyTouchesTheSnapshotColumn() = runBlocking {
        val row = FakeProjectRow(unfinishedRow().copy(reportDataJson = "OLD"))

        val written = persistReportSnapshotLocked(
            mutex = Mutex(),
            readProject = { row.row },
            serialize = { step4Snapshot },
            writeProject = { row.write(it) }
        )

        assertEquals(true, written)
        assertEquals(step4Snapshot, row.row.reportDataJson)
        assertEquals("the snapshot write must not quietly change the flow state", "IN_PROGRESS", row.row.status)
        assertEquals("the snapshot write must not quietly assign finishedAt", null, row.row.finishedAt)
    }

    @Test
    fun finishWrite_marksFinishedAndCarriesTheSnapshot() = runBlocking {
        val row = FakeProjectRow(unfinishedRow())

        val written = finishProjectLocked(
            mutex = Mutex(),
            readProject = { row.row },
            serialize = { finishSnapshot },
            writeProject = { row.write(it) }
        )

        assertEquals(true, written)
        assertEquals("FINISHED", row.row.status)
        assertNotNull(row.row.finishedAt)
        assertEquals(finishSnapshot, row.row.reportDataJson)
    }
}
