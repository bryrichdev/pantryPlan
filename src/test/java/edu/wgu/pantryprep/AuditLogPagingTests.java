package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.wgu.pantryprep.audit.AuditLogService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pager's five-page window. Plain unit tests: no Spring and no database. */
class AuditLogPagingTests {

    @Test
    void keepsTheCurrentPageInTheMiddle() {
        assertEquals(List.of(3, 4, 5, 6, 7), AuditLogService.pageWindow(5, 20));
    }

    @Test
    void slidesOverAtTheStartSoFivePagesStillShow() {
        assertEquals(List.of(0, 1, 2, 3, 4), AuditLogService.pageWindow(0, 20));
        assertEquals(List.of(0, 1, 2, 3, 4), AuditLogService.pageWindow(1, 20));
    }

    @Test
    void slidesOverAtTheEndSoFivePagesStillShow() {
        assertEquals(List.of(15, 16, 17, 18, 19), AuditLogService.pageWindow(19, 20));
        assertEquals(List.of(15, 16, 17, 18, 19), AuditLogService.pageWindow(18, 20));
    }

    @Test
    void showsEveryPageWhenThereAreFewerThanFive() {
        assertEquals(List.of(0, 1, 2), AuditLogService.pageWindow(1, 3));
        assertEquals(List.of(0), AuditLogService.pageWindow(0, 1));
    }
}
