/*
 * File:    menu.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Menu page: search, category filter, and the menu grouped by
 *          category so it is easy to scan.
 */

(() => {
  const holder = document.getElementById("menu");
  const search = document.getElementById("search");
  const chips = document.getElementById("categories");
  let categories = [];
  let products = [];
  let category = "";
  let timer;

  function render() {
    if (!products.length) {
      holder.innerHTML = `<div class="empty glass"><div class="big">🔍</div><p>Nothing matches that. Try another word or category.</p></div>`;
      return;
    }
    // Group by category, in menu order.
    const order = categories.length ? categories : [...new Set(products.map((p) => p.category))];
    holder.innerHTML = order
      .map((name) => {
        const items = products.filter((p) => p.category === name);
        if (!items.length) return "";
        return `<h2 class="category-title">${KC.escapeHtml(name)} <span class="tag tag-soft">${items.length}</span></h2>
          <div class="menu-grid">${items.map(KC.itemCard).join("")}</div>`;
      })
      .join("");
  }

  async function load() {
    const params = new URLSearchParams();
    if (search.value.trim()) params.set("search", search.value.trim());
    if (category) params.set("category", category);
    try {
      ({ products } = await KC.api("/api/products?" + params));
      render();
    } catch (error) {
      holder.innerHTML = `<div class="alert alert-error">${KC.escapeHtml(error.message)}</div>`;
    }
  }

  chips.addEventListener("click", (event) => {
    const chip = event.target.closest("[data-category]");
    if (!chip) return;
    category = chip.dataset.category;
    chips.querySelectorAll(".chip").forEach((c) => c.classList.toggle("active", c === chip));
    load();
  });

  search.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(load, 250);
  });

  KC.bindAddButtons(holder, () => products);

  (async () => {
    try {
      ({ categories } = await KC.api("/api/products/categories"));
      chips.innerHTML = ["All", ...categories]
        .map((name) => `<button class="chip ${name === "All" ? "active" : ""}" type="button" data-category="${name === "All" ? "" : KC.escapeHtml(name)}">${KC.escapeHtml(name)}</button>`)
        .join("");
    } catch {
      // Without categories the menu still loads as one list.
    }
    load();
  })();
})();
