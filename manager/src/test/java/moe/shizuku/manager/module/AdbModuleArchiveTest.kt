package moe.shizuku.manager.module

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbModuleArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `copyStreamBounded copies within budget and aborts mid-stream when limit is exceeded`() {
        val payload = ByteArray(16 * 1024) { (it and 0xFF).toByte() }
        val out = ByteArrayOutputStream()
        val copied = AdbModuleManager.copyStreamBounded(
            ByteArrayInputStream(payload),
            out,
            maxBytes = 16 * 1024L
        )
        assertEquals(16 * 1024L, copied)
        assertArrayEquals(payload, out.toByteArray())

        val oversizedOut = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            AdbModuleManager.copyStreamBounded(
                ByteArrayInputStream(payload),
                oversizedOut,
                maxBytes = 4096L
            )
        }
        assertTrue("Must abort before writing entire oversized stream", oversizedOut.size() <= 4096)
    }

    @Test
    fun `cleanZipName normalizes safe relative paths and rejects traversal segments`() {
        assertEquals("common/service.sh", AdbModuleManager.cleanZipName("/common\\service.sh/"))
        assertEquals("module.prop", AdbModuleManager.cleanZipName("module.prop"))

        for (unsafe in listOf("../evil.sh", "a/../../b", "a/./b", "/", "   ")) {
            assertThrows("Expected rejection for '$unsafe'", IllegalArgumentException::class.java) {
                AdbModuleManager.cleanZipName(unsafe)
            }
        }
    }

    @Test
    fun `ensureInside blocks canonical path escapes outside staging directory`() {
        val root = tempFolder.newFolder("staging")
        val inside = File(root, "webroot/index.html")
        AdbModuleManager.ensureInside(root, inside)

        val outside = File(root, "../escaped.sh")
        assertThrows(IllegalArgumentException::class.java) {
            AdbModuleManager.ensureInside(root, outside)
        }
    }

    @Test
    fun `parseModuleProp parses key-value pairs and ignores comments and blank lines`() {
        val raw = """
            # Module descriptor
            id = night_perf
            name=Night Performance Pack
            version = v1.4.2
            versionCode=142
            usesShellBridge = true
        """.trimIndent()

        val parsed = AdbModuleManager.parseModuleProp(raw)
        assertEquals("night_perf", parsed["id"])
        assertEquals("Night Performance Pack", parsed["name"])
        assertEquals("v1.4.2", parsed["version"])
        assertEquals("142", parsed["versionCode"])
        assertEquals("true", parsed["usesShellBridge"])
    }

    @Test
    fun `verifyModuleHostRequirements enforces Nightzuku runtime and API minimums`() {
        ModuleRuntimeUtils.verifyModuleHostRequirements(
            mapOf(
                "id" to "night_perf",
                "requireNightzuku" to "true",
                "minNightzukuApi" to "1",
                "minNightzukuVersion" to "13"
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            ModuleRuntimeUtils.verifyModuleHostRequirements(
                mapOf(
                    "id" to "future_mod",
                    "minNightzukuApi" to "999"
                )
            )
        }
    }

    @Test
    fun `buildHostAttestationEnv and writeHostAttestationFile produce deterministic verifiable tokens`() {
        val modDir = tempFolder.newFolder("mod_test")
        val env = ModuleRuntimeUtils.buildHostAttestationEnv("mod_test")

        assertEquals("kerneldroid.nightzuku", env["NIGHTZUKU_HOST_PACKAGE"])
        assertEquals("kerneldroid.nightzuku.INightService", env["NIGHTZUKU_WIRE_PROTOCOL"])
        assertTrue(env["NIGHTZUKU_HOST_TOKEN"]!!.length == 64)

        val response = ModuleRuntimeUtils.computeChallengeToken("mod_test", "nonce-123")
        val otherResponse = ModuleRuntimeUtils.computeChallengeToken("mod_test", "nonce-456")
        assertEquals(64, response.length)
        assertFalse(response == otherResponse)

        val envFile = ModuleRuntimeUtils.writeHostAttestationFile(modDir, "mod_test")
        assertTrue(envFile.isFile)
        val envContent = envFile.readText()
        assertTrue(envContent.contains("NIGHTZUKU_HOST_PACKAGE='kerneldroid.nightzuku'"))
        assertTrue(envContent.contains("NIGHTZUKU_HOST_TOKEN='${env["NIGHTZUKU_HOST_TOKEN"]}'"))
    }
}
