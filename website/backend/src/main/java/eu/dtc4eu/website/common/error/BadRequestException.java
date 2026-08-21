package eu.dtc4eu.website.common.error;

/** 400 for business-rule violations detected in services. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
