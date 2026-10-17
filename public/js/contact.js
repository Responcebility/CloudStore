/*
 * File:    contact.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Contact form: checks each field in the browser, then sends it.
 *          The server checks everything again before saving.
 */

(() => {
  const form = document.getElementById("contact-form");
  const result = document.getElementById("contact-result");
  const message = document.getElementById("message");
  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

  // Stack the two columns on small screens.
  const layout = document.getElementById("contact-layout");
  const media = window.matchMedia("(max-width: 900px)");
  const applyLayout = () => (layout.style.gridTemplateColumns = media.matches ? "1fr" : "minmax(0,1.3fr) minmax(0,1fr)");
  media.addEventListener("change", applyLayout);
  applyLayout();

  message.addEventListener("input", () => {
    document.getElementById("message-count").textContent = message.value.length;
  });

  function validate() {
    const checks = [
      ["name", (v) => v.length >= 2 || "Please enter your name (at least 2 characters)."],
      ["email", (v) => EMAIL.test(v) || "Please enter a valid email address, for example name@example.com."],
      ["subject", (v) => v.length >= 3 || "Please enter a subject of at least 3 characters."],
      ["message", (v) => v.length >= 10 || "Please write a message of at least 10 characters."],
    ];
    let ok = true;
    for (const [id, rule] of checks) {
      const input = document.getElementById(id);
      const outcome = rule(input.value.trim());
      ok = showFieldError(input, outcome === true ? "" : outcome) && ok;
    }
    return ok;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    result.innerHTML = "";
    if (!validate()) return;
    const button = form.querySelector("[type=submit]");
    button.disabled = true;
    try {
      const reply = await CS.api("/api/contact", { method: "POST", data: Object.fromEntries(new FormData(form)) });
      result.innerHTML = `<div class="alert alert-success">${CS.escapeHtml(reply.message)}</div>`;
      form.reset();
      document.getElementById("message-count").textContent = "0";
    } catch (error) {
      result.innerHTML = `<div class="alert alert-error">${CS.escapeHtml(error.message)}</div>`;
    } finally {
      button.disabled = false;
    }
  });
})();
