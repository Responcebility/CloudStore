/*
 * File:    dashboard.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Admin dashboard: statistics, charts, recent orders with status
 *          changes, messages and adding menu items.
 */

(async () => {
  const user = await KC.ready;
  if (!user || user.role !== "admin") {
    KC.toast("Please log in with the admin account to see the dashboard.", "error");
    setTimeout(() => location.replace("login.html?next=dashboard.html"), 900);
    return;
  }
  document.getElementById("dashboard").hidden = false;
  const set = (id, text) => (document.getElementById(id).textContent = text);

  async function loadStats() {
    const s = await KC.api("/api/admin/stats");
    const t = s.totals;
    set("t-revenue", KC.money(t.revenue));
    set("t-orders", `${t.orders} orders`);
    set("t-profit", KC.money(t.grossProfit));
    set("t-margin", `${t.grossMargin}% gross margin`);
    set("t-clubs", KC.money(t.clubIncomeMonthly));
    set("t-discounts", `${KC.money(t.discounts)} given in club discounts`);
    set("t-customers", t.customers);
    set("t-menu", `${t.menuItems} menu items · ${t.messages} messages`);

    const v = s.orderValue;
    set("s-mean", KC.money(v.mean));
    set("s-median", KC.money(v.median));
    set("s-range", KC.money(v.range));
    set("s-minmax", `${KC.money(v.min)} – ${KC.money(v.max)}`);
    set("s-note", `${v.freeDeliveryShare}% of orders were over R250 and got free delivery. `
      + (v.mean > v.median ? "The mean is above the median, so a few big family orders pull the average up." : "Mean and median are close, so order sizes are fairly even."));

    const c = s.conversions;
    document.getElementById("conversions").innerHTML = `
      <tr><th>Orders per day</th><td class="num">${c.ordersPerDay}</td></tr>
      <tr><th>Orders per month</th><td class="num">${c.ordersPerMonth}</td></tr>
      <tr><th>Revenue per day</th><td class="num">${KC.money(c.revenuePerDay)}</td></tr>
      <tr><th>Revenue per month</th><td class="num">${KC.money(c.revenuePerMonth)}</td></tr>`;

    drawChart(document.getElementById("trend-chart"), {
      type: "line",
      data: { labels: s.trend.map((d) => d.date.slice(5)),
        datasets: [{ label: "Revenue", data: s.trend.map((d) => d.revenue), borderColor: CHART_COLORS[0], backgroundColor: "rgba(249,115,22,0.18)", fill: true, tension: 0.35, pointRadius: 2 }] },
      options: { plugins: { legend: { display: false } }, scales: { y: randAxis } },
    });
    drawChart(document.getElementById("category-chart"), {
      type: "bar",
      data: { labels: s.byCategory.map((x) => x.category), datasets: [{ label: "Sales", data: s.byCategory.map((x) => x.sales), backgroundColor: CHART_COLORS, borderRadius: 10 }] },
      options: { plugins: { legend: { display: false } }, scales: { y: randAxis } },
    });
    drawChart(document.getElementById("bands-chart"), {
      type: "bar",
      data: { labels: s.orderBands.map((b) => b.band), datasets: [{ label: "Orders", data: s.orderBands.map((b) => b.count), backgroundColor: CHART_COLORS[1], borderRadius: 10 }] },
      options: { plugins: { legend: { display: false } }, scales: { y: { ticks: { precision: 0 } } } },
    });
    drawChart(document.getElementById("income-chart"), {
      type: "pie",
      data: { labels: ["Food gross profit", "Delivery fees", "Clubs (1 month)"],
        datasets: [{ data: [t.grossProfit, t.deliveryFees, t.clubIncomeMonthly], backgroundColor: CHART_COLORS, borderWidth: 0 }] },
      options: { plugins: { legend: { position: "right" }, tooltip: { callbacks: { label: (ctx) => ` ${ctx.label}: ${KC.money(ctx.parsed)}` } } } },
    });

    document.getElementById("top-items").innerHTML = s.topItems.map((i) =>
      `<tr><td>${KC.escapeHtml(i.emoji)} ${KC.escapeHtml(i.name)}</td><td class="num">${i.units}</td><td class="num">${KC.money(i.sales)}</td></tr>`).join("");
    document.getElementById("margins").innerHTML = s.byCategory.map((x) =>
      `<tr><td>${KC.escapeHtml(x.category)}</td><td class="num">${x.share}%</td><td class="num">${x.margin}%</td></tr>`).join("");
  }

  async function loadOrders() {
    const { orders, statuses } = await KC.api("/api/admin/orders");
    document.getElementById("orders").innerHTML = orders.map((o) => `
      <tr><td>#${o.id}</td><td>${KC.escapeHtml(o.customer)}</td><td>${KC.escapeHtml(o.createdAt)}</td><td class="num">${KC.money(o.total)}</td>
        <td><label class="visually-hidden" for="status-${o.id}">Status of order ${o.id}</label>
          <select class="input" id="status-${o.id}" data-order="${o.id}" style="min-height:38px;padding:6px 10px;min-width:160px">
            ${statuses.map((st) => `<option ${st === o.status ? "selected" : ""}>${st}</option>`).join("")}</select></td></tr>`).join("");
  }

  document.getElementById("orders").addEventListener("change", async (event) => {
    const select = event.target.closest("[data-order]");
    if (!select) return;
    try {
      const result = await KC.api("/api/admin/orders/status", { method: "POST", data: { id: select.dataset.order, status: select.value } });
      KC.toast(result.message);
      loadStats();
    } catch (error) {
      KC.toast(error.message, "error");
    }
  });

  async function loadMessages() {
    const { messages } = await KC.api("/api/admin/messages");
    document.getElementById("messages").innerHTML = messages.length
      ? messages.map((m) => `<div style="padding:12px;border-radius:14px;background:rgba(42,6,6,0.4)">
          <div class="row space-between"><strong>${KC.escapeHtml(m.subject)}</strong><span class="faint">${KC.escapeHtml(m.createdAt)}</span></div>
          <div class="faint">${KC.escapeHtml(m.name)} · ${KC.escapeHtml(m.email)}</div>
          <p class="muted" style="margin:6px 0 0;white-space:pre-wrap">${KC.escapeHtml(m.message)}</p></div>`).join("")
      : `<p class="muted">No messages yet.</p>`;
  }

  // ---------- Add a menu item ----------
  const form = document.getElementById("item-form");
  const { categories } = await KC.api("/api/products/categories");
  form.querySelector("[name=category]").innerHTML = categories.map((c) => `<option>${KC.escapeHtml(c)}</option>`).join("");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const get = (name) => form.querySelector(`[name=${name}]`);
    const price = Number(get("price").value);
    const cost = Number(get("cost").value);
    const stock = Number(get("stock").value);
    let ok = showFieldError(get("name"), get("name").value.trim().length >= 2 ? "" : "Please enter a name of at least 2 characters.");
    ok = showFieldError(get("description"), get("description").value.trim().length >= 5 ? "" : "Please enter a description of at least 5 characters.") && ok;
    ok = showFieldError(get("price"), get("price").value !== "" && price >= 1 && price <= 5000 ? "" : "Please enter a price between R1 and R5000.") && ok;
    ok = showFieldError(get("cost"), get("cost").value !== "" && cost >= 0 && cost < price ? "" : "Please enter a food cost lower than the price.") && ok;
    ok = showFieldError(get("stock"), get("stock").value !== "" && Number.isInteger(stock) && stock >= 0 && stock <= 10000 ? "" : "Please enter a whole number between 0 and 10000.") && ok;
    if (!ok) return;
    try {
      const result = await KC.api("/api/products", { method: "POST", data: Object.fromEntries(new FormData(form)) });
      KC.toast(result.message);
      form.reset();
      loadStats();
    } catch (error) {
      KC.toast(error.message, "error");
    }
  });

  try {
    await Promise.all([loadStats(), loadOrders(), loadMessages()]);
  } catch (error) {
    KC.toast(error.message, "error");
  }
})();
