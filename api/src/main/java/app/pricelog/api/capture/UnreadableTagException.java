package app.pricelog.api.capture;

/** The photo reached the model but no price could be read from it. */
public class UnreadableTagException extends RuntimeException {

    public UnreadableTagException(String message) {
        super(message);
    }
}
