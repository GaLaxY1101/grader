package ua.kpi.grader.group.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.grader.common.exception.InvalidImportException;
import ua.kpi.grader.common.util.PhoneNumberNormalizer;
import ua.kpi.grader.group.dto.BulkCommitRequest;
import ua.kpi.grader.group.dto.BulkImportResult;
import ua.kpi.grader.group.entity.AcademicGroup;
import ua.kpi.grader.group.entity.GroupStudent;
import ua.kpi.grader.group.repository.AcademicGroupRepository;
import ua.kpi.grader.group.repository.GroupStudentRepository;
import ua.kpi.grader.user.dto.CreateUserRequest;
import ua.kpi.grader.user.dto.StudentInput;
import ua.kpi.grader.user.dto.UserResponse;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.StudentRepository;
import ua.kpi.grader.user.repository.UserRepository;
import ua.kpi.grader.user.service.StudentXlsxReader;
import ua.kpi.grader.user.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupBulkImportServiceTest {

    @Mock private AcademicGroupRepository groupRepository;
    @Mock private GroupStudentRepository groupStudentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudentXlsxReader xlsxReader;
    @Mock private UserService userService;

    @Spy private PhoneNumberNormalizer phoneNumberNormalizer = new PhoneNumberNormalizer();

    private Validator validator;

    @InjectMocks
    private GroupBulkImportService bulkService;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
        bulkService = new GroupBulkImportService(
                groupRepository, groupStudentRepository, studentRepository,
                userRepository, xlsxReader, phoneNumberNormalizer, userService, validator);
    }

    // --- commitIntoGroup: happy path all new ---

    @Test
    void commitIntoGroup_createsAllUsersAndMemberships_whenAllNew() {
        AcademicGroup group = groupWithId(10L, "CS-21");
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        StudentInput alice = new StudentInput("alice@test.com", "Alice", "Smith", "0508529087");
        StudentInput bob = new StudentInput("bob@test.com", "Bob", "Jones", null);

        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bob@test.com")).thenReturn(Optional.empty());

        User aliceUser = userWithId(1L, "alice@test.com");
        User bobUser = userWithId(2L, "bob@test.com");
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenAnswer(inv -> {
                    CreateUserRequest req = inv.getArgument(0);
                    return "alice@test.com".equals(req.email())
                            ? UserResponse.from(aliceUser)
                            : UserResponse.from(bobUser);
                });
        when(userRepository.findById(1L)).thenReturn(Optional.of(aliceUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bobUser));
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(studentRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class)))
                .thenAnswer(inv -> {
                    Student s = inv.getArgument(0);
                    s.setId(s.getUser().getId() + 100L);
                    return s;
                });
        when(groupStudentRepository.existsByGroupIdAndStudentId(any(), any())).thenReturn(false);
        when(groupStudentRepository.save(any(GroupStudent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BulkImportResult result = bulkService.commitIntoGroup(10L, new BulkCommitRequest(List.of(alice, bob)));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.created()).extracting(BulkImportResult.ImportedStudent::email)
                .containsExactly("alice@test.com", "bob@test.com");
        assertThat(result.linked()).isEmpty();

        verify(userService, times(2)).createUser(any());
        verify(groupStudentRepository, times(2)).save(any(GroupStudent.class));
    }

    // --- commitIntoGroup: linked path (email already exists) ---

    @Test
    void commitIntoGroup_linksExistingUser_whenEmailAlreadyRegistered() {
        AcademicGroup group = groupWithId(10L, "CS-21");
        User existingUser = userWithId(1L, "alice@test.com");
        Student existingStudent = studentWithId(500L, existingUser);

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(existingUser));
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(existingStudent));
        when(groupStudentRepository.findActiveByStudentId(500L)).thenReturn(Optional.empty());
        when(groupStudentRepository.existsByGroupIdAndStudentId(10L, 500L)).thenReturn(false);
        when(groupStudentRepository.save(any(GroupStudent.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentInput alice = new StudentInput("alice@test.com", "Alice", "Smith", null);
        BulkImportResult result = bulkService.commitIntoGroup(10L, new BulkCommitRequest(List.of(alice)));

        assertThat(result.created()).isEmpty();
        assertThat(result.linked()).singleElement()
                .extracting(BulkImportResult.ImportedStudent::email).isEqualTo("alice@test.com");
        verify(userService, never()).createUser(any());
        verify(groupStudentRepository).save(any(GroupStudent.class));
    }

    // --- commitIntoGroup: idempotent (already a member) ---

    @Test
    void commitIntoGroup_skipsMembership_whenAlreadyMemberOfSameGroup() {
        AcademicGroup group = groupWithId(10L, "CS-21");
        User user = userWithId(1L, "alice@test.com");
        Student student = studentWithId(500L, user);

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(groupStudentRepository.findActiveByStudentId(500L))
                .thenReturn(Optional.of(GroupStudent.builder().group(group).student(student).build()));
        when(groupStudentRepository.existsByGroupIdAndStudentId(10L, 500L)).thenReturn(true);

        StudentInput alice = new StudentInput("alice@test.com", "Alice", "Smith", null);
        BulkImportResult result = bulkService.commitIntoGroup(10L, new BulkCommitRequest(List.of(alice)));

        assertThat(result.linked()).hasSize(1);
        verify(groupStudentRepository, never()).save(any(GroupStudent.class));
    }

    // --- pre-validation: duplicate in batch ---

    @Test
    void commitIntoGroup_rejectsBatch_whenDuplicateEmailWithinBatch() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(groupWithId(10L, "CS-21")));
        StudentInput a1 = new StudentInput("alice@test.com", "Alice", "Smith", null);
        StudentInput a2 = new StudentInput("ALICE@test.com", "Alicia", "S", null);

        assertThatThrownBy(() -> bulkService.commitIntoGroup(10L, new BulkCommitRequest(List.of(a1, a2))))
                .isInstanceOfSatisfying(InvalidImportException.class, ex -> {
                    assertThat(ex.getErrors()).anySatisfy(e -> {
                        assertThat(e.rowNumber()).isEqualTo(2);
                        assertThat(e.field()).isEqualTo("email");
                        assertThat(e.message()).contains("duplicate");
                    });
                });

        verify(userService, never()).createUser(any());
        verify(groupStudentRepository, never()).save(any(GroupStudent.class));
    }

    // --- pre-validation: student already active elsewhere ---

    @Test
    void commitIntoGroup_rejectsBatch_whenStudentActiveInAnotherGroup() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(groupWithId(10L, "CS-21")));
        User existingUser = userWithId(1L, "alice@test.com");
        Student existingStudent = studentWithId(500L, existingUser);
        AcademicGroup otherGroup = groupWithId(99L, "IP-22");
        GroupStudent activeElsewhere = GroupStudent.builder()
                .group(otherGroup).student(existingStudent).build();

        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(existingUser));
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(existingStudent));
        when(groupStudentRepository.findActiveByStudentId(500L)).thenReturn(Optional.of(activeElsewhere));

        StudentInput alice = new StudentInput("alice@test.com", "Alice", "Smith", null);

        assertThatThrownBy(() -> bulkService.commitIntoGroup(10L, new BulkCommitRequest(List.of(alice))))
                .isInstanceOfSatisfying(InvalidImportException.class, ex -> {
                    assertThat(ex.getErrors()).singleElement().satisfies(e -> {
                        assertThat(e.rowNumber()).isEqualTo(1);
                        assertThat(e.field()).isEqualTo("email");
                        assertThat(e.message()).contains("IP-22");
                    });
                });

        verify(userService, never()).createUser(any());
    }

    // --- pre-validation: bean-validation catches bad email ---

    @Test
    void commitIntoGroup_rejectsBatch_whenBeanValidationFails() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(groupWithId(10L, "CS-21")));
        StudentInput bad = new StudentInput("not-an-email", "", "Smith", null);

        assertThatThrownBy(() -> bulkService.commitIntoGroup(10L, new BulkCommitRequest(List.of(bad))))
                .isInstanceOfSatisfying(InvalidImportException.class, ex -> {
                    assertThat(ex.getErrors())
                            .extracting(InvalidImportException.RowError::field)
                            .contains("email", "firstName");
                });

        verify(userService, never()).createUser(any());
    }

    // --- pre-validation: bad phone in edited-input path ---

    @Test
    void commitIntoGroup_rejectsBatch_whenPhoneCannotBeNormalised() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(groupWithId(10L, "CS-21")));
        StudentInput bad = new StudentInput("alice@test.com", "Alice", "Smith", "not-a-phone");

        assertThatThrownBy(() -> bulkService.commitIntoGroup(10L, new BulkCommitRequest(List.of(bad))))
                .isInstanceOfSatisfying(InvalidImportException.class, ex ->
                        assertThat(ex.getErrors())
                                .extracting(InvalidImportException.RowError::field)
                                .contains("phone"));

        verify(userService, never()).createUser(any());
    }

    // --- helpers ---

    private AcademicGroup groupWithId(long id, String code) {
        AcademicGroup g = AcademicGroup.builder().code(code).yearOfCreation(2021).build();
        g.setId(id);
        return g;
    }

    private User userWithId(long id, String email) {
        User u = User.builder()
                .email(email).firstName("F").lastName("L").role(Role.STUDENT).build();
        u.setId(id);
        return u;
    }

    private Student studentWithId(long id, User user) {
        Student s = Student.builder().user(user).build();
        s.setId(id);
        return s;
    }
}
