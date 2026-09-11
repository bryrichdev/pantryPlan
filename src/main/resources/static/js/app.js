/*
 * PantryPlan browser behaviour.
 *
 * Two features live here: modal dialogs built on the native <dialog> element,
 * and the repeating ingredient rows on the recipe form. Neither does any
 * validation — that stays on the server, so a rejected form comes back with
 * its messages already rendered.
 */
(function () {
    "use strict";

    /* ---------------------------------------------------------------- dialogs */

    function openDialog(dialog) {
        if (typeof dialog.showModal === "function") {
            dialog.showModal();
        } else {
            dialog.setAttribute("open", "");
        }
    }

    /*
     * The usual amount means nothing without its unit, so the label beside it
     * follows whatever "Kept in" is set to.
     */
    function syncIngredientUnit(dialog) {
        var select = dialog.querySelector("#dialog-stock-unit");
        var label = dialog.querySelector("[data-ingredient-unit]");
        if (!select || !label) {
            return;
        }
        var chosen = select.options[select.selectedIndex];
        label.textContent = chosen ? chosen.textContent : "\u2014";
    }

    function fillIngredientDialog(dialog, trigger) {
        var mode = trigger.getAttribute("data-mode");
        var title = dialog.querySelector("[data-dialog-title]");
        var idField = dialog.querySelector("input[name='id']");
        var nameField = dialog.querySelector("#dialog-name");
        var categoryField = dialog.querySelector("#dialog-category");
        var gramsField = dialog.querySelector("#dialog-grams");

        dialog.querySelectorAll(".field__error").forEach(function (node) {
            node.remove();
        });

        var unitField = dialog.querySelector("#dialog-stock-unit");
        var locationField = dialog.querySelector("#dialog-default-location");
        var quantityField = dialog.querySelector("#dialog-default-quantity");

        if (mode === "edit") {
            title.textContent = "Edit ingredient";
            idField.value = trigger.getAttribute("data-id") || "";
            nameField.value = trigger.getAttribute("data-name") || "";
            categoryField.value = trigger.getAttribute("data-category") || "OTHER";
            gramsField.value = trigger.getAttribute("data-grams") || "";
            if (unitField) {
                unitField.value = trigger.getAttribute("data-stock-unit") || "GRAM";
            }
            if (locationField) {
                locationField.value = trigger.getAttribute("data-default-location") || "PANTRY";
            }
            if (quantityField) {
                quantityField.value = trigger.getAttribute("data-default-quantity") || "";
            }
        } else {
            title.textContent = "Add ingredient";
            idField.value = "";
            nameField.value = "";
            categoryField.value = "OTHER";
            gramsField.value = "";
            if (unitField) {
                unitField.value = "GRAM";
            }
            if (locationField) {
                locationField.value = "PANTRY";
            }
            if (quantityField) {
                quantityField.value = "";
            }
        }
        syncIngredientUnit(dialog);
    }

    /*
     * Shows the stocking unit of whichever ingredient is selected. The unit is
     * a property of the ingredient now, so the pantry dialog reports it rather
     * than asking for it.
     */
    function syncPantryUnit(dialog) {
        var select = dialog.querySelector("#pantry-ingredient");
        var label = dialog.querySelector("[data-pantry-unit]");
        if (!select || !label) {
            return null;
        }
        var chosen = select.options[select.selectedIndex];
        var unit = chosen ? chosen.getAttribute("data-unit") : null;
        label.textContent = unit || "\u2014";
        return chosen;
    }

    /*
     * Prefills the amount from the chosen ingredient's usual amount. It only
     * replaces a value the dialog put there itself: switching from eggs to milk
     * swaps 12 for the milk amount, but a number the cook typed is kept.
     */
    function applyUsualQuantity(dialog, chosen) {
        var field = dialog.querySelector("#pantry-quantity");
        if (!field) {
            return;
        }
        if (field.value !== "" && !field.hasAttribute("data-autofilled")) {
            return;
        }
        var usual = chosen.getAttribute("data-quantity");
        if (usual) {
            field.value = usual;
            field.setAttribute("data-autofilled", "");
        } else {
            field.value = "";
            field.removeAttribute("data-autofilled");
        }
    }

    function fillPantryDialog(dialog, trigger) {
        var mode = trigger.getAttribute("data-mode");
        var title = dialog.querySelector("[data-dialog-title]");

        dialog.querySelectorAll(".field__error").forEach(function (node) {
            node.remove();
        });

        var values = {
            "input[name='id']": mode === "edit" ? trigger.getAttribute("data-id") : "",
            "#pantry-ingredient": mode === "edit" ? trigger.getAttribute("data-ingredient") : "",
            "#pantry-quantity": mode === "edit" ? trigger.getAttribute("data-quantity") : "",
            "#pantry-location": mode === "edit" ? trigger.getAttribute("data-location") : "PANTRY",
            "#pantry-purchased": mode === "edit" ? trigger.getAttribute("data-purchased") : new Date().toISOString().split('T')[0],
            "#pantry-expires": mode === "edit" ? trigger.getAttribute("data-expires") : ""
        };

        Object.keys(values).forEach(function (selector) {
            var field = dialog.querySelector(selector);
            if (field) {
                field.value = values[selector] || "";
            }
        });

        var quantityInput = dialog.querySelector("#pantry-quantity");
        if (quantityInput) {
            quantityInput.removeAttribute("data-autofilled");
        }

        dialog.setAttribute("data-mode", mode || "create");
        syncPantryUnit(dialog);
        title.textContent = mode === "edit" ? "Edit pantry item" : "Add pantry item";
    }

    function fillPlanDialog(dialog, trigger) {
        var mode = trigger.getAttribute("data-mode");
        var title = dialog.querySelector("[data-dialog-title]");
        var idField = dialog.querySelector("input[name='id']");
        var nameField = dialog.querySelector("#plan-name");
        var weekField = dialog.querySelector("#plan-week");

        if (mode === "edit") {
            title.textContent = "Rename plan";
            idField.value = trigger.getAttribute("data-id") || "";
            nameField.value = trigger.getAttribute("data-name") || "";
            weekField.value = trigger.getAttribute("data-week") || "";
        } else {
            title.textContent = "New plan";
            idField.value = "";
            nameField.value = "";
        }
    }

    /*
     * Opening the entry dialog from a specific day prefills that date, so the
     * common case is choosing a recipe and pressing add.
     */
    function fillEntryDialog(dialog, trigger) {
        var dateField = dialog.querySelector("#entry-date");
        var date = trigger.getAttribute("data-date");
        if (dateField && date) {
            dateField.value = date;
        }
    }

    /*
     * One delete dialog serves every list. The trigger supplies the URL to post
     * to, the display name, the noun for the copy, and — when the record cannot
     * be removed — the reason, in which case the confirm button is hidden rather
     * than letting the cook submit something the server refuses.
     */
    function fillDeleteDialog(dialog, trigger) {
        var name = trigger.getAttribute("data-name") || "this record";
        var entity = trigger.getAttribute("data-entity") || "record";
        var url = trigger.getAttribute("data-delete-url");
        var blocked = trigger.getAttribute("data-blocked");

        var form = dialog.querySelector("[data-delete-form]");
        var heading = dialog.querySelector("[data-delete-heading]");
        var message = dialog.querySelector("[data-delete-message]");
        var blockedNote = dialog.querySelector("[data-delete-blocked]");
        var submit = dialog.querySelector("[data-delete-submit]");

        if (url) {
            form.action = url;
        }
        heading.textContent = "Delete " + entity + "?";
        submit.textContent = "Delete " + entity;

        if (blocked) {
            message.textContent = name + " cannot be deleted yet.";
            blockedNote.textContent = "Still in use because " + blocked + ".";
            blockedNote.hidden = false;
            submit.hidden = true;
        } else {
            message.textContent = "Delete " + name + "? This cannot be undone.";
            blockedNote.hidden = true;
            submit.hidden = false;
        }
    }

    /* ------------------------------------------------------- repeating rows */

    /*
     * Spring binds an indexed list by field name: lines[0].quantity,
     * lines[1].quantity and so on. The indexes must run 0..n-1 with no gaps, or
     * binding stops at the first missing position. So every add and every remove
     * renumbers the whole set.
     */
    function renumberLines(container) {
        var rows = container.querySelectorAll(".lineitem");
        rows.forEach(function (row, index) {
            row.querySelectorAll("input, select").forEach(function (field) {
                if (field.name) {
                    field.name = field.name.replace(/lines\[\d+\]/, "lines[" + index + "]");
                }
                if (field.id) {
                    field.id = field.id.replace(/lines\d+\./, "lines" + index + ".");
                }
            });
        });
    }

    function addLineRow(container) {
        var template = document.getElementById("line-template");
        if (!template) {
            return;
        }
        var index = container.querySelectorAll(".lineitem").length;
        var markup = template.innerHTML.split("INDEX").join(String(index));
        var holder = document.createElement("div");
        holder.innerHTML = markup.trim();

        var row = holder.firstElementChild;
        container.appendChild(row);
        renumberLines(container);

        var firstField = row.querySelector("select, input");
        if (firstField) {
            firstField.focus();
        }
    }

    /* --------------------------------------------------------- bulk select */

    function bulkForm() {
        return document.querySelector("[data-bulk-form]");
    }

    function selectedBoxes() {
        var form = bulkForm();
        if (!form) {
            return [];
        }
        return Array.prototype.slice.call(form.querySelectorAll("[data-select-row]"))
            .filter(function (box) {
                return box.checked;
            });
    }

    /*
     * Keeps the bar, the count, and the header checkbox in step with the rows.
     * The header box shows an indeterminate state when only some are ticked,
     * which is what makes "select all" unambiguous either way.
     */
    function refreshSelection() {
        var form = bulkForm();
        var bar = document.querySelector("[data-bulk-bar]");
        if (!form || !bar) {
            return;
        }
        var boxes = form.querySelectorAll("[data-select-row]");
        var chosen = selectedBoxes().length;

        bar.hidden = chosen === 0;
        var counter = bar.querySelector("[data-bulk-count]");
        if (counter) {
            counter.textContent = String(chosen);
        }

        var master = form.querySelector("[data-select-all]");
        if (master) {
            master.checked = chosen > 0 && chosen === boxes.length;
            master.indeterminate = chosen > 0 && chosen < boxes.length;
        }
    }

    function setAllRows(checked) {
        var form = bulkForm();
        if (!form) {
            return;
        }
        form.querySelectorAll("[data-select-row]").forEach(function (box) {
            box.checked = checked;
        });
        refreshSelection();
    }

    /* ---------------------------------------------------------------- wiring */

    document.addEventListener("click", function (event) {
        var opener = event.target.closest("[data-dialog-open]");
        if (opener) {
            var dialog = document.getElementById(opener.getAttribute("data-dialog-open"));
            if (!dialog) {
                return;
            }
            if (dialog.id === "ingredient-dialog") {
                fillIngredientDialog(dialog, opener);
            } else if (dialog.id === "pantry-dialog") {
                fillPantryDialog(dialog, opener);
            } else if (dialog.id === "plan-dialog") {
                fillPlanDialog(dialog, opener);
            } else if (dialog.id === "entry-dialog") {
                fillEntryDialog(dialog, opener);
            } else if (dialog.id === "delete-dialog") {
                fillDeleteDialog(dialog, opener);
            }
            openDialog(dialog);
            return;
        }

        var closer = event.target.closest("[data-dialog-close]");
        if (closer) {
            var owner = closer.closest("dialog");
            if (owner) {
                owner.close();
            }
            return;
        }

        /* The print sheet's button. An onclick attribute would be simpler, but
           the content security policy blocks inline script. */
        if (event.target.closest("[data-print]")) {
            window.print();
            return;
        }

        if (event.target.closest("[data-add-line]")) {
            var addContainer = document.querySelector("[data-lines]");
            if (addContainer) {
                addLineRow(addContainer);
            }
            return;
        }

        var confirmer = event.target.closest("[data-bulk-confirm]");
        if (confirmer) {
            var chosen = selectedBoxes().length;
            if (chosen === 0) {
                return;
            }
            var dialog = document.getElementById("bulk-dialog");
            if (!dialog) {
                return;
            }
            var noun = confirmer.getAttribute("data-noun") || "record";
            var message = dialog.querySelector("[data-bulk-message]");
            if (message) {
                message.textContent = "Delete " + chosen + " " + noun
                    + (chosen === 1 ? "?" : "s?") + " This cannot be undone.";
            }
            openDialog(dialog);
            return;
        }

        if (event.target.closest("[data-bulk-submit]")) {
            var form = bulkForm();
            if (form) {
                form.submit();
            }
            return;
        }

        if (event.target.closest("[data-bulk-clear]")) {
            setAllRows(false);
            return;
        }

        var remover = event.target.closest("[data-remove-line]");
        if (remover) {
            var container = remover.closest("[data-lines]");
            var row = remover.closest(".lineitem");
            if (!container || !row) {
                return;
            }
            /* Never leave the form with nothing to type into. Clearing the last
               row is more useful than removing it. */
            if (container.querySelectorAll(".lineitem").length === 1) {
                row.querySelectorAll("input, select").forEach(function (field) {
                    field.value = "";
                });
            } else {
                row.remove();
                renumberLines(container);
            }
            return;
        }

        /* A click landing on the dialog element itself is a click on the
           backdrop, since the panel's contents are its children. */
        if (event.target.tagName === "DIALOG") {
            event.target.close();
            return;
        }

        /*
         * Whole-row activation. Anything already interactive keeps its own
         * behaviour, so the Edit link, the Delete button, and any form control
         * inside a row are untouched. Rows either navigate somewhere or stand in
         * for a button already present in the row.
         */
        if (event.target.closest("a, button, input, select, textarea, label")) {
            return;
        }

        var navRow = event.target.closest("[data-row-href]");
        if (navRow) {
            window.location.href = navRow.getAttribute("data-row-href");
            return;
        }

        var activateRow = event.target.closest("[data-row-activate]");
        if (activateRow) {
            var proxy = activateRow.querySelector(activateRow.getAttribute("data-row-activate"));
            if (proxy) {
                proxy.click();
            }
        }
    });

    /*
     * Choosing an ingredient updates the unit shown beside the amount. On a new
     * row it also preselects that ingredient's usual shelf and usual amount;
     * while editing it leaves both alone, since the cook may have changed them
     * on purpose.
     */
    document.addEventListener("change", function (event) {
        if (event.target.matches && event.target.matches("[data-select-all]")) {
            setAllRows(event.target.checked);
            return;
        }
        if (event.target.matches && event.target.matches("[data-select-row]")) {
            refreshSelection();
            return;
        }
        if (event.target.matches && event.target.matches("#entry-recipe")) {
            var servingsField = document.getElementById("entry-servings");
            var picked = event.target.options[event.target.selectedIndex];
            var suggested = picked ? picked.getAttribute("data-servings") : null;
            if (servingsField && suggested) {
                servingsField.value = suggested;
            }
            return;
        }
        if (event.target.matches && event.target.matches("#dialog-stock-unit")) {
            var ingredientDialog = event.target.closest("dialog");
            if (ingredientDialog) {
                syncIngredientUnit(ingredientDialog);
            }
            return;
        }
        if (!event.target.matches || !event.target.matches("#pantry-ingredient")) {
            return;
        }
        var dialog = event.target.closest("dialog");
        if (!dialog) {
            return;
        }
        var chosen = syncPantryUnit(dialog);
        if (chosen && dialog.getAttribute("data-mode") !== "edit") {
            var locationField = dialog.querySelector("#pantry-location");
            var preferred = chosen.getAttribute("data-location");
            if (locationField && preferred) {
                locationField.value = preferred;
            }
            applyUsualQuantity(dialog, chosen);
        }
    });

    /* Typing an amount makes it the cook's own, so a later ingredient change
       leaves it alone. Setting .value from script does not fire this event. */
    document.addEventListener("input", function (event) {
        if (event.target.matches && event.target.matches("#pantry-quantity")) {
            event.target.removeAttribute("data-autofilled");
        }
    });

    /* Reopen a dialog after a rejected submission so the errors are visible. */
    document.addEventListener("DOMContentLoaded", function () {
        refreshSelection();

        var requested = document.body.getAttribute("data-open-dialog");
        if (!requested) {
            return;
        }
        var dialog = document.getElementById(requested + "-dialog");
        if (!dialog) {
            return;
        }
        var title = dialog.querySelector("[data-dialog-title]");
        var idField = dialog.querySelector("input[name='id']");
        if (title && idField) {
            var noun = requested === "pantry" ? "pantry item" : requested;
            title.textContent = (idField.value ? "Edit " : "Add ") + noun;
        }
        if (requested === "pantry") {
            dialog.setAttribute("data-mode", idField && idField.value ? "edit" : "create");
            syncPantryUnit(dialog);
        }
        if (requested === "ingredient") {
            syncIngredientUnit(dialog);
        }
        openDialog(dialog);
    });
}());
