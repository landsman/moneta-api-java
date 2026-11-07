package cz.insuit.moneta.internal.json;

import static java.util.Objects.requireNonNull;

public record BalanceType(CodeOrProprietary codeOrProprietary) {
    public BalanceType {
        requireNonNull(codeOrProprietary, "codeOrProprietary");
    }
}
