/*
 * File:    product-form.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The "Add a product" form shared by vendors (My Account) and the
 *          admin (Dashboard): fills the category list, validates and submits.
 */

async function setupProductForm(form, onAdded) {
  const { categories } = await CS.api("/api/products/categories");
  form.querySelector("[name=category]").innerHTML = categories.map((c) => `<option>${CS.escapeHtml(c)}</option>`).join("");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const get = (name) => form.querySelector(`[name=${name}]`);
    const price = Number(get("price").value);
    const stock = Number(get("stock").value);
    let ok = showFieldError(get("name"), get("name").value.trim().length >= 2 ? "" : "Please enter a product name of at least 2 characters.");
    ok = showFieldError(get("description"), get("description").value.trim().length >= 5 ? "" : "Please enter a description of at least 5 characters.") && ok;
    ok = showFieldError(get("price"), get("price").value !== "" && price >= 1 && price <= 50000 ? "" : "Please enter a price between R1 and R50 000.") && ok;
    ok = showFieldError(get("stock"), get("stock").value !== "" && Number.isInteger(stock) && stock >= 0 && stock <= 10000 ? "" : "Please enter a whole number of items between 0 and 10 000.") && ok;
    if (get("vendorId")) {
      ok = showFieldError(get("vendorId"), get("vendorId").value ? "" : "Please choose a vendor.") && ok;
    }
    if (!ok) return;
    try {
      const result = await CS.api("/api/products", { method: "POST", data: Object.fromEntries(new FormData(form)) });
      CS.toast(result.message);
      form.reset();
      onAdded();
    } catch (error) {
      CS.toast(error.message, "error");
    }
  });
}
