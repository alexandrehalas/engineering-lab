package br.com.halas.investment_simulator.quote.domain.policy;

import br.com.halas.investment_simulator.quote.domain.model.Ticker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedAssetCatalogTest {

    private final SupportedAssetCatalog catalog = new SupportedAssetCatalog();

    @Test
    void supportsPetr4() {
        assertTrue(catalog.supports(new Ticker("PETR4")));
    }

    @Test
    void supportsVale3() {
        assertTrue(catalog.supports(new Ticker("VALE3")));
    }

    @Test
    void supportsItub4() {
        assertTrue(catalog.supports(new Ticker("ITUB4")));
    }

    @Test
    void doesNotSupportUnknownTicker() {
        assertFalse(catalog.supports(new Ticker("ABCD9")));
    }
}
