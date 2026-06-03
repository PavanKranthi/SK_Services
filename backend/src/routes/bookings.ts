import { Router } from "express";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";

export const bookingsRouter = Router();

function mapBooking(row: Record<string, unknown>) {
  return {
    id: row.id,
    customerName: row.customer_name,
    customerPhone: row.customer_phone,
    serviceId: row.service_id,
    serviceName: row.service_name,
    serviceType: row.service_type,
    pricePerUnit: Number(row.price_per_unit),
    unitsCount: row.units_count,
    date: row.booking_date,
    timeSlot: row.time_slot,
    status: row.status,
    assignedTechnicianId: row.assigned_technician_id,
    assignedTechnicianName: row.assigned_technician_name,
    serviceAddress: row.service_address,
    createdAt: new Date(row.created_at as string).getTime(),
  };
}

bookingsRouter.get("/", requireAuth(), async (req, res) => {
  const auth = req.auth!;
  let result;
  if (auth.role === "admin") {
    result = await pool.query("SELECT * FROM bookings ORDER BY created_at DESC");
  } else if (auth.role === "customer") {
    result = await pool.query(
      "SELECT * FROM bookings WHERE customer_id = $1 ORDER BY created_at DESC",
      [auth.sub]
    );
  } else {
    result = await pool.query(
      "SELECT * FROM bookings WHERE assigned_technician_id = $1 ORDER BY created_at DESC",
      [auth.sub]
    );
  }
  res.json(result.rows.map(mapBooking));
});

bookingsRouter.post("/", requireAuth(["customer"]), async (req, res) => {
  const auth = req.auth!;
  const body = req.body as {
    serviceId?: number;
    serviceName?: string;
    serviceType?: string;
    pricePerUnit?: number;
    unitsCount?: number;
    date?: string;
    timeSlot?: string;
    serviceAddress?: string;
    customerName?: string;
    customerPhone?: string;
  };
  const customer = await pool.query("SELECT * FROM customers WHERE id = $1", [auth.sub]);
  const c = customer.rows[0];
  if (!c) {
    res.status(404).json({ error: "Customer not found" });
    return;
  }
  const inserted = await pool.query(
    `INSERT INTO bookings (
      customer_id, customer_name, customer_phone, service_id, service_name, service_type,
      price_per_unit, units_count, booking_date, time_slot, status, service_address
    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,'Pending',$11) RETURNING *`,
    [
      auth.sub,
      body.customerName ?? c.name,
      body.customerPhone ?? c.phone,
      body.serviceId,
      body.serviceName,
      body.serviceType,
      body.pricePerUnit,
      body.unitsCount ?? 1,
      body.date,
      body.timeSlot,
      body.serviceAddress ?? "Default Service Location",
    ]
  );
  res.status(201).json(mapBooking(inserted.rows[0]));
});

bookingsRouter.patch("/:id", requireAuth(["admin"]), async (req, res) => {
  const id = Number(req.params.id);
  const body = req.body as {
    status?: string;
    assignedTechnicianId?: number;
    assignedTechnicianName?: string;
  };
  if (body.assignedTechnicianId != null) {
    await pool.query(
      `UPDATE bookings SET status = $1, assigned_technician_id = $2, assigned_technician_name = $3
       WHERE id = $4`,
      [body.status ?? "Confirmed", body.assignedTechnicianId, body.assignedTechnicianName, id]
    );
  } else if (body.status) {
    await pool.query("UPDATE bookings SET status = $1 WHERE id = $2", [body.status, id]);
  }
  const updated = await pool.query("SELECT * FROM bookings WHERE id = $1", [id]);
  if (!updated.rows[0]) {
    res.status(404).json({ error: "Booking not found" });
    return;
  }
  res.json(mapBooking(updated.rows[0]));
});

bookingsRouter.get("/active-for-date", requireAuth(), async (req, res) => {
  const date = req.query.date as string;
  const result = await pool.query(
    `SELECT * FROM bookings WHERE booking_date = $1 AND status IN ('Confirmed', 'In Progress')`,
    [date]
  );
  res.json(result.rows.map(mapBooking));
});
