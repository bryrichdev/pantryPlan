package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.Role;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.UserRepository;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.util.NoSuchElementException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account creation, lookup, and self-service changes.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an account, storing only a BCrypt digest of the password.
     *
     * @throws EmailAlreadyUsedException when the email is already registered
     */
    @Transactional
    public User register(RegistrationForm form) {
        String email = form.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        String hash = passwordEncoder.encode(form.getPassword());
        User user = new User(email, hash, form.getDisplayName().trim());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User requireById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No user with id " + id));
    }

    @Transactional(readOnly = true)
    public boolean emailIsTaken(String email) {
        return userRepository.existsByEmailIgnoreCase(email.trim());
    }

    /** True when the email belongs to an account other than this one. */
    @Transactional(readOnly = true)
    public boolean emailIsTakenByAnother(String email, Long userId) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .filter(match -> !match.getId().equals(userId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean passwordMatches(User user, String rawPassword) {
        return rawPassword != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /**
     * @throws EmailAlreadyUsedException when another account has the new email
     */
    @Transactional
    public User updateProfile(Long userId, String displayName, String email) {
        User user = requireById(userId);
        String normalized = email.trim().toLowerCase();
        if (emailIsTakenByAnother(normalized, userId)) {
            throw new EmailAlreadyUsedException(normalized);
        }
        user.setDisplayName(displayName.trim());
        user.setEmail(normalized);
        return userRepository.save(user);
    }

    @Transactional
    public User changePassword(Long userId, String newRawPassword) {
        User user = requireById(userId);
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        return userRepository.save(user);
    }

    /** Whether removing this account would leave the app with no administrator. */
    @Transactional(readOnly = true)
    public boolean isLastAdmin(User user) {
        return user.getRole() == Role.ROLE_ADMIN && userRepository.countByRole(Role.ROLE_ADMIN) <= 1;
    }
}
