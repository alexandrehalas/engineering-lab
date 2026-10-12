package br.com.halas.investment_simulator.quote.infrastructure.config;

import br.com.halas.investment_simulator.quote.application.GetQuoteUseCase;
import br.com.halas.investment_simulator.quote.application.QuoteProvider;
import br.com.halas.investment_simulator.quote.domain.policy.SupportedAssetCatalog;
import br.com.halas.investment_simulator.quote.infrastructure.outbound.marketdata.MarketDataHttpAdapter;
import br.com.halas.investment_simulator.quote.infrastructure.outbound.marketdata.MarketDataQuoteMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class QuoteConfiguration {

    @Bean
    public RestClient marketDataRestClient(
            RestClient.Builder builder,
            @Value("${market-data.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    public MarketDataQuoteMapper marketDataQuoteMapper() {
        return new MarketDataQuoteMapper();
    }

    @Bean
    public QuoteProvider quoteProvider(RestClient restClient, MarketDataQuoteMapper quoteMapper) {
        return new MarketDataHttpAdapter(restClient, quoteMapper);
    }

    @Bean
    public SupportedAssetCatalog supportedAssetCatalog() {
        return new SupportedAssetCatalog();
    }

    @Bean
    public GetQuoteUseCase quoteUseCase(
            SupportedAssetCatalog supportedAssetCatalog,
            QuoteProvider quoteProvider) {
        return new GetQuoteUseCase(supportedAssetCatalog, quoteProvider);
    }

}
