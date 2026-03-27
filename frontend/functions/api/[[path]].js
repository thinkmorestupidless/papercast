/**
 * Cloudflare Pages Function — reverse proxy for Akka backend.
 *
 * Handles all requests to /api/* and forwards them to the Akka service,
 * stripping the /api prefix. This avoids CORS entirely: the browser only
 * ever talks to the Cloudflare Pages domain (same origin).
 *
 * AKKA_SERVICE_URL is set in wrangler.toml [vars] and can be overridden
 * in the Cloudflare Pages dashboard (Settings → Environment Variables).
 */
export async function onRequest(context) {
  const { request, env } = context

  const serviceUrl = env.AKKA_SERVICE_URL
  if (!serviceUrl) {
    return new Response('AKKA_SERVICE_URL is not configured', { status: 502 })
  }

  const url = new URL(request.url)
  const targetPath = url.pathname.replace(/^\/api/, '') + url.search
  const targetUrl = serviceUrl.replace(/\/$/, '') + targetPath

  const hasBody = !['GET', 'HEAD'].includes(request.method)

  try {
    const body = hasBody ? await request.arrayBuffer() : undefined
    const response = await fetch(targetUrl, {
      method: request.method,
      headers: request.headers,
      body,
    })
    return response
  } catch (err) {
    return new Response(`Proxy error: ${err.message}`, { status: 502 })
  }
}
