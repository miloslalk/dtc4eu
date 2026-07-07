package eu.dtc4eu.common.error;

/** 409 for state conflicts (duplicates, closed cycles, submitted responses). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
