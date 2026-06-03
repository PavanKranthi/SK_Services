import { Router } from "express";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";

export const syncRouter = Router();

/** Pull all data needed after login into Room cache. */
syncRouter.get("/bootstrap", requireAuth(), async (req, res) => {
  const auth = req.auth!;
  const services = await pool.query("SELECT * FROM services ORDER BY name ASC, type ASC");
  const mapService = (row: Record<string, unknown>) => ({
    id: row.id,
    name: row.name,
    type: row.type,
    price: Number(row.price),
    isActive: row.is_active,
  });

  if (auth.role === "admin") {
    const [bookings, technicians] = await Promise.all([
      pool.query("SELECT * FROM bookings ORDER BY created_at DESC"),
      pool.query("SELECT * FROM technicians ORDER BY name ASC"),
    ]);
    res.json({
      services: services.rows.map(mapService),
      bookings: bookings.rows,
      technicians: technicians.rows.map((r) => ({
        id: r.id,
        name: r.name,
        email: r.email,
        services: r.services ?? "",
        status: r.status,
        role: "Technician",
      })),
    });
    return;
  }

  if (auth.role === "customer") {
    const [bookings, addresses, customer] = await Promise.all([
      pool.query("SELECT * FROM bookings WHERE customer_id = $1 ORDER BY created_at DESC", [
        auth.sub,
      ]),
      pool.query(
        "SELECT * FROM addresses WHERE customer_id = $1 ORDER BY is_default DESC, id DESC",
        [auth.sub]
      ),
      pool.query("SELECT * FROM customers WHERE id = $1", [auth.sub]),
    ]);
    const phone = customer.rows[0]?.phone ?? "";
    res.json({
      services: services.rows.filter((r) => r.is_active).map(mapService),
      bookings: bookings.rows,
      addresses: addresses.rows.map((r) => ({
        id: r.id,
        userPhone: phone,
        addressLine1: r.address_line1,
        addressLine2: r.address_line2,
        city: r.city,
        district: r.district,
        state: r.state,
        country: r.country,
        pinCode: r.pin_code,
        isDefault: r.is_default,
      })),
    });
    return;
  }

  res.json({ services: services.rows.map(mapService) });
});
