package br.com.halas.investment_simulator.quote.application;

import br.com.halas.investment_simulator.quote.application.port.QuoteProvider;
import br.com.halas.investment_simulator.quote.application.exception.UnsupportedTickerException;
import br.com.halas.investment_simulator.quote.domain.model.Quote;
import br.com.halas.investment_simulator.quote.domain.model.Ticker;
import br.com.halas.investment_simulator.quote.domain.policy.SupportedAssetCatalog;

import java.util.Objects;
import java.util.Optional;

public class GetQuoteUseCase {

    private final SupportedAssetCatalog supportedAssetCatalog;
    private final QuoteProvider quoteProvider;

    public GetQuoteUseCase(SupportedAssetCatalog supportedAssetCatalog, QuoteProvider quoteProvider) {
        this.supportedAssetCatalog = Objects.requireNonNull(supportedAssetCatalog, "Supported asset catalog must not be null");
        this.quoteProvider = Objects.requireNonNull(quoteProvider, "Quote provider must not be null");
    }

    public Optional<Quote> execute(Ticker ticker) {
        Objects.requireNonNull(ticker, "ticker must not be null");

        if (!supportedAssetCatalog.supports(ticker)) {
            throw new UnsupportedTickerException("Ticker is not supported " + ticker.code());
        }

        return quoteProvider.findByTicker(ticker);
    }
}
