package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.MealSlot;
import edu.wgu.pantryprep.domain.Unit;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything on the home page for one account. Plain SQL: it is a handful of
 * counts and short lists across many tables, and nothing here is edited.
 *
 * <p>Every method takes "today" so the results can be tested on a fixed date.
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);

    /** How many of each thing the account has. */
    public record Counts(long ingredients, long recipes, long pantryItems, long mealPlans, long groceryLists) {

        /** Nothing to plan around yet: no recipes, no stock, no plans. */
        public boolean isNew() {
            return recipes == 0 && pantryItems == 0 && mealPlans == 0;
        }

        /** The four steps before a grocery list can be built are all done. */
        public boolean setupComplete() {
            return ingredients > 0 && recipes > 0 && pantryItems > 0 && mealPlans > 0;
        }
    }

    /** Pantry rows with stock left that are past their date, or near it. */
    public record ExpiryCounts(long expired, long soon) {
    }

    /** One pantry row with an expiry date, and how many days it has left. */
    public record Expiring(Long id, String name, BigDecimal quantity, Unit unit, LocalDate expiresOn, int daysLeft) {

        public boolean expired() {
            return daysLeft < 0;
        }

        /** "Expired 3 days ago", "Expired yesterday", "Expires today", "Expires tomorrow", "Expires in 4 days". */
        public String when() {
            if (daysLeft < -1) {
                return "Expired " + (-daysLeft) + " days ago";
            }
            if (daysLeft == -1) {
                return "Expired yesterday";
            }
            if (daysLeft == 0) {
                return "Expires today";
            }
            if (daysLeft == 1) {
                return "Expires tomorrow";
            }
            return "Expires in " + daysLeft + " days";
        }
    }

    /** A recipe placed on a day of the plan. */
    public record PlannedMeal(Long entryId, Long recipeId, String recipeName, LocalDate date,
                              MealSlot slot, int servings, boolean cooked) {
    }

    /** One day of the plan and the meals on it, breakfast first. */
    public record PlanDay(LocalDate date, boolean isToday, boolean isPast, List<PlannedMeal> meals) {

        public String label() {
            if (isToday) {
                return "Today";
            }
            return date.format(DAY);
        }
    }

    /** The plan whose week includes today. */
    public record WeekPlan(Long id, String name, LocalDate weekStart, List<PlanDay> days, Long groceryListId) {

        public long mealsLeft() {
            return days.stream().filter(day -> !day.isPast())
                    .flatMap(day -> day.meals().stream())
                    .filter(meal -> !meal.cooked())
                    .count();
        }

        public long mealsPlanned() {
            return days.stream().mapToLong(day -> day.meals().size()).sum();
        }
    }

    /** The newest grocery list and how far through it the shopping is. */
    public record Shopping(Long id, String title, Instant generatedAt, long total, long bought, long toPutAway) {

        public long remaining() {
            return total - bought;
        }

        public int percentBought() {
            return total == 0 ? 0 : (int) Math.round(100.0 * bought / total);
        }
    }

    /** A meal marked cooked, newest first. */
    public record Cooked(Long recipeId, String recipeName, Instant cookedAt) {

        /** "just now", "5 minutes ago", "3 hours ago", "yesterday", "12 days ago". */
        public String ago() {
            long minutes = Duration.between(cookedAt, Instant.now()).toMinutes();
            if (minutes < 1) {
                return "just now";
            }
            if (minutes < 60) {
                return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
            }
            long hours = minutes / 60;
            if (hours < 24) {
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            }
            long days = hours / 24;
            return days == 1 ? "yesterday" : days + " days ago";
        }
    }

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Counts counts(Long userId) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM ingredients WHERE user_id = ?) AS ingredients,
                       (SELECT count(*) FROM recipes WHERE user_id = ?) AS recipes,
                       (SELECT count(*) FROM pantry_items WHERE user_id = ?) AS pantry_items,
                       (SELECT count(*) FROM meal_plans WHERE user_id = ?) AS meal_plans,
                       (SELECT count(*) FROM grocery_lists WHERE user_id = ?) AS grocery_lists
                """, (rs, row) -> new Counts(rs.getLong("ingredients"), rs.getLong("recipes"),
                        rs.getLong("pantry_items"), rs.getLong("meal_plans"), rs.getLong("grocery_lists")),
                userId, userId, userId, userId, userId);
    }

    /** Rows with stock left: how many are past their date, and how many run out within the window. */
    @Transactional(readOnly = true)
    public ExpiryCounts expiryCounts(Long userId, LocalDate today, int warningDays) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE expires_on < CAST(? AS date)) AS expired,
                       count(*) FILTER (WHERE expires_on >= CAST(? AS date)
                                          AND expires_on <= CAST(? AS date) + ?) AS soon
                FROM pantry_items
                WHERE user_id = ? AND expires_on IS NOT NULL AND quantity > 0
                """, (rs, row) -> new ExpiryCounts(rs.getLong("expired"), rs.getLong("soon")),
                today, today, today, warningDays, userId);
    }

    /**
     * What to use up first: expired rows and rows expiring within the window,
     * soonest first. Rows cooked down to nothing are left out.
     */
    @Transactional(readOnly = true)
    public List<Expiring> expiring(Long userId, LocalDate today, int warningDays, int limit) {
        return jdbc.query("""
                SELECT p.id, i.name, p.quantity, i.stock_unit, p.expires_on,
                       (p.expires_on - CAST(? AS date)) AS days_left
                FROM pantry_items p
                JOIN ingredients i ON i.id = p.ingredient_id
                WHERE p.user_id = ? AND p.expires_on IS NOT NULL AND p.quantity > 0
                  AND p.expires_on <= CAST(? AS date) + ?
                ORDER BY p.expires_on, i.name
                LIMIT ?
                """, (rs, row) -> new Expiring(rs.getLong("id"), rs.getString("name"),
                        rs.getBigDecimal("quantity"), Unit.valueOf(rs.getString("stock_unit")),
                        rs.getObject("expires_on", LocalDate.class), rs.getInt("days_left")),
                today, userId, today, warningDays, limit);
    }

    /**
     * The plan whose seven days include today, or null. When two plans overlap
     * today, the one that started most recently wins.
     */
    @Transactional(readOnly = true)
    public WeekPlan weekPlan(Long userId, LocalDate today) {
        List<Object[]> plans = jdbc.query("""
                SELECT id, name, week_start_date FROM meal_plans
                WHERE user_id = ?
                  AND week_start_date <= CAST(? AS date)
                  AND week_start_date + 6 >= CAST(? AS date)
                ORDER BY week_start_date DESC, id DESC
                LIMIT 1
                """, (rs, row) -> new Object[] {rs.getLong("id"), rs.getString("name"),
                        rs.getObject("week_start_date", LocalDate.class)},
                userId, today, today);
        if (plans.isEmpty()) {
            return null;
        }
        Long planId = (Long) plans.getFirst()[0];
        String name = (String) plans.getFirst()[1];
        LocalDate weekStart = (LocalDate) plans.getFirst()[2];

        List<PlannedMeal> meals = jdbc.query("""
                SELECT e.id, e.recipe_id, r.name, e.plan_date, e.meal_slot, e.servings, e.cooked
                FROM plan_entries e
                JOIN recipes r ON r.id = e.recipe_id
                WHERE e.meal_plan_id = ?
                ORDER BY e.plan_date, r.name
                """, (rs, row) -> new PlannedMeal(rs.getLong("id"), rs.getLong("recipe_id"), rs.getString("name"),
                        rs.getObject("plan_date", LocalDate.class), MealSlot.valueOf(rs.getString("meal_slot")),
                        rs.getInt("servings"), rs.getBoolean("cooked")),
                planId);

        List<PlanDay> days = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = weekStart.plusDays(offset);
            List<PlannedMeal> onDay = meals.stream()
                    .filter(meal -> meal.date().equals(date))
                    .sorted(Comparator.comparing(PlannedMeal::slot))
                    .toList();
            days.add(new PlanDay(date, date.equals(today), date.isBefore(today), onDay));
        }

        List<Long> lists = jdbc.queryForList("""
                SELECT id FROM grocery_lists WHERE meal_plan_id = ?
                ORDER BY generated_at DESC, id DESC LIMIT 1
                """, Long.class, planId);

        return new WeekPlan(planId, name, weekStart, days, lists.isEmpty() ? null : lists.getFirst());
    }

    /** The most recently built grocery list, or null when there is none. */
    @Transactional(readOnly = true)
    public Shopping latestList(Long userId) {
        List<Shopping> lists = jdbc.query("""
                SELECT g.id, g.title, g.generated_at,
                       count(i.id) AS total,
                       count(i.id) FILTER (WHERE i.purchased) AS bought,
                       count(i.id) FILTER (WHERE i.purchased AND i.stocked_at IS NULL) AS to_put_away
                FROM grocery_lists g
                LEFT JOIN grocery_list_items i ON i.grocery_list_id = g.id
                WHERE g.user_id = ?
                GROUP BY g.id, g.title, g.generated_at
                ORDER BY g.generated_at DESC, g.id DESC
                LIMIT 1
                """, (rs, row) -> new Shopping(rs.getLong("id"), rs.getString("title"),
                        rs.getObject("generated_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("total"), rs.getLong("bought"), rs.getLong("to_put_away")),
                userId);
        return lists.isEmpty() ? null : lists.getFirst();
    }

    @Transactional(readOnly = true)
    public List<Cooked> recentlyCooked(Long userId, int limit) {
        return jdbc.query("""
                SELECT r.id, r.name, e.cooked_at
                FROM plan_entries e
                JOIN meal_plans m ON m.id = e.meal_plan_id
                JOIN recipes r ON r.id = e.recipe_id
                WHERE m.user_id = ? AND e.cooked AND e.cooked_at IS NOT NULL
                ORDER BY e.cooked_at DESC
                LIMIT ?
                """, (rs, row) -> new Cooked(rs.getLong("id"), rs.getString("name"),
                        rs.getObject("cooked_at", OffsetDateTime.class).toInstant()),
                userId, limit);
    }
}
