package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.GroupMember;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /** The membership every group permission check starts from. */
    @Query("""
            SELECT m FROM GroupMember m
            JOIN FETCH m.group g
            JOIN FETCH g.owner
            WHERE g.id = :groupId AND m.user = :user
            """)
    Optional<GroupMember> findMembership(@Param("groupId") Long groupId, @Param("user") User user);

    Optional<GroupMember> findByIdAndGroup(Long id, KitchenGroup group);

    Optional<GroupMember> findByGroupAndUser(KitchenGroup group, User user);

    boolean existsByGroupAndUser(KitchenGroup group, User user);

    /** Every group this user belongs to, with the group loaded for display. */
    @Query("""
            SELECT m FROM GroupMember m
            JOIN FETCH m.group g
            JOIN FETCH g.owner
            WHERE m.user = :user
            ORDER BY LOWER(g.name)
            """)
    List<GroupMember> findAllForUser(@Param("user") User user);

    /** Everyone in a group, with their accounts loaded for display. */
    @Query("""
            SELECT m FROM GroupMember m
            JOIN FETCH m.user
            WHERE m.group = :group
            """)
    List<GroupMember> findAllInGroup(@Param("group") KitchenGroup group);

    long countByGroup(KitchenGroup group);

    long countByUser(User user);
}
