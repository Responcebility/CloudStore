/*
 * File:    account.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: My account: Crunch Pass status and order history with live status.
 */

(async () => {
  const user = await KC.ready;
  if (!user) {
    location.replace("login.html?next=account.html");
    return;
  }
  if (user.role === "admin") {
    location.replace("dashboard.html");
    return;
  }
  document.getElementById("greeting").innerHTML = `Hi, <em>${KC.escapeHtml(user.fullName.split(" ")[0])}.</em>`;
  document.getElementById("email").textContent = user.email;
  document.getElementById("logout").addEventListener("click", KC.logout);

  // ---------- Crunch Pass ----------
  const passBox = document.getElementById("pass-box");
  passBox.innerHTML = user.pass
    ? `<div class="row between">
        <div><h3 style="margin-bottom:4px">✳ Crunch Pass: ${user.pass.maxKm} km</h3>
          <p class="muted" style="margin:0">Free delivery within ${user.pass.maxKm} km until ${KC.escapeHtml(user.pass.endsAt)} · ${KC.money(user.pass.price)} this month</p></div>
        <a class="button button-outline" href="pass.html">Change or cancel</a></div>`
    : `<div class="row between">
        <div><h3 style="margin-bottom:4px">No Crunch Pass yet</h3>
          <p class="muted" style="margin:0">Pick your distance and get free delivery all month, from ${KC.money(KC.passPrice(1))}.</p></div>
        <a class="button button-primary" href="pass.html">Get a Crunch Pass ↗</a></div>`;

  // ---------- Orders ----------
  const holder = document.getElementById("orders");
  try {
    const { orders } = await KC.api("/api/orders/mine");
    holder.innerHTML = orders.length
      ? orders.map((order) => `
        <article class="card">
          <div class="row between">
            <div><h3 style="margin:0">Order #${order.id}</h3>
              <span class="muted small">${KC.escapeHtml(order.createdAt)} · ${order.fulfilment === "delivery" ? `Delivery, ${order.distanceKm} km` : "Collection"} · ${KC.escapeHtml(order.paymentMethod)}</span></div>
            <span class="tag ${order.status === "Cancelled" ? "tag-red" : "tag-orange"}">${KC.escapeHtml(order.status)}</span>
          </div>
          <p class="muted" style="margin:10px 0">${order.items.map((i) => `${KC.escapeHtml(i.name)} × ${i.quantity}`).join(" · ")}</p>
          <div class="row between">
            <span class="muted small">Food ${KC.money(order.subtotal)} · ${order.fulfilment === "delivery" ? `Delivery ${order.deliveryFee ? KC.money(order.deliveryFee) : "free"}` : "Collection free"}</span>
            <strong style="font-family:var(--heading);font-size:20px">${KC.money(order.total)}</strong>
          </div>
        </article>`).join("")
      : `<div class="card empty"><div class="big">✳</div><h3>No orders yet.</h3><a class="button button-primary" href="menu.html">See the menu ↗</a></div>`;
  } catch (error) {
    holder.innerHTML = `<div class="notice notice-error">${KC.escapeHtml(error.message)}</div>`;
  }
})();
