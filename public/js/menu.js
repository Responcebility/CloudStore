/*
 * File:    menu.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Menu page: search, category filter, and the menu grouped by
 *          category so it is quick to scan.
 */

(() => {
  const holder = document.getElementById("menu");
  const search = document.getElementById("search");
  const chips = document.getElementById("categories");
  let categories = [];
  let products = [];
  // A link such as menu.html?category=Drinks opens straight on that category.
  let category = new URLSearchParams(location.search).get("category") || "";
  let timer;

  function render() {
    if (!products.length) {
      holder.innerHTML = `<div class="loading-card">Nothing matches that. Try another word or category.</div>`;
      return;
    }
    const order = categories.length ? categories : [...new Set(products.map((p) => p.category))];
    holder.innerHTML = order
      .map((name) => {
        const items = products.filter((p) => p.category === name);
        if (!items.length) return "";
        return `<h2 class="category-heading">${KC.escapeHtml(name)}</h2><div class="menu-grid">${items.map(KC.itemCard).join("")}</div>`;
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
      holder.innerHTML = `<div class="loading-card">${KC.escapeHtml(error.message)}</div>`;
    }
  }

  chips.addEventListener("click", (event) => {
    const chip = event.target.closest("[data-category]");
    if (!chip) return;
    category = chip.dataset.category;
    chips.querySelectorAll(".category-chip").forEach((c) => c.classList.toggle("active", c === chip));
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
        .map((name) => {
          const value = name === "All" ? "" : name;
          return `<button class="category-chip ${value === category ? "active" : ""}" type="button" data-category="${KC.escapeHtml(value)}">${KC.escapeHtml(name)}</button>`;
        })
        .join("");
    } catch {
      // Without categories the menu still loads as one list.
    }
    load();
  })();
})();
