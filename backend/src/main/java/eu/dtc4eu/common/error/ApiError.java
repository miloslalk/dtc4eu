package eu.dtc4eu.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Error body shape fixed by spec §4:
 * { "timestamp", "status", "error", "message", "path", "details":[{"field","message"}] }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> details) {

    public record FieldErrorDetail(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, null);
    }

    public static ApiError of(int status, String error, String message, String path, List<FieldErrorDetail> details) {
        return new ApiError(OffsetDateTime.now(), status, error, message, path, details);
    }
}
