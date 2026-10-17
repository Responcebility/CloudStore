/*
 * File:    app.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Shared code for every page: header and footer, login state, the
 *          cart (kept in the browser), API calls, toasts and the CloudBot
 *          chat assistant.
 */

const CS = (() => {
  const NAV = [
    { href: "index.html", label: "Home", page: "home" },
    { href: "shop.html", label: "Shop", page: "shop" },
    { href: "about.html", label: "About", page: "about" },
    { href: "persona.html", label: "Customer Persona", page: "persona" },
    { href: "revenue.html", label: "Revenue Model", page: "revenue" },
    { href: "research.html", label: "Research", page: "research" },
    { href: "contact.html", label: "Contact", page: "contact" },
  ];
  const CART_KEY = "cs_cart";
  let currentUser = null;
  let pricing = { deliveryFee: 35, freeDeliveryThreshold: 500, maxQuantityPerItem: 20 };

  // ---------- Helpers ----------
  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  function money(amount) {
    return "R " + Number(amount || 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
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
      throw new Error("We could not reach CloudStore. Please check your connection and try again.");
    }
    const json = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(json.error || "Something went wrong. Please try again.");
      error.status = response.status;
      throw error;
    }
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
    item.className = `toast glass glass-strong ${type}`;
    item.textContent = message;
    area.appendChild(item);
    setTimeout(() => item.remove(), 4200);
  }

  // ---------- Cart (stored in the browser; prices are re-checked by the server at checkout) ----------
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

  function cartItems() {
    return Object.values(readCart());
  }

  function addToCart(product, quantity = 1) {
    const cart = readCart();
    const existing = cart[product.id];
    const next = (existing ? existing.qty : 0) + quantity;
    const limit = Math.min(pricing.maxQuantityPerItem, product.stock ?? pricing.maxQuantityPerItem);
    if (next > limit) {
      toast(`You can add at most ${limit} of ${product.name}.`, "error");
      return;
    }
    cart[product.id] = {
      id: product.id, name: product.name, price: product.price, emoji: product.emoji,
      vendorName: product.vendorName, stock: product.stock, qty: next,
    };
    writeCart(cart);
    toast(`${product.emoji} ${product.name} added to your cart.`);
  }

  function setQuantity(id, qty) {
    const cart = readCart();
    if (!cart[id]) return;
    if (qty <= 0) delete cart[id];
    else cart[id].qty = Math.min(qty, pricing.maxQuantityPerItem, cart[id].stock ?? pricing.maxQuantityPerItem);
    writeCart(cart);
  }

  function clearCart() {
    writeCart({});
  }

  function cartTotals(hasPlus = false) {
    const subtotal = Math.round(cartItems().reduce((sum, item) => sum + item.price * item.qty, 0) * 100) / 100;
    // Same rule as Pricing.java: free above the threshold or for Plus members.
    const delivery = subtotal === 0 || hasPlus || subtotal > pricing.freeDeliveryThreshold ? 0 : pricing.deliveryFee;
    return { subtotal, delivery, total: Math.round((subtotal + delivery) * 100) / 100 };
  }

  function updateCartCount() {
    const count = cartItems().reduce((sum, item) => sum + item.qty, 0);
    document.querySelectorAll(".cart-count").forEach((badge) => {
      badge.textContent = count || "";
      badge.dataset.count = count;
    });
  }

  // ---------- Layout ----------
  function renderShell() {
    const page = document.body.dataset.page;
    document.body.insertAdjacentHTML("afterbegin", `
      <div class="blobs" aria-hidden="true"><div class="blob blob-1"></div><div class="blob blob-2"></div><div class="blob blob-3"></div></div>
      <a class="visually-hidden" href="#main">Skip to content</a>
      <header class="site-header container">
        <nav class="nav glass" aria-label="Main">
          <a class="brand" href="index.html"><span class="brand-mark">☁</span>CloudStore</a>
          <button class="btn icon-btn menu-toggle" type="button" aria-label="Open menu" aria-expanded="false">☰</button>
          <ul class="nav-links">
            ${NAV.map((item) => `<li><a href="${item.href}" class="${item.page === page ? "active" : ""}">${item.label}</a></li>`).join("")}
          </ul>
          <div class="nav-actions">
            <a class="btn icon-btn cart-link" href="cart.html" aria-label="Cart">🛒<span class="cart-count"></span></a>
            <span class="auth-slot"><a class="btn btn-primary btn-sm" href="login.html">Log in</a></span>
          </div>
        </nav>
      </header>`);

    document.body.insertAdjacentHTML("beforeend", `
      <footer class="site-footer container">
        <div class="footer-inner glass">
          <div>
            <a class="brand" href="index.html"><span class="brand-mark">☁</span>CloudStore</a>
            <p class="muted" style="margin-top:12px">An online marketplace that helps township and campus vendors sell to their community —
              digital entrepreneurship for sustainable community development.</p>
          </div>
          <div>
            <h4>Explore</h4>
            <ul>${NAV.slice(1, 5).map((item) => `<li><a href="${item.href}">${item.label}</a></li>`).join("")}</ul>
          </div>
          <div>
            <h4>More</h4>
            <ul>
              <li><a href="research.html">Market research &amp; sources</a></li>
              <li><a href="contact.html">Contact us</a></li>
              <li><a href="cart.html">Your cart</a></li>
              <li><a href="account.html">My account</a></li>
            </ul>
          </div>
        </div>
        <p class="footer-bottom">© 2026 CloudStore · JGA 2026 prototype (Faculty of ICT) · Demo data only — no real payments are processed.</p>
      </footer>`);

    const toggle = document.querySelector(".menu-toggle");
    const links = document.querySelector(".nav-links");
    toggle.addEventListener("click", () => {
      const open = links.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
    });
    updateCartCount();
  }

  function renderAuth() {
    const slot = document.querySelector(".auth-slot");
    if (!slot) return;
    if (!currentUser) {
      slot.innerHTML = `<a class="btn btn-primary btn-sm" href="login.html">Log in</a>`;
      return;
    }
    const firstName = escapeHtml(currentUser.fullName.split(" ")[0]);
    slot.innerHTML = `
      ${currentUser.role === "admin" ? `<a class="btn btn-sm" href="dashboard.html">📊 Dashboard</a>` : ""}
      <a class="btn btn-sm" href="account.html">👤 ${firstName}</a>
      <button class="btn btn-sm" type="button" data-logout>Log out</button>`;
    slot.querySelector("[data-logout]").addEventListener("click", async () => {
      await api("/api/auth/logout", { method: "POST", data: {} }).catch(() => {});
      currentUser = null;
      toast("You have been logged out.");
      setTimeout(() => (window.location.href = "index.html"), 500);
    });
  }

  function revealOnScroll() {
    const items = document.querySelectorAll(".reveal");
    if (!("IntersectionObserver" in window)) {
      items.forEach((el) => el.classList.add("visible"));
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });
    items.forEach((el) => observer.observe(el));
  }

  // ---------- Product card ----------
  function productCard(product) {
    const lowStock = product.stock > 0 && product.stock <= 5;
    return `
      <article class="product glass reveal">
        <div class="product-art" aria-hidden="true">${escapeHtml(product.emoji)}
          ${product.featured ? `<span class="badge badge-featured">★ Featured vendor</span>` : ""}
        </div>
        <span class="badge" style="align-self:flex-start;margin-bottom:8px">${escapeHtml(product.category)}</span>
        <h3>${escapeHtml(product.name)}</h3>
        <div class="vendor">by ${escapeHtml(product.vendorName)} · ${escapeHtml(product.vendorLocation)}</div>
        <p class="desc">${escapeHtml(product.description)}</p>
        ${lowStock ? `<span class="badge badge-amber" style="align-self:flex-start">Only ${product.stock} left</span>` : ""}
        <div class="product-foot">
          <span class="price">${money(product.price)}</span>
          ${product.stock > 0
            ? `<button class="btn btn-primary btn-sm" type="button" data-add="${product.id}">Add to cart</button>`
            : `<span class="badge badge-red">Sold out</span>`}
        </div>
      </article>`;
  }

  /** Wires "Add to cart" buttons inside a container to a product list (or a function returning the current list). */
  function bindAddButtons(container, products) {
    container.addEventListener("click", (event) => {
      const button = event.target.closest("[data-add]");
      if (!button) return;
      const list = typeof products === "function" ? products() : products;
      const product = list.find((p) => String(p.id) === button.dataset.add);
      if (product) addToCart(product);
    });
  }

  // ---------- CloudBot assistant ----------
  function renderAssistant() {
    document.body.insertAdjacentHTML("beforeend", `
      <button class="btn btn-primary chat-fab" type="button" aria-label="Open CloudBot assistant" aria-expanded="false">💬</button>
      <section class="chat-panel glass glass-strong" hidden aria-label="CloudBot assistant">
        <div class="chat-head">
          <div class="avatar" aria-hidden="true">🤖</div>
          <div><strong>CloudBot</strong><small>Shopping assistant · rule-based</small></div>
          <button class="btn icon-btn btn-sm" type="button" data-close style="margin-left:auto;width:34px;height:34px" aria-label="Close assistant">✕</button>
        </div>
        <div class="chat-log" aria-live="polite"></div>
        <div class="chat-suggestions">
          ${["Delivery fees?", "Show me crafts", "Track my order", "How do I sell?"]
            .map((s) => `<button class="chip" type="button" data-suggest>${s}</button>`).join("")}
        </div>
        <form class="chat-form">
          <label class="visually-hidden" for="chat-input">Ask CloudBot</label>
          <input id="chat-input" class="input" maxlength="300" placeholder="Ask about products, delivery…" autocomplete="off">
          <button class="btn btn-primary" type="submit">Send</button>
        </form>
      </section>`);

    const fab = document.querySelector(".chat-fab");
    const panel = document.querySelector(".chat-panel");
    const log = panel.querySelector(".chat-log");
    const form = panel.querySelector(".chat-form");
    const input = panel.querySelector("#chat-input");
    let greeted = false;

    function addMessage(text, who, products = []) {
      const bubble = document.createElement("div");
      bubble.className = `msg ${who}`;
      bubble.textContent = text;
      products.forEach((product) => {
        const row = document.createElement("div");
        row.className = "msg-product";
        row.innerHTML = `<span class="emoji">${escapeHtml(product.emoji)}</span>
          <div><strong>${escapeHtml(product.name)}</strong><br>${money(product.price)} · ${escapeHtml(product.vendorName)}</div>`;
        const add = document.createElement("button");
        add.className = "btn btn-sm";
        add.type = "button";
        add.textContent = "Add";
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
      if (open) {
        if (!greeted) {
          addMessage("Hi! I'm CloudBot. Ask me about products, delivery, plans or your latest order.", "bot");
          greeted = true;
        }
        input.focus();
      }
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
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      ask(input.value);
    });
    panel.querySelectorAll("[data-suggest]").forEach((chip) => chip.addEventListener("click", () => ask(chip.textContent)));
  }

  // ---------- Start-up ----------
  const ready = (async () => {
    renderShell();
    renderAssistant();
    revealOnScroll();
    const [me, prices] = await Promise.allSettled([api("/api/auth/me"), api("/api/pricing")]);
    if (me.status === "fulfilled") currentUser = me.value.user;
    if (prices.status === "fulfilled") pricing = prices.value;
    renderAuth();
    return currentUser;
  })();

  return {
    api, money, escapeHtml, toast, ready, productCard, bindAddButtons, revealOnScroll,
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
