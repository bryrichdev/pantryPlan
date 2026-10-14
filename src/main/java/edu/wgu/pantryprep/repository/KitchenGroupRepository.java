package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KitchenGroupRepository extends JpaRepository<KitchenGroup, Long> {

    Optional<KitchenGroup> findByOwner(User owner);

    boolean existsByOwner(User owner);

    /**
     * Deletes in the database rather than through the persistence context.
     * Memberships, invites, and shares go with it by ON DELETE CASCADE, and
     * clearing afterwards drops any of them already loaded.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM KitchenGroup g WHERE g.id = :id")
    int deleteGroup(@Param("id") Long id);
}
