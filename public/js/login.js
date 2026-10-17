/*
 * File:    login.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Log in and registration forms, with friendly validation.
 */

(() => {
  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  const loginForm = document.getElementById("login-form");
  const registerForm = document.getElementById("register-form");
  // Only follow "next" links that stay on this site.
  const nextParam = new URLSearchParams(location.search).get("next") || "";
  const next = /^[a-z]+\.html(#[a-z-]+)?$/i.test(nextParam) ? nextParam : "account.html";

  CS.ready.then((user) => {
    if (user) location.replace(user.role === "admin" ? "dashboard.html" : next);
  });

  document.querySelectorAll("[data-tab]").forEach((tab) => {
    tab.addEventListener("click", () => {
      const showRegister = tab.dataset.tab === "register";
      document.querySelectorAll("[data-tab]").forEach((t) => {
        t.classList.toggle("active", t === tab);
        t.setAttribute("aria-selected", String(t === tab));
      });
      loginForm.hidden = showRegister;
      registerForm.hidden = !showRegister;
    });
  });

  async function submit(form, path, data) {
    const result = form.querySelector(".result");
    const button = form.querySelector("[type=submit]");
    result.innerHTML = "";
    button.disabled = true;
    try {
      const { user } = await CS.api(path, { method: "POST", data });
      CS.toast(`Welcome, ${user.fullName.split(" ")[0]}!`);
      setTimeout(() => location.replace(user.role === "admin" ? "dashboard.html" : next), 500);
    } catch (error) {
      result.innerHTML = `<div class="alert alert-error">${CS.escapeHtml(error.message)}</div>`;
      button.disabled = false;
    }
  }

  loginForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const email = document.getElementById("login-email");
    const password = document.getElementById("login-password");
    let ok = showFieldError(email, EMAIL.test(email.value.trim()) ? "" : "Please enter a valid email address.");
    ok = showFieldError(password, password.value ? "" : "Please enter your password.") && ok;
    if (ok) submit(loginForm, "/api/auth/login", { email: email.value.trim(), password: password.value });
  });

  registerForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = document.getElementById("reg-name");
    const email = document.getElementById("reg-email");
    const password = document.getElementById("reg-password");
    const confirm = document.getElementById("reg-confirm");
    const strong = password.value.length >= 8 && /[a-z]/i.test(password.value) && /\d/.test(password.value);
    let ok = showFieldError(name, name.value.trim().length >= 2 ? "" : "Please enter your full name.");
    ok = showFieldError(email, EMAIL.test(email.value.trim()) ? "" : "Please enter a valid email address, for example name@example.com.") && ok;
    ok = showFieldError(password, strong ? "" : "Please use at least 8 characters with a letter and a number.") && ok;
    ok = showFieldError(confirm, confirm.value === password.value && confirm.value ? "" : "The passwords do not match.") && ok;
    if (ok) submit(registerForm, "/api/auth/register", { fullName: name.value.trim(), email: email.value.trim(), password: password.value });
  });
})();
