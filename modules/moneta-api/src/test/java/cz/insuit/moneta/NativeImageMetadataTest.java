package cz.insuit.moneta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NativeImageMetadataTest {

    private static final String METADATA = "/META-INF/native-image/cz.insuit/moneta-api/reachability-metadata.json";

    /** A *Json type missing from the file binds on the JVM and fails only inside a native image. */
    @Test
    void registersEveryJsonTypeForReflection() throws IOException, URISyntaxException {
        JsonNode metadata;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream(METADATA))) {
            metadata = JsonMapper.builder().build().readTree(in);
        }
        var registered = new TreeSet<String>();
        metadata.get("reflection")
                .forEach(entry -> registered.add(entry.get("type").asString()));

        assertEquals(jsonTypes(), registered);
    }

    private static Set<String> jsonTypes() throws IOException, URISyntaxException {
        var classes = Path.of(AccountJson.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolve("cz/insuit/moneta");
        try (var files = Files.list(classes)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith("Json.class"))
                    .map(name -> "cz.insuit.moneta." + name.substring(0, name.length() - ".class".length()))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
