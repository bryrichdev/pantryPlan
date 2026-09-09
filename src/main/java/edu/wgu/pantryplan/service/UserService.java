package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.UserRepository;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.util.NoSuchElementException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account creation and lookup.
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
}
