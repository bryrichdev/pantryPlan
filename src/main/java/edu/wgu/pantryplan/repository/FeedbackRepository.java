package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.Feedback;
import edu.wgu.pantryplan.domain.FeedbackStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findAllByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    long countByStatus(FeedbackStatus status);
}
