package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.GroupInvitation;
import edu.wgu.pantryprep.domain.KitchenGroup;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    Optional<GroupInvitation> findByGroupAndEmail(KitchenGroup group, String email);

    Optional<GroupInvitation> findByIdAndGroup(Long id, KitchenGroup group);

    /** An invitation addressed to this email, which is how the invitee proves it is theirs. */
    @Query("""
            SELECT i FROM GroupInvitation i
            JOIN FETCH i.group g
            JOIN FETCH g.owner
            WHERE i.id = :id AND i.email = :email
            """)
    Optional<GroupInvitation> findByIdAndEmail(@Param("id") Long id, @Param("email") String email);

    /** Unexpired invitations waiting for whoever signs in with this email. */
    @Query("""
            SELECT i FROM GroupInvitation i
            JOIN FETCH i.group g
            JOIN FETCH g.owner
            LEFT JOIN FETCH i.invitedBy
            WHERE i.email = :email AND i.expiresAt > :now
            ORDER BY i.createdAt DESC
            """)
    List<GroupInvitation> findOpenFor(@Param("email") String email, @Param("now") Instant now);

    @Query("""
            SELECT i FROM GroupInvitation i
            LEFT JOIN FETCH i.invitedBy
            WHERE i.group = :group AND i.expiresAt > :now
            ORDER BY i.createdAt DESC
            """)
    List<GroupInvitation> findOpenInGroup(@Param("group") KitchenGroup group, @Param("now") Instant now);

    @Query("SELECT COUNT(i) FROM GroupInvitation i WHERE i.group = :group AND i.expiresAt > :now")
    long countOpenInGroup(@Param("group") KitchenGroup group, @Param("now") Instant now);
}
