package br.com.halas.investment_simulator.quote.domain.model;

import java.util.Objects;

public record Ticker(
        String code
) {

    public Ticker {
        Objects.requireNonNull(code, "Code must not be null");

        if (code.isEmpty()) {
            throw new IllegalArgumentException("Code must not be empty");
        }
    }

}
