export type Row = Record<string, any>;
export type Session = { accessToken: string; refreshToken: string; user: Row };
let session: Session | null = null;
let refreshing: Promise<void> | null = null;
export const getSession = () => session;
export function setSession(value: Session | null) {
  session = value;
  window.dispatchEvent(new Event("session"));
}
export async function api<T = any>(
  path: string,
  init: RequestInit = {},
  retry = true,
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init.headers as Record<string, string>),
  };
  if (session) headers.Authorization = `Bearer ${session.accessToken}`;
  const response = await fetch(path, { ...init, headers });
  if (
    response.status === 401 &&
    session &&
    retry &&
    !path.startsWith("/api/auth/")
  ) {
    if (!refreshing)
      refreshing = (async () => {
        const r = await fetch("/api/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token: session?.refreshToken }),
        });
        if (!r.ok) {
          setSession(null);
          throw new Error("Your session expired. Sign in again.");
        }
        setSession(await r.json());
      })().finally(() => {
        refreshing = null;
      });
    await refreshing;
    return api(path, init, false);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  let data: any = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(
      `The server could not process the request (${response.status}). Check the configured frontend origin.`,
    );
  }
  if (!response.ok)
    throw new Error(data.message || `Request failed (${response.status})`);
  return data;
}
export function post(path: string, body?: unknown, key = crypto.randomUUID()) {
  return api(path, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
export const money = (value: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(value);
export const date = (value: string) =>
  value
    ? new Date(value).toLocaleString("en-IN", {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "—";
export const label = (value: string) =>
  value
    ?.toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (s) => s.toUpperCase()) || "—";
