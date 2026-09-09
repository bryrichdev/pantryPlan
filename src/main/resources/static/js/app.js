/*
 * PantryPlan dialog behaviour.
 *
 * Uses the native <dialog> element, which supplies the backdrop, Escape to
 * close, and focus containment. This file only wires buttons to dialogs and
 * fills in the values for the row being acted on. Validation stays on the
 * server: a rejected form re-renders the page with data-open-dialog set, and
 * the dialog reopens with the messages already in the markup.
 */
(function () {
    "use strict";

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

        if (mode === "edit") {
            title.textContent = "Edit ingredient";
            idField.value = trigger.getAttribute("data-id") || "";
            nameField.value = trigger.getAttribute("data-name") || "";
            categoryField.value = trigger.getAttribute("data-category") || "OTHER";
            gramsField.value = trigger.getAttribute("data-grams") || "";
        } else {
            title.textContent = "Add ingredient";
            idField.value = "";
            nameField.value = "";
            categoryField.value = "OTHER";
            gramsField.value = "";
        }
    }

    /*
     * One delete dialog serves every list. The trigger supplies the full URL to
     * post to, the display name, the noun to use in the copy, and — when the
     * record cannot be removed — the reason, in which case the confirm button is
     * hidden rather than letting the cook submit something the server refuses.
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

    document.addEventListener("click", function (event) {
        var trigger = event.target.closest("[data-dialog-open]");
        if (trigger) {
            var dialog = document.getElementById(trigger.getAttribute("data-dialog-open"));
            if (!dialog) {
                return;
            }
            if (dialog.id === "ingredient-dialog") {
                fillIngredientDialog(dialog, trigger);
            } else if (dialog.id === "delete-dialog") {
                fillDeleteDialog(dialog, trigger);
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

        /* A click landing on the dialog element itself is a click on the
           backdrop, since the panel's contents are its children. */
        if (event.target.tagName === "DIALOG") {
            event.target.close();
        }
    });

    /* Reopen after a rejected submission so the errors are visible. */
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
            title.textContent = idField.value ? "Edit ingredient" : "Add ingredient";
        }
        openDialog(dialog);
    });
}());
