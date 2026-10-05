// Keep session cookies on the website's origin, including LAN and 127.0.0.1 access.
export const runtime = "nodejs";
export const dynamic = "force-dynamic";

async function proxy(request: Request) {
  const incoming = new URL(request.url);
  if (!["GET", "HEAD"].includes(request.method)) {
    const origin = request.headers.get("origin");
    // Next may normalize request.url to its internal localhost address.
    // Host retains the browser-facing authority (including its port).
    if (origin && new URL(origin).host !== request.headers.get("host")) {
      return Response.json({ message: "Request origin is not allowed." }, { status: 403 });
    }
  }
  const base = process.env.BACKEND_URL || "http://127.0.0.1:8080";
  const url = new URL(incoming.pathname + incoming.search, base);
  const headers = new Headers();
  for (const name of ["content-type", "accept", "cookie", "authorization"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  try {
    const upstream = await fetch(url, {
      method: request.method, headers, redirect: "manual", cache: "no-store",
      body: ["GET", "HEAD"].includes(request.method) ? undefined : await request.arrayBuffer(),
      signal: AbortSignal.timeout(10000),
    });
    const output = new Headers({ "Cache-Control": "no-store" });
    const type = upstream.headers.get("content-type");
    if (type) output.set("content-type", type);
    for (const cookie of upstream.headers.getSetCookie()) output.append("set-cookie", cookie);
    const body = await upstream.arrayBuffer();
    return new Response([204, 304].includes(upstream.status) || request.method === "HEAD" ? null : body,
      { status: upstream.status, headers: output });
  } catch {
    return Response.json({ message: "The service is temporarily unavailable. Please try again shortly." },
      { status: 503, headers: { "Cache-Control": "no-store" } });
  }
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE, proxy as HEAD };
