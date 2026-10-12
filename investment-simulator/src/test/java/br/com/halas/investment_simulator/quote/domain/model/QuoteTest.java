package br.com.halas.investment_simulator.quote.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuoteTest {

    private static final Ticker TICKER = new Ticker("PETR4");
    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Money REFERENCE_PRICE = new Money(new BigDecimal("35.50"), BRL);
    private static final Instant REFERENCE_TIME = Instant.parse("2026-10-10T14:30:00Z");

    @Test
    void preservesValidQuoteComponents() {
        var quote = new Quote(TICKER, REFERENCE_PRICE, REFERENCE_TIME);

        assertAll(
                () -> assertEquals(TICKER, quote.ticker()),
                () -> assertEquals(REFERENCE_PRICE, quote.money()),
                () -> assertEquals(REFERENCE_TIME, quote.referenceTime())
        );
    }

    @Test
    void rejectsNullTicker() {
        assertThrows(NullPointerException.class,
                () -> new Quote(null, REFERENCE_PRICE, REFERENCE_TIME));
    }

    @Test
    void rejectsNullMoney() {
        assertThrows(NullPointerException.class,
                () -> new Quote(TICKER, null, REFERENCE_TIME));
    }

    @Test
    void rejectsNullReferenceTime() {
        assertThrows(NullPointerException.class,
                () -> new Quote(TICKER, REFERENCE_PRICE, null));
    }

    @Test
    void rejectsZeroPrice() {
        var money = new Money(new BigDecimal("0.00"), BRL);

        assertThrows(IllegalArgumentException.class,
                () -> new Quote(TICKER, money, REFERENCE_TIME));
    }

    @Test
    void rejectsNegativePrice() {
        var money = new Money(new BigDecimal("-0.01"), BRL);

        assertThrows(IllegalArgumentException.class,
                () -> new Quote(TICKER, money, REFERENCE_TIME));
    }

    @Test
    void rejectsCurrencyOtherThanBrl() {
        var money = new Money(new BigDecimal("35.50"), Currency.getInstance("USD"));

        assertThrows(IllegalArgumentException.class,
                () -> new Quote(TICKER, money, REFERENCE_TIME));
    }

    @Test
    void acceptsOldReferenceTime() {
        var oldReferenceTime = Instant.parse("2000-01-01T00:00:00Z");

        var quote = new Quote(TICKER, REFERENCE_PRICE, oldReferenceTime);

        assertAll(
                () -> assertEquals(REFERENCE_PRICE, quote.money()),
                () -> assertEquals(oldReferenceTime, quote.referenceTime())
        );
    }

    @Test
    void acceptsPriceSmallerThanOneCent() {
        var money = new Money(new BigDecimal("0.0001"), BRL);

        var quote = new Quote(TICKER, money, REFERENCE_TIME);

        assertEquals(money, quote.money());
    }
}
