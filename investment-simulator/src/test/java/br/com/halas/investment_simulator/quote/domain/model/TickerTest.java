package br.com.halas.investment_simulator.quote.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickerTest {

    @Test
    void preservesCode() {
        var ticker = new Ticker("PETR4");

        assertEquals("PETR4", ticker.code());
    }

    @Test
    void rejectsNullCode() {
        assertThrows(NullPointerException.class, () -> new Ticker(null));
    }

    @Test
    void rejectsEmptyCode() {
        assertThrows(IllegalArgumentException.class, () -> new Ticker(""));
    }

    @Test
    void allowsCodeOutsideSupportedAssetCatalog() {
        var ticker = new Ticker("ABCD9");

        assertEquals("ABCD9", ticker.code());
    }
}
