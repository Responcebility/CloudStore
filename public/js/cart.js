/*
 * File:    cart.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Cart page: change quantities, live totals with the delivery rule,
 *          and checkout. The server recalculates every amount before saving.
 */

(() => {
  const list = document.getElementById("cart-items");
  const form = document.getElementById("checkout-form");
  const result = document.getElementById("checkout-result");
  const PHONE = /^(0\d{9}|\+27\d{9})$/;

  function render() {
    const items = CS.cartItems();
    const hasPlus = Boolean(CS.user && CS.user.hasPlus);
    if (!items.length) {
      list.innerHTML = `<div class="empty"><div class="big">🛒</div><p>Your cart is empty.</p>
        <a class="btn btn-primary" href="shop.html">Browse the shop</a></div>`;
    } else {
      list.innerHTML = items.map((item) => `
        <div class="cart-item">
          <div class="art" aria-hidden="true">${CS.escapeHtml(item.emoji)}</div>
          <div>
            <strong>${CS.escapeHtml(item.name)}</strong>
            <div class="faint">${CS.escapeHtml(item.vendorName)} · ${CS.money(item.price)} each</div>
            <div class="qty" style="margin-top:8px" aria-label="Quantity of ${CS.escapeHtml(item.name)}">
              <button type="button" data-dec="${item.id}" aria-label="Decrease">−</button>
              <span>${item.qty}</span>
              <button type="button" data-inc="${item.id}" aria-label="Increase">+</button>
            </div>
          </div>
          <div style="text-align:right">
            <div class="price" style="font-size:1.05rem">${CS.money(item.price * item.qty)}</div>
            <button class="btn btn-sm btn-danger" type="button" data-remove="${item.id}" style="margin-top:8px">Remove</button>
          </div>
        </div>`).join("");
    }

    const totals = CS.cartTotals(hasPlus);
    document.getElementById("sum-subtotal").textContent = CS.money(totals.subtotal);
    document.getElementById("sum-delivery").textContent = totals.subtotal === 0 ? CS.money(0) : (totals.delivery === 0 ? "Free" : CS.money(totals.delivery));
    document.getElementById("sum-total").textContent = CS.money(totals.total);

    const threshold = CS.pricing.freeDeliveryThreshold;
    const note = document.getElementById("free-delivery");
    if (hasPlus) {
      note.innerHTML = "⭐ Free delivery with CloudStore Plus.";
    } else if (totals.subtotal > threshold) {
      note.innerHTML = "🎉 Your order qualifies for free delivery.";
    } else if (totals.subtotal > 0) {
      const remaining = threshold - totals.subtotal + 0.01;
      const percent = Math.min(100, (totals.subtotal / threshold) * 100);
      note.innerHTML = `Add ${CS.money(remaining)} more for free delivery.
        <div class="progress" aria-hidden="true"><span style="width:${percent}%"></span></div>`;
    } else {
      note.textContent = "";
    }
    document.getElementById("place-order").disabled = !items.length;
  }

  list.addEventListener("click", (event) => {
    const target = event.target.closest("button");
    if (!target) return;
    const items = CS.cartItems();
    const find = (id) => items.find((item) => String(item.id) === id);
    if (target.dataset.inc) {
      const item = find(target.dataset.inc);
      if (item.qty >= Math.min(CS.pricing.maxQuantityPerItem, item.stock ?? 99)) {
        CS.toast(`You can order at most ${Math.min(CS.pricing.maxQuantityPerItem, item.stock ?? 99)} of ${item.name}.`, "error");
      } else {
        CS.setQuantity(item.id, item.qty + 1);
      }
    } else if (target.dataset.dec) {
      const item = find(target.dataset.dec);
      CS.setQuantity(item.id, item.qty - 1);
    } else if (target.dataset.remove) {
      CS.setQuantity(target.dataset.remove, 0);
    }
    render();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    result.innerHTML = "";
    if (!CS.user) {
      document.getElementById("login-note").hidden = false;
      return;
    }
    const address = document.getElementById("address");
    const phone = document.getElementById("phone");
    const cleanPhone = phone.value.replace(/[\s-]/g, "");
    let ok = showFieldError(address, address.value.trim().length >= 5 ? "" : "Please enter your delivery address (at least 5 characters).");
    ok = showFieldError(phone, PHONE.test(cleanPhone) ? "" : "Please enter a South African phone number, for example 0712345678.") && ok;
    if (!ok) return;

    const items = CS.cartItems().map((item) => `${item.id}:${item.qty}`).join(",");
    const button = document.getElementById("place-order");
    button.disabled = true;
    try {
      const order = await CS.api("/api/orders", {
        method: "POST",
        data: { items, address: address.value.trim(), phone: cleanPhone, paymentMethod: document.getElementById("paymentMethod").value },
      });
      CS.clearCart();
      form.reset();
      render();
      result.innerHTML = `<div class="alert alert-success">${CS.escapeHtml(order.message)} Total ${CS.money(order.total)}
        ${order.freeDeliveryReason ? `(free delivery: ${CS.escapeHtml(order.freeDeliveryReason)})` : ""}.
        <a href="account.html">View your orders →</a></div>`;
    } catch (error) {
      result.innerHTML = `<div class="alert alert-error">${CS.escapeHtml(error.message)}</div>`;
      button.disabled = false;
    }
  });

  CS.ready.then(() => {
    document.getElementById("login-note").hidden = Boolean(CS.user);
    render();
  });
  render();
})();
