package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.IngredientRepository;
import edu.wgu.pantryprep.repository.PantryItemRepository;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.wgu.pantryprep.repository.GroceryListItemRepository;

/**
 * What is on the shelf, for a single cook.
 *
 * <p>Read methods touch each item's ingredient before returning, because
 * {@code spring.jpa.open-in-view} is off and the templates read the ingredient
 * name, category, and density.
 */
@Service
public class PantryService {

    /** The same one-week window shown by the pantry warning and expiry report. */
    public static final int EXPIRY_WARNING_DAYS = 7;

    private final PantryItemRepository pantryItemRepository;
    private final IngredientRepository ingredientRepository;

    private final GroceryListItemRepository groceryListItemRepository;

    public PantryService(PantryItemRepository pantryItemRepository,
                         IngredientRepository ingredientRepository,
                         GroceryListItemRepository groceryListItemRepository) {
        this.pantryItemRepository = pantryItemRepository;
        this.ingredientRepository = ingredientRepository;
        this.groceryListItemRepository = groceryListItemRepository;
    }

    @Transactional(readOnly = true)
    public List<PantryItem> findAll(User user) {
        return initialize(pantryItemRepository.findAllByUserOrderByIngredientNameAsc(user));
    }

    /**
     * Searches by ingredient name and optionally narrows to one storage
     * location. A blank term returns everything rather than nothing, so
     * clearing the box restores the list.
     */
    @Transactional(readOnly = true)
    public List<PantryItem> search(User user, String term, StorageLocation location) {
        boolean hasTerm = term != null && !term.isBlank();
        String trimmed = hasTerm ? term.trim() : "";

        if (location == null && !hasTerm) {
            return findAll(user);
        }
        if (location == null) {
            return initialize(pantryItemRepository
                    .findAllByUserAndIngredientNameContainingIgnoreCaseOrderByIngredientNameAsc(
                            user, trimmed));
        }
        if (!hasTerm) {
            return initialize(pantryItemRepository
                    .findAllByUserAndLocationOrderByIngredientNameAsc(user, location));
        }
        return initialize(pantryItemRepository
                .findAllByUserAndLocationAndIngredientNameContainingIgnoreCaseOrderByIngredientNameAsc(
                        user, location, trimmed));
    }

    /**
     * Items already expired or due within the given number of days.
     */
    @Transactional(readOnly = true)
    public List<PantryItem> findExpiringWithin(User user, int days, LocalDate today) {
        return initialize(pantryItemRepository
                .findAllByUserAndExpiresOnLessThanEqualOrderByExpiresOnAsc(user, today.plusDays(days)));
    }

    /**
     * Every shelf entry for one ingredient. The grocery list adds these up to
     * work out how much is actually on hand.
     */
    @Transactional(readOnly = true)
    public List<PantryItem> findAllOf(User user, Ingredient ingredient) {
        return initialize(pantryItemRepository.findAllByUserAndIngredient(user, ingredient));
    }

    @Transactional(readOnly = true)
    public PantryItem requireOwned(Long id, User user) {
        PantryItem item = pantryItemRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("No pantry item " + id + " for this account"));
        item.getIngredient().getName();
        return item;
    }

    private List<PantryItem> initialize(List<PantryItem> items) {
        items.forEach(item -> item.getIngredient().getName());
        return items;
    }

    @Transactional
    public PantryItem create(PantryItemForm form, User user) {
        Ingredient ingredient = requireIngredient(form.getIngredientId(), user);
        PantryItem item = new PantryItem(user, ingredient, form.getQuantity());
        applyForm(item, form);
        return pantryItemRepository.save(item);
    }

    @Transactional
    public PantryItem update(Long id, PantryItemForm form, User user) {
        PantryItem item = requireOwned(id, user);
        item.setIngredient(requireIngredient(form.getIngredientId(), user));
        item.setQuantity(form.getQuantity());
        applyForm(item, form);
        return pantryItemRepository.save(item);
    }

    private void applyForm(PantryItem item, PantryItemForm form) {
        item.setLocation(form.getLocation());
        item.setPurchasedOn(form.getPurchasedOn());
        item.setExpiresOn(form.getExpiresOn());
    }

    /**
     * Re-reads the ingredient scoped to the owner, so a tampered id belonging to
     * another account fails here rather than attaching someone else's record.
     */
    private Ingredient requireIngredient(Long ingredientId, User user) {
        return ingredientRepository.findByIdAndUser(ingredientId, user)
                .orElseThrow(() -> new NoSuchElementException(
                        "No ingredient " + ingredientId + " for this account"));
    }


    /**
     * Removes several shelf entries at once.
     *
     * <p>Nothing references a pantry item, so none of these can be blocked. Ids
     * that do not belong to this account are counted as missing rather than
     * raising, since a stale page can easily submit one.
     */
    @Transactional
    public BulkDeleteResult deleteAll(Collection<Long> ids, User user) {
        BulkDeleteResult result = new BulkDeleteResult();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (Long id : ids) {
            PantryItem item = pantryItemRepository.findByIdAndUser(id, user).orElse(null);
            if (item == null) {
                result.recordMissing();
                continue;
            }
            unlinkFromGroceryLists(item);
            pantryItemRepository.delete(item);
            result.recordDeleted();
        }
        return result;
    }

    /**
     * Nothing references a pantry item, so deletion never has to be blocked.
     */
    @Transactional
    public void delete(Long id, User user) {
        PantryItem item = requireOwned(id, user);
        unlinkFromGroceryLists(item);
        pantryItemRepository.delete(item);
    }

    /**
     * A grocery line remembers the shelf entry it created. The database would
     * set that link to null on its own, but Hibernate refuses to flush a
     * reference to a row it is deleting in the same transaction, so the link is
     * cleared here first. What the line bought stays recorded either way.
     */
    private void unlinkFromGroceryLists(PantryItem item) {
        groceryListItemRepository.findAllByPantryItem(item)
                .forEach(line -> {
                    line.unlinkPantryItem();
                    groceryListItemRepository.save(line);
                });
    }
}
