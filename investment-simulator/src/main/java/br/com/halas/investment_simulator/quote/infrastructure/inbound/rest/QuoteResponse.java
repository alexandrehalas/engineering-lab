package br.com.halas.investment_simulator.quote.infrastructure.inbound.rest;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteResponse(
        String ticker,
        BigDecimal referencePrice,
        String currency,
        Instant referenceTime
) {
}
