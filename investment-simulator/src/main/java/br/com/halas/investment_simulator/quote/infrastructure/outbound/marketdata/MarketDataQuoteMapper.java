package br.com.halas.investment_simulator.quote.infrastructure.outbound.marketdata;

import br.com.halas.investment_simulator.quote.application.exception.InvalidQuoteDataException;
import br.com.halas.investment_simulator.quote.domain.model.Money;
import br.com.halas.investment_simulator.quote.domain.model.Quote;
import br.com.halas.investment_simulator.quote.domain.model.Ticker;

import java.util.Currency;
import java.util.Objects;

public class MarketDataQuoteMapper {

    public Quote toQuote(Ticker requestedTicker, MarketDataQuoteResponse response) {
        Objects.requireNonNull(requestedTicker, "Requested ticker must not be null");

        if (response == null) {
            throw new InvalidQuoteDataException("Market data response must not be null");
        }

        Quote quote;
        try {
            var quoteTicker = new Ticker(response.symbol());
            var currency = Currency.getInstance(response.currency());
            var money = new Money(response.lastPrice(), currency);
            quote = new Quote(quoteTicker, money, response.timestamp());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidQuoteDataException("Market data response contains invalid quote data", e);
        }

        if (!requestedTicker.equals(quote.ticker())) {
            throw new InvalidQuoteDataException(
                    "Returned quote ticker: " + quote.ticker()
                            + " does not match requested ticker: " + requestedTicker.code());
        }

        return quote;
    }
}
