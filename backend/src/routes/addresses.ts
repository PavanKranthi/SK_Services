import { Router } from "express";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";

export const addressesRouter = Router();

function mapAddress(row: Record<string, unknown>, phone: string) {
  return {
    id: row.id,
    userPhone: phone,
    addressLine1: row.address_line1,
    addressLine2: row.address_line2,
    city: row.city,
    district: row.district,
    state: row.state,
    country: row.country,
    pinCode: row.pin_code,
    isDefault: row.is_default,
  };
}

addressesRouter.get("/", requireAuth(["customer"]), async (req, res) => {
  const auth = req.auth!;
  const customer = await pool.query("SELECT phone FROM customers WHERE id = $1", [auth.sub]);
  const phone = customer.rows[0]?.phone ?? "";
  const result = await pool.query(
    "SELECT * FROM addresses WHERE customer_id = $1 ORDER BY is_default DESC, id DESC",
    [auth.sub]
  );
  res.json(result.rows.map((r) => mapAddress(r, phone)));
});

addressesRouter.post("/", requireAuth(["customer"]), async (req, res) => {
  const auth = req.auth!;
  const body = req.body as {
    addressLine1?: string;
    addressLine2?: string;
    city?: string;
    district?: string;
    state?: string;
    country?: string;
    pinCode?: string;
    isDefault?: boolean;
  };
  if (body.isDefault) {
    await pool.query("UPDATE addresses SET is_default = false WHERE customer_id = $1", [auth.sub]);
  }
  const inserted = await pool.query(
    `INSERT INTO addresses (
      customer_id, address_line1, address_line2, city, district, state, country, pin_code, is_default
    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
    [
      auth.sub,
      body.addressLine1,
      body.addressLine2 ?? "",
      body.city,
      body.district,
      body.state,
      body.country ?? "India",
      body.pinCode,
      body.isDefault ?? false,
    ]
  );
  const customer = await pool.query("SELECT phone FROM customers WHERE id = $1", [auth.sub]);
  res.status(201).json(mapAddress(inserted.rows[0], customer.rows[0].phone));
});

addressesRouter.patch("/:id/default", requireAuth(["customer"]), async (req, res) => {
  const auth = req.auth!;
  const id = Number(req.params.id);
  await pool.query("UPDATE addresses SET is_default = false WHERE customer_id = $1", [auth.sub]);
  await pool.query("UPDATE addresses SET is_default = true WHERE id = $1 AND customer_id = $2", [
    id,
    auth.sub,
  ]);
  res.json({ ok: true });
});

addressesRouter.delete("/:id", requireAuth(["customer"]), async (req, res) => {
  const auth = req.auth!;
  await pool.query("DELETE FROM addresses WHERE id = $1 AND customer_id = $2", [
    Number(req.params.id),
    auth.sub,
  ]);
  res.status(204).send();
});
