/*
 * File:    dashboard.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Admin dashboard: statistics tiles, charts, recent orders with
 *          status changes, contact messages and adding products.
 */

(async () => {
  const user = await CS.ready;
  if (!user || user.role !== "admin") {
    CS.toast("Please log in with the admin account to view the dashboard.", "error");
    setTimeout(() => location.replace("login.html?next=dashboard.html"), 900);
    return;
  }
  document.getElementById("dashboard").hidden = false;

  const media = window.matchMedia("(max-width: 900px)");
  const applyLayout = () => (document.getElementById("admin-grid").style.gridTemplateColumns = media.matches ? "1fr" : "minmax(0,1.4fr) minmax(0,1fr)");
  media.addEventListener("change", applyLayout);
  applyLayout();

  const set = (id, text) => (document.getElementById(id).textContent = text);

  async function loadStats() {
    const s = await CS.api("/api/admin/stats");
    set("t-revenue", CS.money(s.totals.revenue));
    set("t-orders", `${s.totals.orders} orders · ${CS.money(s.totals.merchandiseSales)} in product sales`);
    set("t-commission", CS.money(s.platformIncome.commission));
    set("t-commission-share", `${s.platformIncome.commissionShareOfSales}% of product sales`);
    set("t-mrr", CS.money(s.platformIncome.vendorPlansMonthly + s.platformIncome.plusMonthly));
    set("t-community", `${s.totals.vendors} vendors`);
    set("t-community-note", `${s.totals.customers} customers · ${s.totals.products} products`);

    set("s-mean", CS.money(s.orderValue.mean));
    set("s-median", CS.money(s.orderValue.median));
    set("s-range", CS.money(s.orderValue.range));
    set("s-minmax", `${CS.money(s.orderValue.min)} to ${CS.money(s.orderValue.max)}`);
    document.getElementById("s-free").innerHTML = `<strong>${s.orderValue.freeDeliveryShare}%</strong> of orders were over R500 and got free delivery.
      ${s.orderValue.mean > s.orderValue.median ? "The mean is above the median, so a few large orders pull the average up." : "The mean and median are close, so order values are fairly even."}`;

    const c = s.conversions;
    document.getElementById("conversions").innerHTML = `
      <tr><th>Orders per day</th><td class="num">${c.ordersPerDay}</td></tr>
      <tr><th>Orders per month</th><td class="num">${c.ordersPerMonth}</td></tr>
      <tr><th>Revenue per day</th><td class="num">${CS.money(c.revenuePerDay)}</td></tr>
      <tr><th>Revenue per month</th><td class="num">${CS.money(c.revenuePerMonth)}</td></tr>
      <tr><th>Delivery fees collected</th><td class="num">${CS.money(s.platformIncome.deliveryFees)}</td></tr>`;

    drawChart(document.getElementById("trend-chart"), {
      type: "line",
      data: {
        labels: s.trend.map((d) => d.date.slice(5)),
        datasets: [{ label: "Revenue", data: s.trend.map((d) => d.revenue), borderColor: CHART_COLORS[0],
          backgroundColor: "rgba(34,211,238,0.15)", fill: true, tension: 0.35, pointRadius: 2 }],
      },
      options: { plugins: { legend: { display: false } }, scales: { y: randAxis } },
    });

    drawChart(document.getElementById("category-chart"), {
      type: "bar",
      data: {
        labels: s.byCategory.map((c) => c.category),
        datasets: [{ label: "Sales", data: s.byCategory.map((c) => c.sales), backgroundColor: CHART_COLORS, borderRadius: 10 }],
      },
      options: { plugins: { legend: { display: false } }, scales: { y: randAxis } },
    });

    drawChart(document.getElementById("bands-chart"), {
      type: "bar",
      data: {
        labels: s.orderBands.map((b) => b.band),
        datasets: [{ label: "Orders", data: s.orderBands.map((b) => b.count), backgroundColor: CHART_COLORS[1], borderRadius: 10 }],
      },
      options: { plugins: { legend: { display: false } }, scales: { y: { ticks: { precision: 0 } } } },
    });

    const income = s.platformIncome;
    drawChart(document.getElementById("income-chart"), {
      type: "pie",
      data: {
        labels: ["Commission", "Delivery fees", "Vendor plans (1 month)", "Plus (1 month)"],
        datasets: [{ data: [income.commission, income.deliveryFees, income.vendorPlansMonthly, income.plusMonthly], backgroundColor: CHART_COLORS, borderWidth: 0 }],
      },
      options: { plugins: { legend: { position: "right" }, tooltip: { callbacks: { label: (ctx) => ` ${ctx.label}: ${CS.money(ctx.parsed)}` } } } },
    });

    document.getElementById("top-products").innerHTML = s.topProducts.map((p) =>
      `<tr><td>${CS.escapeHtml(p.emoji)} ${CS.escapeHtml(p.name)}</td><td class="num">${p.units}</td><td class="num">${CS.money(p.sales)}</td></tr>`).join("");
    document.getElementById("plans").innerHTML = s.plans.map((p) =>
      `<tr><td>${CS.escapeHtml(p.plan)}</td><td class="num">${p.count}</td></tr>`).join("");
  }

  const STATUSES = ["Placed", "Out for delivery", "Delivered", "Cancelled"];

  async function loadOrders() {
    const { orders } = await CS.api("/api/admin/orders");
    document.getElementById("orders").innerHTML = orders.map((o) => `
      <tr><td>#${o.id}</td><td>${CS.escapeHtml(o.customer)}</td><td>${CS.escapeHtml(o.createdAt)}</td><td>${CS.escapeHtml(o.paymentMethod)}</td>
        <td class="num">${CS.money(o.total)}</td>
        <td><label class="visually-hidden" for="status-${o.id}">Status of order ${o.id}</label>
          <select class="input" id="status-${o.id}" data-order="${o.id}" style="padding:6px 10px;min-width:150px">
            ${STATUSES.map((st) => `<option ${st === o.status ? "selected" : ""}>${st}</option>`).join("")}</select></td></tr>`).join("");
  }

  document.getElementById("orders").addEventListener("change", async (event) => {
    const select = event.target.closest("[data-order]");
    if (!select) return;
    try {
      const result = await CS.api("/api/admin/orders/status", { method: "POST", data: { id: select.dataset.order, status: select.value } });
      CS.toast(result.message);
      loadStats();
    } catch (error) {
      CS.toast(error.message, "error");
    }
  });

  async function loadMessages() {
    const { messages } = await CS.api("/api/admin/messages");
    document.getElementById("messages").innerHTML = messages.length
      ? messages.map((m) => `<div style="padding:12px;border-radius:14px;background:rgba(255,255,255,0.05)">
          <div class="row space-between"><strong>${CS.escapeHtml(m.subject)}</strong><span class="faint">${CS.escapeHtml(m.createdAt)}</span></div>
          <div class="faint">${CS.escapeHtml(m.name)} · <a href="mailto:${encodeURIComponent(m.email)}">${CS.escapeHtml(m.email)}</a></div>
          <p class="muted" style="margin:6px 0 0;white-space:pre-wrap">${CS.escapeHtml(m.message)}</p></div>`).join("")
      : `<p class="muted">No messages yet.</p>`;
  }

  async function loadVendors() {
    const { vendors } = await CS.api("/api/vendors");
    document.getElementById("p-vendor").innerHTML = `<option value="">Choose a vendor…</option>` +
      vendors.map((v) => `<option value="${v.id}">${CS.escapeHtml(v.name)} (${CS.escapeHtml(v.plan)})</option>`).join("");
  }

  try {
    await Promise.all([loadStats(), loadOrders(), loadMessages(), loadVendors()]);
    await setupProductForm(document.getElementById("product-form"), loadStats);
  } catch (error) {
    CS.toast(error.message, "error");
  }
})();
