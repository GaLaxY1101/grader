package ua.kpi.grader.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.user.dto.CreateStudentRequest;
import ua.kpi.grader.user.dto.StudentResponse;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.repository.StudentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserService userService;

    /**
     * Returns all student profiles.
     *
     * @return list of StudentResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<StudentResponse> findAll() {
        return studentRepository.findAll().stream()
                .map(StudentResponse::from)
                .toList();
    }

    /**
     * Returns a student profile by its primary key.
     *
     * @param id the student profile ID
     * @return the matching StudentResponse DTO
     * @throws ResourceNotFoundException if no student profile exists with that ID
     */
    @Transactional(readOnly = true)
    public StudentResponse findById(Long id) {
        return studentRepository.findById(id)
                .map(StudentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with id: " + id));
    }

    /**
     * Creates a student profile linked to an existing user.
     *
     * @param request the creation payload containing userId
     * @return the persisted StudentResponse DTO
     * @throws ResourceNotFoundException if the referenced user does not exist
     * @throws IllegalStateException     if the user already has a student profile
     */
    @Transactional
    public StudentResponse createStudent(CreateStudentRequest request) {
        var user = userService.findById(request.userId());
        if (studentRepository.existsByUserId(request.userId())) {
            throw new IllegalStateException(
                    "Student profile already exists for user id: " + request.userId());
        }
        Student student = Student.builder()
                .user(user)
                .build();
        return StudentResponse.from(studentRepository.save(student));
    }

    /**
     * Deletes a student profile by its primary key.
     *
     * @param id the student profile ID
     * @throws ResourceNotFoundException if no student profile exists with that ID
     */
    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with id: " + id));
        studentRepository.delete(student);
    }
}
