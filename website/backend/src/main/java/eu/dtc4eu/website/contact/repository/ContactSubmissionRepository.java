package eu.dtc4eu.website.contact.repository;

import eu.dtc4eu.website.contact.entity.ContactSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactSubmissionRepository extends JpaRepository<ContactSubmission, Long> {

    List<ContactSubmission> findAllByOrderByIdDesc();

    long countByReadFalse();
}
