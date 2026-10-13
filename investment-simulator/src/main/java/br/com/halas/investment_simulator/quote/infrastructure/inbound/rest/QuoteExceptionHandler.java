package br.com.halas.investment_simulator.quote.infrastructure.inbound.rest;

import br.com.halas.investment_simulator.quote.application.exception.InvalidQuoteDataException;
import br.com.halas.investment_simulator.quote.application.exception.QuoteNotFoundException;
import br.com.halas.investment_simulator.quote.application.exception.QuoteProviderUnavailableException;
import br.com.halas.investment_simulator.quote.application.exception.UnsupportedTickerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = QuoteController.class)
@Order(-1)
public class QuoteExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteExceptionHandler.class);

    @ExceptionHandler(UnsupportedTickerException.class)
    public ProblemDetail handleUnsupportedTicker() {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("code", "UNSUPPORTED_TICKER");
        problem.setProperty("message", "The requested ticker is not supported.");
        return problem;
    }

    @ExceptionHandler(QuoteProviderUnavailableException.class)
    public ProblemDetail handleQuoteProviderUnavailable(QuoteProviderUnavailableException e) {
        log.warn("Quote provider unavailable: {}", e.getMessage(), e);
        var problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setProperty("code", "QUOTE_PROVIDER_UNAVAILABLE");
        problem.setProperty("message", "Unable to retrieve a quote at this time.");
        return problem;
    }

    @ExceptionHandler(InvalidQuoteDataException.class)
    public ProblemDetail handleInvalidQuoteData(InvalidQuoteDataException e) {
        log.error("Invalid quote data received from provider: {}", e.getMessage(), e);
        var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setProperty("code", "INVALID_QUOTE_DATA");
        problem.setProperty("message", "Unable to return a valid quote.");
        return problem;
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    public ProblemDetail handleQuoteNotFound() {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setProperty("code", "QUOTE_NOT_FOUND");
        problem.setProperty("message", "No quote is available for the requested ticker.");
        return problem;
    }
}
