package ua.kpi.grader.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.user.dto.CreateTeacherRequest;
import ua.kpi.grader.user.dto.TeacherResponse;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final UserService userService;

    /**
     * Returns all teacher profiles.
     *
     * @return list of TeacherResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<TeacherResponse> findAll() {
        return teacherRepository.findAll().stream()
                .map(TeacherResponse::from)
                .toList();
    }

    /**
     * Returns a teacher profile by its primary key.
     *
     * @param id the teacher profile ID
     * @return the matching TeacherResponse DTO
     * @throws ResourceNotFoundException if no teacher profile exists with that ID
     */
    @Transactional(readOnly = true)
    public TeacherResponse findById(Long id) {
        return teacherRepository.findById(id)
                .map(TeacherResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found with id: " + id));
    }

    /**
     * Creates a teacher profile linked to an existing user.
     *
     * @param request the creation payload containing userId and optional profile fields
     * @return the persisted TeacherResponse DTO
     * @throws ResourceNotFoundException if the referenced user does not exist
     * @throws IllegalStateException     if the user already has a teacher profile
     */
    @Transactional
    public TeacherResponse createTeacher(CreateTeacherRequest request) {
        var user = userService.findById(request.userId());
        if (teacherRepository.existsByUserId(request.userId())) {
            throw new IllegalStateException(
                    "Teacher profile already exists for user id: " + request.userId());
        }
        Teacher teacher = Teacher.builder()
                .user(user)
                .department(request.department())
                .academicDegree(request.academicDegree())
                .build();
        return TeacherResponse.from(teacherRepository.save(teacher));
    }

    /**
     * Deletes a teacher profile by its primary key.
     *
     * @param id the teacher profile ID
     * @throws ResourceNotFoundException if no teacher profile exists with that ID
     */
    @Transactional
    public void deleteTeacher(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found with id: " + id));
        teacherRepository.delete(teacher);
    }
}
