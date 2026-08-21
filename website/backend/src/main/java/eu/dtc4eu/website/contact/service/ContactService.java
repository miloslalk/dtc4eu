package eu.dtc4eu.website.contact.service;

import eu.dtc4eu.website.common.error.NotFoundException;
import eu.dtc4eu.website.contact.dto.ContactDtos;
import eu.dtc4eu.website.contact.entity.ContactSubmission;
import eu.dtc4eu.website.contact.repository.ContactSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactSubmissionRepository contactSubmissionRepository;

    /** Stores a public submission; honeypot hits succeed silently without storing anything. */
    @Transactional
    public void submit(ContactDtos.SubmitRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }
        ContactSubmission submission = new ContactSubmission();
        submission.setName(request.name().trim());
        submission.setEmail(request.email().trim());
        submission.setSubject(request.subject() != null ? request.subject().trim() : null);
        submission.setMessage(request.message().trim());
        contactSubmissionRepository.save(submission);
    }

    @Transactional(readOnly = true)
    public List<ContactDtos.SubmissionDto> list() {
        return contactSubmissionRepository.findAllByOrderByIdDesc().stream()
                .map(ContactDtos.SubmissionDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return contactSubmissionRepository.countByReadFalse();
    }

    @Transactional
    public void setRead(Long id, boolean read) {
        ContactSubmission submission = contactSubmissionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Submission", id));
        submission.setRead(read);
    }

    @Transactional
    public void delete(Long id) {
        ContactSubmission submission = contactSubmissionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Submission", id));
        contactSubmissionRepository.delete(submission);
    }
}
