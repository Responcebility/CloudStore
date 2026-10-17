/*
 * File:    account.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: My Account: club membership and order history with live status.
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
  document.getElementById("greeting").textContent = `Hi, ${user.fullName.split(" ")[0]} 👋`;
  document.getElementById("account-email").textContent = user.email;

  const STATUS_TAGS = { Placed: "tag-soft", Preparing: "tag-orange", "Out for delivery": "tag-orange", Delivered: "tag-soft", Cancelled: "tag-hot" };
  const STATUS_ICONS = { Placed: "🧾", Preparing: "👩🏾‍🍳", "Out for delivery": "🛵", Delivered: "✅", Cancelled: "✖" };

  // ---------- Club ----------
  const clubBox = document.getElementById("club");
  if (user.club) {
    clubBox.innerHTML = `<div class="row space-between">
        <div><h2 style="margin-bottom:4px">⭐ ${KC.escapeHtml(user.club.name)}</h2>
          <p class="muted" style="margin:0">Free delivery${user.club.discountRate ? ` and ${Math.round(user.club.discountRate * 100)}% off` : ""} on every order.</p></div>
        <div class="row"><a class="btn" href="revenue.html#clubs">Change club</a><button class="btn" type="button" data-cancel>Cancel</button></div>
      </div>`;
    clubBox.querySelector("[data-cancel]").addEventListener("click", async () => {
      if (!confirm("Cancel your club membership? You'll pay normal delivery fees again.")) return;
      try {
        const result = await KC.api("/api/subscriptions/cancel", { method: "POST", data: {} });
        KC.toast(result.message);
        setTimeout(() => location.reload(), 900);
      } catch (error) {
        KC.toast(error.message, "error");
      }
    });
  } else {
    clubBox.innerHTML = `<div class="row space-between">
        <div><h2 style="margin-bottom:4px">Not in a club yet</h2>
          <p class="muted" style="margin:0">Crunch Club: free delivery for R49 a month. Family Club adds 10% off.</p></div>
        <a class="btn btn-primary" href="revenue.html#clubs">See the clubs</a>
      </div>`;
  }

  // ---------- Orders ----------
  const holder = document.getElementById("orders");
  try {
    const { orders } = await KC.api("/api/orders/mine");
    holder.innerHTML = orders.length
      ? orders.map((order) => `
        <article class="card glass">
          <div class="row space-between">
            <div><h3 style="margin:0">Order #${order.id}</h3><span class="faint">${KC.escapeHtml(order.createdAt)} · ${KC.escapeHtml(order.paymentMethod)}</span></div>
            <span class="tag ${STATUS_TAGS[order.status] || ""}" style="font-size:0.85rem">${STATUS_ICONS[order.status] || ""} ${KC.escapeHtml(order.status)}</span>
          </div>
          <p class="muted" style="margin:10px 0">${order.items.map((i) => `${KC.escapeHtml(i.emoji)} ${KC.escapeHtml(i.name)} × ${i.quantity}`).join(" · ")}</p>
          <div class="row space-between">
            <span class="faint">Delivery ${order.deliveryFee ? KC.money(order.deliveryFee) : "free"}${order.discount ? ` · Saved ${KC.money(order.discount)}` : ""}</span>
            <strong class="price" style="font-size:1.2rem">${KC.money(order.total)}</strong>
          </div>
        </article>`).join("")
      : `<div class="empty glass"><div class="big">🍗</div><p>No orders yet.</p><a class="btn btn-primary" href="menu.html">See the menu</a></div>`;
  } catch (error) {
    holder.innerHTML = `<div class="alert alert-error">${KC.escapeHtml(error.message)}</div>`;
  }
})();
