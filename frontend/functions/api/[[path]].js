/**
 * Cloudflare Pages Function — reverse proxy for Akka backend.
 *
 * Handles all requests to /api/* and forwards them to the Akka service,
 * stripping the /api prefix. This avoids CORS entirely: the browser only
 * ever talks to the Cloudflare Pages domain (same origin).
 *
 * Required env var (set in Cloudflare Pages dashboard → Settings → Variables):
 *   AKKA_SERVICE_URL  e.g. https://young-fire-2481.us-east1.akka.app
 */
export async function onRequest(context) {
  const { request, env } = context

  if (!env.AKKA_SERVICE_URL) {
    return new Response('AKKA_SERVICE_URL is not configured', { status: 502 })
  }

  const url = new URL(request.url)
  const targetPath = url.pathname.replace(/^\/api/, '') + url.search
  const targetUrl = env.AKKA_SERVICE_URL.replace(/\/$/, '') + targetPath

  const hasBody = !['GET', 'HEAD'].includes(request.method)

  return fetch(targetUrl, {
    method: request.method,
    headers: request.headers,
    body: hasBody ? request.body : undefined,
  })
}
