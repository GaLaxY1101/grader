package ua.kpi.grader.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.DuplicateEmailException;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.user.dto.CreateUserRequest;
import ua.kpi.grader.user.dto.UpdateUserRequest;
import ua.kpi.grader.user.dto.UserResponse;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Finds a user by their email address.
     *
     * @param email the email to search for
     * @return the matching User entity
     * @throws ResourceNotFoundException if no user exists with that email
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
    }

    /**
     * Finds a user by their primary key.
     *
     * @param id the user's ID
     * @return the matching User entity
     * @throws ResourceNotFoundException if no user exists with that ID
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));
    }

    /**
     * Returns all users as response DTOs.
     *
     * @return list of UserResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Creates a new user, encoding their password with BCrypt.
     *
     * @param request the creation payload
     * @return the persisted user as a UserResponse
     * @throws DuplicateEmailException if the email is already registered
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .dateOfBirth(request.dateOfBirth())
                .role(request.role())
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Updates mutable profile fields for an existing user.
     *
     * @param id      the user's ID
     * @param request the update payload
     * @return the updated user as a UserResponse
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findById(id);
        user.update(request.firstName(), request.lastName(),
                request.phone(), request.dateOfBirth(), request.isActive());
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Soft-deletes a user by setting isActive to false.
     *
     * @param id the user's ID
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public void deactivateUser(Long id) {
        User user = findById(id);
        user.update(user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getDateOfBirth(), false);
        userRepository.save(user);
    }

    /**
     * Permanently deletes a user record.
     *
     * @param id the user's ID
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = findById(id);
        userRepository.delete(user);
    }
}
