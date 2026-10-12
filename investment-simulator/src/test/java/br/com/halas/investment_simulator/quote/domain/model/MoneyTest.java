package br.com.halas.investment_simulator.quote.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void preservesAmountAndCurrency() {
        var amount = new BigDecimal("35.50");
        var currency = Currency.getInstance("BRL");

        var money = new Money(amount, currency);

        assertAll(
                () -> assertEquals(0, amount.compareTo(money.amount())),
                () -> assertEquals(currency, money.currency())
        );
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class,
                () -> new Money(null, Currency.getInstance("BRL")));
    }

    @Test
    void rejectsNullCurrency() {
        assertThrows(NullPointerException.class,
                () -> new Money(new BigDecimal("35.5"), null));
    }

    @Test
    void considersDifferentScalesEqual() {
        var currency = Currency.getInstance("BRL");
        var first = new Money(new BigDecimal("35.5"), currency);
        var second = new Money(new BigDecimal("35.50"), currency);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode())
        );
    }

    @Test
    void preservesPrecision() {
        var currency = Currency.getInstance("BRL");
        var precise = new Money(new BigDecimal("35.501"), currency);
        var rounded = new Money(new BigDecimal("35.50"), currency);

        assertAll(
                () -> assertEquals(new BigDecimal("35.501"), precise.amount()),
                () -> assertNotEquals(precise, rounded)
        );
    }

    @Test
    void allowsZeroAmount() {
        var money = new Money(new BigDecimal("0.00"), Currency.getInstance("BRL"));

        assertEquals(0, BigDecimal.ZERO.compareTo(money.amount()));
    }

    @Test
    void allowsNegativeAmount() {
        var amount = new BigDecimal("-35.50");

        var money = new Money(amount, Currency.getInstance("BRL"));

        assertEquals(0, amount.compareTo(money.amount()));
    }

    @Test
    void distinguishesDifferentCurrencies() {
        var amount = new BigDecimal("35.5");
        var brl = new Money(amount, Currency.getInstance("BRL"));
        var usd = new Money(amount, Currency.getInstance("USD"));

        assertNotEquals(brl, usd);
    }

    @Test
    void distinguishesDifferentAmounts() {
        var currency = Currency.getInstance("BRL");
        var first = new Money(new BigDecimal("35.5"), currency);
        var second = new Money(new BigDecimal("35.6"), currency);

        assertNotEquals(first, second);
    }
}
