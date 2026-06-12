import bcrypt from "bcrypt";
import { pool } from "../db/pool.js";

const SESSION_TTL_MS = 10 * 60 * 1000;

export type PhoneSessionPurpose = "signup" | "reset_password";

export function normalizeIndianPhone(raw: string): string {
  const digits = raw.replace(/\D/g, "");
  const phone =
    digits.length === 12 && digits.startsWith("91") ? digits.slice(2) : digits;
  if (!/^[6-9]\d{9}$/.test(phone)) {
    throw Object.assign(new Error("Invalid Indian mobile number"), { status: 400 });
  }
  return phone;
}

export function isValidIndianPhone(raw: string): boolean {
  try {
    normalizeIndianPhone(raw);
    return true;
  } catch {
    return false;
  }
}

/** Whitelist phone for Firebase OTP (signup). */
export async function createSignupSession(
  rawPhone: string,
  name: string,
  password: string
): Promise<string> {
  const phone = normalizeIndianPhone(rawPhone);
  const existing = await pool.query("SELECT id FROM customers WHERE phone = $1 LIMIT 1", [phone]);
  if (existing.rows.length > 0) {
    throw Object.assign(new Error("Phone number is already registered"), { status: 409 });
  }
  const passwordHash = await bcrypt.hash(password, 10);
  const expiresAt = new Date(Date.now() + SESSION_TTL_MS);
  await pool.query(
    `INSERT INTO phone_verification_sessions (phone, purpose, name, password_hash, expires_at)
     VALUES ($1, 'signup', $2, $3, $4)
     ON CONFLICT (phone, purpose) DO UPDATE SET
       name = EXCLUDED.name,
       password_hash = EXCLUDED.password_hash,
       expires_at = EXCLUDED.expires_at,
       created_at = NOW()`,
    [phone, name.trim(), passwordHash, expiresAt]
  );
  return phone;
}

/** Whitelist phone for Firebase OTP (reset password). */
export async function createResetPasswordSession(rawPhone: string): Promise<string> {
  const phone = normalizeIndianPhone(rawPhone);
  const existing = await pool.query("SELECT id FROM customers WHERE phone = $1 LIMIT 1", [phone]);
  if (existing.rows.length === 0) {
    throw Object.assign(new Error("No account found for this phone number"), { status: 404 });
  }
  const expiresAt = new Date(Date.now() + SESSION_TTL_MS);
  await pool.query(
    `INSERT INTO phone_verification_sessions (phone, purpose, name, password_hash, expires_at)
     VALUES ($1, 'reset_password', NULL, NULL, $2)
     ON CONFLICT (phone, purpose) DO UPDATE SET
       expires_at = EXCLUDED.expires_at,
       created_at = NOW()`,
    [phone, expiresAt]
  );
  return phone;
}

type ConsumedSignupSession = {
  purpose: "signup";
  name: string;
  passwordHash: string;
};

type ConsumedResetSession = {
  purpose: "reset_password";
};

/** Confirm whitelist entry exists, then remove it (one-time use). */
export async function consumePhoneSession(
  phone10: string,
  purpose: PhoneSessionPurpose
): Promise<ConsumedSignupSession | ConsumedResetSession> {
  const result = await pool.query(
    `SELECT * FROM phone_verification_sessions
     WHERE phone = $1 AND purpose = $2 LIMIT 1`,
    [phone10, purpose]
  );
  const row = result.rows[0];
  if (!row) {
    throw Object.assign(
      new Error("Phone verification session expired. Please start again."),
      { status: 400 }
    );
  }
  if (new Date(row.expires_at) < new Date()) {
    await pool.query(
      "DELETE FROM phone_verification_sessions WHERE phone = $1 AND purpose = $2",
      [phone10, purpose]
    );
    throw Object.assign(new Error("Verification session expired. Please start again."), {
      status: 400,
    });
  }
  await pool.query(
    "DELETE FROM phone_verification_sessions WHERE phone = $1 AND purpose = $2",
    [phone10, purpose]
  );
  if (purpose === "signup") {
    return {
      purpose: "signup",
      name: row.name as string,
      passwordHash: row.password_hash as string,
    };
  }
  return { purpose: "reset_password" };
}
