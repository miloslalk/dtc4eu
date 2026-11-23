package eu.dtc4eu.assignment.controller;

import eu.dtc4eu.assignment.dto.AssignmentDtos.MyAssignmentsDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.ProfileDto;
import eu.dtc4eu.assignment.dto.AssignmentDtos.SaveProfileRequest;
import eu.dtc4eu.assignment.service.AssignmentService;
import eu.dtc4eu.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own profile and assigned questionnaires (spec CORE-04, COL-03). */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "My questionnaires")
public class MyAssignmentsController {

    private final AssignmentService assignmentService;

    @Operation(summary = "Own profile for the selected cycle, with the registry context derived from the account")
    @GetMapping("/profile")
    public ProfileDto profile(@AuthenticationPrincipal AppUserDetails principal) {
        return assignmentService.myProfile(principal);
    }

    @Operation(summary = "Confirm or update the own profile for the selected cycle")
    @PutMapping("/profile")
    public ProfileDto saveProfile(@Valid @RequestBody SaveProfileRequest request,
                                  @AuthenticationPrincipal AppUserDetails principal) {
        return assignmentService.saveProfile(request, principal);
    }

    @Operation(summary = "Forms assigned to the caller by the active rules, as tasks with their status")
    @GetMapping("/assignments")
    public MyAssignmentsDto assignments(@AuthenticationPrincipal AppUserDetails principal) {
        return assignmentService.myAssignments(principal);
    }
}
