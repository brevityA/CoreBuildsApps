package tv.corebuilds.eq

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `@kind/name` in `res/` and every `R.kind.name` in `src/main/kotlin`
 * must resolve to something the app actually declares.
 *
 * This is a link-time check that normally only aapt2 performs, run here
 * because the unit tests are the only build this project can execute without
 * the Android SDK. It exists because 1.7.0's A/B button was written against
 * `@drawable/bg_ghost` and `@color/ghost_text` from memory: both happened to
 * exist, but nothing in the harness would have said so if they had not, and a
 * missing colour fails a release build with one line about a resource name.
 *
 * Deliberately not covered: `@android:`/`@androidx:` references (the platform
 * and the libraries, not this repo), `?attr/` theme attributes, and ids
 * declared by libraries. Those are the cases where a name can be absent from
 * these files and still be correct.
 */
class ResourceReferencesTest {

    private val res: File = locateRes()

    @Test
    fun everyXmlResourceReferenceIsDefined() {
        val definitions = definitions()
        val missing = mutableListOf<String>()
        res.walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { file ->
            val text = file.readText()
            referencePattern.findAll(text).forEach { match ->
                val kind = match.groupValues[2]
                val name = match.groupValues[3]
                if (name.startsWith("android:") || name.startsWith("*android:")) return@forEach
                val declared = definitions[kind] ?: return@forEach
                if (name !in declared) {
                    missing += "@$kind/$name in ${file.relativeTo(res)}"
                }
            }
        }
        assertEquals("resources referenced but never declared:\n${missing.joinToString("\n")}", 0, missing.size)
    }

    @Test
    fun everyKotlinResourceReferenceIsDefined() {
        val source = res.parentFile.resolve("kotlin")
        assertTrue("no Kotlin source at ${source.absolutePath}", source.isDirectory)
        val definitions = definitions()
        val missing = mutableListOf<String>()
        source.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            // `android.R.string.ok` is the platform's resource, not this app's.
            val text = file.readText().replace(ANDROID_R, "")
            kotlinReferencePattern.findAll(text).forEach { match ->
                val kind = match.groupValues[1]
                val name = match.groupValues[2]
                val declared = definitions[kind] ?: return@forEach
                if (name !in declared) {
                    missing += "R.$kind.$name in ${file.relativeTo(source)}"
                }
            }
        }
        assertEquals("resources referenced from Kotlin but never declared:\n${missing.joinToString("\n")}", 0, missing.size)
    }

    /** The A/B switch must stay reachable: every string it needs has to exist. */
    @Test
    fun theAbSwitchStringsExist() {
        val strings = definitions()["string"] ?: emptySet()
        for (name in listOf(
            "ab_hear_bypass",
            "ab_hear_corrected",
            "ab_bypass_note",
            "status_bypass_on",
            "status_bypass_off"
        )) {
            assertTrue("missing string/$name", name in strings)
        }
    }

    private fun definitions(): Map<String, Set<String>> {
        val found = mutableMapOf<String, MutableSet<String>>()
        fun add(kind: String, name: String) {
            found.getOrPut(kind) { mutableSetOf() }.add(name)
        }
        res.walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { file ->
            val text = file.readText()
            for (kind in VALUE_KINDS) {
                declarationPattern(kind).findAll(text).forEach { match ->
                    val name = match.groupValues[1]
                    add(kind, name)
                    // A dotted style name becomes an underscore constant.
                    add(kind, name.replace('.', '_'))
                }
            }
            idPattern.findAll(text).forEach { add("id", it.groupValues[1]) }
            // res/color/*.xml are colour state lists; they answer @color/name.
            if (file.parentFile.name == "color") add("color", file.nameWithoutExtension)
        }
        for (kind in FILE_KINDS) {
            val dir = File(res, kind)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile }.forEach { add(kind, it.name.substringBefore('.')) }
        }
        return found
    }

    private fun declarationPattern(kind: String) = Regex("<$kind[^>]*\\bname=\"([^\"]+)\"")

    private fun locateRes(): File {
        val direct = File("src/main/res")
        if (direct.isDirectory) return direct
        var cursor: File? = File("").absoluteFile
        repeat(6) {
            val candidate = cursor?.let { File(it, "coreeq/app/src/main/res") }
            if (candidate != null && candidate.isDirectory) return candidate
            val moduleLocal = cursor?.let { File(it, "app/src/main/res") }
            if (moduleLocal != null && moduleLocal.isDirectory) return moduleLocal
            cursor = cursor?.parentFile
        }
        throw IllegalStateException("could not locate src/main/res from ${File("").absolutePath}")
    }

    private companion object {
        const val ANDROID_R = """android\.R\.[a-z]+(\.[A-Za-z0-9_]+)?"""
        val referencePattern = Regex("""@(\+?)(color|string|dimen|drawable|style|layout|bool|integer|string-array|integer-array|array|fraction|plurals|xml|mipmap|anim|raw|font|menu)/([A-Za-z0-9_.]+)""")
        val idPattern = Regex("""@\+id/([A-Za-z0-9_]+)""")
        val kotlinReferencePattern = Regex("""(?<![\w.])R\.([a-z]+)\.([A-Za-z0-9_]+)""")
        val VALUE_KINDS = listOf("color", "string", "dimen", "style", "bool", "integer", "string-array", "integer-array", "array", "fraction", "plurals", "attr")
        val FILE_KINDS = listOf("drawable", "layout", "xml", "mipmap", "anim", "raw", "font", "menu")
    }
}
