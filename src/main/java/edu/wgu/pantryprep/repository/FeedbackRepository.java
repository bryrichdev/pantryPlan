package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.Feedback;
import edu.wgu.pantryprep.domain.FeedbackStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findAllByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    long countByStatus(FeedbackStatus status);
}
