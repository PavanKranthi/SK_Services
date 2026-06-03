import { Router } from "express";
import bcrypt from "bcrypt";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";

export const usersRouter = Router();

type PortalRole = "admin" | "customer" | "technician";

function mapPortalUser(role: PortalRole, row: Record<string, unknown>) {
  const base = {
    id: row.id as number,
    role,
    email: row.email as string,
    isActive: row.is_active !== false,
    createdAt: new Date(row.created_at as string).getTime(),
  };
  if (role === "admin") return base;
  if (role === "customer") {
    return { ...base, name: row.name as string, phone: row.phone as string };
  }
  return {
    ...base,
    name: row.name as string,
    services: (row.services as string) ?? "",
    status: (row.status as string) ?? "Active",
  };
}

async function countActiveAdmins(excludeId?: number): Promise<number> {
  const result = excludeId
    ? await pool.query(
        "SELECT COUNT(*)::int AS c FROM admins WHERE is_active = TRUE AND id <> $1",
        [excludeId]
      )
    : await pool.query("SELECT COUNT(*)::int AS c FROM admins WHERE is_active = TRUE");
  return result.rows[0]?.c ?? 0;
}

usersRouter.get("/", requireAuth(["admin"]), async (_req, res) => {
  const [admins, customers, technicians] = await Promise.all([
    pool.query(
      "SELECT id, email, is_active, created_at FROM admins ORDER BY LOWER(email)"
    ),
    pool.query(
      "SELECT id, name, email, phone, is_active, created_at FROM customers ORDER BY LOWER(name)"
    ),
    pool.query(
      "SELECT id, name, email, services, status, is_active, created_at FROM technicians ORDER BY LOWER(name)"
    ),
  ]);

  res.json({
    users: [
      ...admins.rows.map((r) => mapPortalUser("admin", r)),
      ...customers.rows.map((r) => mapPortalUser("customer", r)),
      ...technicians.rows.map((r) => mapPortalUser("technician", r)),
    ],
  });
});

usersRouter.post("/", requireAuth(["admin"]), async (req, res) => {
  const body = req.body as {
    role?: PortalRole;
    email?: string;
    password?: string;
    name?: string;
    phone?: string;
    services?: string;
  };

  const role = body.role;
  const email = body.email?.trim().toLowerCase();
  const password = body.password?.trim();

  if (!role || !email || !password) {
    res.status(400).json({ error: "role, email, and password are required" });
    return;
  }
  if (password.length < 4) {
    res.status(400).json({ error: "Password must be at least 4 characters" });
    return;
  }

  const hash = await bcrypt.hash(password, 10);

  try {
    if (role === "admin") {
      const inserted = await pool.query(
        `INSERT INTO admins (email, password_hash, is_active)
         VALUES ($1, $2, TRUE) RETURNING id, email, is_active, created_at`,
        [email, hash]
      );
      res.status(201).json(mapPortalUser("admin", inserted.rows[0]));
      return;
    }

    if (role === "customer") {
      const name = body.name?.trim();
      const phone = body.phone?.trim();
      if (!name || !phone) {
        res.status(400).json({ error: "name and phone are required for customers" });
        return;
      }
      const inserted = await pool.query(
        `INSERT INTO customers (name, email, phone, password_hash, is_active)
         VALUES ($1, $2, $3, $4, TRUE)
         RETURNING id, name, email, phone, is_active, created_at`,
        [name, email, phone, hash]
      );
      res.status(201).json(mapPortalUser("customer", inserted.rows[0]));
      return;
    }

    if (role === "technician") {
      const name = body.name?.trim();
      if (!name) {
        res.status(400).json({ error: "name is required for technicians" });
        return;
      }
      const services = (body.services ?? "")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
        .join(",");
      const inserted = await pool.query(
        `INSERT INTO technicians (name, email, password_hash, services, status, is_active)
         VALUES ($1, $2, $3, $4, 'Active', TRUE)
         RETURNING id, name, email, services, status, is_active, created_at`,
        [name, email, hash, services]
      );
      res.status(201).json(mapPortalUser("technician", inserted.rows[0]));
      return;
    }

    res.status(400).json({ error: "Invalid role" });
  } catch (err: unknown) {
    const code = (err as { code?: string }).code;
    if (code === "23505") {
      res.status(409).json({ error: "Email or phone already exists" });
      return;
    }
    console.error("Create user error:", err);
    res.status(500).json({ error: "Could not create user" });
  }
});

usersRouter.patch("/:role/:id", requireAuth(["admin"]), async (req, res) => {
  const role = req.params.role as PortalRole;
  const id = Number(req.params.id);
  const auth = req.auth!;
  const body = req.body as {
    email?: string;
    password?: string;
    name?: string;
    phone?: string;
    services?: string;
    isActive?: boolean;
  };

  if (!Number.isFinite(id)) {
    res.status(400).json({ error: "Invalid user id" });
    return;
  }

  if (role === "admin" && auth.role === "admin" && auth.sub === id) {
    if (body.isActive === false) {
      res.status(400).json({ error: "You cannot disable your own admin account" });
      return;
    }
  }

  if (role === "admin" && body.isActive === false) {
    const others = await countActiveAdmins(id);
    if (others < 1) {
      res.status(400).json({ error: "Cannot disable the last active admin" });
      return;
    }
  }

  try {
    if (role === "admin") {
      const updates: string[] = [];
      const values: unknown[] = [];
      let i = 1;

      if (body.email) {
        updates.push(`email = $${i++}`);
        values.push(body.email.trim().toLowerCase());
      }
      if (body.password?.trim()) {
        updates.push(`password_hash = $${i++}`);
        values.push(await bcrypt.hash(body.password.trim(), 10));
      }
      if (typeof body.isActive === "boolean") {
        updates.push(`is_active = $${i++}`);
        values.push(body.isActive);
      }
      if (updates.length === 0) {
        res.status(400).json({ error: "No fields to update" });
        return;
      }
      values.push(id);
      await pool.query(
        `UPDATE admins SET ${updates.join(", ")} WHERE id = $${i}`,
        values
      );
      const row = await pool.query(
        "SELECT id, email, is_active, created_at FROM admins WHERE id = $1",
        [id]
      );
      if (!row.rows[0]) {
        res.status(404).json({ error: "User not found" });
        return;
      }
      res.json(mapPortalUser("admin", row.rows[0]));
      return;
    }

    if (role === "customer") {
      const updates: string[] = [];
      const values: unknown[] = [];
      let i = 1;

      if (body.name?.trim()) {
        updates.push(`name = $${i++}`);
        values.push(body.name.trim());
      }
      if (body.email) {
        updates.push(`email = $${i++}`);
        values.push(body.email.trim().toLowerCase());
      }
      if (body.phone?.trim()) {
        updates.push(`phone = $${i++}`);
        values.push(body.phone.trim());
      }
      if (body.password?.trim()) {
        updates.push(`password_hash = $${i++}`);
        values.push(await bcrypt.hash(body.password.trim(), 10));
      }
      if (typeof body.isActive === "boolean") {
        updates.push(`is_active = $${i++}`);
        values.push(body.isActive);
      }
      if (updates.length === 0) {
        res.status(400).json({ error: "No fields to update" });
        return;
      }
      values.push(id);
      await pool.query(
        `UPDATE customers SET ${updates.join(", ")} WHERE id = $${i}`,
        values
      );
      const row = await pool.query(
        "SELECT id, name, email, phone, is_active, created_at FROM customers WHERE id = $1",
        [id]
      );
      if (!row.rows[0]) {
        res.status(404).json({ error: "User not found" });
        return;
      }
      res.json(mapPortalUser("customer", row.rows[0]));
      return;
    }

    if (role === "technician") {
      const updates: string[] = [];
      const values: unknown[] = [];
      let i = 1;

      if (body.name?.trim()) {
        updates.push(`name = $${i++}`);
        values.push(body.name.trim());
      }
      if (body.email) {
        updates.push(`email = $${i++}`);
        values.push(body.email.trim().toLowerCase());
      }
      if (body.password?.trim()) {
        updates.push(`password_hash = $${i++}`);
        values.push(await bcrypt.hash(body.password.trim(), 10));
      }
      if (body.services !== undefined) {
        const normalized = body.services
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean)
          .join(",");
        updates.push(`services = $${i++}`);
        values.push(normalized);
      }
      if (typeof body.isActive === "boolean") {
        updates.push(`is_active = $${i++}`);
        values.push(body.isActive);
        updates.push(`status = $${i++}`);
        values.push(body.isActive ? "Active" : "Disabled");
      }
      if (updates.length === 0) {
        res.status(400).json({ error: "No fields to update" });
        return;
      }
      values.push(id);
      await pool.query(
        `UPDATE technicians SET ${updates.join(", ")} WHERE id = $${i}`,
        values
      );
      const row = await pool.query(
        "SELECT id, name, email, services, status, is_active, created_at FROM technicians WHERE id = $1",
        [id]
      );
      if (!row.rows[0]) {
        res.status(404).json({ error: "User not found" });
        return;
      }
      res.json(mapPortalUser("technician", row.rows[0]));
      return;
    }

    res.status(400).json({ error: "Invalid role" });
  } catch (err: unknown) {
    const code = (err as { code?: string }).code;
    if (code === "23505") {
      res.status(409).json({ error: "Email or phone already exists" });
      return;
    }
    console.error("Update user error:", err);
    res.status(500).json({ error: "Could not update user" });
  }
});

usersRouter.delete("/:role/:id", requireAuth(["admin"]), async (req, res) => {
  const role = req.params.role as PortalRole;
  const id = Number(req.params.id);
  const auth = req.auth!;

  if (!Number.isFinite(id)) {
    res.status(400).json({ error: "Invalid user id" });
    return;
  }

  if (role === "admin" && auth.role === "admin" && auth.sub === id) {
    res.status(400).json({ error: "You cannot delete your own admin account" });
    return;
  }

  if (role === "admin") {
    const row = await pool.query("SELECT is_active FROM admins WHERE id = $1", [id]);
    if (!row.rows[0]) {
      res.status(404).json({ error: "User not found" });
      return;
    }
    if (row.rows[0].is_active) {
      const others = await countActiveAdmins(id);
      if (others < 1) {
        res.status(400).json({ error: "Cannot delete the last active admin" });
        return;
      }
    }
    await pool.query("DELETE FROM admins WHERE id = $1", [id]);
    res.json({ ok: true });
    return;
  }

  if (role === "customer") {
    const result = await pool.query("DELETE FROM customers WHERE id = $1 RETURNING id", [id]);
    if (!result.rowCount) {
      res.status(404).json({ error: "User not found" });
      return;
    }
    res.json({ ok: true });
    return;
  }

  if (role === "technician") {
    const result = await pool.query("DELETE FROM technicians WHERE id = $1 RETURNING id", [id]);
    if (!result.rowCount) {
      res.status(404).json({ error: "User not found" });
      return;
    }
    res.json({ ok: true });
    return;
  }

  res.status(400).json({ error: "Invalid role" });
});
