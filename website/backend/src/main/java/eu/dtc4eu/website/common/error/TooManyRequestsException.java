package eu.dtc4eu.website.common.error;

/** 429 when sign-in is throttled after repeated failures. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
