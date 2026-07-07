package eu.dtc4eu.audit;

import eu.dtc4eu.audit.entity.AuditLog;
import eu.dtc4eu.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The actual audit insert, in its own bean so the REQUIRES_NEW boundary is a
 * real proxy call (self-invocation would silently skip it): the row commits
 * independently of the business transaction — surviving its rollback and
 * working from read-only transactions — and any failure surfaces to the
 * calling {@link AuditService}, which swallows it.
 */
@Component
@RequiredArgsConstructor
class AuditWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        auditLogRepository.save(entry);
    }
}
