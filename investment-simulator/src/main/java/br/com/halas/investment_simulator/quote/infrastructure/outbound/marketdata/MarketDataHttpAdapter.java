package br.com.halas.investment_simulator.quote.infrastructure.outbound.marketdata;

import br.com.halas.investment_simulator.quote.application.port.QuoteProvider;
import br.com.halas.investment_simulator.quote.application.exception.InvalidQuoteDataException;
import br.com.halas.investment_simulator.quote.application.exception.QuoteProviderUnavailableException;
import br.com.halas.investment_simulator.quote.domain.model.Quote;
import br.com.halas.investment_simulator.quote.domain.model.Ticker;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.*;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public class MarketDataHttpAdapter implements QuoteProvider {

    private static final URI QUOTE_NOT_FOUND_TYPE =
            URI.create("https://market-data.example/problems/quote-not-found");
    private static final String QUOTE_NOT_FOUND_CODE = "QUOTE_NOT_FOUND";

    private final RestClient restClient;
    private final MarketDataQuoteMapper quoteMapper;

    public MarketDataHttpAdapter(RestClient restClient, MarketDataQuoteMapper quoteMapper) {
        this.restClient = Objects.requireNonNull(restClient, "Rest client must not be null");
        this.quoteMapper = Objects.requireNonNull(quoteMapper, "Quote mapper must not be null");
    }

    @Override
    public Optional<Quote> findByTicker(Ticker ticker) {

        Objects.requireNonNull(ticker, "Ticker must not be null");

        try {

            var response = restClient.get()
                    .uri("/market-data/quotes/{ticker}", ticker.code())
                    .accept(MediaType.APPLICATION_JSON,
                            MediaType.APPLICATION_PROBLEM_JSON)
                    .retrieve()
                    .body(MarketDataQuoteResponse.class);

            return Optional.of(quoteMapper.toQuote(ticker, response));

        } catch (HttpClientErrorException.NotFound e) {
            return handleNotFound(e);
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            throw new QuoteProviderUnavailableException("Market data provider is unavailable", e);
        } catch (ResourceAccessException e) {
            throw new QuoteProviderUnavailableException("Could not access market data provider", e);
        } catch (UnknownContentTypeException e) {
            throw new InvalidQuoteDataException("Market data response has an unsupported content type", e);
        } catch (RestClientException e) {
            if (e.contains(HttpMessageNotReadableException.class)) {
                throw new InvalidQuoteDataException("Market data response could not be deserialized", e);
            }
            if (e instanceof HttpClientErrorException) {
                throw new InvalidQuoteDataException("Unexpected client error from market data provider", e);
            }
            if (e instanceof HttpServerErrorException) {
                throw new QuoteProviderUnavailableException("Market data provider returned a server error", e);
            }
            throw new QuoteProviderUnavailableException("Unexpected error communicating with market data provider", e);
        }
    }

    private Optional<Quote> handleNotFound(HttpClientErrorException.NotFound e) {

        var headers = e.getResponseHeaders();
        var contentType = headers != null ? headers.getContentType() : null;

        if (contentType == null || !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
            throw new InvalidQuoteDataException("Unexpected content type in market data not-found response", e);
        }

        ProblemDetail problem;

        try {
            problem = e.getResponseBodyAs(ProblemDetail.class);
        } catch (RestClientException conversionException) {
            throw new InvalidQuoteDataException(
                    "Invalid problem details in market data not-found response",
                    conversionException);
        }

        if (problem != null) {

            if (QUOTE_NOT_FOUND_TYPE.equals(problem.getType())
                    && problem.getStatus() == e.getStatusCode().value()
                    && problem.getProperties() != null
                    && QUOTE_NOT_FOUND_CODE.equals(problem.getProperties().get("code"))) {
                return Optional.empty();
            }
        }

        throw new InvalidQuoteDataException(
                "Unexpected not-found response from market data provider", e);
    }

}
