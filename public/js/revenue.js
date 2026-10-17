/*
 * File:    revenue.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Revenue Model page: Crunch Pass price table, sales-by-category
 *          chart, and the first-year cash-flow / cost-benefit projection.
 */

(async () => {
  await KC.ready;

  // ---------- Crunch Pass price table ----------
  document.getElementById("pass-table").innerHTML = [1, 3, 5, 8, 10, 15, 20].map((km) =>
    `<tr><td>${km} km</td><td class="num"><strong>${KC.money(KC.passPrice(km))}</strong></td><td class="num">${KC.money(KC.deliveryFee(km, 0))}</td></tr>`).join("");

  // ---------- Sales by category (doughnut: parts of a whole) ----------
  try {
    const { categoryShare } = await KC.api("/api/insights");
    drawChart(document.getElementById("category-chart"), {
      type: "doughnut",
      data: { labels: categoryShare.map((c) => c.category),
        datasets: [{ data: categoryShare.map((c) => c.share), backgroundColor: CHART_COLORS, borderWidth: 2, borderColor: "#fff" }] },
      options: { cutout: "58%", plugins: { legend: { position: "right" }, tooltip: { callbacks: { label: (ctx) => ` ${ctx.label}: ${ctx.parsed}%` } } } },
    });
  } catch {
    // The chart is optional.
  }

  // ---------- First-year projection ----------
  const DAYS_PER_MONTH = 30.44; // conversion: per-day figures to per-month
  const FIELDS = {
    ordersPerDay: [1, 5000], averageOrder: [10, 2000], foodCost: [0, 100], deliveryShare: [0, 100], averageKm: [0.1, 30],
    passOrders: [0, 100], passMembers: [0, 100000], passKm: [1, 20], growth: [0, 50], runnerPay: [0, 100],
    fixedCosts: [0, 10000000], startupCost: [0, 10000000],
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

  /** 12 months; orders and pass members grow by the monthly growth rate (compound growth). */
  function project(v) {
    const months = [];
    let cash = 0;
    for (let m = 0; m < 12; m++) {
      const factor = Math.pow(1 + v.growth / 100, m);
      const orders = Math.round(v.ordersPerDay * DAYS_PER_MONTH * factor);
      const deliveries = Math.round(orders * (v.deliveryShare / 100));
      const foodProfit = orders * v.averageOrder * (1 - v.foodCost / 100);
      // Pass holders' deliveries are free; everyone else pays R16 per km.
      const paidDeliveries = deliveries * (1 - v.passOrders / 100);
      const deliveryFees = paidDeliveries * KC.deliveryFee(v.averageKm, 0);
      const passIncome = Math.round(v.passMembers * factor) * KC.passPrice(Math.round(v.passKm));
      const income = foodProfit + deliveryFees + passIncome;
      const costs = v.fixedCosts + deliveries * v.averageKm * v.runnerPay + (m === 0 ? v.startupCost : 0);
      const profit = income - costs;
      cash += profit;
      months.push({ month: m + 1, orders, foodProfit, deliveryFees, passIncome, income, costs, profit, cash });
    }
    return months;
  }

  function render() {
    const inputs = readInputs();
    if (!inputs) return;
    const months = project(inputs);
    const sum = (key) => months.reduce((total, m) => total + m[key], 0);
    const income = sum("income");
    const costs = sum("costs");
    const breakEven = months.find((m) => m.cash >= 0);
    const set = (id, text) => (document.getElementById(id).textContent = text);

    set("p-income", KC.money(income));
    set("p-costs", KC.money(costs));
    set("p-profit", KC.money(income - costs));
    set("p-breakeven", breakEven ? `Month ${breakEven.month}` : "Not in year 1");
    // A benefit-cost ratio above 1 means income outweighs costs.
    const ratio = costs ? income / costs : 0;
    document.getElementById("p-ratio").innerHTML = `<strong>Cost-benefit:</strong> income ÷ costs = ${ratio.toFixed(2)}.
      ${ratio >= 1 ? "Kasi-Crunch earns more than it spends in year one." : "Costs are higher than income in year one, so we'd need more orders or lower costs."}
      Worst month: ${KC.money(Math.min(...months.map((m) => m.profit)))}.`;

    document.querySelector("#projection-table tbody").innerHTML = months.map((m) => `<tr>
      <td>Month ${m.month}</td><td class="num">${m.orders.toLocaleString("en-ZA")}</td><td class="num">${KC.money(m.foodProfit)}</td>
      <td class="num">${KC.money(m.deliveryFees)}</td><td class="num">${KC.money(m.passIncome)}</td><td class="num">${KC.money(m.income)}</td>
      <td class="num">${KC.money(m.costs)}</td><td class="num">${KC.money(m.profit)}</td><td class="num">${KC.money(m.cash)}</td></tr>`).join("");
    document.querySelector("#projection-table tfoot").innerHTML = `<tr><th>Year</th><th class="num">${sum("orders").toLocaleString("en-ZA")}</th>
      <th class="num">${KC.money(sum("foodProfit"))}</th><th class="num">${KC.money(sum("deliveryFees"))}</th><th class="num">${KC.money(sum("passIncome"))}</th>
      <th class="num">${KC.money(income)}</th><th class="num">${KC.money(costs)}</th><th class="num">${KC.money(income - costs)}</th><th></th></tr>`;

    // Line chart: trends over time.
    drawChart(document.getElementById("projection-chart"), {
      type: "line",
      data: {
        labels: months.map((m) => "M" + m.month),
        datasets: [
          { label: "Income", data: months.map((m) => m.income), borderColor: CHART_COLORS[0], backgroundColor: "rgba(207,52,59,0.1)", fill: true, tension: 0.35 },
          { label: "Costs", data: months.map((m) => m.costs), borderColor: CHART_COLORS[2], tension: 0.35 },
          { label: "Cash to date", data: months.map((m) => m.cash), borderColor: CHART_COLORS[1], borderDash: [6, 4], tension: 0.35 },
        ],
      },
      options: { interaction: { mode: "index", intersect: false }, scales: { y: randAxis } },
    });
  }

  document.getElementById("projection-form").addEventListener("input", render);
  document.getElementById("projection-form").addEventListener("submit", (event) => event.preventDefault());
  render();
})();
