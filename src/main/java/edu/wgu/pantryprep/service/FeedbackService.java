package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.Feedback;
import edu.wgu.pantryprep.domain.FeedbackKind;
import edu.wgu.pantryprep.domain.FeedbackStatus;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.FeedbackRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Problem reports and change requests. Any signed-in account can send one;
 * only admins read them, from the Feedback tab.
 */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserService userService;

    public FeedbackService(FeedbackRepository feedbackRepository, UserService userService) {
        this.feedbackRepository = feedbackRepository;
        this.userService = userService;
    }

    /**
     * Saves a report. Blank details are stored as null. The page path must
     * already be checked by the caller; it is only trimmed to fit here.
     */
    @Transactional
    public Feedback submit(Long userId, FeedbackKind kind, String summary, String details, String pagePath) {
        User sender = userService.requireById(userId);
        String cleanDetails = details == null || details.isBlank() ? null : details.trim();
        String cleanPath = pagePath == null ? null : pagePath.substring(0, Math.min(pagePath.length(), 255));
        return feedbackRepository.save(new Feedback(sender, kind, summary.trim(), cleanDetails, cleanPath));
    }

    /** Newest first. A null status lists every report. */
    @Transactional(readOnly = true)
    public List<Feedback> list(FeedbackStatus status) {
        return status == null
                ? feedbackRepository.findAllByOrderByCreatedAtDesc()
                : feedbackRepository.findAllByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return feedbackRepository.countByStatus(FeedbackStatus.OPEN);
    }

    @Transactional
    public Feedback setStatus(Long id, FeedbackStatus status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No feedback with id " + id));
        if (status == FeedbackStatus.RESOLVED) {
            feedback.resolve();
        } else {
            feedback.reopen();
        }
        return feedback;
    }
}
