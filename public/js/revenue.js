/*
 * File:    revenue.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Revenue Model page: club cards with join buttons, the
 *          sales-by-category chart, and the first-year cash-flow / cost-benefit
 *          projection.
 */

(() => {
  // ---------- Clubs ----------
  const planGrid = document.getElementById("plan-grid");
  let plans = [];

  async function loadPlans() {
    await KC.ready;
    try {
      ({ plans } = await KC.api("/api/plans"));
      const current = KC.user && KC.user.club ? KC.user.club.code : null;
      planGrid.innerHTML = plans.map((plan) => `
        <article class="plan glass ${plan.code === "family-club" ? "highlight" : ""}">
          <h3>${KC.escapeHtml(plan.name)} ${plan.code === "family-club" ? `<span class="tag tag-orange">Best value</span>` : ""}</h3>
          <div class="plan-price">R${plan.priceMonthly.toFixed(0)}<small> / month</small></div>
          <p class="muted">${KC.escapeHtml(plan.description)}</p>
          <ul class="ticks">${plan.features.map((f) => `<li>${KC.escapeHtml(f)}</li>`).join("")}</ul>
          ${plan.code === current
            ? `<span class="btn btn-block" aria-disabled="true">✓ You're in this club</span>`
            : `<button class="btn ${plan.code === "family-club" ? "btn-primary" : ""} btn-block" type="button" data-plan="${plan.code}">Join ${KC.escapeHtml(plan.name)}</button>`}
        </article>`).join("");
    } catch (error) {
      planGrid.innerHTML = `<div class="alert alert-error">${KC.escapeHtml(error.message)}</div>`;
    }
  }

  planGrid.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-plan]");
    if (!button) return;
    if (!KC.user) {
      KC.toast("Please log in or create an account to join a club.", "error");
      setTimeout(() => (location.href = "login.html?next=revenue.html%23clubs"), 900);
      return;
    }
    const plan = plans.find((p) => p.code === button.dataset.plan);
    if (!confirm(`Join the ${plan.name} for R${plan.priceMonthly.toFixed(0)} a month? (Demo only: no money is charged.)`)) return;
    try {
      const result = await KC.api("/api/subscriptions", { method: "POST", data: { planCode: plan.code } });
      KC.toast(result.message);
      setTimeout(() => location.reload(), 1200);
    } catch (error) {
      KC.toast(error.message, "error");
    }
  });

  // ---------- Sales by category (doughnut: parts of a whole) ----------
  async function loadCategoryChart() {
    try {
      const { categoryShare } = await KC.api("/api/insights");
      drawChart(document.getElementById("category-chart"), {
        type: "doughnut",
        data: {
          labels: categoryShare.map((c) => c.category),
          datasets: [{ data: categoryShare.map((c) => c.share), backgroundColor: CHART_COLORS, borderWidth: 0 }],
        },
        options: { cutout: "58%", plugins: { legend: { position: "right" }, tooltip: { callbacks: { label: (ctx) => ` ${ctx.label}: ${ctx.parsed}%` } } } },
      });
    } catch {
      // The chart is optional.
    }
  }

  // ---------- First-year projection ----------
  const DAYS_PER_MONTH = 30.44;   // conversion: per-day figures to per-month
  const DELIVERY_FEE = 25;        // parameter, matches Pricing.java
  const AVERAGE_CLUB_PRICE = 75;  // assumption: a mix of R49 and R129 members
  const FIELDS = {
    ordersPerDay: [1, 5000], averageOrder: [10, 2000], foodCost: [0, 100], paidDelivery: [0, 100],
    clubMembers: [0, 100000], growth: [0, 50], runnerPay: [0, 200], fixedCosts: [0, 10000000], startupCost: [0, 10000000],
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

  /** 12 months of figures; orders and club members grow by the monthly growth rate (compound growth). */
  function project(v) {
    const months = [];
    let cash = 0;
    for (let m = 0; m < 12; m++) {
      const factor = Math.pow(1 + v.growth / 100, m);
      const orders = Math.round(v.ordersPerDay * DAYS_PER_MONTH * factor);
      const foodProfit = orders * v.averageOrder * (1 - v.foodCost / 100);
      const clubs = Math.round(v.clubMembers * factor) * AVERAGE_CLUB_PRICE;
      const delivery = orders * (v.paidDelivery / 100) * DELIVERY_FEE;
      const income = foodProfit + clubs + delivery;
      const costs = v.fixedCosts + orders * v.runnerPay + (m === 0 ? v.startupCost : 0);
      const profit = income - costs;
      cash += profit;
      months.push({ month: m + 1, orders, foodProfit, clubs, delivery, income, costs, profit, cash });
    }
    return months;
  }

  function renderProjection() {
    const inputs = readInputs();
    if (!inputs) return;
    const months = project(inputs);
    const sum = (key) => months.reduce((total, m) => total + m[key], 0);
    const income = sum("income");
    const costs = sum("costs");
    const breakEven = months.find((m) => m.cash >= 0);

    document.getElementById("p-revenue").textContent = KC.money(income);
    document.getElementById("p-costs").textContent = KC.money(costs);
    document.getElementById("p-profit").textContent = KC.money(income - costs);
    document.getElementById("p-breakeven").textContent = breakEven ? `Month ${breakEven.month}` : "Not in year 1";
    // A benefit-cost ratio above 1 means the income outweighs the costs.
    const ratio = costs ? income / costs : 0;
    document.getElementById("p-ratio").innerHTML = `<strong>Cost-benefit:</strong> income ÷ costs = ${ratio.toFixed(2)}.
      ${ratio >= 1 ? "Kasi-Crunch earns more than it spends in year one." : "Costs are higher than income in year one, so we'd need more orders or lower costs."}
      Worst month: ${KC.money(Math.min(...months.map((m) => m.profit)))}.`;

    document.querySelector("#projection-table tbody").innerHTML = months.map((m) => `<tr>
      <td>Month ${m.month}</td><td class="num">${m.orders.toLocaleString("en-ZA")}</td><td class="num">${KC.money(m.foodProfit)}</td>
      <td class="num">${KC.money(m.clubs)}</td><td class="num">${KC.money(m.delivery)}</td><td class="num">${KC.money(m.income)}</td>
      <td class="num">${KC.money(m.costs)}</td><td class="num">${KC.money(m.profit)}</td><td class="num">${KC.money(m.cash)}</td></tr>`).join("");
    document.querySelector("#projection-table tfoot").innerHTML = `<tr><th>Year</th><th class="num">${sum("orders").toLocaleString("en-ZA")}</th>
      <th class="num">${KC.money(sum("foodProfit"))}</th><th class="num">${KC.money(sum("clubs"))}</th><th class="num">${KC.money(sum("delivery"))}</th>
      <th class="num">${KC.money(income)}</th><th class="num">${KC.money(costs)}</th><th class="num">${KC.money(income - costs)}</th><th></th></tr>`;

    // Line chart: trends over time.
    drawChart(document.getElementById("projection-chart"), {
      type: "line",
      data: {
        labels: months.map((m) => "M" + m.month),
        datasets: [
          { label: "Income", data: months.map((m) => m.income), borderColor: CHART_COLORS[0], backgroundColor: "rgba(249,115,22,0.18)", fill: true, tension: 0.35 },
          { label: "Costs", data: months.map((m) => m.costs), borderColor: CHART_COLORS[1], tension: 0.35 },
          { label: "Cash to date", data: months.map((m) => m.cash), borderColor: CHART_COLORS[2], borderDash: [6, 4], tension: 0.35 },
        ],
      },
      options: { interaction: { mode: "index", intersect: false }, scales: { y: randAxis } },
    });
  }

  document.getElementById("projection-form").addEventListener("input", renderProjection);
  document.getElementById("projection-form").addEventListener("submit", (event) => event.preventDefault());

  loadPlans();
  loadCategoryChart();
  renderProjection();
})();
