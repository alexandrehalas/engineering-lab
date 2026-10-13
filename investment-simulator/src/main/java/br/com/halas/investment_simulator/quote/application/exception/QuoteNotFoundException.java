package br.com.halas.investment_simulator.quote.application.exception;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(String message) {
        super(message);
    }
}
