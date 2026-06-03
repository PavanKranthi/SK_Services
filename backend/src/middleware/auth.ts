import type { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";

export type AuthRole = "customer" | "admin" | "technician";

export interface JwtPayload {
  role: AuthRole;
  sub: number;
  email: string;
}

declare global {
  namespace Express {
    interface Request {
      auth?: JwtPayload;
    }
  }
}

const secret = () => process.env.JWT_SECRET ?? "dev-secret-change-me";

export function signToken(payload: JwtPayload): string {
  return jwt.sign(payload, secret(), { expiresIn: "7d" });
}

export function requireAuth(roles?: AuthRole[]) {
  return (req: Request, res: Response, next: NextFunction) => {
    const header = req.headers.authorization;
    if (!header?.startsWith("Bearer ")) {
      res.status(401).json({ error: "Missing authorization token" });
      return;
    }
    try {
      const token = header.slice(7);
      const decoded = jwt.verify(token, secret()) as jwt.JwtPayload & JwtPayload;
      if (roles && !roles.includes(decoded.role)) {
        res.status(403).json({ error: "Forbidden for this role" });
        return;
      }
      req.auth = decoded;
      next();
    } catch {
      res.status(401).json({ error: "Invalid or expired token" });
    }
  };
}
