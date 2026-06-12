import { Router } from "express";
import bcrypt from "bcrypt";
import { pool } from "../db/pool.js";
import { signToken, requireAuth } from "../middleware/auth.js";
import { verifyFirebasePhoneToken } from "../services/firebase.js";
import {
  createResetPasswordSession,
  createSignupSession,
  consumePhoneSession,
  isValidIndianPhone,
  normalizeIndianPhone,
} from "../services/phoneSessions.js";

export const authRouter = Router();

type AuthedHandler = (
  req: import("express").Request,
  res: import("express").Response
) => Promise<void>;

function safeAuth(handler: AuthedHandler) {
  return async (
    req: import("express").Request,
    res: import("express").Response
  ) => {
    try {
      await handler(req, res);
    } catch (err) {
      console.error("Auth route error:", err);
      res.status(503).json({ error: "Service temporarily unavailable" });
    }
  };
}

function mapCustomer(row: Record<string, unknown>) {
  return {
    id: row.id,
    name: row.name,
    email: row.email,
    phone: row.phone,
  };
}

function mapTechnician(row: Record<string, unknown>) {
  return {
    id: row.id,
    name: row.name,
    email: row.email,
    services: row.services ?? "",
    status: row.status ?? "Active",
    role: "Technician",
  };
}

authRouter.post("/customer/login", safeAuth(async (req, res) => {
  const { phone, password } = req.body as { phone?: string; password?: string };
  if (!phone || !password) {
    res.status(400).json({ error: "Phone and password required" });
    return;
  }
  if (!isValidIndianPhone(phone)) {
    res.status(400).json({ error: "Invalid Indian mobile number" });
    return;
  }
  const normalized = normalizeIndianPhone(phone);
  const result = await pool.query(
    "SELECT * FROM customers WHERE phone = $1 LIMIT 1",
    [normalized]
  );
  const row = result.rows[0];
  if (!row || !(await bcrypt.compare(password, row.password_hash))) {
    res.status(401).json({ error: "Invalid phone or password" });
    return;
  }
  if (row.is_active === false) {
    res.status(403).json({ error: "Account disabled" });
    return;
  }
  const token = signToken({ role: "customer", sub: row.id, email: row.email });
  res.json({ token, user: mapCustomer(row) });
}));

/**
 * Step 1 signup: whitelist phone on backend, then client triggers Firebase Phone Auth SMS.
 */
authRouter.post("/customer/register/init", safeAuth(async (req, res) => {
  const { name, phone, password } = req.body as {
    name?: string;
    phone?: string;
    password?: string;
  };
  if (!name?.trim() || !phone || !password) {
    res.status(400).json({ error: "Name, phone and password are required" });
    return;
  }
  try {
    const normalized = await createSignupSession(phone, name, password);
    res.json({
      message: "Phone whitelisted for verification. Send Firebase OTP from the app.",
      phone: normalized,
    });
  } catch (err) {
    const e = err as Error & { status?: number };
    res.status(e.status ?? 500).json({ error: e.message });
  }
}));

/**
 * Step 2 signup: verify Firebase ID token, consume whitelist row, create customer.
 */
authRouter.post("/customer/register/verify", safeAuth(async (req, res) => {
  const { phone, firebaseIdToken } = req.body as {
    phone?: string;
    firebaseIdToken?: string;
  };
  if (!phone || !firebaseIdToken) {
    res.status(400).json({ error: "Phone and firebaseIdToken are required" });
    return;
  }
  try {
    const normalized = normalizeIndianPhone(phone);
    const { phone10 } = await verifyFirebasePhoneToken(firebaseIdToken);
    if (phone10 !== normalized) {
      res.status(401).json({ error: "Phone number does not match Firebase verification" });
      return;
    }
    const session = await consumePhoneSession(phone10, "signup");
    if (session.purpose !== "signup") {
      res.status(400).json({ error: "Invalid verification session" });
      return;
    }
    const email = `${phone10}@satyam.local`;
    const inserted = await pool.query(
      `INSERT INTO customers (name, email, phone, password_hash)
       VALUES ($1, $2, $3, $4) RETURNING *`,
      [session.name, email, phone10, session.passwordHash]
    );
    const row = inserted.rows[0];
    const token = signToken({ role: "customer", sub: row.id, email: row.email });
    res.status(201).json({ token, user: mapCustomer(row) });
  } catch (err) {
    const e = err as Error & { status?: number };
    res.status(e.status ?? 500).json({ error: e.message });
  }
}));

/** Step 1 reset password: whitelist phone for Firebase OTP. */
authRouter.post("/customer/reset-password/init", safeAuth(async (req, res) => {
  const { phone } = req.body as { phone?: string };
  if (!phone) {
    res.status(400).json({ error: "Phone is required" });
    return;
  }
  try {
    const normalized = await createResetPasswordSession(phone);
    res.json({
      message: "Phone whitelisted for password reset. Send Firebase OTP from the app.",
      phone: normalized,
    });
  } catch (err) {
    const e = err as Error & { status?: number };
    res.status(e.status ?? 500).json({ error: e.message });
  }
}));

/** Step 2 reset password: verify Firebase token and set new password. */
authRouter.post("/customer/reset-password/verify", safeAuth(async (req, res) => {
  const { phone, firebaseIdToken, newPassword } = req.body as {
    phone?: string;
    firebaseIdToken?: string;
    newPassword?: string;
  };
  if (!phone || !firebaseIdToken || !newPassword) {
    res.status(400).json({ error: "Phone, firebaseIdToken and newPassword are required" });
    return;
  }
  if (newPassword.length < 6) {
    res.status(400).json({ error: "Password must be at least 6 characters" });
    return;
  }
  try {
    const normalized = normalizeIndianPhone(phone);
    const { phone10 } = await verifyFirebasePhoneToken(firebaseIdToken);
    if (phone10 !== normalized) {
      res.status(401).json({ error: "Phone number does not match Firebase verification" });
      return;
    }
    await consumePhoneSession(phone10, "reset_password");
    const hash = await bcrypt.hash(newPassword, 10);
    const updated = await pool.query(
      `UPDATE customers SET password_hash = $1 WHERE phone = $2 RETURNING *`,
      [hash, phone10]
    );
    if (updated.rows.length === 0) {
      res.status(404).json({ error: "Account not found" });
      return;
    }
    const row = updated.rows[0];
    const token = signToken({ role: "customer", sub: row.id, email: row.email });
    res.json({ token, user: mapCustomer(row), message: "Password updated successfully" });
  } catch (err) {
    const e = err as Error & { status?: number };
    res.status(e.status ?? 500).json({ error: e.message });
  }
}));

/** Logged-in customer changes password (no OTP — requires current password). */
authRouter.post(
  "/customer/change-password",
  requireAuth(["customer"]),
  safeAuth(async (req, res) => {
    const { currentPassword, newPassword } = req.body as {
      currentPassword?: string;
      newPassword?: string;
    };
    if (!currentPassword || !newPassword) {
      res.status(400).json({ error: "currentPassword and newPassword are required" });
      return;
    }
    if (newPassword.length < 6) {
      res.status(400).json({ error: "Password must be at least 6 characters" });
      return;
    }
    const customerId = req.auth!.sub;
    const result = await pool.query("SELECT * FROM customers WHERE id = $1 LIMIT 1", [customerId]);
    const row = result.rows[0];
    if (!row || !(await bcrypt.compare(currentPassword, row.password_hash))) {
      res.status(401).json({ error: "Current password is incorrect" });
      return;
    }
    const hash = await bcrypt.hash(newPassword, 10);
    await pool.query("UPDATE customers SET password_hash = $1 WHERE id = $2", [hash, customerId]);
    res.json({ message: "Password changed successfully" });
  })
);

authRouter.post("/admin/login", safeAuth(async (req, res) => {
  const { email, password } = req.body as { email?: string; password?: string };
  if (!email || !password) {
    res.status(400).json({ error: "Email and password required" });
    return;
  }
  const normalized = email.trim().toLowerCase();
  const result = await pool.query(
    "SELECT * FROM admins WHERE LOWER(email) = $1 OR LOWER(email) = $2 LIMIT 1",
    [normalized, normalized === "admin" ? "admin@gmail.com" : normalized]
  );
  const row = result.rows[0];
  if (!row || !(await bcrypt.compare(password, row.password_hash))) {
    res.status(401).json({ error: "Invalid email or password" });
    return;
  }
  if (row.is_active === false) {
    res.status(403).json({ error: "Account disabled" });
    return;
  }
  const token = signToken({ role: "admin", sub: row.id, email: row.email });
  res.json({ token, admin: { id: row.id, email: row.email } });
}));

authRouter.post("/technician/login", safeAuth(async (req, res) => {
  const { email, password } = req.body as { email?: string; password?: string };
  if (!email || !password) {
    res.status(400).json({ error: "Email and password required" });
    return;
  }
  const result = await pool.query(
    `SELECT * FROM technicians
     WHERE (LOWER(email) = LOWER($1) OR LOWER(name) = LOWER($1)) LIMIT 1`,
    [email.trim()]
  );
  const row = result.rows[0];
  if (!row || !(await bcrypt.compare(password, row.password_hash))) {
    res.status(401).json({ error: "Invalid email or password" });
    return;
  }
  if (row.is_active === false) {
    res.status(403).json({ error: "Account disabled" });
    return;
  }
  const token = signToken({ role: "technician", sub: row.id, email: row.email });
  res.json({ token, technician: mapTechnician(row) });
}));
