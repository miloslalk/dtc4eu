package eu.dtc4eu.website.contact.dto;

import eu.dtc4eu.website.contact.entity.ContactSubmission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class ContactDtos {

    private ContactDtos() {
    }

    /** Public form payload; `website` is a honeypot — bots that fill it are dropped silently. */
    public record SubmitRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 200) String email,
            @Size(max = 200) String subject,
            @NotBlank @Size(max = 5000) String message,
            String website) {
    }

    public record SubmissionDto(Long id, String name, String email, String subject, String message, boolean read,
                                OffsetDateTime createdAt) {

        public static SubmissionDto of(ContactSubmission submission) {
            return new SubmissionDto(submission.getId(), submission.getName(), submission.getEmail(),
                    submission.getSubject(), submission.getMessage(), submission.isRead(),
                    submission.getCreatedAt());
        }
    }
}
