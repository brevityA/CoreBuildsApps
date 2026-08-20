const CACHE = 'core-line-v1';
const SHELL = [
  './', './index.html', './css/app.css',
  './js/app.js', './js/tv.js', './js/state.js', './js/qr.js',
  './icon.svg',
  '/lib/parser.mjs', '/lib/scoreboard.mjs', '/lib/client-slate.mjs',
  '/lib/channels.mjs', '/lib/teams.mjs',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (url.pathname.startsWith('/api/')) return;
  event.respondWith(
    fetch(event.request).then((res) => {
      const copy = res.clone();
      caches.open(CACHE).then((cache) => cache.put(event.request, copy));
      return res;
    }).catch(() => caches.match(event.request)),
  );
});
