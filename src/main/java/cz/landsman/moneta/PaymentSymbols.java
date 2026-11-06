package cz.landsman.moneta;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The Czech payment symbols of a transfer. MONETA sends all three in one string, {@code "VS:123456 KS:0308
 * SS:0001"}, separated by spaces or by commas depending on the source. Leading zeros are kept: a symbol is
 * an identifier, not a number.
 */
public record PaymentSymbols(@Nullable String variable, @Nullable String constant, @Nullable String specific) {

    private static final Pattern VARIABLE = symbol("VS");
    private static final Pattern CONSTANT = symbol("KS");
    private static final Pattern SPECIFIC = symbol("SS");

    /** Reads the symbols out of MONETA's reference string; a missing one is null. */
    public static PaymentSymbols parse(@Nullable String reference) {
        if (reference == null) {
            return new PaymentSymbols(null, null, null);
        }
        return new PaymentSymbols(find(VARIABLE, reference), find(CONSTANT, reference), find(SPECIFIC, reference));
    }

    private static Pattern symbol(String name) {
        return Pattern.compile("\\b" + name + ":\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    }

    private static @Nullable String find(Pattern pattern, String reference) {
        var match = pattern.matcher(reference);
        return match.find() ? match.group(1) : null;
    }
}
