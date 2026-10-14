/*
 * PantryPrep motion: the moments that need a little script. The styles live in
 * motion.css. Everything here is optional; without it every form and link
 * works exactly the same, just without the flourish. Anyone who prefers
 * reduced motion gets none of it, and no form is ever held back for them.
 *
 *   rows and cards       numbered so they cascade in (capped, see motion.css)
 *   good notices         draw a check mark; data-celebrate throws a few herbs
 *   dashboard tiles      count up from zero
 *   Mark cooked          a puff of steam, then submit; the meal glows on return
 *   Remove (a meal)      the meal slides away, then submit
 *   grocery list         a burst when the last item is ticked
 *   [data-busy]          a button that says it is working after it is pressed
 */
(function () {
    "use strict";

    var calm = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var JUST_COOKED = "pantryprep.justCooked";

    /* ------------------------------------------------------------ helpers */

    function holdThenSubmit(form, delay) {
        form.dataset.motionDone = "true";
        window.setTimeout(function () {
            if (typeof form.requestSubmit === "function") {
                form.requestSubmit();
            } else {
                form.submit();
            }
        }, delay);
    }

    /*
     * A handful of herb leaves and honey drops thrown up from a point. Pieces
     * fan out upward with a little randomness and fall away; the whole thing
     * is removed once it has finished.
     */
    function burst(x, y, count) {
        if (calm) {
            return;
        }
        var colors = ["#2F5D3F", "#4F7F5A", "#7FA36B", "#C7861F", "#E0A84A"];
        var holder = document.createElement("div");
        holder.className = "burst";
        holder.setAttribute("aria-hidden", "true");
        holder.style.left = x + "px";
        holder.style.top = y + "px";
        var n = count || 14;
        for (var i = 0; i < n; i++) {
            var bit = document.createElement("span");
            var drop = i % 3 === 2;
            bit.className = "burst__bit" + (drop ? " burst__bit--drop" : "");
            var angle = (-160 + (140 * i) / (n - 1) + (Math.random() * 16 - 8)) * Math.PI / 180;
            var reach = 46 + Math.random() * 44;
            bit.style.setProperty("--x", Math.round(Math.cos(angle) * reach) + "px");
            bit.style.setProperty("--y", Math.round(Math.sin(angle) * reach) + "px");
            bit.style.setProperty("--r", Math.round(Math.random() * 360 - 180) + "deg");
            bit.style.setProperty("--d", Math.round(Math.random() * 90) + "ms");
            bit.style.setProperty("--t", (0.8 + Math.random() * 0.35).toFixed(2) + "s");
            bit.style.setProperty("--c", colors[drop ? 3 + (i % 2) : i % 3]);
            if (drop) {
                bit.style.setProperty("--w", "6px");
                bit.style.setProperty("--h", "6px");
            }
            holder.appendChild(bit);
        }
        document.body.appendChild(holder);
        window.setTimeout(function () { holder.remove(); }, 1500);
    }

    function burstFrom(element, count) {
        var box = element.getBoundingClientRect();
        burst(box.left + Math.min(box.width / 2, 120), box.top + box.height / 2, count);
    }

    /* ------------------------------------------------- arriving on a page */

    function numberRows() {
        var groups = [
            ".table tbody tr",
            ".home-tile",
            ".home .card",
            ".week .day",
            ".aisle",
            ".howto__step"
        ];
        groups.forEach(function (selector) {
            var items = document.querySelectorAll(selector);
            Array.prototype.forEach.call(items, function (item, index) {
                item.style.setProperty("--i", String(index));
            });
        });
    }

    function drawNoticeChecks() {
        var notices = document.querySelectorAll(".flash .notice--good, .split__form > .notice--good");
        Array.prototype.forEach.call(notices, function (notice) {
            if (notice.querySelector(".notice__check")) {
                return;
            }
            notice.insertAdjacentHTML("afterbegin",
                '<svg class="notice__check" viewBox="0 0 16 16" aria-hidden="true" focusable="false">'
                + '<path d="M3 8.5l3.2 3.2L13 4.8" fill="none" stroke="currentColor" stroke-width="2.2" '
                + 'stroke-linecap="round" stroke-linejoin="round"/></svg>');
        });

        var party = document.querySelector(".notice[data-celebrate]");
        if (party) {
            /* After the notice has slid in, so the herbs come from where it lands. */
            window.setTimeout(function () { burstFrom(party, 16); }, 380);
        }
    }

    function countUp() {
        if (calm) {
            return;
        }
        var counts = document.querySelectorAll(".home-tile__count");
        Array.prototype.forEach.call(counts, function (count) {
            var target = parseInt(count.textContent, 10);
            if (!(target > 0)) {
                return;
            }
            var start = null;
            var duration = Math.min(400 + target * 25, 900);
            count.textContent = "0";
            function step(now) {
                if (start === null) {
                    start = now;
                }
                var progress = Math.min((now - start) / duration, 1);
                var eased = 1 - Math.pow(1 - progress, 3);
                count.textContent = String(Math.round(target * eased));
                if (progress < 1) {
                    window.requestAnimationFrame(step);
                }
            }
            window.requestAnimationFrame(step);
        });
    }

    /* The meal cooked just before this page loaded, found by its checkbox. */
    function glowJustCooked() {
        var id = null;
        try {
            id = window.sessionStorage.getItem(JUST_COOKED);
            window.sessionStorage.removeItem(JUST_COOKED);
        } catch (ignored) {
            return;
        }
        if (!id || calm) {
            return;
        }
        var box = document.querySelector('.meal__check[value="' + id.replace(/[^0-9]/g, "") + '"]');
        var meal = box ? box.closest(".meal") : null;
        if (meal) {
            meal.classList.add("is-just-cooked");
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        numberRows();
        drawNoticeChecks();
        countUp();
        glowJustCooked();
    });

    /* ----------------------------------------------------- forms that wait */

    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!form.matches || form.dataset.motionDone) {
            return;
        }

        /* Mark cooked: steam first, then the real submit. */
        var cookButton = form.matches(".meal__cook") && form.action.indexOf("/cook") !== -1
            && form.action.indexOf("/uncook") === -1;
        if (cookButton) {
            var meal = form.closest(".meal");
            var box = meal ? meal.querySelector(".meal__check") : null;
            try {
                if (box) {
                    window.sessionStorage.setItem(JUST_COOKED, box.value);
                }
            } catch (ignored) {
                /* Private windows can refuse storage; the glow is just skipped. */
            }
            if (calm || !meal) {
                return;
            }
            event.preventDefault();
            meal.classList.add("is-cooking");
            var steam = document.createElement("span");
            steam.className = "steam";
            steam.setAttribute("aria-hidden", "true");
            steam.innerHTML = "<span></span><span></span><span></span>";
            meal.appendChild(steam);
            holdThenSubmit(form, 560);
            return;
        }

        /* Removing a meal: it slides off its day first. */
        if (form.matches(".meal__remove") && !calm) {
            var leaving = form.closest(".meal");
            if (leaving) {
                event.preventDefault();
                leaving.classList.add("is-leaving");
                holdThenSubmit(form, 260);
            }
            return;
        }

        /* A button that takes a moment: say so. It does not delay anything. */
        var busy = event.submitter && event.submitter.matches("[data-busy]") ? event.submitter
            : form.querySelector("button[data-busy]");
        if (busy) {
            busy.textContent = busy.getAttribute("data-busy");
            busy.classList.add("is-busy");
        }
    }, true);

    /* ---------------------------------------------- the last grocery item */

    /*
     * The tick itself is handled in app.js; this only watches for the moment
     * nothing is left to buy, and celebrates once.
     */
    document.addEventListener("click", function (event) {
        var tick = event.target.closest(".tick");
        if (!tick || calm) {
            return;
        }
        window.setTimeout(function () {
            var items = document.querySelectorAll(".shoplist__item");
            var left = document.querySelectorAll(".shoplist__item:not(.shoplist__item--bought)");
            if (items.length && left.length === 0) {
                var heading = document.querySelector(".pagehead h1") || tick;
                burstFrom(heading, 20);
            }
        }, 350);
    });
})();
