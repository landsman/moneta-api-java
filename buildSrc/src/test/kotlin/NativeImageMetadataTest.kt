import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NativeImageMetadataTest {
    @Test
    fun `registers each type for reflection, sorted`() {
        assertEquals(
            // language=JSON
            """
            {
              "reflection": [
                {"type": "a.AJson", "allDeclaredConstructors": true, "allDeclaredMethods": true, "allDeclaredFields": true},
                {"type": "a.BJson", "allDeclaredConstructors": true, "allDeclaredMethods": true, "allDeclaredFields": true}
              ]
            }

            """.trimIndent(),
            reachabilityMetadata(listOf("a.BJson", "a.AJson")),
        )
    }

    @Test
    fun `takes only the json sources`(
        @TempDir dir: File,
    ) {
        listOf("AccountJson.java", "MonetaClient.java", "AccountJsonTest.java", "notes.txt").forEach {
            dir.resolve(it).writeText("")
        }

        assertEquals(listOf("cz.insuit.moneta.AccountJson"), jsonRecords(dir, "cz.insuit.moneta"))
    }
}
