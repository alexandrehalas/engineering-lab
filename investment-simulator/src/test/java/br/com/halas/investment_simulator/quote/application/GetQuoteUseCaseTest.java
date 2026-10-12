package br.com.halas.investment_simulator.quote.application;

import br.com.halas.investment_simulator.quote.application.exception.InvalidQuoteDataException;
import br.com.halas.investment_simulator.quote.application.exception.QuoteProviderUnavailableException;
import br.com.halas.investment_simulator.quote.application.exception.UnsupportedTickerException;
import br.com.halas.investment_simulator.quote.domain.model.Money;
import br.com.halas.investment_simulator.quote.domain.model.Quote;
import br.com.halas.investment_simulator.quote.domain.model.Ticker;
import br.com.halas.investment_simulator.quote.domain.policy.SupportedAssetCatalog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GetQuoteUseCaseTest {

    private static final Ticker TICKER = new Ticker("PETR4");
    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Money REFERENCE_PRICE = new Money(new BigDecimal("35.50"), BRL);
    private static final Instant REFERENCE_TIME = Instant.parse("2026-10-10T14:30:00Z");
    private static final Quote QUOTE = new Quote(TICKER, REFERENCE_PRICE, REFERENCE_TIME);

    private final QuoteProvider quoteProvider = mock(QuoteProvider.class);
    private final GetQuoteUseCase useCase = new GetQuoteUseCase(
            new SupportedAssetCatalog(), quoteProvider);

    @Test
    void returnsQuoteFromProvider() {
        when(quoteProvider.findByTicker(TICKER)).thenReturn(Optional.of(QUOTE));

        var result = useCase.execute(TICKER);

        assertEquals(Optional.of(QUOTE), result);
        verify(quoteProvider).findByTicker(TICKER);
        verifyNoMoreInteractions(quoteProvider);
    }

    @Test
    void returnsEmptyWhenSupportedAssetHasNoQuote() {
        var ticker = new Ticker("VALE3");
        when(quoteProvider.findByTicker(ticker)).thenReturn(Optional.empty());

        var result = useCase.execute(ticker);

        assertEquals(Optional.empty(), result);
        verify(quoteProvider).findByTicker(ticker);
        verifyNoMoreInteractions(quoteProvider);
    }

    @Test
    void rejectsUnsupportedTickerWithoutCallingProvider() {
        var ticker = new Ticker("ABCD9");

        assertThrows(UnsupportedTickerException.class, () -> useCase.execute(ticker));

        verifyNoInteractions(quoteProvider);
    }

    @Test
    void propagatesProviderUnavailableFailureAndCause() {
        var cause = new IllegalStateException("Simulated provider timeout");
        var failure = new QuoteProviderUnavailableException("Provider is unavailable", cause);
        when(quoteProvider.findByTicker(TICKER)).thenThrow(failure);

        var thrown = assertThrows(QuoteProviderUnavailableException.class,
                () -> useCase.execute(TICKER));

        assertAll(
                () -> assertSame(failure, thrown),
                () -> assertSame(cause, thrown.getCause())
        );
        verify(quoteProvider).findByTicker(TICKER);
        verifyNoMoreInteractions(quoteProvider);
    }

    @Test
    void propagatesInvalidQuoteDataFailureAndCause() {
        var cause = new IllegalArgumentException("Simulated invalid quote price");
        var failure = new InvalidQuoteDataException("Provider returned invalid data", cause);
        when(quoteProvider.findByTicker(TICKER)).thenThrow(failure);

        var thrown = assertThrows(InvalidQuoteDataException.class,
                () -> useCase.execute(TICKER));

        assertAll(
                () -> assertSame(failure, thrown),
                () -> assertSame(cause, thrown.getCause())
        );
        verify(quoteProvider).findByTicker(TICKER);
        verifyNoMoreInteractions(quoteProvider);
    }

    @Test
    void returnsOldQuoteWithOriginalReferenceTime() {
        var oldReferenceTime = Instant.parse("2000-01-01T00:00:00Z");
        var oldQuote = new Quote(TICKER, REFERENCE_PRICE, oldReferenceTime);
        when(quoteProvider.findByTicker(TICKER)).thenReturn(Optional.of(oldQuote));

        var result = useCase.execute(TICKER);

        assertEquals(Optional.of(oldQuote), result);
        verify(quoteProvider).findByTicker(TICKER);
        verifyNoMoreInteractions(quoteProvider);
    }

    @Test
    void queriesProviderAgainAndReturnsUpdatedQuote() {
        var updatedPrice = new Money(new BigDecimal("36.25"), BRL);
        var updatedTime = Instant.parse("2026-10-10T14:31:00Z");
        var updatedQuote = new Quote(TICKER, updatedPrice, updatedTime);
        when(quoteProvider.findByTicker(TICKER))
                .thenReturn(Optional.of(QUOTE))
                .thenReturn(Optional.of(updatedQuote));

        var firstResult = useCase.execute(TICKER);
        var secondResult = useCase.execute(TICKER);

        assertAll(
                () -> assertEquals(Optional.of(QUOTE), firstResult),
                () -> assertEquals(Optional.of(updatedQuote), secondResult)
        );
        verify(quoteProvider, times(2)).findByTicker(TICKER);
        verifyNoMoreInteractions(quoteProvider);
    }

    @Test
    void doesNotReturnPreviousQuoteWhenNextProviderCallFails() {
        var failure = new QuoteProviderUnavailableException("Provider is unavailable");
        when(quoteProvider.findByTicker(TICKER))
                .thenReturn(Optional.of(QUOTE))
                .thenThrow(failure);

        assertEquals(Optional.of(QUOTE), useCase.execute(TICKER));

        var thrown = assertThrows(QuoteProviderUnavailableException.class,
                () -> useCase.execute(TICKER));

        assertSame(failure, thrown);
        verify(quoteProvider, times(2)).findByTicker(TICKER);
        verifyNoMoreInteractions(quoteProvider);
    }

    @Test
    void rejectsNullTickerWithoutCallingProvider() {
        assertThrows(NullPointerException.class, () -> useCase.execute(null));

        verifyNoInteractions(quoteProvider);
    }

    @Test
    void rejectsNullCatalog() {
        assertThrows(NullPointerException.class,
                () -> new GetQuoteUseCase(null, quoteProvider));
    }

    @Test
    void rejectsNullProvider() {
        assertThrows(NullPointerException.class,
                () -> new GetQuoteUseCase(new SupportedAssetCatalog(), null));
    }
}
