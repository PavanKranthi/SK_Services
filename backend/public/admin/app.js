const TOKEN_KEY = "sk_admin_token";

const api = (path, options = {}) => {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(path, { ...options, headers }).then(async (res) => {
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || res.statusText || "Request failed");
    return data;
  });
};

let state = {
  view: "dashboard",
  users: [],
  stats: null,
  filterRole: "all",
  message: "",
  messageType: "success",
};

const app = document.getElementById("app");

function setMessage(text, type = "success") {
  state.message = text;
  state.messageType = type;
  render();
  if (text) setTimeout(() => { state.message = ""; render(); }, 4000);
}

function logout() {
  sessionStorage.removeItem(TOKEN_KEY);
  render();
}

async function login(email, password) {
  const data = await api("/auth/admin/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  sessionStorage.setItem(TOKEN_KEY, data.token);
  state.view = "dashboard";
  await loadDashboard();
  render();
}

async function loadDashboard() {
  state.stats = await api("/admin/stats");
}

async function loadUsers() {
  const data = await api("/users");
  state.users = data.users || [];
}

function renderLogin() {
  app.innerHTML = `
    <div class="login-wrap">
      <div class="card login-card">
        <h1>SK Services Admin</h1>
        <p style="color:var(--muted);margin-bottom:16px">Sign in with your admin account</p>
        <div id="login-error"></div>
        <form class="form" id="login-form">
          <label>Email<input type="email" name="email" required autocomplete="username" /></label>
          <label>Password<input type="password" name="password" required autocomplete="current-password" /></label>
          <button type="submit" class="btn primary" style="width:100%">Sign in</button>
        </form>
      </div>
    </div>`;
  document.getElementById("login-form").onsubmit = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await login(fd.get("email"), fd.get("password"));
    } catch (err) {
      document.getElementById("login-error").innerHTML =
        `<div class="alert error">${escapeHtml(err.message)}</div>`;
    }
  };
}

function statCard(title, value, sub) {
  return `<div class="card stat-card"><h3>${title}</h3><div class="value">${value}</div>${sub ? `<div class="sub">${sub}</div>` : ""}</div>`;
}

function renderDashboard() {
  const s = state.stats;
  if (!s) return `<p>Loading dashboard...</p>`;

  const b = s.counts.bookings;
  const maxBar = Math.max(1, ...(s.bookingsByDay || []).map((d) => d.count));

  const bars = (s.bookingsByDay || [])
    .map((d) => {
      const h = Math.round((d.count / maxBar) * 100);
      const label = String(d.date).slice(5);
      return `<div class="bar" style="height:${Math.max(h, 4)}%" title="${d.count} bookings"><span>${label}</span></div>`;
    })
    .join("");

  const recentRows = (s.recentBookings || [])
    .map(
      (bk) => `<tr>
        <td>${escapeHtml(bk.customerName)}</td>
        <td>${escapeHtml(bk.serviceName)} ${escapeHtml(bk.serviceType)}</td>
        <td>${escapeHtml(bk.status)}</td>
        <td>${escapeHtml(bk.date)}</td>
      </tr>`
    )
    .join("");

  return `
    <div class="grid-stats">
      ${statCard("Admins", s.counts.admins.active, `${s.counts.admins.total} total · ${s.counts.admins.disabled} disabled`)}
      ${statCard("Customers", s.counts.customers.active, `${s.counts.customers.total} total · ${s.counts.customers.disabled} disabled`)}
      ${statCard("Technicians", s.counts.technicians.active, `${s.counts.technicians.total} total · ${s.counts.technicians.disabled} disabled`)}
      ${statCard("Bookings (7d)", b.last_7_days, `${b.total} all time`)}
      ${statCard("Pending", b.pending, `${b.completed} completed`)}
      ${statCard("Revenue (completed)", "₹" + Math.round(s.revenue.completedValue).toLocaleString("en-IN"), "From completed jobs")}
    </div>
    <div class="two-col">
      <div class="card">
        <h2 style="margin:0 0 8px;font-size:1rem">Bookings activity (14 days)</h2>
        <div class="bar-chart">${bars || "<p style='color:var(--muted)'>No recent bookings</p>"}</div>
      </div>
      <div class="card">
        <h2 style="margin:0 0 8px;font-size:1rem">Booking status</h2>
        <table>
          <tr><td>Pending</td><td>${b.pending}</td></tr>
          <tr><td>Confirmed</td><td>${b.confirmed}</td></tr>
          <tr><td>In progress</td><td>${b.in_progress}</td></tr>
          <tr><td>Completed</td><td>${b.completed}</td></tr>
          <tr><td>Declined</td><td>${b.declined}</td></tr>
        </table>
      </div>
    </div>
    <div class="card" style="margin-top:16px">
      <h2 style="margin:0 0 12px;font-size:1rem">Recent bookings</h2>
      <table>
        <thead><tr><th>Customer</th><th>Service</th><th>Status</th><th>Date</th></tr></thead>
        <tbody>${recentRows || "<tr><td colspan=4>No bookings yet</td></tr>"}</tbody>
      </table>
    </div>`;
}

function filteredUsers() {
  if (state.filterRole === "all") return state.users;
  return state.users.filter((u) => u.role === state.filterRole);
}

function renderUsers() {
  const rows = filteredUsers()
    .map(
      (u) => `<tr>
        <td><span class="badge role-${u.role}">${u.role}</span></td>
        <td>${escapeHtml(u.name || "—")}<br><small style="color:var(--muted)">${escapeHtml(u.email)}</small></td>
        <td>${escapeHtml(u.phone || "—")}</td>
        <td><span class="badge ${u.isActive ? "active" : "disabled"}">${u.isActive ? "Active" : "Disabled"}</span></td>
        <td style="white-space:nowrap">
          <button class="btn" data-action="reset" data-role="${u.role}" data-id="${u.id}">Reset password</button>
          <button class="btn" data-action="toggle" data-role="${u.role}" data-id="${u.id}">${u.isActive ? "Disable" : "Enable"}</button>
          <button class="btn danger" data-action="delete" data-role="${u.role}" data-id="${u.id}">Delete</button>
        </td>
      </tr>`
    )
    .join("");

  return `
    <div class="toolbar">
      <select id="filter-role">
        <option value="all">All roles</option>
        <option value="admin">Admins</option>
        <option value="customer">Customers</option>
        <option value="technician">Technicians</option>
      </select>
      <button class="btn primary" id="btn-add-user">+ Add user</button>
      <button class="btn" id="btn-refresh-users">Refresh</button>
    </div>
    <div class="card">
      <table>
        <thead><tr><th>Role</th><th>Account</th><th>Phone</th><th>Status</th><th>Actions</th></tr></thead>
        <tbody>${rows || "<tr><td colspan=5>No users found</td></tr>"}</tbody>
      </table>
    </div>`;
}

function renderShell(content) {
  const msg = state.message
    ? `<div class="alert ${state.messageType}">${escapeHtml(state.message)}</div>`
    : "";

  app.innerHTML = `
    <div class="layout">
      <div class="header">
        <div>
          <h1>SK Services Admin Portal</h1>
          <p>Manage users and monitor app activity</p>
        </div>
        <div class="nav">
          <button class="${state.view === "dashboard" ? "active" : ""}" data-nav="dashboard">Dashboard</button>
          <button class="${state.view === "users" ? "active" : ""}" data-nav="users">Users</button>
          <button class="btn" id="btn-logout">Logout</button>
        </div>
      </div>
      ${msg}
      ${content}
    </div>`;

  document.querySelectorAll("[data-nav]").forEach((btn) => {
    btn.onclick = async () => {
      state.view = btn.dataset.nav;
      if (state.view === "dashboard") await loadDashboard();
      if (state.view === "users") await loadUsers();
      render();
    };
  });

  document.getElementById("btn-logout").onclick = logout;

  if (state.view === "users") bindUsersEvents();
}

function bindUsersEvents() {
  const sel = document.getElementById("filter-role");
  if (sel) {
    sel.value = state.filterRole;
    sel.onchange = () => {
      state.filterRole = sel.value;
      render();
    };
  }
  document.getElementById("btn-refresh-users")?.addEventListener("click", async () => {
    await loadUsers();
    setMessage("User list refreshed");
  });
  document.getElementById("btn-add-user")?.addEventListener("click", () => showUserModal("create"));

  document.querySelectorAll("[data-action]").forEach((btn) => {
    btn.onclick = async () => {
      const user = state.users.find((u) => u.role === btn.dataset.role && String(u.id) === btn.dataset.id);
      if (!user) return;
      if (btn.dataset.action === "reset") showResetModal(user);
      if (btn.dataset.action === "toggle") toggleUser(user);
      if (btn.dataset.action === "delete") deleteUser(user);
    };
  });
}

function showModal(html, onMount) {
  const backdrop = document.createElement("div");
  backdrop.className = "modal-backdrop";
  backdrop.innerHTML = `<div class="card modal">${html}</div>`;
  backdrop.onclick = (e) => { if (e.target === backdrop) backdrop.remove(); };
  document.body.appendChild(backdrop);
  onMount?.(backdrop);
}

function showUserModal(mode, user = null) {
  showModal(
    `
    <h2>${mode === "create" ? "Add user" : "Edit user"}</h2>
    <form class="form" id="user-form">
      <label>Role
        <select name="role" ${mode === "edit" ? "disabled" : ""}>
          <option value="admin">Admin</option>
          <option value="customer">Customer</option>
          <option value="technician">Technician</option>
        </select>
      </label>
      <label>Email<input name="email" type="email" required value="${user?.email || ""}" /></label>
      ${mode === "create" ? '<label>Password<input name="password" type="password" required minlength="4" /></label>' : ""}
      <label class="field-name">Full name<input name="name" value="${user?.name || ""}" /></label>
      <label class="field-phone">Phone<input name="phone" value="${user?.phone || ""}" /></label>
      <label class="field-services">Services (comma-separated)<input name="services" value="${user?.services || ""}" /></label>
      <div class="modal-actions">
        <button type="button" class="btn" id="modal-cancel">Cancel</button>
        <button type="submit" class="btn primary">${mode === "create" ? "Create" : "Save"}</button>
      </div>
    </form>`,
    (backdrop) => {
      const form = backdrop.querySelector("#user-form");
      const roleSel = form.querySelector('[name="role"]');
      if (user) roleSel.value = user.role;
      const syncFields = () => {
        const role = roleSel.value;
        backdrop.querySelector(".field-name").classList.toggle("hidden", role === "admin" && mode === "create");
        backdrop.querySelector(".field-phone").classList.toggle("hidden", role !== "customer");
        backdrop.querySelector(".field-services").classList.toggle("hidden", role !== "technician");
      };
      roleSel.onchange = syncFields;
      syncFields();
      backdrop.querySelector("#modal-cancel").onclick = () => backdrop.remove();
      form.onsubmit = async (e) => {
        e.preventDefault();
        const fd = new FormData(form);
        try {
          if (mode === "create") {
            await api("/users", {
              method: "POST",
              body: JSON.stringify({
                role: fd.get("role"),
                email: fd.get("email"),
                password: fd.get("password"),
                name: fd.get("name") || undefined,
                phone: fd.get("phone") || undefined,
                services: fd.get("services") || undefined,
              }),
            });
            setMessage("User created");
          }
          backdrop.remove();
          await loadUsers();
          if (state.view === "dashboard") await loadDashboard();
          render();
        } catch (err) {
          setMessage(err.message, "error");
        }
      };
    }
  );
}

function showResetModal(user) {
  showModal(
    `
    <h2>Reset password</h2>
    <p style="color:var(--muted);margin:0 0 12px">${escapeHtml(user.email)}</p>
    <form class="form" id="reset-form">
      <label>New password<input name="password" type="password" required minlength="4" /></label>
      <div class="modal-actions">
        <button type="button" class="btn" id="modal-cancel">Cancel</button>
        <button type="submit" class="btn primary">Update password</button>
      </div>
    </form>`,
    (backdrop) => {
      backdrop.querySelector("#modal-cancel").onclick = () => backdrop.remove();
      backdrop.querySelector("#reset-form").onsubmit = async (e) => {
        e.preventDefault();
        const password = new FormData(e.target).get("password");
        try {
          await api(`/users/${user.role}/${user.id}`, {
            method: "PATCH",
            body: JSON.stringify({ password }),
          });
          backdrop.remove();
          setMessage("Password updated");
        } catch (err) {
          setMessage(err.message, "error");
        }
      };
    }
  );
}

async function toggleUser(user) {
  if (!confirm(`${user.isActive ? "Disable" : "Enable"} ${user.email}?`)) return;
  try {
    await api(`/users/${user.role}/${user.id}`, {
      method: "PATCH",
      body: JSON.stringify({ isActive: !user.isActive }),
    });
    await loadUsers();
    if (state.view === "dashboard") await loadDashboard();
    setMessage(user.isActive ? "User disabled" : "User enabled");
  } catch (err) {
    setMessage(err.message, "error");
  }
}

async function deleteUser(user) {
  if (!confirm(`Permanently delete ${user.email}? This cannot be undone.`)) return;
  try {
    await api(`/users/${user.role}/${user.id}`, { method: "DELETE" });
    await loadUsers();
    if (state.view === "dashboard") await loadDashboard();
    setMessage("User deleted");
  } catch (err) {
    setMessage(err.message, "error");
  }
}

function escapeHtml(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

async function render() {
  if (!sessionStorage.getItem(TOKEN_KEY)) {
    renderLogin();
    return;
  }
  try {
    if (state.view === "dashboard" && !state.stats) await loadDashboard();
    if (state.view === "users" && state.users.length === 0) await loadUsers();
    const content = state.view === "users" ? renderUsers() : renderDashboard();
    renderShell(content);
  } catch (err) {
    if (String(err.message).includes("401") || String(err.message).includes("token")) {
      logout();
      return;
    }
    renderShell(`<div class="alert error">${escapeHtml(err.message)}</div>`);
  }
}

render();
