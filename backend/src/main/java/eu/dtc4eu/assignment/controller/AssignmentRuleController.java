package eu.dtc4eu.assignment.controller;

import eu.dtc4eu.assignment.dto.AssignmentDtos.RuleDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.RuleTestRequest;
import eu.dtc4eu.assignment.dto.AssignmentDtos.RuleTestResult;
import eu.dtc4eu.assignment.dto.AssignmentDtos.SaveRuleRequest;
import eu.dtc4eu.assignment.service.AssignmentService;
import eu.dtc4eu.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Module 5-bis rule administration (spec COL-01/02): the Upper Coordinator and admins only. */
@RestController
@RequestMapping("/api/v1/assignment-rules")
@RequiredArgsConstructor
@Tag(name = "Assignment rules")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','UPPER_COORDINATOR')")
public class AssignmentRuleController {

    private final AssignmentService assignmentService;

    @GetMapping
    public List<RuleDto> list() {
        return assignmentService.listRules();
    }

    @Operation(summary = "Create a rule: AND/OR condition tree → target form")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleDto create(@Valid @RequestBody SaveRuleRequest request, @AuthenticationPrincipal AppUserDetails actor) {
        return assignmentService.createRule(request, actor);
    }

    @PutMapping("/{id}")
    public RuleDto update(@PathVariable Long id, @Valid @RequestBody SaveRuleRequest request,
                          @AuthenticationPrincipal AppUserDetails actor) {
        return assignmentService.updateRule(id, request, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails actor) {
        assignmentService.deleteRule(id, actor);
    }

    @Operation(summary = "Rule test tool: which forms a sample profile would receive")
    @PostMapping("/test")
    public RuleTestResult test(@Valid @RequestBody RuleTestRequest request) {
        return assignmentService.test(request);
    }
}
