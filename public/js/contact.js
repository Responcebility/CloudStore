/*
 * File:    contact.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Contact form: checks each field in the browser, then sends it.
 *          The server checks everything again before saving.
 */

(() => {
  const form = document.getElementById("contact-form");
  const result = document.getElementById("contact-result");
  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

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
      const reply = await KC.api("/api/contact", { method: "POST", data: Object.fromEntries(new FormData(form)) });
      result.innerHTML = `<div class="alert alert-success">${KC.escapeHtml(reply.message)}</div>`;
      form.reset();
    } catch (error) {
      result.innerHTML = `<div class="alert alert-error">${KC.escapeHtml(error.message)}</div>`;
    } finally {
      button.disabled = false;
    }
  });
})();
