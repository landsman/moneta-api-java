package example.kotlin

import cz.insuit.moneta.MonetaException
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/** What a Kotlin caller sees of the JSpecify annotations and the sealed exception. */
class KotlinCallerTest {
    @Test
    fun aWhenOverEveryFailureNeedsNoElse() {
        // Compiles only while Kotlin sees MonetaException as sealed.
        fun action(failure: MonetaException): String =
            when (failure) {
                is MonetaException.InvalidToken -> "ask for a new token"
                is MonetaException.RateLimited -> "wait"
                is MonetaException.HttpError -> "report"
                is MonetaException.NoAnswer -> "retry"
                is MonetaException.InvalidResponse -> "report"
            }

        assertEquals("wait", action(MonetaException.RateLimited("slow", null)))
    }

    @Test
    fun aNonNullComponentNeedsNoSafeCall() {
        assertEquals(listOf<String>(), compile("fun f(a: cz.insuit.moneta.Account): Int = a.id.length"))
    }

    @Test
    fun aNullableComponentCannotBeReadAsNonNull() {
        val errors = compile("fun f(a: cz.insuit.moneta.Account): Int = a.iban.length")
        assertTrue(errors.isNotEmpty(), "iban read as non-null compiled, so Kotlin sees it as a platform type")
    }

    /** The errors compiling [source] against the library gives, empty when it compiles. */
    private fun compile(source: String): List<String> {
        val dir = createTempDirectory()
        val file = dir.resolve("Caller.kt").apply { writeText(source) }
        val errors = mutableListOf<String>()
        val collector =
            object : MessageCollector {
                override fun clear() = errors.clear()

                override fun hasErrors() = errors.isNotEmpty()

                override fun report(
                    severity: CompilerMessageSeverity,
                    message: String,
                    location: CompilerMessageSourceLocation?,
                ) {
                    if (severity.isError) errors += message
                }
            }
        val arguments =
            K2JVMCompilerArguments().apply {
                freeArgs = listOf(file.toString())
                classpath = System.getProperty("java.class.path")
                destination = dir.resolve("out").toString()
                noStdlib = true
                noReflect = true
            }
        K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
        return errors
    }
}
