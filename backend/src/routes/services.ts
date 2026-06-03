import { Router } from "express";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";

export const servicesRouter = Router();

function mapService(row: Record<string, unknown>) {
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    price: Number(row.price),
    isActive: row.is_active,
  };
}

servicesRouter.get("/", requireAuth(), async (_req, res) => {
  const result = await pool.query("SELECT * FROM services ORDER BY name ASC, type ASC");
  res.json(result.rows.map(mapService));
});

servicesRouter.post("/", requireAuth(["admin"]), async (req, res) => {
  const { name, type, price, isActive } = req.body as {
    name?: string;
    type?: string;
    price?: number;
    isActive?: boolean;
  };
  const inserted = await pool.query(
    `INSERT INTO services (name, type, price, is_active) VALUES ($1,$2,$3,$4) RETURNING *`,
    [name, type, price, isActive ?? true]
  );
  res.status(201).json(mapService(inserted.rows[0]));
});

servicesRouter.patch("/:id", requireAuth(["admin"]), async (req, res) => {
  const id = Number(req.params.id);
  const { isActive, name, type, price } = req.body as {
    isActive?: boolean;
    name?: string;
    type?: string;
    price?: number;
  };
  if (isActive !== undefined) {
    await pool.query("UPDATE services SET is_active = $1 WHERE id = $2", [isActive, id]);
  }
  if (name || type || price !== undefined) {
    await pool.query(
      "UPDATE services SET name = COALESCE($1, name), type = COALESCE($2, type), price = COALESCE($3, price) WHERE id = $4",
      [name ?? null, type ?? null, price ?? null, id]
    );
  }
  const row = await pool.query("SELECT * FROM services WHERE id = $1", [id]);
  res.json(mapService(row.rows[0]));
});

servicesRouter.delete("/:id", requireAuth(["admin"]), async (req, res) => {
  await pool.query("DELETE FROM services WHERE id = $1", [Number(req.params.id)]);
  res.status(204).send();
});
