/*
 * File:    shop.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Shop page: search, category filter, product grid and vendors.
 */

(() => {
  const grid = document.getElementById("products");
  const search = document.getElementById("search");
  const chips = document.getElementById("categories");
  const count = document.getElementById("result-count");
  let products = [];
  let category = new URLSearchParams(location.search).get("category") || "";
  let timer;

  async function loadProducts() {
    const params = new URLSearchParams();
    if (search.value.trim()) params.set("search", search.value.trim());
    if (category) params.set("category", category);
    try {
      ({ products } = await CS.api("/api/products?" + params));
      count.textContent = `${products.length} ${products.length === 1 ? "product" : "products"} found`;
      grid.innerHTML = products.length
        ? products.map(CS.productCard).join("")
        : `<div class="empty glass" style="grid-column:1/-1"><div class="big">🔍</div>
             <p>No products match your search. Try another word or category.</p></div>`;
      CS.revealOnScroll();
    } catch (error) {
      grid.innerHTML = `<div class="alert alert-error" style="grid-column:1/-1">${CS.escapeHtml(error.message)}</div>`;
    }
  }

  async function loadCategories() {
    const { categories } = await CS.api("/api/products/categories");
    chips.innerHTML = ["All", ...categories]
      .map((name) => {
        const value = name === "All" ? "" : name;
        return `<button class="chip ${value === category ? "active" : ""}" type="button" data-category="${CS.escapeHtml(value)}">${CS.escapeHtml(name)}</button>`;
      })
      .join("");
  }

  async function loadVendors() {
    const { vendors } = await CS.api("/api/vendors");
    document.getElementById("vendors").innerHTML = vendors.map((vendor) => `
      <div class="card glass reveal">
        <div class="row space-between" style="margin-bottom:8px">
          <h3 style="margin:0">${CS.escapeHtml(vendor.name)}</h3>
          <span class="badge badge-cyan">${CS.escapeHtml(vendor.plan)}</span>
        </div>
        <p class="faint">📍 ${CS.escapeHtml(vendor.location)} · ${vendor.productCount} products</p>
        <p class="muted">${CS.escapeHtml(vendor.description)}</p>
      </div>`).join("");
    CS.revealOnScroll();
  }

  chips.addEventListener("click", (event) => {
    const chip = event.target.closest("[data-category]");
    if (!chip) return;
    category = chip.dataset.category;
    chips.querySelectorAll(".chip").forEach((c) => c.classList.toggle("active", c === chip));
    loadProducts();
  });

  search.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(loadProducts, 250);
  });

  CS.bindAddButtons(grid, () => products);
  loadCategories().catch(() => {});
  loadProducts();
  loadVendors().catch(() => {});
})();
