package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Role;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.service.EmailAlreadyUsedException;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserServiceTests {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RegistrationForm formFor(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Sample Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return form;
    }

    @Test
    void storesOnlyAHashedPassword() {
        User user = userService.register(formFor("hash@example.com"));

        assertNotEquals("correcthorsebattery", user.getPasswordHash(),
                "the raw password must never be stored");
        assertTrue(user.getPasswordHash().startsWith("$2"), "should be a BCrypt digest");
        assertTrue(passwordEncoder.matches("correcthorsebattery", user.getPasswordHash()));
        assertFalse(passwordEncoder.matches("wrongpassword", user.getPasswordHash()));
    }

    @Test
    void normalizesEmailAndAssignsDefaultRole() {
        User user = userService.register(formFor("  MixedCase@Example.COM  "));

        assertEquals("mixedcase@example.com", user.getEmail());
        assertEquals(Role.ROLE_USER, user.getRole());
        assertTrue(user.isEnabled());
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfCase() {
        userService.register(formFor("dupe@example.com"));

        assertThrows(EmailAlreadyUsedException.class,
                () -> userService.register(formFor("DUPE@example.com")));
    }

    @Test
    void saltsHashesSoIdenticalPasswordsDiffer() {
        User first = userService.register(formFor("salt1@example.com"));
        User second = userService.register(formFor("salt2@example.com"));

        assertNotEquals(first.getPasswordHash(), second.getPasswordHash(),
                "BCrypt salts each hash, so identical passwords must not collide");
    }

    @Test
    void detectsTakenEmail() {
        userService.register(formFor("taken@example.com"));

        assertTrue(userService.emailIsTaken("TAKEN@example.com"));
        assertFalse(userService.emailIsTaken("free@example.com"));
    }
}
