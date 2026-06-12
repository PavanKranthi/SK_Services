import admin from "firebase-admin";
import fs from "fs";

let initialized = false;

function initFirebaseAdmin(): boolean {
  if (initialized) return true;
  const jsonPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH?.trim();
  const jsonInline = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
  try {
    if (jsonPath && fs.existsSync(jsonPath)) {
      const serviceAccount = JSON.parse(fs.readFileSync(jsonPath, "utf8"));
      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
      initialized = true;
      return true;
    }
    if (jsonInline) {
      const serviceAccount = JSON.parse(jsonInline);
      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
      initialized = true;
      return true;
    }
  } catch (err) {
    console.error("Firebase Admin init failed:", err);
  }
  return false;
}

/** Verify Firebase ID token from Phone Auth and return E.164 phone (+91XXXXXXXXXX). */
export async function verifyFirebasePhoneToken(
  idToken: string
): Promise<{ phoneE164: string; phone10: string }> {
  if (!initFirebaseAdmin()) {
    throw Object.assign(new Error("Firebase Admin is not configured on the server"), {
      status: 503,
    });
  }
  const decoded = await admin.auth().verifyIdToken(idToken);
  const phone = decoded.phone_number;
  if (!phone) {
    throw Object.assign(new Error("Firebase token does not contain a phone number"), {
      status: 401,
    });
  }
  const digits = phone.replace(/\D/g, "");
  const phone10 =
    digits.length === 12 && digits.startsWith("91")
      ? digits.slice(2)
      : digits.length === 10
        ? digits
        : "";
  if (!/^[6-9]\d{9}$/.test(phone10)) {
    throw Object.assign(new Error("Invalid phone number in Firebase token"), { status: 401 });
  }
  return { phoneE164: phone, phone10 };
}
