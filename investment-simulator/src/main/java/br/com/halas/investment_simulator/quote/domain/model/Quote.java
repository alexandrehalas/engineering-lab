package br.com.halas.investment_simulator.quote.domain.model;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record Quote(
        Ticker ticker,
        Money money,
        Instant referenceTime
) {

    public Quote {
        Objects.requireNonNull(ticker, "Ticker must not be null");
        Objects.requireNonNull(money, "Money must not be null");
        Objects.requireNonNull(referenceTime, "Reference time must not be null");

        if (money.amount().signum() <= 0) {
            throw new IllegalArgumentException("Money must be positive");
        }

        if (!Currency.getInstance("BRL").equals(money.currency())) {
            throw new IllegalArgumentException("Quote currency must be BRL");
        }
    }

}
