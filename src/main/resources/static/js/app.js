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
            nameField.removeAttribute("data-generated-plan-name");
        } else {
            title.textContent = "New plan";
            idField.value = "";
            syncPlanName(dialog, true);
        }
    }

    /* Uses the date input's YYYY-MM-DD value directly, avoiding the timezone
       shift that can happen when a date-only value is passed to Date. */
    function suggestedPlanName(weekStart) {
        var parts = (weekStart || "").split("-");
        if (parts.length !== 3) {
            return "";
        }
        return parts[1] + "/" + parts[2] + " Meal Plan";
    }

    /* A changed start date updates only the generated default. Once the cook
       types a name, it belongs to them and date changes leave it alone. */
    function syncPlanName(dialog, force) {
        var nameField = dialog.querySelector("#plan-name");
        var weekField = dialog.querySelector("#plan-week");
        if (!nameField || !weekField) {
            return;
        }
        if (!force && nameField.value !== "" && !nameField.hasAttribute("data-generated-plan-name")) {
            return;
        }
        nameField.value = suggestedPlanName(weekField.value);
        nameField.setAttribute("data-generated-plan-name", "");
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

    /* ------------------------------------------------------ feedback dialog */

    /*
     * The report dialog opens on every page, so each opening starts it fresh:
     * the form showing, the thank-you hidden, and no leftover error.
     */
    function resetFeedbackDialog(dialog) {
        var form = dialog.querySelector("[data-feedback-form]");
        var done = dialog.querySelector("[data-feedback-done]");
        var error = dialog.querySelector("[data-feedback-error]");
        var submit = dialog.querySelector("[data-feedback-submit]");
        if (form) {
            form.hidden = false;
        }
        if (done) {
            done.hidden = true;
        }
        if (error) {
            error.hidden = true;
            error.textContent = "";
        }
        if (submit) {
            submit.disabled = false;
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

    /* Meal-plan rows carry a form attribute so their individual cook/remove
       forms can remain valid siblings rather than becoming nested forms. */
    function selectionControls(form, selector) {
        if (!form) {
            return [];
        }
        var controls = Array.prototype.slice.call(form.querySelectorAll(selector));
        if (form.id) {
            var external = document.querySelectorAll(selector + "[form='" + form.id + "']");
            external.forEach(function (control) {
                if (controls.indexOf(control) === -1) {
                    controls.push(control);
                }
            });
        }
        return controls;
    }

    function selectedBoxes() {
        var form = bulkForm();
        if (!form) {
            return [];
        }
        return selectionControls(form, "[data-select-row]")
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
        var boxes = selectionControls(form, "[data-select-row]");
        var chosen = selectedBoxes().length;

        bar.hidden = chosen === 0;
        var counter = bar.querySelector("[data-bulk-count]");
        if (counter) {
            counter.textContent = String(chosen);
        }

        var master = selectionControls(form, "[data-select-all]")[0];
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
        selectionControls(form, "[data-select-row]").forEach(function (box) {
            box.checked = checked;
        });
        refreshSelection();
    }

    /* ---------------------------------------------------------------- wiring */

    /* ------------------------------------------------------------ phone menu */

    function setMenuOpen(toggle, open) {
        var header = toggle.closest(".topnav");
        if (!header) {
            return;
        }
        if (open) {
            header.setAttribute("data-nav-open", "");
        } else {
            header.removeAttribute("data-nav-open");
        }
        toggle.setAttribute("aria-expanded", open ? "true" : "false");
    }

    /* Escape closes the menu and puts focus back on its button. Dialogs handle
       Escape themselves, so this only acts when the menu is open. */
    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }
        var toggle = document.querySelector("[data-nav-toggle][aria-expanded='true']");
        if (toggle) {
            setMenuOpen(toggle, false);
            toggle.focus();
        }
    });

    /* -------------------------------------------------------- table labels */

    /*
     * On a phone each table row becomes a card, and a card has no header row
     * to say which value is which. This copies each column's heading onto its
     * cells as data-label, which the phone stylesheet prints beside the value.
     * Doing it here means no template has to repeat its headings by hand.
     */
    function labelTableCells() {
        var tables = document.querySelectorAll("table.table");
        Array.prototype.forEach.call(tables, function (table) {
            var headings = Array.prototype.map.call(
                table.querySelectorAll("thead th"),
                function (th) { return th.textContent.replace(/\s+/g, " ").trim(); });
            Array.prototype.forEach.call(table.querySelectorAll("tbody tr"), function (row) {
                Array.prototype.forEach.call(row.children, function (cell, index) {
                    if (headings[index] && !cell.hasAttribute("data-label")) {
                        cell.setAttribute("data-label", headings[index]);
                    }
                });
            });
        });
    }

    document.addEventListener("DOMContentLoaded", labelTableCells);

    /* --------------------------------------------------- keeping the scroll */

    /*
     * A form post that redirects lands the browser on a fresh page at the top.
     * Halfway down a long list that is jarring, so the position is stashed on
     * the way out and restored on the way back in.
     *
     * Grocery ticks no longer reload at all, but this still covers the case
     * where that fails and the form is submitted the ordinary way.
     */
    var SCROLL_KEY = "pantryplan:scroll";

    function rememberScroll() {
        try {
            sessionStorage.setItem(SCROLL_KEY, String(window.scrollY));
        } catch (error) {
            /* Private browsing can refuse storage. Losing the position is not
               worth breaking the tick over. */
        }
    }

    function restoreScroll() {
        var saved = null;
        try {
            saved = sessionStorage.getItem(SCROLL_KEY);
            sessionStorage.removeItem(SCROLL_KEY);
        } catch (error) {
            return;
        }
        /* A real link to an anchor wins: that scroll was asked for. */
        if (saved !== null && !window.location.hash) {
            window.scrollTo(0, parseInt(saved, 10) || 0);
        }
    }

    document.addEventListener("submit", function (event) {
        if (event.target.matches && event.target.matches("[data-keep-scroll]")) {
            rememberScroll();
        }
    });

    document.addEventListener("DOMContentLoaded", restoreScroll);

    /* ------------------------------------------------ ticking without a reload */

    /*
     * Ticking an item off a grocery list posts in the background and the page
     * updates itself, so a list stays exactly where it was with no flash of a
     * reloading page.
     *
     * Four things have to move together: the row's struck-through look, the
     * "3 of 12 bought" count, the label on the put-away button, and which rows
     * the stock-up dialog offers. The dialog already holds a row for every line
     * that has not been put away, so keeping it current is a matter of showing
     * or hiding one and flipping its hidden include field.
     *
     * If the request fails for any reason the form is submitted normally, which
     * is also what happens when JavaScript is off.
     */

    function stockUpRow(itemId) {
        return document.querySelector("[data-stockup-row='" + itemId + "']");
    }

    function refreshStockUpControls() {
        var rows = document.querySelectorAll("[data-stockup-row]");
        var ready = 0;
        Array.prototype.forEach.call(rows, function (row) {
            if (!row.hidden) {
                ready++;
            }
        });

        var button = document.querySelector("[data-stockup-button]");
        if (button) {
            button.textContent = "Put " + ready + " bought " + (ready === 1 ? "item" : "items") + " away";
            button.disabled = ready === 0;
        }

        var empty = document.querySelector("[data-stockup-empty]");
        if (empty) {
            empty.hidden = ready > 0;
        }
        var submit = document.querySelector("[data-stockup-submit]");
        if (submit) {
            submit.disabled = ready === 0;
        }
    }

    function applyTick(button, purchased) {
        button.setAttribute("aria-pressed", purchased ? "true" : "false");

        var listItem = button.closest(".shoplist__item");
        if (listItem) {
            listItem.classList.toggle("shoplist__item--bought", purchased);
        }

        var counter = document.querySelector("[data-bought-count]");
        if (counter) {
            var current = parseInt(counter.textContent, 10) || 0;
            counter.textContent = String(Math.max(0, current + (purchased ? 1 : -1)));
        }

        var row = stockUpRow(button.getAttribute("data-tick"));
        if (row) {
            row.hidden = !purchased;
            var include = row.querySelector("[data-stockup-include]");
            if (include) {
                include.value = purchased ? "true" : "false";
            }
        }
        refreshStockUpControls();
    }

    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!form.matches || !form.matches("[data-tick-form]") || typeof window.fetch !== "function") {
            return;
        }
        var button = form.querySelector("[data-tick]");
        if (!button) {
            return;
        }

        event.preventDefault();
        button.disabled = true;

        fetch(form.action, {
            method: "POST",
            body: new FormData(form),
            headers: { "X-Requested-With": "fetch" },
            credentials: "same-origin"
        }).then(function (response) {
            if (!response.ok) {
                throw new Error("Tick refused: " + response.status);
            }
            button.disabled = false;
            applyTick(button, button.getAttribute("aria-pressed") !== "true");
        }).catch(function () {
            /* Something went wrong, most likely an expired session. Submitting
               the ordinary way lets the server answer, redirect, or send the
               cook to the sign-in page. */
            rememberScroll();
            form.submit();
        });
    });

    document.addEventListener("DOMContentLoaded", refreshStockUpControls);

    /*
     * The report posts in the background so whatever the cook was typing on
     * the page underneath stays put. 204 means saved; 400 carries the problem
     * as text. Anything else, such as an expired session answered with the
     * sign-in page, falls back to an ordinary submit so the server can respond.
     */
    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!form.matches || !form.matches("[data-feedback-form]") || typeof window.fetch !== "function") {
            return;
        }
        var dialog = form.closest("dialog");
        var error = form.querySelector("[data-feedback-error]");
        var submit = form.querySelector("[data-feedback-submit]");

        event.preventDefault();
        if (submit) {
            submit.disabled = true;
        }

        fetch(form.action, {
            method: "POST",
            body: new FormData(form),
            headers: { "X-Requested-With": "fetch" },
            credentials: "same-origin"
        }).then(function (response) {
            if (response.status === 204) {
                form.reset();
                form.hidden = true;
                var done = dialog ? dialog.querySelector("[data-feedback-done]") : null;
                if (done) {
                    done.hidden = false;
                    var closer = done.querySelector(".button[data-dialog-close]");
                    if (closer) {
                        closer.focus();
                    }
                }
                return;
            }
            if (response.status === 400) {
                return response.text().then(function (message) {
                    if (error) {
                        error.textContent = message;
                        error.hidden = false;
                    }
                    if (submit) {
                        submit.disabled = false;
                    }
                });
            }
            throw new Error("Report refused: " + response.status);
        }).catch(function () {
            form.submit();
        });
    });

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
            } else if (dialog.id === "feedback-dialog") {
                resetFeedbackDialog(dialog);
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

        /* The phone menu. The open state lives on the header so the CSS can
           show the links and account controls together. */
        var navToggle = event.target.closest("[data-nav-toggle]");
        if (navToggle) {
            setMenuOpen(navToggle, navToggle.getAttribute("aria-expanded") !== "true");
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
        if (event.target.matches && event.target.matches("#plan-week")) {
            var planDialog = event.target.closest("dialog");
            if (planDialog && !planDialog.querySelector("input[name='id']").value) {
                syncPlanName(planDialog, false);
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
        if (event.target.matches && event.target.matches("#plan-name")) {
            event.target.removeAttribute("data-generated-plan-name");
            return;
        }
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
