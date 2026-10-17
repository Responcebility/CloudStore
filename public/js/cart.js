/*
 * File:    cart.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Basket and checkout: change quantities, choose delivery or
 *          collection, enter the distance, and see the delivery fee (minus
 *          what a Crunch Pass covers) before ordering. The server recalculates
 *          every amount.
 */

(() => {
  const list = document.getElementById("items");
  const form = document.getElementById("checkout");
  const result = document.getElementById("result");
  const distance = document.getElementById("distance");
  const PHONE = /^(0\d{9}|\+27\d{9})$/;
  const set = (id, text) => (document.getElementById(id).textContent = text);
  const fulfilment = () => form.querySelector("[name=fulfilment]:checked").value;

  function renderItems() {
    const items = KC.basketItems();
    list.innerHTML = items.length
      ? items.map((item) => `
        <div class="basket-item">
          <img src="${KC.escapeHtml(item.image)}" alt="">
          <div>
            <h3>${KC.escapeHtml(item.name)}</h3>
            <span class="muted small">${KC.money(item.price)} each</span>
            <div class="quantity" aria-label="Quantity of ${KC.escapeHtml(item.name)}">
              <button type="button" data-dec="${item.id}" aria-label="One less">−</button>
              <span>${item.qty}</span>
              <button type="button" data-inc="${item.id}" aria-label="One more">+</button>
            </div>
          </div>
          <div style="text-align:right">
            <strong>${KC.money(item.price * item.qty)}</strong><br>
            <button class="button button-outline button-small" type="button" data-remove="${item.id}" style="margin-top:8px">Remove</button>
          </div>
        </div>`).join("")
      : `<div class="empty"><div class="big">✳</div><h3>It's a little empty in here.</h3><p>Let's fix that with something delicious.</p>
          <a class="button button-primary" href="menu.html">Explore the menu ↗</a></div>`;
  }

  function renderTotals() {
    const subtotal = KC.subtotal();
    const pass = KC.user ? KC.user.pass : null;
    const delivering = fulfilment() === "delivery";
    document.getElementById("delivery-fields").hidden = !delivering;

    const km = Number(distance.value);
    const validKm = distance.value !== "" && km >= 0.1 && km <= KC.pricing.maxDeliveryKm;
    let fee = 0;
    let label = "Delivery";
    if (!delivering) {
      label = "Collection";
    } else if (validKm) {
      fee = KC.deliveryFee(km, pass ? pass.maxKm : 0);
      if (pass) label = km <= pass.maxKm ? `Delivery (Crunch Pass, ${pass.maxKm} km)` : `Delivery (${KC.round2(km - pass.maxKm)} km beyond your pass)`;
    }
    set("distance-hint", pass
      ? `Your Crunch Pass covers ${pass.maxKm} km for free. Beyond that: R16 per km.`
      : "Delivery is R16 per km, up to 30 km. A Crunch Pass makes it free.");
    set("sum-subtotal", KC.money(subtotal));
    set("delivery-label", label);
    set("sum-delivery", !delivering ? "Free" : validKm ? (fee === 0 ? "Free" : KC.money(fee)) : "Enter distance");
    set("sum-total", KC.money(subtotal + fee));
    document.getElementById("place").disabled = !KC.basketItems().length;
  }

  function render() {
    renderItems();
    renderTotals();
  }

  list.addEventListener("click", (event) => {
    const button = event.target.closest("button");
    if (!button) return;
    const item = KC.basketItems().find((i) => String(i.id) === (button.dataset.inc || button.dataset.dec || button.dataset.remove));
    if (!item) return;
    if (button.dataset.inc) {
      const limit = Math.min(KC.pricing.maxQuantityPerItem, item.stock ?? 99);
      if (item.qty >= limit) KC.toast(`You can order at most ${limit} × ${item.name}.`, "error");
      else KC.setQuantity(item.id, item.qty + 1);
    } else if (button.dataset.dec) {
      KC.setQuantity(item.id, item.qty - 1);
    } else {
      KC.setQuantity(item.id, 0);
    }
    render();
  });

  form.addEventListener("input", renderTotals);
  form.addEventListener("change", renderTotals);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    result.innerHTML = "";
    if (!KC.user) {
      document.getElementById("login-note").hidden = false;
      return;
    }
    const delivering = fulfilment() === "delivery";
    const address = document.getElementById("address");
    const phone = document.getElementById("phone");
    const cleanPhone = phone.value.replace(/[\s-]/g, "");
    let ok = true;
    if (delivering) {
      const km = Number(distance.value);
      ok = showFieldError(address, address.value.trim().length >= 5 ? "" : "Please enter your delivery address (at least 5 characters).") && ok;
      ok = showFieldError(distance, distance.value !== "" && km >= 0.1 && km <= 30 ? "" : "Please enter a distance between 0.1 and 30 km.") && ok;
    }
    ok = showFieldError(phone, PHONE.test(cleanPhone) ? "" : "Please enter a South African phone number, for example 0712345678.") && ok;
    if (!ok) return;

    const button = document.getElementById("place");
    button.disabled = true;
    try {
      const data = {
        items: KC.basketItems().map((item) => `${item.id}:${item.qty}`).join(","),
        fulfilment: fulfilment(),
        phone: cleanPhone,
        paymentMethod: document.getElementById("payment").value,
      };
      if (delivering) {
        data.address = address.value.trim();
        data.distanceKm = distance.value;
      }
      const order = await KC.api("/api/orders", { method: "POST", data });
      KC.clearBasket();
      form.reset();
      render();
      result.innerHTML = `<div class="notice notice-success">${KC.escapeHtml(order.message)} Total: <strong>${KC.money(order.total)}</strong>.
        ${order.deliveryNote ? KC.escapeHtml(order.deliveryNote) : ""} <a href="account.html"><strong>Track it in My account →</strong></a></div>`;
    } catch (error) {
      result.innerHTML = `<div class="notice notice-error">${KC.escapeHtml(error.message)}</div>`;
      button.disabled = false;
    }
  });

  render();
  KC.ready.then(() => {
    document.getElementById("login-note").hidden = Boolean(KC.user);
    renderTotals();
  });
})();
