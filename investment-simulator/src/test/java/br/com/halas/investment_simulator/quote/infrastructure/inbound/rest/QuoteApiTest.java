package br.com.halas.investment_simulator.quote.infrastructure.inbound.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuoteApiTest {

    private static final String PROVIDER_PATH = "/market-data/quotes/PETR4";
    private static final String PUBLIC_PATH = "/v1/quotes/PETR4";
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
                "message": "Synthetic provider message that must not become the public message."
            }
            """;
    private static final WireMockServer MARKET_DATA = new WireMockServer(wireMockConfig().dynamicPort());
    private static final HttpClient API_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @DynamicPropertySource
    static void configureMarketData(DynamicPropertyRegistry registry) {
        MARKET_DATA.start();
        registry.add("market-data.base-url", MARKET_DATA::baseUrl);
    }

    @AfterAll
    static void stopServer() {
        MARKET_DATA.stop();
        API_HTTP_CLIENT.close();
    }

    @BeforeEach
    void setUp() {
        MARKET_DATA.resetAll();
        var requestFactory = new JdkClientHttpRequestFactory(API_HTTP_CLIENT);
        // The test client must wait longer than the application's two-second provider timeout.
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        client = RestTestClient.bindToServer(requestFactory)
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PETR4", "VALE3", "ITUB4"})
    void returnsPublicQuoteContractForSupportedTicker(String ticker) {
        var providerPath = "/market-data/quotes/" + ticker;
        MARKET_DATA.stubFor(get(urlEqualTo(providerPath))
                .willReturn(okJson(VALID_QUOTE.replace("PETR4", ticker))));

        getQuote("/v1/quotes/" + ticker)
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.ticker").isEqualTo(ticker)
                .jsonPath("$.referencePrice").isNumber()
                .jsonPath("$.referencePrice").isEqualTo(35.5)
                .jsonPath("$.currency").isEqualTo("BRL")
                .jsonPath("$.referenceTime").isEqualTo(REFERENCE_TIME)
                .jsonPath("$.symbol").doesNotExist()
                .jsonPath("$.lastPrice").doesNotExist()
                .jsonPath("$.money").doesNotExist();
        MARKET_DATA.verify(1, getRequestedFor(urlEqualTo(providerPath)));
    }

    @Test
    void rejectsUnsupportedTickerWithoutCallingProvider() {
        expectProblem("/v1/quotes/ABCD9", HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_TICKER", "The requested ticker is not supported.");

        MARKET_DATA.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void returnsNotFoundOnlyForConfirmedMissingQuote() {
        MARKET_DATA.stubFor(get(urlEqualTo("/market-data/quotes/VALE3"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(NOT_FOUND_PROBLEM)));

        expectProblem("/v1/quotes/VALE3", HttpStatus.NOT_FOUND,
                "QUOTE_NOT_FOUND", "No quote is available for the requested ticker.");
        MARKET_DATA.verify(1, getRequestedFor(urlEqualTo("/market-data/quotes/VALE3")));
    }

    @Test
    void returnsServiceUnavailableForProvider503() {
        MARKET_DATA.stubFor(get(urlEqualTo("/market-data/quotes/ITUB4"))
                .willReturn(aResponse().withStatus(503)
                        .withBody("Synthetic internal provider diagnostic.")));

        expectProblem("/v1/quotes/ITUB4", HttpStatus.SERVICE_UNAVAILABLE,
                "QUOTE_PROVIDER_UNAVAILABLE", "Unable to retrieve a quote at this time.");
    }

    @Test
    void returnsServiceUnavailableForConfiguredReadTimeout() {
        MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(okJson(VALID_QUOTE).withFixedDelay(4000)));

        expectProblem(PUBLIC_PATH, HttpStatus.SERVICE_UNAVAILABLE,
                "QUOTE_PROVIDER_UNAVAILABLE", "Unable to retrieve a quote at this time.");
    }

    @Test
    void returnsServiceUnavailableForBrokenConnection() {
        MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        expectProblem(PUBLIC_PATH, HttpStatus.SERVICE_UNAVAILABLE,
                "QUOTE_PROVIDER_UNAVAILABLE", "Unable to retrieve a quote at this time.");
    }

    @Test
    void returnsOldQuoteWithItsOriginalReferenceTime() {
        var oldTime = "2000-01-01T00:00:00Z";
        stubQuote(VALID_QUOTE.replace(REFERENCE_TIME, oldTime));

        getQuote(PUBLIC_PATH)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.referencePrice").isEqualTo(35.5)
                .jsonPath("$.referenceTime").isEqualTo(oldTime);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void returnsInvalidDataProblemForNonPositivePrice(String price) {
        stubQuote(VALID_QUOTE.replace("35.50", price));

        expectInvalidDataProblem();
    }

    @Test
    void returnsInvalidDataProblemForCurrencyOtherThanBrl() {
        stubQuote(VALID_QUOTE.replace("BRL", "USD"));

        expectInvalidDataProblem();
    }

    @Test
    void returnsInvalidDataProblemForMissingReferenceTime() {
        stubQuote(VALID_QUOTE.replace("\"" + REFERENCE_TIME + "\"", "null"));

        expectInvalidDataProblem();
    }

    @Test
    void returnsInvalidDataProblemForMismatchedTicker() {
        stubQuote(VALID_QUOTE.replace("PETR4", "VALE3"));

        expectInvalidDataProblem();
    }

    @Test
    void returnsInvalidDataProblemForEmptySuccessResponse() {
        MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        expectInvalidDataProblem();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"symbol\":", "[]"})
    void returnsInvalidDataProblemForUndecodableJson(String body) {
        stubQuote(body);

        expectInvalidDataProblem();
    }

    @Test
    void returnsInvalidDataProblemForInvalidTimestamp() {
        stubQuote(VALID_QUOTE.replace(REFERENCE_TIME, "not-a-timestamp"));

        expectInvalidDataProblem();
    }

    @Test
    void returnsInvalidDataProblemForUnexpectedProviderContentType() {
        MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(VALID_QUOTE)));

        expectInvalidDataProblem();
    }

    @Test
    void returnsInvalidDataProblemForUnmatchedProviderRoute() {
        expectInvalidDataProblem();

        MARKET_DATA.verify(1, getRequestedFor(urlEqualTo(PROVIDER_PATH)));
    }

    @Test
    void returnsUpdatedQuoteOnNewRequest() {
        var firstStub = MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(okJson(VALID_QUOTE)));
        getQuote(PUBLIC_PATH).expectStatus().isOk()
                .expectBody().jsonPath("$.referencePrice").isEqualTo(35.5);
        MARKET_DATA.removeStub(firstStub);
        stubQuote(VALID_QUOTE.replace("35.50", "36.25")
                .replace(REFERENCE_TIME, "2026-10-10T14:31:00Z"));

        getQuote(PUBLIC_PATH).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.referencePrice").isEqualTo(36.25)
                .jsonPath("$.referenceTime").isEqualTo("2026-10-10T14:31:00Z");
        MARKET_DATA.verify(2, getRequestedFor(urlEqualTo(PROVIDER_PATH)));
    }

    @Test
    void doesNotReturnPreviousQuoteDuringProviderFailure() {
        var firstStub = MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(okJson(VALID_QUOTE)));
        getQuote(PUBLIC_PATH).expectStatus().isOk()
                .expectBody().jsonPath("$.referencePrice").isEqualTo(35.5);
        MARKET_DATA.removeStub(firstStub);
        MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(aResponse().withStatus(503)));

        expectProblem(PUBLIC_PATH, HttpStatus.SERVICE_UNAVAILABLE,
                "QUOTE_PROVIDER_UNAVAILABLE", "Unable to retrieve a quote at this time.");
        MARKET_DATA.verify(2, getRequestedFor(urlEqualTo(PROVIDER_PATH)));
    }

    @Test
    void doesNotReturnPreviousQuoteWhenNewQuoteIsInvalid() {
        var firstStub = MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH))
                .willReturn(okJson(VALID_QUOTE)));
        getQuote(PUBLIC_PATH).expectStatus().isOk().expectBody();
        MARKET_DATA.removeStub(firstStub);
        stubQuote(VALID_QUOTE.replace("35.50", "0"));

        expectInvalidDataProblem();
        MARKET_DATA.verify(2, getRequestedFor(urlEqualTo(PROVIDER_PATH)));
    }

    @Test
    void doesNotClassifyUnknownPublicRouteAsMissingQuote() {
        getQuote("/v1/quotes/PETR4/extra")
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").doesNotExist();
        MARKET_DATA.verify(0, anyRequestedFor(anyUrl()));
    }

    private RestTestClient.ResponseSpec getQuote(String path) {
        return client.get().uri(path)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .exchange();
    }

    private void expectInvalidDataProblem() {
        expectProblem(PUBLIC_PATH, HttpStatus.INTERNAL_SERVER_ERROR,
                "INVALID_QUOTE_DATA", "Unable to return a valid quote.");
    }

    private void expectProblem(String path, HttpStatus status, String code, String message) {
        getQuote(path)
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(status.value())
                .jsonPath("$.title").isEqualTo(status.getReasonPhrase())
                .jsonPath("$.instance").isEqualTo(path)
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.message").isEqualTo(message)
                .jsonPath("$.trace").doesNotExist()
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.referencePrice").doesNotExist();
    }

    private void stubQuote(String body) {
        MARKET_DATA.stubFor(get(urlEqualTo(PROVIDER_PATH)).willReturn(okJson(body)));
    }
}
