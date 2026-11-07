package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

record BalanceTypeJson(CodeOrProprietaryJson codeOrProprietary) {
    BalanceTypeJson {
        requireNonNull(codeOrProprietary, "codeOrProprietary");
    }
}
