/*
 * File:    revenue.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Revenue Model page: plan cards with subscribe, the category-share
 *          chart, and the first-year cash-flow / cost-benefit projection.
 */

(() => {
  // ---------- Plans ----------
  const planGrid = document.getElementById("plan-grid");
  const modal = document.getElementById("plan-modal");
  const form = document.getElementById("plan-form");
  let plans = [];
  let chosen = null;

  async function loadPlans() {
    try {
      ({ plans } = await CS.api("/api/plans"));
      planGrid.innerHTML = plans.map((plan) => `
        <article class="plan glass reveal ${plan.code === "growth" ? "highlight" : ""}">
          ${plan.code === "growth" ? `<span class="badge badge-featured" style="position:absolute;top:-12px;left:20px">Most popular</span>` : ""}
          <span class="badge ${plan.audience === "vendor" ? "badge-cyan" : "badge-amber"}" style="align-self:flex-start">
            For ${plan.audience === "vendor" ? "vendors" : "shoppers"}</span>
          <h3 style="margin-top:12px">${CS.escapeHtml(plan.name)}</h3>
          <div class="plan-price">${plan.priceMonthly === 0 ? "Free" : CS.money(plan.priceMonthly).replace(".00", "")}
            ${plan.priceMonthly === 0 ? "" : "<small>/month</small>"}</div>
          ${plan.commissionRate !== null ? `<p class="faint">${(plan.commissionRate * 100).toFixed(0)}% commission per sale</p>` : `<p class="faint">No commission</p>`}
          <p class="muted">${CS.escapeHtml(plan.description)}</p>
          <ul>${plan.features.map((f) => `<li>${CS.escapeHtml(f)}</li>`).join("")}</ul>
          <button class="btn ${plan.code === "growth" ? "btn-primary" : ""} btn-block" type="button" data-plan="${plan.code}">
            ${plan.audience === "vendor" ? "Choose " + CS.escapeHtml(plan.name) : "Join Plus"}</button>
        </article>`).join("");
      CS.revealOnScroll();
    } catch (error) {
      planGrid.innerHTML = `<div class="alert alert-error">${CS.escapeHtml(error.message)}</div>`;
    }
  }

  planGrid.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-plan]");
    if (!button) return;
    await CS.ready;
    if (!CS.user) {
      CS.toast("Please log in or create an account to subscribe.", "error");
      setTimeout(() => (location.href = "login.html?next=revenue.html%23plans"), 900);
      return;
    }
    chosen = plans.find((p) => p.code === button.dataset.plan);
    const needsShop = chosen.audience === "vendor" && CS.user.role !== "vendor";
    document.getElementById("plan-modal-title").textContent = `Subscribe to ${chosen.name}`;
    document.getElementById("plan-modal-text").textContent = chosen.audience === "vendor"
      ? (needsShop ? "Tell us about your shop. You can add products from My Account afterwards." : "Your shop will move to this plan straight away.")
      : `R${chosen.priceMonthly} per month for free delivery on every order. This is a demo, so no money is charged.`;
    document.getElementById("shop-fields").hidden = !needsShop;
    modal.hidden = false;
    (needsShop ? document.getElementById("shopName") : form.querySelector("[type=submit]")).focus();
  });

  function closeModal() {
    modal.hidden = true;
    form.reset();
    form.querySelectorAll(".input").forEach((input) => showFieldError(input, ""));
  }

  form.querySelector("[data-cancel]").addEventListener("click", closeModal);
  modal.addEventListener("click", (event) => { if (event.target === modal) closeModal(); });
  document.addEventListener("keydown", (event) => { if (event.key === "Escape" && !modal.hidden) closeModal(); });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = { planCode: chosen.code };
    if (!document.getElementById("shop-fields").hidden) {
      const fields = [["shopName", 2, "shop name"], ["location", 2, "location"], ["description", 5, "short description"]];
      let ok = true;
      for (const [id, min, label] of fields) {
        const input = document.getElementById(id);
        const value = input.value.trim();
        ok = showFieldError(input, value.length < min ? `Please enter a ${label} of at least ${min} characters.` : "") && ok;
        data[id] = value;
      }
      if (!ok) return;
    }
    try {
      const result = await CS.api("/api/subscriptions", { method: "POST", data });
      closeModal();
      CS.toast(result.message);
      setTimeout(() => location.reload(), 1200);
    } catch (error) {
      CS.toast(error.message, "error");
    }
  });

  // ---------- Category share (pie chart: parts of a whole) ----------
  async function loadCategoryChart() {
    try {
      const { categoryShare } = await CS.api("/api/insights");
      drawChart(document.getElementById("category-chart"), {
        type: "doughnut",
        data: {
          labels: categoryShare.map((c) => c.category),
          datasets: [{ data: categoryShare.map((c) => c.share), backgroundColor: CHART_COLORS, borderWidth: 0 }],
        },
        options: {
          cutout: "58%",
          plugins: { legend: { position: "right" }, tooltip: { callbacks: { label: (ctx) => ` ${ctx.label}: ${ctx.parsed}%` } } },
        },
      });
    } catch {
      // Chart is optional.
    }
  }

  // ---------- First-year projection ----------
  const DAYS_PER_MONTH = 30.44;                 // conversion: per-day figures to per-month
  const DELIVERY_FEE = 35;                      // parameter, matches Pricing.java
  const PLAN_PRICES = { growth: 149, pro: 349, plus: 49 };
  const FIELDS = {
    ordersPerDay: [1, 2000], averageOrder: [20, 5000], commissionRate: [0, 30], paidDeliveryShare: [0, 100],
    growthVendors: [0, 5000], proVendors: [0, 5000], plusMembers: [0, 100000], growth: [0, 50],
    runnerPay: [0, 200], fixedCosts: [0, 1000000], startupCost: [0, 10000000],
  };

  function readInputs() {
    const values = {};
    let ok = true;
    for (const [id, [min, max]] of Object.entries(FIELDS)) {
      const input = document.getElementById(id);
      const value = Number(input.value);
      const valid = input.value.trim() !== "" && Number.isFinite(value) && value >= min && value <= max;
      ok = showFieldError(input, valid ? "" : `Please enter a number between ${min} and ${max}.`) && ok;
      values[id] = value;
    }
    return ok ? values : null;
  }

  /** Builds 12 months of figures; everything grows by the monthly growth rate (compound growth). */
  function project(v) {
    const months = [];
    let cumulative = 0;
    for (let m = 0; m < 12; m++) {
      const factor = Math.pow(1 + v.growth / 100, m);
      const orders = Math.round(v.ordersPerDay * DAYS_PER_MONTH * factor);
      const sales = orders * v.averageOrder;
      const commission = sales * (v.commissionRate / 100);
      const subscriptions = (v.growthVendors * PLAN_PRICES.growth + v.proVendors * PLAN_PRICES.pro + v.plusMembers * PLAN_PRICES.plus) * factor;
      const deliveryFees = orders * (v.paidDeliveryShare / 100) * DELIVERY_FEE;
      const revenue = commission + subscriptions + deliveryFees;
      const costs = v.fixedCosts + orders * v.runnerPay + (m === 0 ? v.startupCost : 0);
      const profit = revenue - costs;
      cumulative += profit;
      months.push({ month: m + 1, orders, commission, subscriptions, deliveryFees, revenue, costs, profit, cumulative });
    }
    return months;
  }

  function renderProjection() {
    const inputs = readInputs();
    if (!inputs) return;
    const months = project(inputs);
    const sum = (key) => months.reduce((total, m) => total + m[key], 0);
    const revenue = sum("revenue");
    const costs = sum("costs");
    const breakEven = months.find((m) => m.cumulative >= 0);

    document.getElementById("p-revenue").textContent = CS.money(revenue);
    document.getElementById("p-costs").textContent = CS.money(costs);
    document.getElementById("p-profit").textContent = CS.money(revenue - costs);
    document.getElementById("p-breakeven").textContent = breakEven ? `Month ${breakEven.month}` : "Not in year 1";
    // Benefit-cost ratio above 1 means the benefits (revenue) outweigh the costs.
    const ratio = costs ? revenue / costs : 0;
    document.getElementById("p-ratio").innerHTML = `<strong>Cost-benefit:</strong> benefit-cost ratio = revenue ÷ costs = ${ratio.toFixed(2)}.
      ${ratio >= 1 ? "Benefits outweigh costs in year one." : "Costs are higher than benefits in year one, so CloudStore would need more orders or lower costs."}
      Possible loss in the worst month: ${CS.money(Math.min(...months.map((m) => m.profit)))}.`;

    const body = document.querySelector("#projection-table tbody");
    body.innerHTML = months.map((m) => `<tr>
      <td>Month ${m.month}</td><td class="num">${m.orders.toLocaleString("en-ZA")}</td>
      <td class="num">${CS.money(m.commission)}</td><td class="num">${CS.money(m.subscriptions)}</td><td class="num">${CS.money(m.deliveryFees)}</td>
      <td class="num">${CS.money(m.revenue)}</td><td class="num">${CS.money(m.costs)}</td>
      <td class="num" style="color:${m.profit >= 0 ? "#a7f3d0" : "#fecdd3"}">${CS.money(m.profit)}</td>
      <td class="num">${CS.money(m.cumulative)}</td></tr>`).join("");
    document.querySelector("#projection-table tfoot").innerHTML = `<tr><th>Year total</th><th class="num">${sum("orders").toLocaleString("en-ZA")}</th>
      <th class="num">${CS.money(sum("commission"))}</th><th class="num">${CS.money(sum("subscriptions"))}</th><th class="num">${CS.money(sum("deliveryFees"))}</th>
      <th class="num">${CS.money(revenue)}</th><th class="num">${CS.money(costs)}</th><th class="num">${CS.money(revenue - costs)}</th><th></th></tr>`;

    // Line chart: trends over time.
    drawChart(document.getElementById("projection-chart"), {
      type: "line",
      data: {
        labels: months.map((m) => "M" + m.month),
        datasets: [
          { label: "Revenue", data: months.map((m) => m.revenue), borderColor: CHART_COLORS[0], backgroundColor: "rgba(34,211,238,0.15)", fill: true, tension: 0.35 },
          { label: "Costs", data: months.map((m) => m.costs), borderColor: CHART_COLORS[2], tension: 0.35 },
          { label: "Cumulative cash flow", data: months.map((m) => m.cumulative), borderColor: CHART_COLORS[3], borderDash: [6, 4], tension: 0.35 },
        ],
      },
      options: { interaction: { mode: "index", intersect: false }, scales: { y: randAxis } },
    });
  }

  document.getElementById("projection-form").addEventListener("submit", (event) => {
    event.preventDefault();
    renderProjection();
  });
  document.getElementById("projection-form").addEventListener("input", renderProjection);

  loadPlans();
  loadCategoryChart();
  renderProjection();
})();
