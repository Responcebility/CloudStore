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
    KC.toast("Please sign in with the admin account to see the dashboard.", "error");
    setTimeout(() => location.replace("login.html?next=dashboard.html"), 900);
    return;
  }
  document.getElementById("dashboard").hidden = false;
  document.getElementById("logout").addEventListener("click", KC.logout);
  const set = (id, text) => (document.getElementById(id).textContent = text);
  const rows = (pairs) => pairs.map(([label, value]) => `<tr><th>${label}</th><td class="num">${value}</td></tr>`).join("");

  async function loadStats() {
    const s = await KC.api("/api/admin/stats");
    const t = s.totals, d = s.delivery, p = s.passes, v = s.orderValue, c = s.conversions;
    set("t-revenue", KC.money(t.revenue));
    set("t-orders", `${t.orders} orders · ${t.customers} customers`);
    set("t-profit", KC.money(t.grossProfit));
    set("t-margin", `${t.grossMargin}% gross margin`);
    set("t-delivery", KC.money(t.deliveryFees));
    set("t-waived", `${KC.money(d.feesWaivedByPasses)} waived by passes`);
    set("t-pass", KC.money(p.incomeThisMonth));
    set("t-pass-note", `${p.active} active passes · average ${p.meanKm} km`);

    document.getElementById("order-stats").innerHTML = rows([
      ["Mean", KC.money(v.mean)], ["Median", KC.money(v.median)], ["Range", KC.money(v.range)], ["Smallest – largest", `${KC.money(v.min)} – ${KC.money(v.max)}`]]);
    document.getElementById("distance-stats").innerHTML = rows([
      ["Mean distance", `${d.meanDistance} km`], ["Median distance", `${d.medianDistance} km`], ["Range", `${d.distanceRange} km`],
      ["Orders delivered", `${d.deliveryShare}%`]]);
    document.getElementById("conversions").innerHTML = rows([
      ["Orders per day", c.ordersPerDay], ["Orders per month", c.ordersPerMonth], ["Revenue per day", KC.money(c.revenuePerDay)], ["Revenue per month", KC.money(c.revenuePerMonth)]]);

    drawChart(document.getElementById("trend-chart"), {
      type: "line",
      data: { labels: s.trend.map((x) => x.date.slice(5)),
        datasets: [{ label: "Revenue", data: s.trend.map((x) => x.revenue), borderColor: CHART_COLORS[0], backgroundColor: "rgba(207,52,59,0.1)", fill: true, tension: 0.35, pointRadius: 2 }] },
      options: { plugins: { legend: { display: false } }, scales: { y: randAxis } },
    });
    drawChart(document.getElementById("category-chart"), {
      type: "bar",
      data: { labels: s.byCategory.map((x) => x.category), datasets: [{ label: "Sales", data: s.byCategory.map((x) => x.sales), backgroundColor: CHART_COLORS[0], borderRadius: 4 }] },
      options: { plugins: { legend: { display: false } }, scales: { y: randAxis } },
    });
    drawChart(document.getElementById("distance-chart"), {
      type: "bar",
      data: { labels: s.distanceGroups.map((x) => x.band), datasets: [{ label: "Deliveries", data: s.distanceGroups.map((x) => x.count), backgroundColor: CHART_COLORS[1], borderRadius: 4 }] },
      options: { plugins: { legend: { display: false } }, scales: { y: { ticks: { precision: 0 } } } },
    });
    drawChart(document.getElementById("fulfilment-chart"), {
      type: "pie",
      data: { labels: ["Delivery", "Collection"], datasets: [{ data: [d.deliveries, d.collections], backgroundColor: [CHART_COLORS[0], CHART_COLORS[2]], borderWidth: 2, borderColor: "#fff" }] },
      options: { plugins: { legend: { position: "right" } } },
    });

    document.getElementById("top-items").innerHTML = s.topItems.map((i) =>
      `<tr><td>${KC.escapeHtml(i.name)}</td><td class="num">${i.units}</td><td class="num">${KC.money(i.sales)}</td></tr>`).join("");
    document.getElementById("margins").innerHTML = s.byCategory.map((x) =>
      `<tr><td>${KC.escapeHtml(x.category)}</td><td class="num">${x.share}%</td><td class="num">${x.margin}%</td></tr>`).join("");
  }

  async function loadOrders() {
    const { orders, statuses } = await KC.api("/api/admin/orders");
    document.getElementById("orders").innerHTML = orders.map((o) => `
      <tr><td>#${o.id}</td><td>${KC.escapeHtml(o.customer)}</td><td>${KC.escapeHtml(o.createdAt)}</td>
        <td>${o.fulfilment === "delivery" ? `Delivery · ${o.distanceKm} km` : "Collection"}</td><td class="num">${KC.money(o.total)}</td>
        <td><label class="visually-hidden" for="status-${o.id}">Status of order ${o.id}</label>
          <select class="input" id="status-${o.id}" data-order="${o.id}" style="min-height:38px;padding:6px 10px;min-width:150px">
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
      ? messages.map((m) => `<div style="padding:12px;border:1px solid var(--line);border-radius:6px">
          <div class="row between"><strong>${KC.escapeHtml(m.subject)}</strong><span class="muted small">${KC.escapeHtml(m.createdAt)}</span></div>
          <div class="muted small">${KC.escapeHtml(m.name)} · ${KC.escapeHtml(m.email)}</div>
          <p style="margin:6px 0 0;white-space:pre-wrap">${KC.escapeHtml(m.message)}</p></div>`).join("")
      : `<p class="muted">No messages yet.</p>`;
  }

  // ---------- Photos ----------
  /**
   * Reads an image file the admin picked and shrinks it in the browser
   * (longest side 1200 px, JPEG) so uploads stay small and fast.
   */
  function readPhoto(file) {
    return new Promise((resolve, reject) => {
      if (!file) return reject(new Error("Please choose a photo."));
      if (!/^image\/(jpeg|png|webp)$/.test(file.type)) return reject(new Error("Only JPG, PNG or WebP photos can be uploaded."));
      if (file.size > 10 * 1024 * 1024) return reject(new Error("That photo is too large. Please choose one under 10 MB."));
      const reader = new FileReader();
      reader.onerror = () => reject(new Error("That photo could not be read. Please try another image."));
      reader.onload = () => {
        const img = new Image();
        img.onerror = () => reject(new Error("That file is not a valid image."));
        img.onload = () => {
          const scale = Math.min(1, 1200 / Math.max(img.width, img.height));
          const canvas = document.createElement("canvas");
          canvas.width = Math.round(img.width * scale);
          canvas.height = Math.round(img.height * scale);
          const ctx = canvas.getContext("2d");
          ctx.fillStyle = "#fff";
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
          resolve(canvas.toDataURL("image/jpeg", 0.85));
        };
        img.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  let menuItems = [];
  async function loadPhotoList() {
    ({ products: menuItems } = await KC.api("/api/products"));
    document.getElementById("photo-list").innerHTML = menuItems.map((p) => `
      <div class="photo-item">
        <img src="${KC.escapeHtml(p.image)}" alt="${KC.escapeHtml(p.name)}">
        <div><strong>${KC.escapeHtml(p.name)}</strong><span class="muted small">${KC.escapeHtml(p.category)} · ${KC.money(p.price)}</span></div>
        <button class="button button-outline button-small" type="button" data-photo="${p.id}">Change photo</button>
      </div>`).join("");
  }

  const replaceInput = document.getElementById("replace-photo");
  let replacingId = null;
  document.getElementById("photo-list").addEventListener("click", (event) => {
    const button = event.target.closest("[data-photo]");
    if (!button) return;
    replacingId = button.dataset.photo;
    replaceInput.value = "";
    replaceInput.click();
  });
  replaceInput.addEventListener("change", async () => {
    if (!replaceInput.files[0] || !replacingId) return;
    try {
      const imageData = await readPhoto(replaceInput.files[0]);
      const result = await KC.api("/api/products/photo", { method: "POST", data: { id: replacingId, imageData } });
      KC.toast(result.message);
      loadPhotoList();
    } catch (error) {
      KC.toast(error.message, "error");
    }
  });

  // ---------- Add a menu item ----------
  const form = document.getElementById("item-form");
  const { categories } = await KC.api("/api/products/categories");
  const { products } = await KC.api("/api/products");
  const photoInput = document.getElementById("i-photo");
  const preview = document.getElementById("i-preview");
  let uploadedPhoto = null;
  photoInput.addEventListener("change", async () => {
    uploadedPhoto = null;
    preview.hidden = true;
    if (!photoInput.files[0]) return;
    try {
      uploadedPhoto = await readPhoto(photoInput.files[0]);
      preview.src = uploadedPhoto;
      preview.hidden = false;
      showFieldError(photoInput, "");
    } catch (error) {
      photoInput.value = "";
      showFieldError(photoInput, error.message);
    }
  });
  form.querySelector("[name=category]").innerHTML = categories.map((c) => `<option>${KC.escapeHtml(c)}</option>`).join("");
  // One option per photo, named after the first menu item that uses it.
  const photos = new Map();
  products.forEach((p) => {
    const id = p.image.replace("img/menu/", "").replace(".jpg", "");
    if (!photos.has(id)) photos.set(id, p.name);
  });
  form.querySelector("[name=image]").innerHTML = [...photos].map(([id, name]) => `<option value="${id}">Like "${KC.escapeHtml(name)}"</option>`).join("");

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
      const data = Object.fromEntries(new FormData(form));
      if (uploadedPhoto) data.imageData = uploadedPhoto;
      const result = await KC.api("/api/products", { method: "POST", data });
      KC.toast(result.message);
      form.reset();
      uploadedPhoto = null;
      preview.hidden = true;
      loadStats();
      loadPhotoList();
    } catch (error) {
      KC.toast(error.message, "error");
    }
  });

  try {
    await Promise.all([loadStats(), loadOrders(), loadMessages(), loadPhotoList()]);
  } catch (error) {
    KC.toast(error.message, "error");
  }
})();
