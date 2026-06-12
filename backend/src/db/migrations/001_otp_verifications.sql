-- Pending signup OTP records (expires automatically via application logic)
CREATE TABLE IF NOT EXISTS otp_verifications (
  phone TEXT PRIMARY KEY,
  otp_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
