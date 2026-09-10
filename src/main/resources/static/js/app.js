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
        }
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
            "#pantry-purchased": mode === "edit" ? trigger.getAttribute("data-purchased") : "",
            "#pantry-expires": mode === "edit" ? trigger.getAttribute("data-expires") : ""
        };

        Object.keys(values).forEach(function (selector) {
            var field = dialog.querySelector(selector);
            if (field) {
                field.value = values[selector] || "";
            }
        });

        dialog.setAttribute("data-mode", mode || "create");
        syncPantryUnit(dialog);
        title.textContent = mode === "edit" ? "Edit pantry item" : "Add pantry item";
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

        if (event.target.closest("[data-add-line]")) {
            var addContainer = document.querySelector("[data-lines]");
            if (addContainer) {
                addLineRow(addContainer);
            }
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
     * row it also preselects that ingredient's usual shelf; while editing it
     * leaves the shelf alone, since the cook may have put this one elsewhere
     * deliberately.
     */
    document.addEventListener("change", function (event) {
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
        }
    });

    /* Reopen a dialog after a rejected submission so the errors are visible. */
    document.addEventListener("DOMContentLoaded", function () {
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
        openDialog(dialog);
    });
}());
