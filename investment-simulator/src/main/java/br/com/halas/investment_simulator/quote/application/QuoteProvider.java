package br.com.halas.investment_simulator.quote.application;

import br.com.halas.investment_simulator.quote.domain.model.Quote;
import br.com.halas.investment_simulator.quote.domain.model.Ticker;

import java.util.Optional;

public interface QuoteProvider {
    Optional<Quote> findByTicker(Ticker ticker);
}
