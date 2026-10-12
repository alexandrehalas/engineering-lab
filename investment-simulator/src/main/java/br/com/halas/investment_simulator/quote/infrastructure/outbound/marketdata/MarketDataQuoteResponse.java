package br.com.halas.investment_simulator.quote.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketDataQuoteResponse(
        String symbol,
        BigDecimal lastPrice,
        String currency,
        Instant timestamp
) {
}
