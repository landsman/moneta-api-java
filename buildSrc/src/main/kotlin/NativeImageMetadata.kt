import java.io.File

/**
 * GraalVM reachability metadata registering [types] for reflection: Jackson binds a record through its constructor,
 * accessors and fields, and a native image keeps only what its metadata names.
 */
fun reachabilityMetadata(types: Collection<String>): String {
    val entries =
        types.sorted().joinToString(",\n") {
            // language=JSON
            """    {"type": "$it", "allDeclaredConstructors": true, "allDeclaredMethods": true, "allDeclaredFields": true}"""
        }
    // Interpolated after trimIndent, which would otherwise measure the entries' indent too; the placeholder keeps
    // the template valid JSON for the editor.
    // language=JSON
    val template =
        """
        {
          "reflection": ["ENTRIES"]
        }
        """.trimIndent()
    return template.replace("[\"ENTRIES\"]", "[\n$entries\n  ]") + "\n"
}

/** Class names of the top-level `*Json.java` sources in [dir], which holds the package [packageName]. */
fun jsonRecords(
    dir: File,
    packageName: String,
): List<String> =
    dir
        .listFiles { file -> file.name.endsWith("Json.java") }
        .orEmpty()
        .map { "$packageName.${it.nameWithoutExtension}" }
