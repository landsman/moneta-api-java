package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

/**
 * The token a merchant issues in Internet Banka. A type of its own so it cannot be swapped with an account id,
 * which would put it in a URL, and so its {@code toString()} never shows it.
 */
public record AccessToken(String value) {

    /**
     * @throws IllegalArgumentException when the value is empty or holds a space or a control character, as one
     *     read from a file or an environment variable often ends in a newline; the message does not repeat it
     */
    public AccessToken {
        requireNonNull(value, "value");
        // The JDK's own header check would quote the whole value in its exception, so it is refused here first.
        if (value.isEmpty() || !value.chars().allMatch(c -> c > 0x20 && c < 0x7F)) {
            throw new IllegalArgumentException("The token is empty or holds a space or a control character");
        }
    }

    @Override
    public String toString() {
        return "AccessToken[redacted]";
    }
}
