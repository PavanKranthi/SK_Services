import { Router } from "express";
import bcrypt from "bcrypt";
import { pool } from "../db/pool.js";
import { signToken } from "../middleware/auth.js";

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
  const { email, password } = req.body as { email?: string; password?: string };
  if (!email || !password) {
    res.status(400).json({ error: "Email and password required" });
    return;
  }
  const result = await pool.query(
    "SELECT * FROM customers WHERE LOWER(email) = LOWER($1) LIMIT 1",
    [email.trim()]
  );
  const row = result.rows[0];
  if (!row || !(await bcrypt.compare(password, row.password_hash))) {
    res.status(401).json({ error: "Invalid email or password" });
    return;
  }
  const token = signToken({ role: "customer", sub: row.id, email: row.email });
  res.json({ token, user: mapCustomer(row) });
}));

authRouter.post("/customer/register", safeAuth(async (req, res) => {
  const { name, email, phone, password } = req.body as {
    name?: string;
    email?: string;
    phone?: string;
    password?: string;
  };
  if (!name || !email || !phone || !password) {
    res.status(400).json({ error: "All fields required" });
    return;
  }
  const existing = await pool.query(
    "SELECT id FROM customers WHERE phone = $1 OR LOWER(email) = LOWER($2) LIMIT 1",
    [phone, email]
  );
  if (existing.rows.length > 0) {
    res.status(409).json({ error: "Phone or email already registered" });
    return;
  }
  const hash = await bcrypt.hash(password, 10);
  const inserted = await pool.query(
    `INSERT INTO customers (name, email, phone, password_hash)
     VALUES ($1, $2, $3, $4) RETURNING *`,
    [name, email.trim().toLowerCase(), phone, hash]
  );
  const row = inserted.rows[0];
  const token = signToken({ role: "customer", sub: row.id, email: row.email });
  res.status(201).json({ token, user: mapCustomer(row) });
}));

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
    res.status(401).json({ error: "Invalid admin credentials" });
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
    res.status(401).json({ error: "Invalid technician credentials" });
    return;
  }
  const token = signToken({ role: "technician", sub: row.id, email: row.email });
  res.json({ token, technician: mapTechnician(row) });
}));
