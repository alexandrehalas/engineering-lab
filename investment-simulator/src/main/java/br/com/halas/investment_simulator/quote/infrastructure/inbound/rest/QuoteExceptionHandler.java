package br.com.halas.investment_simulator.quote.infrastructure.inbound.rest;

import br.com.halas.investment_simulator.quote.application.exception.InvalidQuoteDataException;
import br.com.halas.investment_simulator.quote.application.exception.QuoteProviderUnavailableException;
import br.com.halas.investment_simulator.quote.application.exception.UnsupportedTickerException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = QuoteController.class)
@Order(-1)
public class QuoteExceptionHandler {

    @ExceptionHandler(UnsupportedTickerException.class)
    public ProblemDetail handleUnsupportedTicker() {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("code", "UNSUPPORTED_TICKER");
        problem.setProperty("message", "The requested ticker is not supported.");
        return problem;
    }

    @ExceptionHandler(QuoteProviderUnavailableException.class)
    public ProblemDetail handleQuoteProviderUnavailable() {
        var problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setProperty("code", "QUOTE_PROVIDER_UNAVAILABLE");
        problem.setProperty("message", "Unable to retrieve a quote at this time.");
        return problem;
    }

    @ExceptionHandler(InvalidQuoteDataException.class)
    public ProblemDetail handleInvalidQuoteData() {
        var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setProperty("code", "INVALID_QUOTE_DATA");
        problem.setProperty("message", "Unable to return a valid quote.");
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(
            ResponseStatusException exception) {

        var problem = ProblemDetail.forStatus(exception.getStatusCode());

        if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            problem.setProperty("code", "QUOTE_NOT_FOUND");
            problem.setProperty(
                    "message",
                    "No quote is available for the requested ticker.");
        }

        return problem;
    }
}
