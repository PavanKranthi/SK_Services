-- Short-lived whitelist rows: phone may receive Firebase OTP for signup or reset_password.
CREATE TABLE IF NOT EXISTS phone_verification_sessions (
  phone TEXT NOT NULL,
  purpose TEXT NOT NULL CHECK (purpose IN ('signup', 'reset_password')),
  name TEXT,
  password_hash TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (phone, purpose)
);

CREATE INDEX IF NOT EXISTS idx_phone_verification_expires ON phone_verification_sessions (expires_at);
