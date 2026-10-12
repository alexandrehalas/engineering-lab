package br.com.halas.investment_simulator.quote.application.exception;

public class UnsupportedTickerException extends RuntimeException {

    public UnsupportedTickerException(String message) {
        super(message);
    }

    public UnsupportedTickerException(String message, Throwable cause) {
        super(message, cause);
    }
}
