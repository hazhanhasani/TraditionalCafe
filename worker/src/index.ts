const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
    },
  });

export default {
  async fetch(request) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
          "access-control-allow-headers": "content-type,authorization",
        },
      });
    }

    if (url.pathname === "/api/health") {
      return json({
        ok: true,
        service: "TraditionalCafe API",
        version: "0.1.0",
      });
    }

    if (url.pathname === "/") {
      return json({
        name: "TraditionalCafe",
        message: "API is running",
        health: "/api/health",
      });
    }

    return json({ ok: false, error: "not_found" }, 404);
  },
};
