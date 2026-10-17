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
 * 【v2.2.8-D1】项目行（projects 表）写入的 lost-update 竞态。
 *
 * 同一行要同时承载**报告快照**（Step 4 每出结果就写一次）与**流程状态**（Finish 时写
 * `FINISHED` / `finishedAt`），两条路径各在一个 fire-and-forget 协程里跑。若各自凭手里
 * 的副本 `copy()` 回写，后写的那次会把先写的结果整行冲掉：
 *
 * ```
 * persistReportSnapshot ：读出 (status=IN_PROGRESS, finishedAt=null)   ← 陈旧
 * finishProject         ：写入 (status=FINISHED,    finishedAt=ts)
 * persistReportSnapshot ：回写 (status=IN_PROGRESS, finishedAt=null)   ← 污染
 * ```
 *
 * 症状是**静默**的：`shouldRestoreProvenance()` 要求 `status == "FINISHED"`，一旦被打回
 * `IN_PROGRESS`，从 History 重开时 Step 1/2/4 的溯源就不再还原 —— 也就是说，本次刚修好
 * 的溯源丢失会换个触发路径原样复现。
 *
 * ## 为什么这样就够，以及为什么不用 mockk / Robolectric
 *
 * `ProjectViewModel` 依赖 `Application` + 两个 Room 库，JVM 单测里实例化不了（见
 * `ReopenProvenanceTest` 的类 KDoc）。所以修复把两段写入都抽成了 top-level suspend 函数
 * —— [`finishProjectLocked()`] / [`persistReportSnapshotLocked()`] —— **ViewModel 的生产路径
 * 调用的就是这一份代码**，这里直接用 fake 的读写替身驱动它，而不是复制一份等价实现。
 * 谁要是在 ViewModel 里绕过它们直接 `dao.updateProject(...)`，这两个 fake 都拦不住，但那
 * 种改法（`projectMetadataMutex` 从三处写入里消失）在 review 时一眼可见。
 *
 * ## 为什么这里能出**确定性**用例
 *
 * 两个 barrier 用 rendezvous `Channel`，绝不用 `sleep` / `delay`：
 *
 * - 慢协程在「**读完之后、写之前**」通报考官并等待放行 —— 这个位置正好落在临界区当中，
 *   于是「它手里握着陈旧副本」这件事 100% 发生；
 * - 放行之前 `yield()` 若干次把调度机会全让出去：有锁时对手协程排在锁上（拿不到，
 *   `yield` 多少次都没用），无锁时它就在这一步跑完。同一个脚本于是能同时区分两种实现。
 *
 * 判定不看中间过程（竞态的中间态本来就随机），只看**最终落库的整行**：这正是
 * `openProject()` 唯一会读到的东西。
 */
class FinishProjectRaceTest {

    /** 假装自己就是 projects 表里那一行：整行覆盖写，并留下每次写入的痕迹。 */
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
    // 1. 快照写先起飞：它读到的陈旧行不许冲掉后面那次 Finish
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun flyingSnapshotWrite_cannotClobberFinish() = runBlocking {
        val mutex = Mutex()
        val row = FakeProjectRow(unfinishedRow())
        val readDone = Channel<Unit>(Channel.RENDEZVOUS)
        val allowWrite = Channel<Unit>(Channel.RENDEZVOUS)

        // ① 模拟 Step 4 结果产出触发的 persistReportSnapshot()：持锁 → 读陈旧行 → 卡住
        val flying = launch {
            persistReportSnapshotLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = {
                    readDone.send(Unit)     // 已握着陈旧副本，等你放行
                    allowWrite.receive()
                    step4Snapshot
                },
                writeProject = { row.write(it) }
            )
        }
        readDone.receive()

        // ② 卡住的这一刻用户点了 Finish
        val finishing = launch {
            finishProjectLocked(
                mutex = mutex,
                readProject = { row.row },
                serialize = { finishSnapshot },
                writeProject = { row.write(it) }
            )
        }
        // 把手里的调度机会全让出去：无锁时 finish 在这一步就跑完了。
        repeat(5) { yield() }

        allowWrite.send(Unit)   // ③ 放行那条陈旧的回写
        joinAll(flying, finishing)

        assertEquals(
            "最终落库的必须是 FINISHED：飞行中的快照写若能用它读到的陈旧行回写，就会把 status 打回 IN_PROGRESS，溯源随之丢失",
            "FINISHED", row.row.status
        )
        assertNotNull(
            "finishedAt 不能被冲回 null",
            row.row.finishedAt
        )
        assertEquals(
            "快照必须是 Finish 那一次写的（最后一次写入说了算）",
            finishSnapshot, row.row.reportDataJson
        )
        assertEquals("两次写入都必须发生", 2, row.writes.size)
        assertEquals(
            "有同一把锁时，Finish 必须排在陈旧回写之后完成",
            listOf("IN_PROGRESS", "FINISHED"), row.writes.map { it.status }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. 反过来的顺序：Finish 先持锁，快照写必须等它写完再读，
    //    于是读到的已经是 FINISHED 行 —— 即使被覆盖也不会丢状态。
    //    （专门防止「只修了一个方向」的实现蒙混过关）
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun finishInFlight_snapshotWriteMustNotReadStaleRow() = runBlocking {
        val mutex = Mutex()
        val row = FakeProjectRow(unfinishedRow())
        val readDone = Channel<Unit>(Channel.RENDEZVOUS)
        val allowWrite = Channel<Unit>(Channel.RENDEZVOUS)

        // ① Finish 先起飞，读完之后卡住
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

        // ② 这期间 Step 4 的快照写被触发
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
            "快照写不得躺在 Finish 之前陷在有锁协程的背后乱写：最终状态必须是 FINISHED",
            "FINISHED", row.row.status
        )
        assertNotNull("finishedAt 必须还在", row.row.finishedAt)
        assertEquals(
            "有锁时快照写排在 Finish 之后，它读到的已是 FINISHED 行，覆盖不得 phases",
            listOf("FINISHED", "FINISHED"), row.writes.map { it.status }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. 契约：没有进行中的项目时，不写一个字、也不改半点状态。
    //    （这条决定 ViewModel 里 finishProject() 要不要继续往下清 uiState）
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

        assertFalse("没有当前项目时必须返回 false", written)
        assertEquals("一次 updateProject 都不许发生", 0, row.writes.size)
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

        assertFalse("没有当前项目时必须返回 false", written)
        assertEquals("一次 updateProject 都不许发生", 0, row.writes.size)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. 单条路径自身：快照写与 Finish 写各自写的字段是对的
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
        assertEquals("快照写不许顺手改流程状态", "IN_PROGRESS", row.row.status)
        assertEquals("快照写不许顺手给 finishedAt 赋值", null, row.row.finishedAt)
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
