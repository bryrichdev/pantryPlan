package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.report.PantryExpiryReport;
import edu.wgu.pantryprep.report.PantryStockReport;
import edu.wgu.pantryprep.report.RecipeUsageReport;
import edu.wgu.pantryprep.report.Report;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produces account-scoped report snapshots for the shared report page. */
@Service
public class ReportService {

    private final PantryService pantryService;
    private final RecipeService recipeService;

    public ReportService(PantryService pantryService, RecipeService recipeService) {
        this.pantryService = pantryService;
        this.recipeService = recipeService;
    }

    @Transactional(readOnly = true)
    public Report pantryStock(User user) {
        return generate(new PantryStockReport(pantryService.findAll(user), Instant.now()));
    }

    @Transactional(readOnly = true)
    public Report recipeUsage(User user) {
        return generate(new RecipeUsageReport(recipeService.findByUsage(user), Instant.now()));
    }

    @Transactional(readOnly = true)
    public Report nearExpiredPantryItems(User user) {
        return nearExpiredPantryItems(user, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Report nearExpiredPantryItems(User user, LocalDate today) {
        return expiryReport(user, today, PantryExpiryReport.Scope.NEAR_EXPIRY);
    }

    @Transactional(readOnly = true)
    public Report expiredPantryItems(User user) {
        return expiredPantryItems(user, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Report expiredPantryItems(User user, LocalDate today) {
        return expiryReport(user, today, PantryExpiryReport.Scope.EXPIRED);
    }

    private Report expiryReport(User user, LocalDate today, PantryExpiryReport.Scope scope) {
        return generate(new PantryExpiryReport(pantryService.findAll(user), today,
                PantryService.EXPIRY_WARNING_DAYS, scope, Instant.now()));
    }

    /** The caller depends only on the base type; subclasses supply the rows. */
    private Report generate(Report report) {
        report.generate();
        return report;
    }
}
