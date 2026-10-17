/*
 * File:    cart.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Order page: change quantities, live totals (club discount and
 *          delivery rule), and checkout. The server recalculates every amount.
 */

(() => {
  const list = document.getElementById("cart-items");
  const form = document.getElementById("checkout-form");
  const result = document.getElementById("checkout-result");
  const PHONE = /^(0\d{9}|\+27\d{9})$/;
  const set = (id, text) => (document.getElementById(id).textContent = text);

  function render() {
    const items = KC.cartItems();
    const club = KC.user ? KC.user.club : null;
    list.innerHTML = items.length
      ? items.map((item) => `
        <div class="cart-item">
          <div class="art" aria-hidden="true">${KC.escapeHtml(item.emoji)}</div>
          <div>
            <strong>${KC.escapeHtml(item.name)}</strong>
            <div class="faint">${KC.money(item.price)} each</div>
            <div class="qty" aria-label="Quantity of ${KC.escapeHtml(item.name)}">
              <button type="button" data-dec="${item.id}" aria-label="One less">−</button>
              <span>${item.qty}</span>
              <button type="button" data-inc="${item.id}" aria-label="One more">+</button>
            </div>
          </div>
          <div style="text-align:right">
            <div class="price" style="font-size:1.15rem">${KC.money(item.price * item.qty)}</div>
            <button class="btn btn-sm" type="button" data-remove="${item.id}" style="margin-top:6px">Remove</button>
          </div>
        </div>`).join("")
      : `<div class="empty"><div class="big">🍗</div><p>Your order is empty. Hungry?</p><a class="btn btn-primary" href="menu.html">See the menu</a></div>`;

    const totals = KC.cartTotals(club);
    set("sum-subtotal", KC.money(totals.subtotal));
    document.getElementById("row-discount").hidden = !totals.discount;
    set("discount-label", club ? `${club.name} discount` : "Discount");
    set("sum-discount", "−" + KC.money(totals.discount));
    set("sum-delivery", totals.subtotal === 0 ? KC.money(0) : totals.delivery === 0 ? "Free" : KC.money(totals.delivery));
    set("sum-total", KC.money(totals.total));

    const threshold = KC.pricing.freeDeliveryThreshold;
    const note = document.getElementById("free-delivery");
    if (club && club.freeDelivery) {
      note.textContent = `⭐ Free delivery with your ${club.name}.`;
    } else if (totals.subtotal > threshold) {
      note.textContent = "🎉 Your order gets free delivery.";
    } else if (totals.subtotal > 0) {
      const percent = Math.min(100, (totals.subtotal / threshold) * 100);
      note.innerHTML = `Add ${KC.money(threshold - totals.subtotal + 0.01)} more for free delivery.
        <div class="progress" aria-hidden="true"><span style="width:${percent}%"></span></div>`;
    } else {
      note.textContent = "";
    }
    document.getElementById("place-order").disabled = !items.length;
  }

  list.addEventListener("click", (event) => {
    const button = event.target.closest("button");
    if (!button) return;
    const item = KC.cartItems().find((i) => String(i.id) === (button.dataset.inc || button.dataset.dec || button.dataset.remove));
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

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    result.innerHTML = "";
    if (!KC.user) {
      document.getElementById("login-note").hidden = false;
      return;
    }
    const address = document.getElementById("address");
    const phone = document.getElementById("phone");
    const cleanPhone = phone.value.replace(/[\s-]/g, "");
    let ok = showFieldError(address, address.value.trim().length >= 5 ? "" : "Please enter your delivery address (at least 5 characters).");
    ok = showFieldError(phone, PHONE.test(cleanPhone) ? "" : "Please enter a South African phone number, for example 0712345678.") && ok;
    if (!ok) return;

    const button = document.getElementById("place-order");
    button.disabled = true;
    try {
      const order = await KC.api("/api/orders", {
        method: "POST",
        data: {
          items: KC.cartItems().map((item) => `${item.id}:${item.qty}`).join(","),
          address: address.value.trim(),
          phone: cleanPhone,
          paymentMethod: document.getElementById("paymentMethod").value,
        },
      });
      KC.clearCart();
      form.reset();
      render();
      result.innerHTML = `<div class="alert alert-success">${KC.escapeHtml(order.message)} Total: ${KC.money(order.total)}.
        <a href="account.html">Track it in My Account →</a></div>`;
    } catch (error) {
      result.innerHTML = `<div class="alert alert-error">${KC.escapeHtml(error.message)}</div>`;
      button.disabled = false;
    }
  });

  render();
  KC.ready.then(() => {
    document.getElementById("login-note").hidden = Boolean(KC.user);
    render();
  });
})();
