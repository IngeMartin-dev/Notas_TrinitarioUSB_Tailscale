// Servidor local de la compilación de PRODUCCIÓN (para probar/usar la PWA).
//
// Por qué existe: `ng serve` modifica los archivos al vuelo, y el service worker
// de Angular (PWA) valida cada archivo contra ngsw.json por su hash; con ng serve
// no coinciden y el service worker queda degradado. Aquí se sirven los archivos
// tal cual salieron de `ng build`, así que el service worker funciona de verdad.
//
// Hace lo mismo que el proxy de desarrollo (proxy.conf.json): reenvía /api,
// /uploads y /Firmas al backend (localhost:8080), de modo que solo hace falta
// publicar UN puerto (4200) con Tailscale Funnel.
//
// Uso:  npm run start:pwa      (compila y arranca)
//       node serve-prod.mjs    (si ya compilaste con `ng build`)
// Variables opcionales: PORT (4200), HOST (127.0.0.1), BACKEND (http://localhost:8080), DIST.

import http from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { join, extname, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const PORT = Number(process.env.PORT || 4200);
const HOST = process.env.HOST || '127.0.0.1';
const BACKEND = new URL(process.env.BACKEND || 'http://localhost:8080');
const DIST = resolve(process.env.DIST || join(here, 'dist', 'Frontend', 'browser'));
const PROXY_PREFIXES = ['/api', '/uploads', '/Firmas'];

if (!existsSync(join(DIST, 'index.html'))) {
  console.error(`No encuentro la compilación en ${DIST}\nEjecuta primero:  npm run build`);
  process.exit(1);
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
};

// Estos NUNCA deben quedar en la caché del navegador: son los que avisan de versiones nuevas.
const NO_CACHE = new Set([
  '/index.html', '/ngsw.json', '/ngsw-worker.js', '/safety-worker.js',
  '/manifest.webmanifest', '/firebase-messaging-sw.js',
]);

function proxy(req, res) {
  const upstream = http.request(
    {
      hostname: BACKEND.hostname,
      port: BACKEND.port || 80,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: BACKEND.host }, // equivale a changeOrigin: true
    },
    (up) => {
      res.writeHead(up.statusCode || 502, up.headers);
      up.pipe(res);
    }
  );
  upstream.on('error', (err) => {
    console.error(`[proxy] ${req.method} ${req.url} → ${err.message}`);
    if (!res.headersSent) res.writeHead(502, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ error: 'Backend no disponible', detail: err.code || err.message }));
  });
  req.pipe(upstream);
}

function sendFile(res, file, urlPath) {
  const ext = extname(file).toLowerCase();
  const headers = { 'content-type': MIME[ext] || 'application/octet-stream' };
  if (NO_CACHE.has(urlPath) || ext === '.html') {
    headers['cache-control'] = 'no-cache';
  } else if (/-[A-Z0-9]{8}\.(js|css)$/.test(file)) {
    headers['cache-control'] = 'public, max-age=31536000, immutable'; // archivos con hash en el nombre
  } else {
    headers['cache-control'] = 'public, max-age=3600';
  }
  headers['content-length'] = statSync(file).size;
  res.writeHead(200, headers);
  createReadStream(file).pipe(res);
}

const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);

  if (PROXY_PREFIXES.some((p) => urlPath === p || urlPath.startsWith(p + '/'))) {
    return proxy(req, res);
  }

  const target = normalize(join(DIST, urlPath));
  if (target !== DIST && !target.startsWith(DIST + sep)) {
    res.writeHead(403).end('Forbidden');
    return;
  }

  if (existsSync(target) && statSync(target).isFile()) {
    return sendFile(res, target, urlPath);
  }

  // Ruta de la app (Angular router) → index.html. Un archivo que no existe → 404 real.
  if (extname(urlPath)) {
    res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' }).end('No encontrado');
    return;
  }
  sendFile(res, join(DIST, 'index.html'), '/index.html');
});

server.listen(PORT, HOST, () => {
  console.log(`Notas Trinitario (producción) en http://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}`);
  console.log(`Sirviendo ${DIST}`);
  console.log(`Backend: ${BACKEND.origin}  (proxy: ${PROXY_PREFIXES.join(', ')})`);
});
