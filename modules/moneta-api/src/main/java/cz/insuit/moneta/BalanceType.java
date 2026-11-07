package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

record BalanceType(CodeOrProprietary codeOrProprietary) {
    BalanceType {
        requireNonNull(codeOrProprietary, "codeOrProprietary");
    }
}
