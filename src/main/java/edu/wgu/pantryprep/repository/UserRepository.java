package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.Role;
import edu.wgu.pantryprep.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    /**
     * The last step of account removal. Ingredients go with the user through
     * ON DELETE CASCADE, which only works once nothing else points at them.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.id = :id")
    int deleteAccountRow(@Param("id") Long id);
}
