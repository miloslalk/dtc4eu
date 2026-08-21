package eu.dtc4eu.website.contact.controller;

import eu.dtc4eu.website.contact.dto.ContactDtos;
import eu.dtc4eu.website.contact.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/contact-submissions")
@RequiredArgsConstructor
public class ContactAdminController {

    private final ContactService contactService;

    @Operation(summary = "Inbox, newest first")
    @GetMapping
    public List<ContactDtos.SubmissionDto> list() {
        return contactService.list();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", contactService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public void setRead(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        contactService.setRead(id, Boolean.TRUE.equals(body.get("read")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        contactService.delete(id);
    }
}
