package br.com.halas.investment_simulator.quote.infrastructure.outbound.marketdata;

import br.com.halas.investment_simulator.quote.application.exception.InvalidQuoteDataException;
import br.com.halas.investment_simulator.quote.application.exception.QuoteProviderUnavailableException;
import br.com.halas.investment_simulator.quote.domain.model.Ticker;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataHttpAdapterTest {

    private static final Ticker PETR4 = new Ticker("PETR4");
    private static final String QUOTE_PATH = "/market-data/quotes/PETR4";
    private static final String REFERENCE_TIME = "2026-10-10T14:30:00Z";
    private static final String VALID_QUOTE = """
            {
                "symbol": "PETR4",
                "lastPrice": 35.50,
                "currency": "BRL",
                "timestamp": "2026-10-10T14:30:00Z"
            }
            """;
    private static final String NOT_FOUND_PROBLEM = """
            {
                "type": "https://market-data.example/problems/quote-not-found",
                "title": "Quote not found",
                "status": 404,
                "code": "QUOTE_NOT_FOUND",
                "message": "No quote is available for the requested ticker."
            }
            """;

    private static WireMockServer marketData;
    private static HttpClient httpClient;
    private MarketDataHttpAdapter adapter;

    @BeforeAll
    static void startServer() {
        marketData = new WireMockServer(wireMockConfig().dynamicPort());
        marketData.start();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
    }

    @AfterAll
    static void stopServer() {
        marketData.stop();
        httpClient.close();
    }

    @BeforeEach
    void setUp() {
        marketData.resetAll();
        adapter = adapterWithReadTimeout(Duration.ofSeconds(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PETR4", "VALE3", "ITUB4"})
    void mapsValidQuoteForRequestedTicker(String code) {
        var path = "/market-data/quotes/" + code;
        marketData.stubFor(get(urlEqualTo(path))
                .willReturn(okJson(VALID_QUOTE.replace("PETR4", code))));

        var quote = adapter.findByTicker(new Ticker(code)).orElseThrow();

        assertAll(
                () -> assertEquals(new Ticker(code), quote.ticker()),
                () -> assertEquals(0, new BigDecimal("35.50").compareTo(quote.money().amount())),
                () -> assertEquals("BRL", quote.money().currency().getCurrencyCode()),
                () -> assertEquals(Instant.parse(REFERENCE_TIME), quote.referenceTime())
        );
        marketData.verify(1, getRequestedFor(urlEqualTo(path))
                .withHeader("Accept", containing("application/json"))
                .withHeader("Accept", containing("application/problem+json")));
    }

    @Test
    void acceptsOldQuoteWithoutChangingReferenceTime() {
        var oldTime = "2000-01-01T00:00:00Z";
        stubQuote(VALID_QUOTE.replace(REFERENCE_TIME, oldTime));

        var quote = adapter.findByTicker(PETR4).orElseThrow();

        assertEquals(Instant.parse(oldTime), quote.referenceTime());
    }

    @Test
    void preservesPricePrecisionSmallerThanOneCent() {
        stubQuote(VALID_QUOTE.replace("35.50", "0.0001"));

        var quote = adapter.findByTicker(PETR4).orElseThrow();

        assertEquals(new BigDecimal("0.0001"), quote.money().amount());
    }

    @Test
    void queriesProviderAgainAndReturnsUpdatedQuote() {
        var firstStub = marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(okJson(VALID_QUOTE)));
        var first = adapter.findByTicker(PETR4).orElseThrow();
        marketData.removeStub(firstStub);
        stubQuote(VALID_QUOTE.replace("35.50", "36.25")
                .replace(REFERENCE_TIME, "2026-10-10T14:31:00Z"));

        var second = adapter.findByTicker(PETR4).orElseThrow();

        assertAll(
                () -> assertEquals(0, new BigDecimal("35.50").compareTo(first.money().amount())),
                () -> assertEquals(0, new BigDecimal("36.25").compareTo(second.money().amount())),
                () -> assertEquals(Instant.parse("2026-10-10T14:31:00Z"), second.referenceTime())
        );
        marketData.verify(2, getRequestedFor(urlEqualTo(QUOTE_PATH)));
    }

    @Test
    void returnsEmptyOnlyForConfirmedNotFoundProblem() {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/problem+json; charset=UTF-8")
                        .withBody(NOT_FOUND_PROBLEM)));

        assertTrue(adapter.findByTicker(PETR4).isEmpty());
        marketData.verify(1, getRequestedFor(urlEqualTo(QUOTE_PATH)));
    }

    @Test
    void rejectsUnmatchedRouteInsteadOfReturningEmpty() {
        var failure = assertThrows(InvalidQuoteDataException.class,
                () -> adapter.findByTicker(PETR4));

        assertInstanceOf(HttpClientErrorException.NotFound.class, failure.getCause());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unconfirmedNotFoundBodies")
    void rejectsUnconfirmedNotFoundProblem(String scenario, String body) {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(body)));

        var failure = assertThrows(InvalidQuoteDataException.class,
                () -> adapter.findByTicker(PETR4));

        assertNotNull(failure.getCause());
    }

    static Stream<Arguments> unconfirmedNotFoundBodies() {
        return Stream.of(
                Arguments.of("different problem type", NOT_FOUND_PROBLEM.replace(
                        "quote-not-found", "route-not-found")),
                Arguments.of("missing problem type", NOT_FOUND_PROBLEM.replace(
                        "\"type\": \"https://market-data.example/problems/quote-not-found\",", "")),
                Arguments.of("different code", NOT_FOUND_PROBLEM.replace(
                        "QUOTE_NOT_FOUND", "ROUTE_NOT_FOUND")),
                Arguments.of("missing code", NOT_FOUND_PROBLEM.replace(
                        "\"code\": \"QUOTE_NOT_FOUND\",", "")),
                Arguments.of("status differs from HTTP status", NOT_FOUND_PROBLEM.replace(
                        "\"status\": 404", "\"status\": 400")),
                Arguments.of("missing status", NOT_FOUND_PROBLEM.replace("\"status\": 404,", "")),
                Arguments.of("empty body", ""),
                Arguments.of("JSON null", "null"),
                Arguments.of("malformed JSON", "{\"type\":")
        );
    }

    @Test
    void rejectsNotFoundProblemWithWrongContentType() {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NOT_FOUND_PROBLEM)));

        var failure = assertThrows(InvalidQuoteDataException.class,
                () -> adapter.findByTicker(PETR4));

        assertInstanceOf(HttpClientErrorException.NotFound.class, failure.getCause());
    }

    @Test
    void translatesServiceUnavailableAndPreservesHttpCause() {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(503)));

        var failure = assertThrows(QuoteProviderUnavailableException.class,
                () -> adapter.findByTicker(PETR4));

        assertInstanceOf(HttpServerErrorException.ServiceUnavailable.class, failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502})
    void translatesOtherServerErrorToProviderUnavailable(int status) {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(status)));

        var failure = assertThrows(QuoteProviderUnavailableException.class,
                () -> adapter.findByTicker(PETR4));

        assertInstanceOf(HttpServerErrorException.class, failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    void translatesOtherClientErrorToInvalidQuoteData(int status) {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(status)));

        var failure = assertThrows(InvalidQuoteDataException.class,
                () -> adapter.findByTicker(PETR4));

        assertInstanceOf(HttpClientErrorException.class, failure.getCause());
    }

    @Test
    void translatesReadTimeoutAndPreservesCommunicationCause() {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(okJson(VALID_QUOTE).withFixedDelay(30_000)));
        var timeoutAdapter = adapterWithReadTimeout(Duration.ofMillis(300));

        var failure = assertThrows(QuoteProviderUnavailableException.class,
                () -> timeoutAdapter.findByTicker(PETR4));

        assertInstanceOf(ResourceAccessException.class, failure.getCause());
        assertNotNull(failure.getCause().getCause());
    }

    @Test
    void translatesBrokenConnectionAndPreservesCommunicationCause() {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        var failure = assertThrows(QuoteProviderUnavailableException.class,
                () -> adapter.findByTicker(PETR4));

        assertInstanceOf(ResourceAccessException.class, failure.getCause());
        assertNotNull(failure.getCause().getCause());
    }

    @Test
    void doesNotReturnPreviousQuoteWhenProviderBecomesUnavailable() {
        var firstStub = marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(okJson(VALID_QUOTE)));
        assertTrue(adapter.findByTicker(PETR4).isPresent());
        marketData.removeStub(firstStub);
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(QuoteProviderUnavailableException.class, () -> adapter.findByTicker(PETR4));
        marketData.verify(2, getRequestedFor(urlEqualTo(QUOTE_PATH)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQuoteBodies")
    void rejectsInvalidQuoteData(String scenario, String body) {
        stubQuote(body);

        assertThrows(InvalidQuoteDataException.class, () -> adapter.findByTicker(PETR4));
    }

    static Stream<Arguments> invalidQuoteBodies() {
        return Stream.of(
                Arguments.of("empty object", "{}"),
                Arguments.of("null symbol", VALID_QUOTE.replace("\"PETR4\"", "null")),
                Arguments.of("empty symbol", VALID_QUOTE.replace("\"PETR4\"", "\"\"")),
                Arguments.of("different ticker", VALID_QUOTE.replace("PETR4", "VALE3")),
                Arguments.of("null price", VALID_QUOTE.replace("35.50", "null")),
                Arguments.of("zero price", VALID_QUOTE.replace("35.50", "0")),
                Arguments.of("negative price", VALID_QUOTE.replace("35.50", "-0.01")),
                Arguments.of("null currency", VALID_QUOTE.replace("\"BRL\"", "null")),
                Arguments.of("unsupported currency", VALID_QUOTE.replace("BRL", "USD")),
                Arguments.of("invalid currency code", VALID_QUOTE.replace("BRL", "INVALID")),
                Arguments.of("null reference time", VALID_QUOTE.replace("\"" + REFERENCE_TIME + "\"", "null"))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    void rejectsEmptySuccessResponseInsteadOfReturningEmpty(int status) {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")));

        assertThrows(InvalidQuoteDataException.class, () -> adapter.findByTicker(PETR4));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undecodableQuoteBodies")
    void translatesDeserializationFailureAndPreservesCause(String scenario, String body) {
        stubQuote(body);

        var failure = assertThrows(InvalidQuoteDataException.class,
                () -> adapter.findByTicker(PETR4));

        assertNotNull(failure.getCause());
    }

    static Stream<Arguments> undecodableQuoteBodies() {
        return Stream.of(
                Arguments.of("malformed JSON", "{\"symbol\":"),
                Arguments.of("array instead of quote", "[]"),
                Arguments.of("non-numeric price", VALID_QUOTE.replace("35.50", "\"not-a-price\"")),
                Arguments.of("invalid timestamp", VALID_QUOTE.replace(REFERENCE_TIME, "not-a-timestamp"))
        );
    }

    @Test
    void translatesUnexpectedSuccessContentTypeAndPreservesCause() {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(VALID_QUOTE)));

        var failure = assertThrows(InvalidQuoteDataException.class,
                () -> adapter.findByTicker(PETR4));

        assertNotNull(failure.getCause());
    }

    @Test
    void rejectsNullTickerWithoutCallingProvider() {
        assertThrows(NullPointerException.class, () -> adapter.findByTicker(null));

        marketData.verify(0, anyRequestedFor(anyUrl()));
    }

    private void stubQuote(String body) {
        marketData.stubFor(get(urlEqualTo(QUOTE_PATH)).willReturn(okJson(body)));
    }

    private MarketDataHttpAdapter adapterWithReadTimeout(Duration timeout) {
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        var client = RestClient.builder()
                .baseUrl(marketData.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new MarketDataHttpAdapter(client, new MarketDataQuoteMapper());
    }
}
