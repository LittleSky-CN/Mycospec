package org.fungalsentinel.app.data.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `canonicalFluorophore()` 的契约测试。
 *
 * 背景：Android 与 Python FSSA 互相导出/导入曲线预设，两侧的荧光素写法不一致。
 * canonical ID 定为 Android enum name（`YPET` / `MCHERRY` / …），所以读取端必须
 * 能把 Python 的写法解析过来，否则下拉会静默 fallback 成「请选择荧光物质」。
 */
class FluorophoreCanonicalTest {

    // ── 1. canonical 本身 ────────────────────────────────────────────────
    @Test
    fun resolvesCanonicalEnumNamesInAnyCase() {
        Fluorophore.values().forEach { expected ->
            assertEquals(expected, canonicalFluorophore(expected.name))
            assertEquals(expected, canonicalFluorophore(expected.name.lowercase()))
            assertEquals(expected, canonicalFluorophore(expected.name.uppercase()))
            assertEquals(expected, canonicalFluorophore("  ${expected.name}  "))
        }
    }

    @Test
    fun resolvesOwnDisplayNameForEveryCategory() {
        // 6 类必须全覆盖 —— 少一类就是某一支预设读不进来
        assertEquals(Fluorophore.YPET, canonicalFluorophore("Ypet"))
        assertEquals(Fluorophore.EGFP, canonicalFluorophore("EGFP"))
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore("mCherry"))
        assertEquals(Fluorophore.CFP, canonicalFluorophore("CFP"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("mTurquoise2"))
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("Fluorescein"))
    }

    // ── 2. 跨端：Python FSSA 的 key / 显示名 ────────────────────────────
    @Test
    fun resolvesPythonFssaKeysWhenItStillShipsOldNames() {
        assertEquals(Fluorophore.EGFP, canonicalFluorophore("EGFP"))
        assertEquals(Fluorophore.CFP, canonicalFluorophore("CFP"))
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore("mCherry"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("mTurquoise2"))
    }

    @Test
    fun resolvesPythonFssaDisplayNames() {
        assertEquals(Fluorophore.YPET, canonicalFluorophore("Ypet (Venus variant)"))
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("Fluorescein (sodium salt)"))
        // 大小写 + 括号空格的脏写法同样要认
        assertEquals(Fluorophore.YPET, canonicalFluorophore("YPET (VENUS VARIANT)"))
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("fluorescein(sodium salt)"))
    }

    @Test
    fun resolvesCommonIdiomVariants() {
        assertEquals(Fluorophore.FLUORESCEIN, canonicalFluorophore("fluorescein"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("m-turquoise2"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("mturquoise2"))
        assertEquals(Fluorophore.MTURQUOISE2, canonicalFluorophore("M_Turquoise2"))
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore("mcherry"))
        assertEquals(Fluorophore.EGFP, canonicalFluorophore("egfp"))
    }

    // ── 3. 认不出来必须返回 null，交给调用方明确报错 ────────────────────
    @Test
    fun unrecognisedInputReturnsNullInsteadOfAFallback() {
        assertNull(canonicalFluorophore(null))
        assertNull(canonicalFluorophore(""))
        assertNull(canonicalFluorophore("   "))
        assertNull(canonicalFluorophore("---"))
        assertNull(canonicalFluorophore("DsRed"))
        assertNull(canonicalFluorophore("mCherri"))   // 差一个字母，不许模糊命中
        assertNull(canonicalFluorophore("mCherry2"))
        assertNull(canonicalFluorophore("sampleFluorophore#60"))
    }

    // ── 4. 曲线预设的 fluorophore 字段走同一条解析路 ────────────────────
    @Test
    fun pythonExportedPresetFluorophoreFieldIsResolvable() {
        val json = """
            {
              "version": 1,
              "fluorophore": "mCherry",
              "slope": 2.0, "intercept": 1.0, "rSquared": 0.99,
              "standardPoints": [
                {"concentration": 0.0, "signal": 1.0, "unit": "a.u."},
                {"concentration": 1.0, "signal": 3.0, "unit": "a.u."}
              ],
              "wavelengthsNm": [500.0, 510.0, 520.0],
              "referenceEmission": [0.0, 10.0, 0.0],
              "referenceAuc": 100.0,
              "windowMinNm": 500.0, "windowMaxNm": 520.0,
              "rbSwapped": false
            }
        """.trimIndent()
        val payload = org.fungalsentinel.app.data.preset.CurvePresetPayload.fromJson(json)
        assertEquals("mCherry", payload.fluorophore)          // 落盘保持原文
        assertEquals(Fluorophore.MCHERRY, canonicalFluorophore(payload.fluorophore))
        assertEquals("MCHERRY", canonicalFluorophore(payload.fluorophore)!!.name)
        assertEquals("590–630 nm", canonicalFluorophore(payload.fluorophore)!!.integrationRangeNm)
    }

    // ── 5. 两份 enum 已合并为一份 ───────────────────────────────────────
    @Test
    fun onlyOneFluorophoreEnumIsDeclaredInTheApp() {
        val declarations = sourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> line.contains("enum class Fluorophore") }
                    .map { (index, _) -> "${file.path.replace('\\', '/')}:${index + 1}" }
            }
            .toList()
        assertEquals(
            "Fluorophore 必须只有一份声明（唯一真源）；实际有 ${declarations.size} 份：$declarations",
            1, declarations.size
        )
        assertEquals(
            "唯一的那份必须在数据层 Entities.kt",
            "org/fungalsentinel/app/data/model/Entities.kt",
            declarations.single().substringAfter("main/java/").substringBefore(":")
        )
    }

    @Test
    fun step3ScreenImportsTheDataLayerEnum() {
        val screen = File(sourceRoot(), "org/fungalsentinel/app/ui/steps/Step3SampleScreen.kt")
        val text = screen.readText()
        assertEquals(true, text.contains("import org.fungalsentinel.app.data.model.Fluorophore"))
        assertEquals(true, text.contains("import org.fungalsentinel.app.data.model.canonicalFluorophore"))
        assertEquals(false, text.contains("enum class Fluorophore"))
        assertEquals(false, text.contains("bandNm"))
    }

    private fun sourceRoot(): File {
        val start = System.getProperty("user.dir") ?: "."
        var dir = File(start)
        while (!File(dir, "src/main/java").isDirectory) {
            dir = dir.parentFile ?: error("Cannot locate src/main/java from $start")
        }
        return File(dir, "src/main/java")
    }
}
