package cz.insuit.moneta;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A query string, built a parameter at a time and encoded once. Immutable: {@link #with} returns a new one, so a
 * base query can be shared across pages.
 */
record Query(List<Map.Entry<String, String>> parameters) {

    static final Query NONE = new Query(List.of());

    Query {
        parameters = List.copyOf(parameters);
    }

    Query with(String name, Object value) {
        var next = new ArrayList<>(parameters);
        next.add(Map.entry(name, String.valueOf(value)));
        return new Query(next);
    }

    /** The path with this query appended; the path alone when there are no parameters. */
    String appendTo(String path) {
        if (parameters.isEmpty()) {
            return path;
        }
        return parameters.stream()
                .map(p -> encode(p.getKey()) + "=" + encode(p.getValue()))
                .collect(Collectors.joining("&", path + "?", ""));
    }

    /** Percent-encodes a query name or value, or one path segment: the one encoding the library uses. */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
