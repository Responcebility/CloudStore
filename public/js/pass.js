/*
 * File:    pass.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Crunch Pass page: the distance slider sets the monthly price
 *          (R49 + R15 per km), a comparison table shows the saving, and the
 *          customer can buy, change or cancel their pass.
 */

(async () => {
  const slider = document.getElementById("km");
  const buy = document.getElementById("buy");
  const set = (id, text) => (document.getElementById(id).textContent = text);
  const ORDERS_PER_MONTH = [4, 8, 12];

  function update() {
    const km = Number(slider.value);
    const price = KC.passPrice(km);
    set("km-label", km);
    set("km-inline", km);
    set("km-tick", km);
    document.getElementById("price").innerHTML = `${KC.money(price).replace(".00", "")}<small> / month</small>`;

    // Compare a month of orders delivered at the pass's full distance.
    const perOrder = KC.deliveryFee(km, 0);
    document.getElementById("compare").innerHTML = ORDERS_PER_MONTH.map((n) => {
      const without = n * perOrder;
      return `<tr><td>${n} deliveries at ${km} km</td><td class="num">${KC.money(without)}</td>
        <td class="num"><strong>${KC.money(price)}</strong></td></tr>`;
    }).join("");
    const breakEven = Math.ceil(price / perOrder);
    set("compare-note", `At ${km} km, one delivery costs ${KC.money(perOrder)}, so the pass pays for itself after ${breakEven} ${breakEven === 1 ? "order" : "orders"} a month.`);
  }

  slider.addEventListener("input", update);

  const user = await KC.ready;
  slider.min = KC.pricing.passMinKm;
  slider.max = KC.pricing.passMaxKm;
  if (user && user.pass) {
    slider.value = user.pass.maxKm;
    const box = document.getElementById("current-pass");
    box.hidden = false;
    box.innerHTML = `You have a <strong>${user.pass.maxKm} km Crunch Pass</strong> (${KC.money(user.pass.price)}) until ${KC.escapeHtml(user.pass.endsAt)}.
      Move the slider to change your distance, or <button class="button button-outline button-small" type="button" id="cancel">cancel it</button>`;
    document.getElementById("cancel").addEventListener("click", async () => {
      if (!confirm("Cancel your Crunch Pass? You'll pay R16/km for delivery again.")) return;
      try {
        const result = await KC.api("/api/pass/cancel", { method: "POST", data: {} });
        KC.toast(result.message);
        setTimeout(() => location.reload(), 900);
      } catch (error) {
        KC.toast(error.message, "error");
      }
    });
    buy.textContent = "Change my distance";
  }
  update();

  buy.addEventListener("click", async () => {
    if (!KC.user) {
      KC.toast("Please sign in or create an account first.", "error");
      setTimeout(() => (location.href = "login.html?next=pass.html"), 900);
      return;
    }
    const km = Number(slider.value);
    if (!confirm(`Get a ${km} km Crunch Pass for ${KC.money(KC.passPrice(km))} this month?`)) return;
    buy.disabled = true;
    try {
      const result = await KC.api("/api/pass", { method: "POST", data: { maxKm: km } });
      KC.toast(result.message);
      setTimeout(() => location.reload(), 1200);
    } catch (error) {
      KC.toast(error.message, "error");
      buy.disabled = false;
    }
  });
})();
