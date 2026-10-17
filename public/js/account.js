/*
 * File:    account.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: My Account: plan, order history and, for vendors, their shop's
 *          sales, payout and an "Add a product" form.
 */

(async () => {
  const user = await CS.ready;
  if (!user) {
    location.replace("login.html?next=account.html");
    return;
  }
  if (user.role === "admin") {
    document.getElementById("role-badge").textContent = "🛡️ Admin";
  } else if (user.role === "vendor") {
    document.getElementById("role-badge").textContent = "🏪 Vendor";
  }
  document.getElementById("greeting").innerHTML = `Hello, <span class="gradient-text">${CS.escapeHtml(user.fullName.split(" ")[0])}</span>`;
  document.getElementById("account-email").textContent = user.email;

  const statusBadge = { Placed: "badge-cyan", "Out for delivery": "badge-amber", Delivered: "badge-green", Cancelled: "badge-red" };

  async function loadSubscriptions() {
    const holder = document.getElementById("subscriptions");
    try {
      const { subscriptions } = await CS.api("/api/subscriptions/mine");
      if (!subscriptions.length) {
        holder.innerHTML = `<p class="muted">You're not on a paid plan. CloudStore Plus gives you free delivery for R49 a month.</p>
          <a class="btn btn-primary" href="revenue.html#plans">See plans</a>`;
        return;
      }
      holder.innerHTML = subscriptions.map((s) => `
        <div class="row space-between" style="padding:10px 0;border-bottom:1px solid rgba(255,255,255,0.08)">
          <div><strong>${CS.escapeHtml(s.name)}</strong><div class="faint">${s.priceMonthly ? CS.money(s.priceMonthly) + " / month" : "Free"} · since ${CS.escapeHtml(s.startedAt)}</div></div>
          ${s.code === "plus" ? `<button class="btn btn-sm btn-danger" type="button" data-cancel-plus>Cancel</button>` : `<a class="btn btn-sm" href="revenue.html#plans">Change</a>`}
        </div>`).join("");
      const cancel = holder.querySelector("[data-cancel-plus]");
      if (cancel) {
        cancel.addEventListener("click", async () => {
          if (!confirm("Cancel your CloudStore Plus membership? You'll pay the normal delivery fee again.")) return;
          try {
            const result = await CS.api("/api/subscriptions/cancel", { method: "POST", data: {} });
            CS.toast(result.message);
            loadSubscriptions();
          } catch (error) {
            CS.toast(error.message, "error");
          }
        });
      }
    } catch (error) {
      holder.innerHTML = `<div class="alert alert-error">${CS.escapeHtml(error.message)}</div>`;
    }
  }

  async function loadOrders() {
    const holder = document.getElementById("orders");
    try {
      const { orders } = await CS.api("/api/orders/mine");
      if (!orders.length) {
        holder.innerHTML = `<div class="empty glass"><div class="big">📦</div><p>No orders yet.</p><a class="btn btn-primary" href="shop.html">Start shopping</a></div>`;
        return;
      }
      holder.innerHTML = orders.map((order) => `
        <article class="card glass">
          <div class="row space-between">
            <div><h3 style="margin:0">Order #${order.id}</h3><span class="faint">${CS.escapeHtml(order.createdAt)} · ${CS.escapeHtml(order.paymentMethod)}</span></div>
            <span class="badge ${statusBadge[order.status] || ""}">${CS.escapeHtml(order.status)}</span>
          </div>
          <div class="row" style="margin-top:12px">${order.items.map((item) =>
            `<span class="badge">${CS.escapeHtml(item.emoji)} ${CS.escapeHtml(item.name)} × ${item.quantity}</span>`).join("")}</div>
          <div class="row space-between" style="margin-top:12px">
            <span class="faint">Subtotal ${CS.money(order.subtotal)} · Delivery ${order.deliveryFee ? CS.money(order.deliveryFee) : "Free"}</span>
            <strong>${CS.money(order.total)}</strong>
          </div>
        </article>`).join("");
    } catch (error) {
      holder.innerHTML = `<div class="alert alert-error">${CS.escapeHtml(error.message)}</div>`;
    }
  }

  async function loadShop() {
    const { shop } = await CS.api("/api/orders/shop");
    document.getElementById("shop-section").hidden = false;
    document.getElementById("shop-title").textContent = shop.name;
    document.getElementById("shop-meta").textContent = `📍 ${shop.location} · ${shop.plan} plan`;
    document.getElementById("shop-products").textContent = shop.products.length;
    document.getElementById("shop-sales").textContent = CS.money(shop.totalSales);
    document.getElementById("shop-payout").textContent = CS.money(shop.totalPayout);
    document.getElementById("shop-rate").textContent = `After ${(shop.commissionRate * 100).toFixed(0)}% commission`;
    document.getElementById("shop-table").innerHTML = shop.products.length
      ? shop.products.map((p) => `<tr><td>${CS.escapeHtml(p.emoji)} ${CS.escapeHtml(p.name)}</td><td class="num">${CS.money(p.price)}</td>
          <td class="num">${p.stock}</td><td class="num">${p.unitsSold}</td><td class="num">${CS.money(p.sales)}</td></tr>`).join("")
      : `<tr><td colspan="5" class="muted">No products yet. Add your first one with the form.</td></tr>`;
  }

  const media = window.matchMedia("(max-width: 900px)");
  const applyLayout = () => (document.getElementById("shop-grid").style.gridTemplateColumns = media.matches ? "1fr" : "minmax(0,1.4fr) minmax(0,1fr)");
  media.addEventListener("change", applyLayout);
  applyLayout();

  loadSubscriptions();
  loadOrders();
  if (user.role === "vendor") {
    try {
      await loadShop();
      await setupProductForm(document.getElementById("product-form"), loadShop);
    } catch (error) {
      CS.toast(error.message, "error");
    }
  }
})();
