package ua.kpi.grader.group.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.group.dto.CreateGroupRequest;
import ua.kpi.grader.group.dto.GroupResponse;
import ua.kpi.grader.group.dto.GroupStudentResponse;
import ua.kpi.grader.group.dto.UpdateGroupRequest;
import ua.kpi.grader.group.entity.AcademicGroup;
import ua.kpi.grader.group.entity.GroupStudent;
import ua.kpi.grader.group.repository.AcademicGroupRepository;
import ua.kpi.grader.group.repository.GroupStudentRepository;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.StudentRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private AcademicGroupRepository groupRepository;

    @Mock
    private GroupStudentRepository groupStudentRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private GroupService groupService;

    // --- findAllActive ---

    @Test
    void findAllActive_returnsOnlyActiveGroups() {
        AcademicGroup active = buildGroup(1L, "CS-21", true);
        when(groupRepository.findAllByIsActiveTrue()).thenReturn(List.of(active));

        List<GroupResponse> result = groupService.findAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("CS-21");
    }

    // --- findById ---

    @Test
    void findById_returnsGroup_whenExists() {
        AcademicGroup group = buildGroup(5L, "IP-22", true);
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));

        GroupResponse result = groupService.findById(5L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.code()).isEqualTo("IP-22");
    }

    @Test
    void findById_throwsResourceNotFoundException_whenNotFound() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- createGroup ---

    @Test
    void createGroup_persistsAndReturnsGroup_whenCodeIsUnique() {
        CreateGroupRequest request = new CreateGroupRequest("CS-23", "Computer Science 2023",
                "FIOT", "CS", 2023);
        AcademicGroup saved = buildGroup(10L, "CS-23", true);
        when(groupRepository.existsByCode("CS-23")).thenReturn(false);
        when(groupRepository.save(any())).thenReturn(saved);

        GroupResponse result = groupService.createGroup(request);

        assertThat(result.id()).isEqualTo(10L);
        verify(groupRepository).save(any(AcademicGroup.class));
    }

    @Test
    void createGroup_throwsIllegalStateException_whenCodeAlreadyExists() {
        CreateGroupRequest request = new CreateGroupRequest("CS-23", "Duplicate", null, null, 2023);
        when(groupRepository.existsByCode("CS-23")).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CS-23");
    }

    // --- updateGroup ---

    @Test
    void updateGroup_updatesFields_whenCodeIsAvailable() {
        AcademicGroup group = buildGroup(3L, "OLD-21", true);
        UpdateGroupRequest request = new UpdateGroupRequest("NEW-21", "New Name", "FIOT", "CS", 2021);
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByCodeAndIdNot("NEW-21", 3L)).thenReturn(false);

        GroupResponse result = groupService.updateGroup(3L, request);

        assertThat(result.code()).isEqualTo("NEW-21");
        assertThat(result.name()).isEqualTo("New Name");
    }

    @Test
    void updateGroup_throwsResourceNotFoundException_whenGroupNotFound() {
        UpdateGroupRequest request = new UpdateGroupRequest("X", "Y", null, null, 2020);
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.updateGroup(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateGroup_throwsIllegalStateException_whenCodeTakenByAnotherGroup() {
        AcademicGroup group = buildGroup(3L, "CS-21", true);
        UpdateGroupRequest request = new UpdateGroupRequest("TAKEN", "Name", null, null, 2021);
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByCodeAndIdNot("TAKEN", 3L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.updateGroup(3L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TAKEN");
    }

    // --- deactivateGroup ---

    @Test
    void deactivateGroup_setsIsActiveFalse() {
        AcademicGroup group = buildGroup(7L, "CS-20", true);
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));

        groupService.deactivateGroup(7L);

        assertThat(group.isActive()).isFalse();
    }

    @Test
    void deactivateGroup_throwsResourceNotFoundException_whenNotFound() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.deactivateGroup(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- addStudent ---

    @Test
    void addStudent_createsMembership_whenBothExistAndNotYetMember() {
        AcademicGroup group = buildGroup(1L, "CS-21", true);
        Student student = buildStudent(2L, "alice@test.com");
        GroupStudent saved = GroupStudent.builder()
                .group(group).student(student).build();
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(groupStudentRepository.existsByGroupIdAndStudentId(1L, 2L)).thenReturn(false);
        when(groupStudentRepository.save(any())).thenReturn(saved);

        GroupStudentResponse result = groupService.addStudent(1L, 2L);

        assertThat(result.email()).isEqualTo("alice@test.com");
        verify(groupStudentRepository).save(any(GroupStudent.class));
    }

    @Test
    void addStudent_throwsResourceNotFoundException_whenGroupNotFound() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.addStudent(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void addStudent_throwsResourceNotFoundException_whenStudentNotFound() {
        AcademicGroup group = buildGroup(1L, "CS-21", true);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.addStudent(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void addStudent_throwsIllegalStateException_whenAlreadyMember() {
        AcademicGroup group = buildGroup(1L, "CS-21", true);
        Student student = buildStudent(2L, "alice@test.com");
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(groupStudentRepository.existsByGroupIdAndStudentId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.addStudent(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already a member");
    }

    // --- removeStudent ---

    @Test
    void removeStudent_deletesMembership_whenExists() {
        AcademicGroup group = buildGroup(1L, "CS-21", true);
        Student student = buildStudent(2L, "alice@test.com");
        GroupStudent membership = GroupStudent.builder().group(group).student(student).build();
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupStudentRepository.findByGroupIdAndStudentId(1L, 2L))
                .thenReturn(Optional.of(membership));

        groupService.removeStudent(1L, 2L);

        verify(groupStudentRepository).delete(membership);
    }

    @Test
    void removeStudent_throwsResourceNotFoundException_whenNotMember() {
        AcademicGroup group = buildGroup(1L, "CS-21", true);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupStudentRepository.findByGroupIdAndStudentId(1L, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.removeStudent(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- findStudents ---

    @Test
    void findStudents_returnsMembers_whenGroupExists() {
        AcademicGroup group = buildGroup(1L, "CS-21", true);
        Student student = buildStudent(2L, "bob@test.com");
        GroupStudent membership = GroupStudent.builder()
                .group(group).student(student)
                .enrolledAt(OffsetDateTime.now()).build();
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(1L))
                .thenReturn(List.of(membership));

        List<GroupStudentResponse> result = groupService.findStudents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("bob@test.com");
    }

    @Test
    void findStudents_throwsResourceNotFoundException_whenGroupNotFound() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.findStudents(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- helpers ---

    private AcademicGroup buildGroup(Long id, String code, boolean active) {
        AcademicGroup group = AcademicGroup.builder()
                .code(code)
                .name("Group " + code)
                .yearOfCreation(2021)
                .build();
        group.setId(id);
        if (!active) {
            group.deactivate();
        }
        return group;
    }

    private Student buildStudent(Long id, String email) {
        User user = User.builder()
                .email(email)
                .firstName("Alice")
                .lastName("Smith")
                .role(Role.STUDENT)
                .build();
        user.setId(100L);
        Student student = Student.builder().user(user).build();
        student.setId(id);
        return student;
    }
}
