package org.fungalsentinel.app.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Static gate (Gate A3): **a template containing placeholders must not be fetched with zero arguments**.
 *
 * Background: on a real device, uploading a DNG in Step 1 crashed with
 * `MissingFormatArgumentException: Format specifier '%+1$.2f'`. The root cause was not an
 * illegal template (`%1$+.2f` itself is legal), but that **no argument at all was passed at the call site**.
 *
 * The exact chain: `ProjectViewModel.str()` used to be
 * `getString(id, *a)`. With zero arguments Kotlin's vararg still expands to
 * `getString(id, Object[0])`, which resolves to Android's **vararg overload**
 * `Resources.getString(int, Object...)` -- and that overload **unconditionally** runs
 * `String.format(locale, raw, formatArgs)`. A template with placeholders plus an empty array
 * throws. The single-argument `getString(int)` does not format, so the two differ in semantics.
 *
 * The crash site is the "fetch the template first, format later" style of
 * `String.format(Locale.US, str(R.string.X), …)`: the outer call never gets to pass arguments,
 * because the inner zero-argument fetch blows up first.
 *
 * This test scans every `.kt` file for zero-argument `str(R.string.KEY)` /
 * `getString(R.string.KEY)` calls and asserts that those KEYs contain no `%` placeholder in
 * the template of **every** locale (`%%` is stripped first).
 * The only exemption: the template-fetch idiom
 * `String.format(Locale.X, str(R.string.KEY), args…)` -- there the format arguments are
 * supplied by the outer call, and this test also checks their count in passing.
 */
class StringResourceArgCountTest {

    private companion object {
        val QUALIFIERS = listOf("values", "values-zh", "values-fr", "values-es", "values-ny")

        /** %[index$][flags][width][.precision]conversion -- the index group comes before the flags. */
        val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])""")

        const val MIN_FILES = 20
        const val MIN_CALLS = 50
    }

    /** One unclosed `(` along with the callee name and the start offset of each top-level argument. */
    private class Frame(val callee: String, val open: Int) {
        var depth = 1
        var close: Int = -1
        val argStarts = mutableListOf(open + 1)
    }

    private data class Fetch(
        val file: File,
        val line: Int,
        val callee: String,
        val key: String,
        val selfArgs: Int,
        val parent: Frame?,
        val parentArgIndex: Int,
        val isTemplateArgOfStringFormat: Boolean
    )

    // ── Main gate ─────────────────────────────────────────────────────────
    @Test
    fun zeroArgumentFetchesNeverTargetTemplatesWithPlaceholders() {
        val catalog = loadCatalog()
        val fetches = kotlinFiles().flatMap { scanFetches(it) }
        val violations = mutableListOf<String>()

        fetches.forEach { fetch ->
            val supplied = suppliedArgCount(fetch)
            val offenders = QUALIFIERS.mapNotNull { qualifier ->
                val template = catalog[qualifier]?.get(fetch.key)
                template?.takeIf { hasPlaceholder(it) }?.let { qualifier to it }
            }
            if (supplied < (offenders.firstOrNull()?.let { required(it.second) } ?: 0)) {
                violations += buildString {
                    append("${fetch.file.path}:${fetch.line}")
                    append("\n    call     : ${fetch.callee}(R.string.${fetch.key})")
                    append("\n    supplied : $supplied format arguments")
                    offenders.forEach { (qualifier, template) ->
                        append("\n    $qualifier : $template")
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            fail(
                "${violations.size} placeholders-containing templates were fetched without enough arguments.\n" +
                    "A zero-argument `getString(id)` does not format, so %1\$s in the template is shown to the user verbatim;\n" +
                    "a zero-argument `str(id, *emptyVararg)` throws MissingFormatArgumentException outright.\n" +
                    "Either supply the arguments or switch to String.format(Locale.US, str(R.string.X), args…).\n\n" +
                    violations.joinToString("\n\n")
            )
        }
    }

    // ── Gate self-check: make sure the scanner or the exemption logic has not quietly broken ──
    @Test
    fun scannerStillCoversTheCodebaseAndTheTemplateFetchIdiom() {
        val files = kotlinFiles()
        assertTrue("only ${files.size} Kotlin files were scanned, the static scan may be broken", files.size >= MIN_FILES)

        val fetches = files.flatMap { scanFetches(it) }
        assertTrue(
            "only ${fetches.size} str()/getString() calls were found, the scanner may be broken",
            fetches.size >= MIN_CALLS
        )
        assertTrue(
            "no String.format(Locale.X, str(…), …) template-fetch idiom was recognised, the exemption logic may be broken",
            fetches.any { it.isTemplateArgOfStringFormat }
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────
    private fun suppliedArgCount(fetch: Fetch): Int =
        if (fetch.isTemplateArgOfStringFormat) {
            // Arguments to the outer String.format = Locale + template + arguments to fill in
            fetch.parent?.argStarts?.size?.minus(2) ?: 0
        } else {
            fetch.selfArgs
        }

    private fun scanFetches(file: File): List<Fetch> {
        val source = file.readText()
        val code = blankNonCode(source) // bracket pairing only looks at code; strings/comments are blanked out
        val stack = ArrayDeque<Frame>()
        val found = mutableListOf<Fetch>()

        for (i in code.indices) {
            when (code[i]) {
                '(' -> {
                    if (stack.isNotEmpty()) stack.last().depth += 1
                    stack.addLast(Frame(calleeBefore(code, i), i))
                }
                ')' -> {
                    if (stack.isEmpty()) continue
                    val frame = stack.removeLast()
                    frame.close = i
                    if (stack.isNotEmpty()) stack.last().depth -= 1

                    val callee = frame.callee
                    if (!isResourceFetch(callee)) continue
                    val args = source.substring(frame.open + 1, frame.close)
                    val key = resourceKey(args) ?: continue
                    val parent = stack.lastOrNull()
                    val parentArgIndex = if (parent == null) {
                        -1
                    } else {
                        parent.argStarts.count { it <= frame.open } - 1
                    }
                    // With a Locale the template is argument 1 of the overload; without one it is argument 0
                    val isTemplateArg = parent != null &&
                        parent.callee.endsWith("String.format") &&
                        (parentArgIndex == 1 || parentArgIndex == 0)
                    found += Fetch(
                        file = file,
                        line = lineOf(source, frame.open),
                        callee = callee.substringAfterLast('.'),
                        key = key,
                        selfArgs = topLevelArgCount(args) - 1,
                        parent = parent.takeIf { isTemplateArg },
                        parentArgIndex = parentArgIndex,
                        isTemplateArgOfStringFormat = isTemplateArg
                    )
                }
                ',' -> {
                    if (stack.isNotEmpty() && stack.last().depth == 1) {
                        stack.last().argStarts.add(i + 1)
                    }
                }
            }
        }

        return found
    }

    /**
     * Blanks out line comments, block comments and string literals into equally long spaces
     * (keeping newlines), so the bracket pairing that follows only sees real code -- otherwise
     * the parentheses inside `"…%1$d (x)…"` would throw the pairing off.
     */
    private fun blankNonCode(src: String): String {
        val out = src.toCharArray()
        var i = 0
        while (i < src.length) {
            if (src.startsWith("//", i)) {
                while (i < src.length && src[i] != '\n') {
                    out[i] = ' '
                    i += 1
                }
            } else if (src.startsWith("/*", i)) {
                out[i] = ' '
                i += 1
                if (i < src.length) out[i] = ' '
                i += 1
                while (i + 1 < src.length && !(src[i] == '*' && src[i + 1] == '/')) {
                    if (src[i] != '\n') out[i] = ' '
                    i += 1
                }
                while (i < src.length && src[i] != '\n') {
                    out[i] = ' '
                    i += 1
                }
            } else if (src.startsWith("\"\"\"", i)) {
                for (k in 0..2) if (i + k < src.length) out[i + k] = ' '
                i += 3
                while (i < src.length && !src.startsWith("\"\"\"", i)) {
                    if (src[i] != '\n') out[i] = ' '
                    i += 1
                }
                for (k in 0..2) if (i + k < src.length) out[i + k] = ' '
                i += 3
            } else if (src[i] == '"') {
                out[i] = ' '
                i += 1
                while (i < src.length && src[i] != '"') {
                    if (src[i] == '\\') {
                        out[i] = ' '
                        i += 1
                    }
                    if (i < src.length && src[i] != '\n') out[i] = ' '
                    i += 1
                }
                if (i < src.length) {
                    out[i] = ' '
                    i += 1
                }
            } else {
                i += 1
            }
        }
        return String(out)
    }

    private fun isResourceFetch(callee: String): Boolean =
        callee == "str" || callee == "getString" ||
            callee.endsWith(".str") || callee.endsWith(".getString")

    /** Whether the argument list contains a placeholder (`%%` is a literal percent sign and is stripped first). */
    private fun hasPlaceholder(template: String): Boolean =
        "%" in template.replace("%%", "")

    /** The largest placeholder index declared by the template; `%s` (no index) counts as 1. */
    private fun required(template: String): Int {
        var max = 0
        var i = 0
        while (i < template.length) {
            if (template[i] == '%') {
                val match = PLACEHOLDER.matchAt(template, i)
                if (match == null) {
                    i += 1
                    continue
                }
                if (match.groupValues[2] != "%") {
                    val index = match.groupValues[1].toIntOrNull() ?: 1
                    if (index > max) max = index
                }
                i = match.range.last + 1
            } else {
                i += 1
            }
        }
        return max
    }

    /** Number of top-level arguments (ignoring commas inside quotes and nested brackets). */
    private fun topLevelArgCount(text: String): Int {
        var depth = 0
        var count = 1
        var quote: Char? = null
        var escaped = false
        for (c in text) {
            when {
                quote != null -> {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == quote) quote = null
                }
                c == '"' -> quote = '"'
                c == '(' || c == '[' || c == '{' -> depth += 1
                c == ')' || c == ']' || c == '}' -> depth -= 1
                c == ',' && depth == 0 -> count += 1
            }
        }
        return count
    }

    /** The `str(R.string.X)` / `getString(R.string.X)` form: return the key, otherwise null. */
    private fun resourceKey(args: String): String? {
        val trimmed = args.trim()
        val match = RESOURCE_ONLY.matchEntire(trimmed) ?: return null
        return match.groupValues[1]
    }

    private val RESOURCE_ONLY = Regex("""R\.string\.(\w+)""")

    private fun calleeBefore(code: String, openIdx: Int): String {
        var j = openIdx - 1
        while (j >= 0 && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.' || code[j] == '$')) {
            j -= 1
        }
        return code.substring(j + 1, openIdx).trim()
    }

    private fun lineOf(source: String, idx: Int): Int =
        source.substring(0, idx).count { it == '\n' } + 1

    private fun loadCatalog(): Map<String, Map<String, String>> {
        val factory = DocumentBuilderFactory.newInstance()
        return QUALIFIERS.associateWith { qualifier ->
            val file = File(resRoot(), "$qualifier/strings.xml")
            assertTrue("resource file not found: ${file.path}", file.isFile)
            val document = factory.newDocumentBuilder().parse(file)
            document.documentElement.normalize()
            val nodes = document.getElementsByTagName("string")
            val entries = LinkedHashMap<String, String>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                entries[node.attributes.getNamedItem("name").nodeValue] = node.textContent ?: ""
            }
            entries
        }
    }

    /**
     * The working directory of JVM unit tests is the module directory (`app/`), but it differs
     * between runners, so walk upwards level by level until
     * `src/main/res/values/strings.xml` is hit.
     */
    private fun resRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/res/values/strings.xml").isFile) {
                return File(dir, "src/main/res")
            }
            dir = dir.parentFile
        }
        throw AssertionError("src/main/res not found -- the JVM unit test working directory is not the Android module directory")
    }

    private fun kotlinFiles(): List<File> = File(resRoot().parentFile, "java")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
}
