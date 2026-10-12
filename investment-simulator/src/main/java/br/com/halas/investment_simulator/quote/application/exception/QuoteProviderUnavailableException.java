package br.com.halas.investment_simulator.quote.application.exception;

public class QuoteProviderUnavailableException extends RuntimeException {

    public QuoteProviderUnavailableException(String message) {
        super(message);
    }

    public QuoteProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
