# Icon-request broker — anonymous requests, filed as real GitHub issues

The icon pack's auditor can file a request from a single button press on the
TV. The reporter needs nothing: no GitHub account, no phone, no email. The
Android app POSTs three validated fields here; this Cloudflare Worker
authenticates as a GitHub App and files the issue.

```
TV auditor row ──POST {app_name, component, device}──> this worker
                                                        │
              validate + rate-limit + dedupe            │ GitHub App: issues: write
              (KV)                                      │ on brevityA/CoreBuildsApps
                                                        ▼
                                            issue "[Icon] <app>" by
                                            CoreBuilds-requests[bot],
                                            label "icon request"
                                            (a repeat press = a +1 comment)
```

**Trust split, in one sentence:** GitHub never accepts anonymous issues,
so *something* has to hold the credential — and that something is only this
worker, never the APK and never this repository. The GitHub App's
installation token is minted per-request and cached in KV (~50 min TTL);
the private key exists solely as a Workers secret, scoped to
**Issues: read/write on this one repo**. Losing the worker leaks exactly
one repo's issue tracker, revocable in one click.

## Why these exact design choices

- **Validated grammar, not free text.** `app_name` / `component` / `device`
  must match (`validate()` in worker.mjs); anything else is a 400. Spam
  cannot write paragraphs into your tracker.
- **Dedupe before create.** The worker searches open issues for the same
  `[Icon] <name>` title and *comments* a +1 instead of opening a duplicate —
  double-taps and double-users collapse automatically.
- **Rate limit:** per client IP, 5/hour by default (`RATE_LIMIT_PER_HOUR`),
  hourly buckets in KV. On the Workers free tier the daily caps
  (100k requests, 1k KV writes/day) sit far above what a missing-icon
  flow can ever need; the limiter exists for shape, not survival, and it
  fails **open** on KV write errors because the dedupe step is the real
  duplicate defense.
- **Label self-heal.** On the first request, if `icon request` doesn't
  exist as a label the worker creates it (the app's Issues: write covers it)
  — the issue forms reference the label but repo labels were minted by hand
  until now.

## Deploy (owner, one time, ~10 minutes)

1. **Create the GitHub App** under the repo owner's account:
   *Settings → Developer settings → GitHub Apps → New*:
   - Name: `CoreBuilds-requests` (that name becomes the bot identity)
   - Homepage: this repo's URL
   - Webhook: **off**
   - Permissions: Repository → **Issues: Read & write** (nothing else)
   - Where can it be installed: Only on this account
2. **Generate & download its private key** (required for server-to-server
   auth). Note the **App ID** on the About page.
3. **Install it** on `brevityA/CoreBuildsApps`
   (*Install App → only this repo*) and note the **Installation ID** from
   the resulting URL (`.../installations/<id>`).
4. **Create the Worker + KV:**
   ```bash
   cd tools/icon_request_broker
   npm i -g wrangler   # once
   wrangler login
   npx wrangler kv namespace create RATE_KV     # paste the id into wrangler.toml
   cp wrangler.toml.example wrangler.toml       # then fill the namespace id
   npx wrangler secret put GITHUB_APP_ID
   npx wrangler secret put GITHUB_APP_INSTALLATION_ID
   npx wrangler secret put GITHUB_APP_PRIVATE_KEY  # paste the whole PEM block
   npx wrangler deploy
   ```
5. **Smoke-test:**
   ```bash
   curl -X POST https://<name>.<subdomain>.workers.dev/ \
     -H 'Content-Type: application/json' \
     -d '{"app_name":"Deluart Test App","component":"com.deluart.test/.MainActivity","device":"Scanned on curl, Android 14, pack dev"}'
   # -> {"ok":true,"issue":N,"duplicate":false}  (then close that issue)
   ```
6. **Bake the URL into the pack** (this is the only app-side step):
   ```bash
   cd ../.. && python tools/build_issue_prefills.py \
     --endpoint https://<name>.<subdomain>.workers.dev/
   ```
   Release the pack; auditor presses file directly from then on. Until that
   value is baked, presses silently keep the QR fallback — nothing breaks.

## Turn it off

`npx wrangler delete` (or uninstall the GitHub App first to stop new tokens).
The app keeps working exactly as it did before: presses go straight to the
QR panel.

## Tests

`node --test` — 19 tests: payload grammar, issue-body parity with
`.github/ISSUE_TEMPLATE/1.new_icon_request.yml` (parsed, so a form edit
fails the parity test), hourly rate-limit buckets, a real RS256 JWT
sign-and-verify, and the full file-or-+1 flows against a stubbed GitHub API.
