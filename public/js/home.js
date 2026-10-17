/*
 * File:    home.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Home page: shows the most popular menu items.
 */

(async () => {
  const holder = document.getElementById("bestsellers");
  try {
    const { products } = await KC.api("/api/products?popular=1");
    const picks = products.filter((p) => p.stock > 0).slice(0, 6);
    holder.innerHTML = picks.map(KC.itemCard).join("");
    KC.bindAddButtons(holder, picks);
  } catch (error) {
    holder.innerHTML = `<div class="loading-card">${KC.escapeHtml(error.message)}</div>`;
  }
})();
