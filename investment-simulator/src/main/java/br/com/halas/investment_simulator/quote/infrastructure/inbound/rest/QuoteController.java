package br.com.halas.investment_simulator.quote.infrastructure.inbound.rest;

import br.com.halas.investment_simulator.quote.application.GetQuoteUseCase;
import br.com.halas.investment_simulator.quote.domain.model.Ticker;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/quotes")
public class QuoteController {

    private final GetQuoteUseCase getQuoteUseCase;

    public QuoteController(GetQuoteUseCase getQuoteUseCase) {
        this.getQuoteUseCase = getQuoteUseCase;
    }

    @GetMapping("/{ticker}")
    public QuoteResponse getQuote(@PathVariable("ticker") String ticker) {
        var quote = getQuoteUseCase.execute(new Ticker(ticker))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return new QuoteResponse(
                quote.ticker().code(),
                quote.money().amount(),
                quote.money().currency().toString(),
                quote.referenceTime());
    }

}
