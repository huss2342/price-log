package app.pricelog.api.extract;

public class TagExtractionException extends RuntimeException {

    public TagExtractionException(String message) {
        super(message);
    }

    public TagExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
