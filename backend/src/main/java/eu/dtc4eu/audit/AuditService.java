package eu.dtc4eu.audit;

import eu.dtc4eu.audit.entity.AuditLog;
import eu.dtc4eu.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Writes to audit_logs. Spec §6: log create/update/delete, login, submit and
 * export. Runs in its own transaction so an audit row survives a rollback of
 * the surrounding operation, and never breaks the main flow on failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, Long entityId, Map<String, Object> details,
                    String status) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetails(details);
            entry.setStatus(status);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log entry: action={} user={}", action, userId, e);
        }
    }

    public void success(Long userId, String action, String entityType, Long entityId) {
        log(userId, action, entityType, entityId, null, STATUS_SUCCESS);
    }

    public void success(Long userId, String action, String entityType, Long entityId, Map<String, Object> details) {
        log(userId, action, entityType, entityId, details, STATUS_SUCCESS);
    }

    public void failure(Long userId, String action, String entityType, Long entityId, Map<String, Object> details) {
        log(userId, action, entityType, entityId, details, STATUS_FAILURE);
    }
}
