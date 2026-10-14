package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.GroupInviteLink;
import edu.wgu.pantryprep.domain.KitchenGroup;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInviteLinkRepository extends JpaRepository<GroupInviteLink, Long> {

    @Query("""
            SELECT l FROM GroupInviteLink l
            JOIN FETCH l.group g
            JOIN FETCH g.owner
            WHERE l.tokenHash = :tokenHash
            """)
    Optional<GroupInviteLink> findByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<GroupInviteLink> findByIdAndGroup(Long id, KitchenGroup group);

    /** Links that can still be used, newest first, for the group page. */
    @Query("""
            SELECT l FROM GroupInviteLink l
            LEFT JOIN FETCH l.createdBy
            WHERE l.group = :group
              AND l.revokedAt IS NULL
              AND l.expiresAt > :now
              AND (l.maxUses IS NULL OR l.useCount < l.maxUses)
            ORDER BY l.createdAt DESC
            """)
    List<GroupInviteLink> findActive(@Param("group") KitchenGroup group, @Param("now") Instant now);

    /**
     * Takes one use of the link, but only if it is still usable at this
     * moment. Done as one conditional UPDATE so two people joining at once
     * cannot both take the last use.
     *
     * @return 1 when a use was taken, 0 when the link was spent, revoked, or expired
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE GroupInviteLink l
            SET l.useCount = l.useCount + 1
            WHERE l.id = :id
              AND l.revokedAt IS NULL
              AND l.expiresAt > :now
              AND (l.maxUses IS NULL OR l.useCount < l.maxUses)
            """)
    int claimUse(@Param("id") Long id, @Param("now") Instant now);
}
