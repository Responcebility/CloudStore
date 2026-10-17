/*
 * File:    home.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Home page: live community numbers and featured products.
 */

(async () => {
  try {
    const insights = await CS.api("/api/insights");
    document.querySelectorAll("[data-stat]").forEach((el) => {
      el.textContent = Number(insights[el.dataset.stat] || 0).toLocaleString("en-ZA");
    });
    document.getElementById("chip-vendors").textContent = `🏪 ${insights.vendors} local vendors`;
  } catch {
    // The numbers are a nice extra; the page still works without them.
  }

  const featured = document.getElementById("featured");
  try {
    const { products } = await CS.api("/api/products");
    const picks = products.filter((p) => p.featured && p.stock > 0).slice(0, 4);
    featured.innerHTML = picks.map(CS.productCard).join("") || `<p class="muted">New products are coming soon.</p>`;
    CS.bindAddButtons(featured, picks);
    CS.revealOnScroll();
  } catch (error) {
    featured.innerHTML = `<div class="alert alert-error">${CS.escapeHtml(error.message)}</div>`;
  }
})();
