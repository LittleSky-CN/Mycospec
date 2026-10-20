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
 * [v2.2.8-D1] The lost-update race on writes to the project row (the projects table).
 *
 * The same row has to carry both the **report snapshot** (written every time Step 4 produces a
 * result) and the **workflow state** (`FINISHED` / `finishedAt`, written on Finish), and the two
 * paths each run in a fire-and-forget coroutine. If each one writes back from the copy it holds
 * via `copy()`, the later write wipes out the whole row written by the earlier one:
 *
 * ```
 * persistReportSnapshot : read  (status=IN_PROGRESS, finishedAt=null)   <- stale
 * finishProject         : write (status=FINISHED,    finishedAt=ts)
 * persistReportSnapshot : write (status=IN_PROGRESS, finishedAt=null)   <- polluted
 * ```
 *
 * The symptom is **silent**: `shouldRestoreProvenance()` requires `status == "FINISHED"`, so once
 * it is knocked back to `IN_PROGRESS` the Step 1/2/4 provenance is no longer restored when the
 * project is reopened from History -- in other words, the provenance loss that was just fixed
 * would reproduce itself verbatim through a different trigger path.
 *
 * ## Why this is enough, and why no mockk / Robolectric
 *
 * `ProjectViewModel` depends on `Application` + two Room databases and cannot be instantiated in
 * a JVM unit test (see the class KDoc of `ReopenProvenanceTest`). The fix therefore extracted both
 * writes into top-level suspend functions -- [`finishProjectLocked()`] /
 * [`persistReportSnapshotLocked()`] -- and **the ViewModel's production path calls exactly this
 * code**, so the fakes below drive it directly instead of duplicating an equivalent implementation.
 * Anyone who bypasses them inside the ViewModel and calls `dao.updateProject(...)` directly will
 * not be caught by these two fakes, but that kind of change (making `projectMetadataMutex`
 * disappear from three write sites) is visible at a glance in review.
 *
 * ## Why the cases here can be **deterministic**
 *
 * Both barriers use a rendezvous `Channel`, never `sleep` / `delay`:
 *
 * - The slow coroutine reports to the examiner and waits for release **after reading and before
 *   writing** -- exactly inside the critical section, so "it is holding a stale copy" happens
 *   100% of the time;
 * - Before releasing it, `yield()` a few times to hand over every scheduling opportunity: with a
 *   lock the opponent coroutine queues on the lock (it cannot get it, no matter how often you
 *   `yield`), without a lock it finishes at this step. The same script can therefore distinguish
 *   the two implementations.
 *
 * The verdict ignores the intermediate process (the middle of a race is random by nature) and
 * looks only at **the final row in the database**: which is exactly the only thing
 * `openProject()` ever reads.
 */
class FinishProjectRaceTest {

    /** Pretends to be that row in the projects table: whole-row overwrite writes, keeping a trace of every write. */
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
    // 1. The snapshot write takes off first: the stale row it read must not clobber the later Finish
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun flyingSnapshotWrite_cannotClobberFinish() = runBlocking {
        val mutex = Mutex()
        val row = FakeProjectRow(unfinishedRow())
        val readDone = Channel<Unit>(Channel.RENDEZVOUS)
        val allowWrite = Channel<Unit>(Channel.RENDEZVOUS)

        // 1. Simulate persistReportSnapshot() triggered by a Step 4 result: take the lock → read the stale row → block
        val flying = launch {
            persistReportSnapshotLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = {
                    readDone.send(Unit)     // already holding the stale copy, waiting for release
                    allowWrite.receive()
                    step4Snapshot
                },
                writeProject = { row.write(it) }
            )
        }
        readDone.receive()

        // 2. While it is blocked, the user taps Finish
        val finishing = launch {
            finishProjectLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = { finishSnapshot },
                writeProject = { row.write(it) }
            )
        }
        // Hand over every scheduling opportunity: without a lock, finish runs to completion at this step.
        repeat(5) { yield() }

        allowWrite.send(Unit)   // 3. Release the stale write-back
        joinAll(flying, finishing)

        assertEquals(
            "The final row must be FINISHED: if the in-flight snapshot write can write back the stale row it read, it knocks status back to IN_PROGRESS and the provenance is lost with it",
            "FINISHED", row.row.status
        )
        assertNotNull(
            "finishedAt must not be clobbered back to null",
            row.row.finishedAt
        )
        assertEquals(
            "The snapshot must be the one written by Finish (the last write wins)",
            finishSnapshot, row.row.reportDataJson
        )
        assertEquals("both writes must happen", 2, row.writes.size)
        assertEquals(
            "With a shared lock, Finish must complete after the stale write-back",
            listOf("IN_PROGRESS", "FINISHED"), row.writes.map { it.status }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. The reverse order: Finish takes the lock first, and the snapshot write must wait for it
    //    to finish before reading, so what it reads is already the FINISHED row -- even if it
    //    overwrites, the state is not lost.
    //    (Specially guards against an implementation that "fixed only one direction".)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun finishInFlight_snapshotWriteMustNotReadStaleRow() = runBlocking {
        val mutex = Mutex()
        val row = FakeProjectRow(unfinishedRow())
        val readDone = Channel<Unit>(Channel.RENDEZVOUS)
        val allowWrite = Channel<Unit>(Channel.RENDEZVOUS)

        // 1. Finish takes off first and blocks after reading
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

        // 2. Meanwhile the Step 4 snapshot write is triggered
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
            "The snapshot write must not sit behind the locked coroutine and scribble before Finish: the final state must be FINISHED",
            "FINISHED", row.row.status
        )
        assertNotNull("finishedAt must still be there", row.row.finishedAt)
        assertEquals(
            "With a lock the snapshot write runs after Finish, so what it read is already the FINISHED row and must not overwrite the phases",
            listOf("FINISHED", "FINISHED"), row.writes.map { it.status }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. Contract: with no project in progress, write nothing and change no state at all.
    //    (This decides whether finishProject() in the ViewModel should go on to clear uiState.)
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
    // 4. Each path on its own: the snapshot write and the Finish write each touch the right fields
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
        assertEquals("the snapshot write must not change the workflow state on the side", "IN_PROGRESS", row.row.status)
        assertEquals("the snapshot write must not assign finishedAt on the side", null, row.row.finishedAt)
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
