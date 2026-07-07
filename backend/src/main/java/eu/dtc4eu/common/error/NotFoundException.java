package eu.dtc4eu.common.error;

/**
 * 404. Also thrown for records that exist but are outside the caller's data
 * scope — out-of-scope data must not reveal its existence (spec §6).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " " + id + " not found");
    }
}
