/*
 * File:    app.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Shared code for every page: header and footer, login state, the
 *          cart (kept in the browser), API calls, toasts and the Crunchy
 *          ordering assistant.
 */

const KC = (() => {
  const NAV = [
    { href: "index.html", label: "Home", page: "home" },
    { href: "menu.html", label: "Menu", page: "menu" },
    { href: "about.html", label: "About", page: "about" },
    { href: "persona.html", label: "Customer Persona", page: "persona" },
    { href: "revenue.html", label: "Revenue Model", page: "revenue" },
    { href: "research.html", label: "Research", page: "research" },
    { href: "contact.html", label: "Contact", page: "contact" },
  ];
  const CART_KEY = "kc_cart";
  let currentUser = null;
  let pricing = { deliveryFee: 25, freeDeliveryThreshold: 250, maxQuantityPerItem: 20 };

  // ---------- Helpers ----------
  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  function money(amount) {
    return "R" + Number(amount || 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /** Calls the Java API. Sends form-encoded data and returns the JSON reply, or throws with a friendly message. */
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
    item.className = `toast glass ${type}`;
    item.textContent = message;
    area.appendChild(item);
    setTimeout(() => item.remove(), 4000);
  }

  // ---------- Cart (kept in the browser; the server re-checks every price at checkout) ----------
  function readCart() {
    try {
      return JSON.parse(localStorage.getItem(CART_KEY)) || {};
    } catch {
      return {};
    }
  }

  function writeCart(cart) {
    try {
      localStorage.setItem(CART_KEY, JSON.stringify(cart));
    } catch {
      toast("Your browser blocked saving the cart. Please allow site storage.", "error");
    }
    updateCartCount();
  }

  const cartItems = () => Object.values(readCart());

  function limitFor(item) {
    return Math.min(pricing.maxQuantityPerItem, item.stock ?? pricing.maxQuantityPerItem);
  }

  function addToCart(product) {
    const cart = readCart();
    const next = (cart[product.id]?.qty || 0) + 1;
    if (next > limitFor(product)) {
      toast(`You can add at most ${limitFor(product)} × ${product.name}.`, "error");
      return;
    }
    cart[product.id] = { id: product.id, name: product.name, price: product.price, emoji: product.emoji, stock: product.stock, qty: next };
    writeCart(cart);
    toast(`${product.emoji} ${product.name} added to your order.`);
  }

  function setQuantity(id, qty) {
    const cart = readCart();
    if (!cart[id]) return;
    if (qty <= 0) delete cart[id];
    else cart[id].qty = Math.min(qty, limitFor(cart[id]));
    writeCart(cart);
  }

  const clearCart = () => writeCart({});

  /** Same rules as Pricing.java: club discount, then free delivery for clubs or orders over the threshold. */
  function cartTotals(club) {
    const round = (n) => Math.round(n * 100) / 100;
    const subtotal = round(cartItems().reduce((sum, item) => sum + item.price * item.qty, 0));
    const discount = club ? round(subtotal * club.discountRate) : 0;
    const delivery = subtotal === 0 || (club && club.freeDelivery) || subtotal > pricing.freeDeliveryThreshold ? 0 : pricing.deliveryFee;
    return { subtotal, discount, delivery, total: round(subtotal - discount + delivery) };
  }

  function updateCartCount() {
    const count = cartItems().reduce((sum, item) => sum + item.qty, 0);
    document.querySelectorAll(".cart-count").forEach((badge) => {
      badge.textContent = count || "";
      badge.dataset.count = count;
    });
  }

  // ---------- Layout ----------
  const brand = `<span class="brand-mark" aria-hidden="true">🍗</span><span class="brand-name">Kasi-<span>Crunch</span></span>`;

  function renderShell() {
    const page = document.body.dataset.page;
    document.body.insertAdjacentHTML("afterbegin", `
      <a class="visually-hidden skip-link" href="#main">Skip to content</a>
      <header class="site-header container">
        <nav class="nav glass" aria-label="Main">
          <a class="brand" href="index.html" aria-label="Kasi-Crunch home">${brand}</a>
          <button class="btn icon-btn menu-toggle" type="button" aria-label="Open menu" aria-expanded="false">☰</button>
          <ul class="nav-links">
            ${NAV.map((item) => `<li><a href="${item.href}" ${item.page === page ? 'class="active" aria-current="page"' : ""}>${item.label}</a></li>`).join("")}
          </ul>
          <div class="nav-actions">
            <a class="btn icon-btn cart-link" href="cart.html" aria-label="Your order">🛒<span class="cart-count"></span></a>
            <span class="auth-slot"><a class="btn btn-primary btn-sm" href="login.html">Log in</a></span>
          </div>
        </nav>
      </header>`);

    document.body.insertAdjacentHTML("beforeend", `
      <footer class="site-footer container">
        <div class="footer-inner glass">
          <div>
            <a class="brand" href="index.html">${brand}</a>
            <p class="faint" style="margin:8px 0 0">Crunch outside, juicy inside.</p>
          </div>
          <nav class="footer-links" aria-label="Footer">
            <a href="menu.html">Menu</a><a href="revenue.html#clubs">Clubs</a><a href="cart.html">Your order</a>
            <a href="account.html">My account</a><a href="contact.html">Contact</a>
          </nav>
        </div>
        <p class="footer-note">© 2026 Kasi-Crunch · JGA 2026 prototype, Faculty of ICT · Demo only, no real payments.
          Background photo by <a href="https://unsplash.com/@tigerrulezzz" target="_blank" rel="noopener">Brian Chan</a> on
          <a href="https://unsplash.com/photos/NbXjZomyNEM" target="_blank" rel="noopener">Unsplash</a>.</p>
      </footer>`);

    const toggle = document.querySelector(".menu-toggle");
    const links = document.querySelector(".nav-links");
    toggle.addEventListener("click", () => {
      const open = links.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
      toggle.textContent = open ? "✕" : "☰";
    });
    updateCartCount();
  }

  function renderAuth() {
    const slot = document.querySelector(".auth-slot");
    if (!currentUser) {
      slot.innerHTML = `<a class="btn btn-primary btn-sm" href="login.html">Log in</a>`;
      return;
    }
    const firstName = escapeHtml(currentUser.fullName.split(" ")[0]);
    slot.innerHTML = `
      <a class="btn btn-sm" href="${currentUser.role === "admin" ? "dashboard.html" : "account.html"}">${currentUser.role === "admin" ? "📊 Dashboard" : "👤 " + firstName}</a>
      <button class="btn btn-sm" type="button" data-logout>Log out</button>`;
    slot.querySelector("[data-logout]").addEventListener("click", async () => {
      await api("/api/auth/logout", { method: "POST", data: {} }).catch(() => {});
      toast("You've been logged out. See you soon!");
      setTimeout(() => (window.location.href = "index.html"), 500);
    });
  }

  // ---------- Menu item card ----------
  function itemCard(product) {
    const soldOut = product.stock <= 0;
    return `
      <article class="item glass">
        <div class="item-art" aria-hidden="true">${escapeHtml(product.emoji)}
          <div class="item-tags">
            ${product.popular ? `<span class="tag tag-orange">🔥 Popular</span>` : ""}
            ${product.spicy ? `<span class="tag tag-hot">🌶️ Spicy</span>` : ""}
          </div>
        </div>
        <h3>${escapeHtml(product.name)}</h3>
        <p class="desc">${escapeHtml(product.description)}</p>
        <div class="item-foot">
          <span class="price">${money(product.price)}</span>
          ${soldOut ? `<span class="tag tag-soft">Sold out today</span>`
            : `<button class="btn btn-primary btn-sm" type="button" data-add="${product.id}" aria-label="Add ${escapeHtml(product.name)} to order">+ Add</button>`}
        </div>
      </article>`;
  }

  /** Wires "+ Add" buttons inside a container to a product list (or a function returning it). */
  function bindAddButtons(container, products) {
    container.addEventListener("click", (event) => {
      const button = event.target.closest("[data-add]");
      if (!button) return;
      const list = typeof products === "function" ? products() : products;
      const product = list.find((p) => String(p.id) === button.dataset.add);
      if (product) addToCart(product);
    });
  }

  // ---------- Crunchy, the ordering assistant ----------
  function renderAssistant() {
    document.body.insertAdjacentHTML("beforeend", `
      <button class="btn btn-primary chat-fab" type="button" aria-label="Chat to Crunchy, our ordering assistant" aria-expanded="false">💬</button>
      <section class="chat-panel glass" hidden aria-label="Crunchy assistant">
        <div class="chat-head">
          <div class="avatar" aria-hidden="true">🐔</div>
          <div><strong>Crunchy</strong><small>Ordering assistant</small></div>
          <button class="btn icon-btn btn-sm" type="button" data-close style="margin-left:auto;width:38px" aria-label="Close assistant">✕</button>
        </div>
        <div class="chat-log" aria-live="polite"></div>
        <div class="chat-suggestions">
          ${["Something spicy", "Delivery fee?", "Student deal", "Track my order"].map((s) => `<button class="chip" type="button" data-suggest>${s}</button>`).join("")}
        </div>
        <form class="chat-form">
          <label class="visually-hidden" for="chat-input">Ask Crunchy</label>
          <input id="chat-input" class="input" maxlength="300" placeholder="Ask about the menu…" autocomplete="off">
          <button class="btn btn-primary" type="submit">Send</button>
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
        row.innerHTML = `<span class="emoji">${escapeHtml(product.emoji)}</span><div><strong>${escapeHtml(product.name)}</strong><br>${money(product.price)}</div>`;
        const add = document.createElement("button");
        add.className = "btn btn-sm";
        add.type = "button";
        add.textContent = "+ Add";
        add.disabled = product.stock <= 0;
        add.addEventListener("click", () => addToCart(product));
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
        addMessage("Sharp sharp! I'm Crunchy 🐔 Ask me about the menu, delivery, the clubs or your latest order.", "bot");
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
    panel.querySelector("[data-close]").addEventListener("click", () => setOpen(false));
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
    api, money, escapeHtml, toast, ready, itemCard, bindAddButtons,
    cartItems, addToCart, setQuantity, clearCart, cartTotals,
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
