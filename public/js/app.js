/*
 * File:    app.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Shared code for every page: header and footer, login state, the
 *          basket (kept in the browser), delivery and Crunch Pass maths for
 *          instant feedback, API calls, toasts and the Crunchy assistant.
 */

const KC = (() => {
  const NAV = [
    { href: "menu.html", label: "Menu", page: "menu" },
    { href: "pass.html", label: "Crunch Pass", page: "pass" },
    { href: "about.html", label: "Our story", page: "about" },
    { href: "contact.html", label: "Contact", page: "contact" },
  ];
  const BASKET_KEY = "kc_basket";
  let currentUser = null;
  let pricing = { deliveryRatePerKm: 16, maxDeliveryKm: 30, passBasePrice: 49, passRatePerKm: 15, passMinKm: 1, passMaxKm: 20, passDays: 30, maxQuantityPerItem: 20 };

  // ---------- Helpers ----------
  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  /** R82, R1 250 or R74.90: cents only when there are some. */
  function money(amount) {
    const value = Math.round(Number(amount || 0) * 100) / 100;
    const whole = Math.trunc(Math.abs(value)).toLocaleString("en-ZA");
    const cents = Math.round(Math.abs(value) * 100) % 100;
    return (value < 0 ? "−R" : "R") + whole + (cents ? "." + String(cents).padStart(2, "0") : "");
  }

  const round2 = (n) => Math.round(n * 100) / 100;

  /** Calls the Java API with form-encoded data; returns the JSON reply or throws a friendly message. */
  async function api(path, { method = "GET", data } = {}) {
    const options = { method, headers: {}, credentials: "same-origin" };
    if (data) {
      options.headers["Content-Type"] = "application/x-www-form-urlencoded";
      options.body = new URLSearchParams(data).toString();
    }
    let response;
    try {
      response = await fetch(path, options);
    } catch {
      throw new Error("We couldn't reach Kasi-Crunch. Please check your connection and try again.");
    }
    const json = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(json.error || "Something went wrong. Please try again.");
    return json;
  }

  function toast(message, type = "success") {
    let area = document.querySelector(".toast-area");
    if (!area) {
      area = document.createElement("div");
      area.className = "toast-area";
      area.setAttribute("role", "status");
      area.setAttribute("aria-live", "polite");
      document.body.appendChild(area);
    }
    const item = document.createElement("div");
    item.className = `toast ${type}`;
    item.textContent = message;
    area.appendChild(item);
    setTimeout(() => item.remove(), 3800);
  }

  // ---------- Pricing (same formulas as Pricing.java; the server has the final say) ----------
  /** R16 for every km beyond what the Crunch Pass covers (passKm = 0 without a pass). */
  const deliveryFee = (distanceKm, passKm = 0) => round2(Math.max(0, distanceKm - passKm) * pricing.deliveryRatePerKm);
  /** One month of free delivery up to maxKm. */
  const passPrice = (maxKm) => round2(pricing.passBasePrice + pricing.passRatePerKm * maxKm);

  // ---------- Basket (kept in the browser) ----------
  function readBasket() {
    try {
      return JSON.parse(localStorage.getItem(BASKET_KEY)) || {};
    } catch {
      return {};
    }
  }

  function writeBasket(basket) {
    try {
      localStorage.setItem(BASKET_KEY, JSON.stringify(basket));
    } catch {
      toast("Your browser blocked saving the basket. Please allow site storage.", "error");
    }
    updateBasketCount();
  }

  const basketItems = () => Object.values(readBasket());
  const limitFor = (item) => Math.min(pricing.maxQuantityPerItem, item.stock ?? pricing.maxQuantityPerItem);

  /** Sends a visitor to sign in, then brings them back to this page. */
  function requireSignIn(message) {
    toast(message, "error");
    const here = location.pathname.split("/").pop() || "index.html";
    setTimeout(() => (location.href = "login.html?next=" + encodeURIComponent(here)), 900);
  }

  function addToBasket(product) {
    // Ordering needs an account, so the basket is only for signed-in customers.
    if (!currentUser) {
      requireSignIn("Please sign in or create an account to start your order.");
      return;
    }
    const basket = readBasket();
    const next = (basket[product.id]?.qty || 0) + 1;
    if (next > limitFor(product)) {
      toast(`You can add at most ${limitFor(product)} × ${product.name}.`, "error");
      return;
    }
    basket[product.id] = { id: product.id, name: product.name, price: product.price, image: product.image, stock: product.stock, qty: next };
    writeBasket(basket);
    toast(`${product.name} added to your basket.`);
  }

  function setQuantity(id, qty) {
    const basket = readBasket();
    if (!basket[id]) return;
    if (qty <= 0) delete basket[id];
    else basket[id].qty = Math.min(qty, limitFor(basket[id]));
    writeBasket(basket);
  }

  const clearBasket = () => writeBasket({});
  const subtotal = () => round2(basketItems().reduce((sum, item) => sum + item.price * item.qty, 0));

  function updateBasketCount() {
    const count = basketItems().reduce((sum, item) => sum + item.qty, 0);
    document.querySelectorAll(".basket-count").forEach((badge) => (badge.textContent = count));
  }

  // ---------- Layout ----------
  const brand = (mark = "KC") => `<span class="brand-mark">${mark}</span>
    <span>KASI<span class="brand-dash"> - </span>CRUNCH<small>CRUNCH OUTSIDE, JUICY INSIDE</small></span>`;

  function renderShell() {
    const page = document.body.dataset.page;
    document.body.insertAdjacentHTML("afterbegin", `
      <a class="visually-hidden skip-link" href="#main">Skip to content</a>
      <div class="announcement">Made fresh in the kasi · Delivery R16/km · Free with your Crunch Pass</div>
      <header class="site-header">
        <div class="container header-inner">
          <a class="brand" href="index.html" aria-label="Kasi-Crunch home">${brand()}</a>
          <nav class="main-nav" aria-label="Main">
            ${NAV.map((item) => `<a href="${item.href}" ${item.page === page ? 'class="active" aria-current="page"' : ""}>${item.label}</a>`).join("")}
          </nav>
          <div class="header-actions">
            <span class="auth-slot"><a class="button button-outline button-small account-link" href="login.html">Sign in</a></span>
            <a class="basket-link" href="cart.html" aria-label="Basket">🛒 <span class="basket-word">Basket</span> <span class="basket-count">0</span></a>
            <button class="mobile-menu" type="button" aria-label="Open menu" aria-expanded="false">☰</button>
          </div>
        </div>
      </header>`);

    document.body.insertAdjacentHTML("beforeend", `
      <footer class="site-footer">
        <div class="container">
          <div class="footer-top">
            <a class="brand" href="index.html">${brand("K")}</a>
            <nav class="footer-links" aria-label="Footer">
              <a href="menu.html">Menu</a><a href="pass.html">Crunch Pass</a><a href="about.html">Our story</a>
              <a href="persona.html">Customer persona</a><a href="revenue.html">Revenue model</a><a href="research.html">Research</a>
              <a href="contact.html">Contact</a><a href="account.html">My account</a>
            </nav>
          </div>
          <p class="footer-note">© 2026 Kasi-Crunch · JGA 2026 prototype, Faculty of ICT · Demo only: no real payments are taken.
            Food photos from <a href="https://unsplash.com" target="_blank" rel="noopener">Unsplash</a> (free licence); hero photo by
            <a href="https://unsplash.com/photos/NbXjZomyNEM" target="_blank" rel="noopener">Brian Chan</a>.</p>
        </div>
      </footer>`);

    const toggle = document.querySelector(".mobile-menu");
    const nav = document.querySelector(".main-nav");
    toggle.addEventListener("click", () => {
      const open = nav.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
      toggle.textContent = open ? "✕" : "☰";
    });
    updateBasketCount();
  }

  function renderAuth() {
    const slot = document.querySelector(".auth-slot");
    const nav = document.querySelector(".main-nav");
    if (!currentUser) {
      nav.insertAdjacentHTML("beforeend", `<a class="nav-only-mobile" href="login.html">Sign in</a>`);
      return;
    }
    const admin = currentUser.role === "admin";
    const href = admin ? "dashboard.html" : "account.html";
    const label = admin ? "Dashboard" : "Hi, " + currentUser.fullName.split(" ")[0];
    slot.innerHTML = `<a class="button button-outline button-small account-link" href="${href}">${escapeHtml(label)}</a>
      <button class="button button-ghost button-small account-link" type="button" data-logout>Sign out</button>`;
    nav.insertAdjacentHTML("beforeend", `<a class="nav-only-mobile" href="${href}">${admin ? "Dashboard" : "My account"}</a>
      <a class="nav-only-mobile" href="#" data-logout>Sign out</a>`);
    document.querySelectorAll("[data-logout]").forEach((el) => el.addEventListener("click", (event) => {
      event.preventDefault();
      logout();
    }));
  }

  async function logout() {
    await api("/api/auth/logout", { method: "POST", data: {} }).catch(() => {});
    clearBasket();
    toast("You've been signed out. See you soon!");
    setTimeout(() => (window.location.href = "index.html"), 600);
  }

  // ---------- Menu card ----------
  function itemCard(product) {
    const soldOut = product.stock <= 0;
    return `
      <article class="menu-card">
        <div class="menu-image">
          <img src="${escapeHtml(product.image)}" alt="${escapeHtml(product.name)}" loading="lazy">
          <div class="menu-tags">
            <span class="tag">${escapeHtml(product.category)}</span>
            ${product.popular ? `<span class="tag tag-orange">Popular</span>` : ""}
            ${product.spicy ? `<span class="tag tag-red">Spicy</span>` : ""}
          </div>
        </div>
        <div class="menu-body">
          <div class="menu-title"><h3>${escapeHtml(product.name)}</h3><strong>${money(product.price)}</strong></div>
          <p>${escapeHtml(product.description)}</p>
          <button class="add-button" type="button" data-add="${product.id}" ${soldOut ? "disabled" : ""}
            aria-label="Add ${escapeHtml(product.name)} to basket">${soldOut ? "SOLD OUT TODAY" : "ADD TO BASKET"} <span aria-hidden="true">${soldOut ? "" : "+"}</span></button>
        </div>
      </article>`;
  }

  /** Wires "Add to basket" buttons in a container to a product list (or a function returning it). */
  function bindAddButtons(container, products) {
    container.addEventListener("click", (event) => {
      const button = event.target.closest("[data-add]");
      if (!button) return;
      const list = typeof products === "function" ? products() : products;
      const product = list.find((p) => String(p.id) === button.dataset.add);
      if (product) addToBasket(product);
    });
  }

  // ---------- Crunchy, the ordering assistant ----------
  function renderAssistant() {
    document.body.insertAdjacentHTML("beforeend", `
      <button class="chat-fab" type="button" aria-label="Chat to Crunchy, our ordering assistant" aria-expanded="false">💬</button>
      <section class="chat-panel" hidden aria-label="Crunchy assistant">
        <div class="chat-head">
          <div class="avatar" aria-hidden="true">🐔</div>
          <div><strong>Crunchy</strong><small>Ordering assistant</small></div>
          <button class="chat-close" type="button" aria-label="Close assistant">✕</button>
        </div>
        <div class="chat-suggestions">
          ${["Something spicy", "Delivery cost?", "Crunch Pass", "Track my order"].map((s) => `<button class="category-chip" type="button" data-suggest>${s}</button>`).join("")}
        </div>
        <div class="chat-log" aria-live="polite"></div>
        <form class="chat-form">
          <label class="visually-hidden" for="chat-input">Ask Crunchy</label>
          <input id="chat-input" class="input" maxlength="300" placeholder="Ask about the menu…" autocomplete="off">
          <button class="button button-primary button-small" type="submit">Send</button>
        </form>
      </section>`);

    const fab = document.querySelector(".chat-fab");
    const panel = document.querySelector(".chat-panel");
    const log = panel.querySelector(".chat-log");
    const input = panel.querySelector("#chat-input");
    let greeted = false;

    function addMessage(text, who, products = []) {
      const bubble = document.createElement("div");
      bubble.className = `msg ${who}`;
      bubble.textContent = text;
      products.forEach((product) => {
        const row = document.createElement("div");
        row.className = "msg-product";
        row.innerHTML = `<img src="${escapeHtml(product.image)}" alt=""><div><strong>${escapeHtml(product.name)}</strong><br>${money(product.price)}</div>`;
        const add = document.createElement("button");
        add.className = "button button-outline button-small";
        add.type = "button";
        add.textContent = "Add";
        add.disabled = product.stock <= 0;
        add.addEventListener("click", () => addToBasket(product));
        row.appendChild(add);
        bubble.appendChild(row);
      });
      log.appendChild(bubble);
      log.scrollTop = log.scrollHeight;
    }

    function setOpen(open) {
      panel.hidden = !open;
      fab.setAttribute("aria-expanded", String(open));
      fab.textContent = open ? "✕" : "💬";
      if (open && !greeted) {
        addMessage("Sharp sharp! I'm Crunchy 🐔 Ask me about the menu, delivery, the Crunch Pass or your latest order.", "bot");
        greeted = true;
      }
      if (open) input.focus();
    }

    async function ask(question) {
      const text = question.trim();
      if (!text) return;
      addMessage(text, "user");
      input.value = "";
      try {
        const answer = await api("/api/assistant", { method: "POST", data: { message: text } });
        addMessage(answer.reply, "bot", answer.products || []);
      } catch (error) {
        addMessage(error.message, "bot");
      }
    }

    fab.addEventListener("click", () => setOpen(panel.hidden));
    panel.querySelector(".chat-close").addEventListener("click", () => setOpen(false));
    panel.querySelector(".chat-form").addEventListener("submit", (event) => {
      event.preventDefault();
      ask(input.value);
    });
    panel.querySelectorAll("[data-suggest]").forEach((chip) => chip.addEventListener("click", () => ask(chip.textContent)));
  }

  // ---------- Start-up ----------
  const ready = (async () => {
    renderShell();
    renderAssistant();
    const [me, prices] = await Promise.allSettled([api("/api/auth/me"), api("/api/pricing")]);
    if (me.status === "fulfilled") currentUser = me.value.user;
    if (prices.status === "fulfilled") pricing = prices.value;
    renderAuth();
    return currentUser;
  })();

  return {
    api, money, escapeHtml, toast, ready, itemCard, bindAddButtons, logout, round2, requireSignIn,
    basketItems, addToBasket, setQuantity, clearBasket, subtotal, deliveryFee, passPrice,
    get user() { return currentUser; },
    get pricing() { return pricing; },
  };
})();

/** Shows a field's error under it and marks it invalid; returns false when there was an error. */
function showFieldError(input, message) {
  const holder = input.closest(".field")?.querySelector(".field-error");
  input.classList.toggle("invalid", Boolean(message));
  input.setAttribute("aria-invalid", message ? "true" : "false");
  if (holder) holder.textContent = message || "";
  return !message;
}
