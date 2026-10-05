// Set NEXT_PUBLIC_API_BASE_URL at build time when hosting the API elsewhere.
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || "")
  .replace(/\/+$/, "");

export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const headers = new Headers(options.headers);
    if (typeof options.body === "string" && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options, headers, credentials: "include", cache: "no-store", signal: controller.signal,
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) {
      throw new ApiError(data?.message || (response.status >= 500
        ? "The service is temporarily unavailable. Please try again."
        : "The request could not be completed."), response.status);
    }
    if (data === null) throw new ApiError("Unexpected server response. Please try again.", 502);
    return data as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(controller.signal.aborted
      ? "The server took too long to respond. Please try again."
      : "Unable to reach the service. Check your connection and try again.", 0);
  } finally { clearTimeout(timeout); }
}

export interface SessionUser {
  id: number; fullName: string; email: string; phone: string; roles: string[];
}

// The cookie is the session. Storage is only a best-effort display cache.
export function cacheUser(user: SessionUser | null) {
  try {
    if (user) localStorage.setItem("ciao_user", JSON.stringify(user));
    else localStorage.removeItem("ciao_user");
  } catch { /* Browsers can disable local storage; authentication still works. */ }
}

export function getCachedUser(): SessionUser | null {
  try {
    const raw = localStorage.getItem("ciao_user");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}


