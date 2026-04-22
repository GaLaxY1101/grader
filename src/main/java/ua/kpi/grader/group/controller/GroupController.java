package ua.kpi.grader.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.group.dto.CreateGroupRequest;
import ua.kpi.grader.group.dto.GroupResponse;
import ua.kpi.grader.group.dto.GroupStudentResponse;
import ua.kpi.grader.group.dto.UpdateGroupRequest;
import ua.kpi.grader.group.service.GroupService;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<GroupResponse>> listGroups() {
        return ResponseEntity.ok(groupService.findAllActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<GroupResponse> createGroup(
            @RequestBody @Valid CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.createGroup(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable Long id,
            @RequestBody @Valid UpdateGroupRequest request) {
        return ResponseEntity.ok(groupService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> deactivateGroup(@PathVariable Long id) {
        groupService.deactivateGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<GroupStudentResponse> addStudent(
            @PathVariable Long id,
            @PathVariable Long studentId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.addStudent(id, studentId));
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> removeStudent(
            @PathVariable Long id,
            @PathVariable Long studentId) {
        groupService.removeStudent(id, studentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<GroupStudentResponse>> listStudents(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.findStudents(id));
    }
}
