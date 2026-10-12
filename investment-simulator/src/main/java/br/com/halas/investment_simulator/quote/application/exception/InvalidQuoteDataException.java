package br.com.halas.investment_simulator.quote.application.exception;

public class InvalidQuoteDataException extends RuntimeException {

    public InvalidQuoteDataException(String message) {
        super(message);
    }

    public InvalidQuoteDataException(String message, Throwable cause) {
        super(message, cause);
    }

}
