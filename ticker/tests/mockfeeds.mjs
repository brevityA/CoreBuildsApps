// Test fixture: hostile feed server for the VERIFICATION soak.
// Serves a healthy RSS feed plus a permanently-failing route so the
// real server path (SSRF → fetchFeed → parseFeed → backoff) can be
// exercised end-to-end. Bind is 127.0.0.1; reach it through the
// mock-*.test hosts mapped in /etc/hosts (SSRF blocks raw loopback).
import http from 'node:http';

const RSS = `<rss><channel><item><title>Leafs vs Habs — TSN4, SN 3</title></item></channel></rss>`;

http.createServer((req, res) => {
  if (req.url.startsWith('/good')) {
    res.writeHead(200, { 'content-type': 'application/rss+xml' });
    return res.end(RSS);
  }
  res.writeHead(502, { 'content-type': 'text/plain' });
  res.end('upstream down');
}).listen(8799, '127.0.0.1', () => console.log('mock feeds on 127.0.0.1:8799'));
