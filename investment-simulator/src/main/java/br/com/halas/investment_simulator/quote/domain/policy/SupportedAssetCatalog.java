package br.com.halas.investment_simulator.quote.domain.policy;

import br.com.halas.investment_simulator.quote.domain.model.Ticker;

import java.util.Set;

public class SupportedAssetCatalog {

    private final Set<Ticker> supportedTickers = Set.of(
            new Ticker("PETR4"),
            new Ticker("VALE3"),
            new Ticker("ITUB4")
    );

    public boolean supports(Ticker ticker) {
        return supportedTickers.contains(ticker);
    }
}
