# Icon-request broker — anonymous requests, filed as real GitHub issues

The icon pack's auditor can file a request from a single button press on the
TV. The reporter needs nothing: no GitHub account, no phone, no email. The
Android app POSTs three validated fields here; this Cloudflare Worker
authenticates as a GitHub App and files the issue.

```
TV auditor row ──POST {app_name, component, device, mapped?}──> this worker
                                                        │
              validate + rate-limit + dedupe            │ GitHub App: issues: write
              (KV)                                      │ on brevityA/CoreBuildsApps
                                                        ▼
                                            issue "[Icon] <app>" by
                                            CoreBuilds-requests[bot],
                                            label "icon request"
                                            (a repeat press = a +1 comment)
```

`mapped: true` marks an app the pack already maps under a different
activity. That is a mapping report, not a request for art, so it is filed
the way `.github/ISSUE_TEMPLATE/2.icon_not_applying.yml` files one:
`[Not applying] <app>`, label `mapping`, that form's fields. Absent or
`false`, the request is a new-icon issue as above.

**Trust split, in one sentence:** GitHub never accepts anonymous issues,
so *something* has to hold the credential — and that something is only this
worker, never the APK and never this repository. The GitHub App's
installation token is minted per-request and cached in KV (~50 min TTL);
the private key exists solely as a Workers secret, scoped to
**Issues: read/write on this one repo**. Losing the worker leaks exactly
one repo's issue tracker, revocable in one click.

## Ops lineage — standing on the webtools worker's shoulders

This broker deliberately adopts the ops conventions of the Core-Builds
`cloudflare-worker` (**core-builds-cors-proxy**, the AIOStreams webtools
service you already run in production): a build-tag (`WORKER_VERSION`) the
smoke check compares to the checkout, a byte-capped body reader
(`readCapped` — a request this small never streams), three-layer rate
limiting (**Workers Rate Limiting binding → KV hourly bucket →
in-isolate floor** — the second layer counts a write collision against the
caller, as that worker's audit decided), an `observability` block with
structured secret-free `logEvent()` lines, and a production-safe
`smoke.mjs` whose checks never create a single issue.

Deliberate differences from a copy: no Durable Object store (there is no
cross-instance document to hold), no Discord *requirement* (it's the
fallback sink, GitHub is the prime sink), no STATS counters beyond the
logs (a missing-icon flow doesn't need a dashboard; the issue list *is*
the dashboard).

### Three deployment shapes, pick one

| | A · dedicated worker *(recommended)* | B · lane in the webtools worker | C · Discord-only start |
|---|---|---|---|
| Code home | **this kit** (`tools/icon_request_broker/`) — the parity test against the issue form lives beside it | the `cloudflare-worker` repo — needs a port + its release discipline + widening its exposure audit | A's worker, but with only the webhook secret set |
| New credentials | GitHub App (Issues: write) + 3 secrets | same, plus touching an audited production file | exactly one: `ICON_REQUEST_DISCORD_WEBHOOK_URL` |
| Time to first filed request | ~10 min | longer | ~5 min, requests appear in Discord first |
| Blast radius | its own worker | shared with the webtools | its own worker |

C upgrades to A whenever the GitHub App gets created: set the three App
secrets and the webhook path silently stops being used (the worker prefers
GitHub when configured). The author's suggestion: **start at C this week,
flip to A when there's appetite for the GitHub App registration** —
reporters get the one-press flow today either way.

## Why these exact design choices

- **Validated grammar, not free text.** `app_name` / `component` / `device`
  (and the boolean `mapped`) must match (`validate()` in worker.mjs); anything else is a 400. Spam
  cannot write paragraphs into your tracker.
- **Dedupe before create.** The worker searches open issues for the same
  `[Icon] <name>` title (`[Not applying] <name>` for a mapping report) and
  *comments* a +1 instead of opening a duplicate —
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
- **Origin-reflect CORS + OPTIONS preflight.** Android's OkHttp ignores
  CORS, but the QR interstitial (docs/icon-request/index.html on
  GitHub Pages) POSTs from a browser, which preflights cross-origin
  JSON. Responses reflect the request's `Origin` header when present and
  preflights get a 204 — safe here because the endpoint is anonymous,
  cookie-free and rate-limited by design; CORS changes nothing else about
  what a curl could already do.

## Rehearse locally first (zero cloud, no credentials)

`node rehearse-local.mjs` boots the worker in the **real Workers runtime**
(`wrangler dev` — no Cloudflare account), swaps in a throwaway receiver for
the Discord webhook, and drives the exact sequence the staging rehearsal
would: health/version → routing → validation → card landing in the receiver
→ the KV hourly bucket clamping the next press. It proves things unit tests
cannot — the worker parses *as deployed* and a subrequest actually leaves
the isolate — and it already earned its keep: the first run caught a
boot-blocking bug (a top-level `export const` string, legal in Node, is
rejected by workerd — hence `constants.mjs` and the entry-exports test).

Two semantics the rehearsal locks in:

- **Every request consumes the rate budget, including refused ones** (the
  clamp runs before validation — cheapest alternative first, and a flood of
  malformed payloads trips the limiter rather than the parser).
- Each source IP carries its own hourly bucket.

It never creates requests, files issues, or touches production.

## Deploy (owner, one time, ~10 minutes — rehearse on staging first)

**Same repo convention as the webtools worker: staging first.**
`wrangler.toml.example` carries an `[env.staging]` block with its own
worker name, KV namespace and rate budget, so nothing a rehearsal does
can touch the production lane — and the deploy CI runs dispatch-only
(`.github/workflows/deploy-icon-request-broker.yml`) with a
staging/production choice exactly like the other repo's.

```bash
cd tools/icon_request_broker
npm i -g wrangler && wrangler login

# ▸ Rehearsal namespace + worker
npx wrangler kv namespace create RATE_KV --env staging
cp wrangler.toml.example wrangler.toml      # paste the staging id where marked
npx wrangler secret put ICON_REQUEST_DISCORD_WEBHOOK_URL --env staging   # test channel
npx wrangler deploy --env staging
node smoke.mjs --strict https://corebuilds-icon-request-staging.<subdomain>.workers.dev
# and if you want the create-path proven end-to-end on staging:
curl -X POST .../icon-request-staging.../ -H 'Content-Type: application/json'   -d '{"app_name":"Rehearsal App","component":"com.rehearse/.Main","device":"Scanned on curl"}'
# expect the card in the test Discord channel

# ▸ Production (same steps, no --env; GitHub trio or the community webhook)
npx wrangler kv namespace create RATE_KV
#   (fill the production id in wrangler.toml, then:)
npx wrangler secret put GITHUB_APP_ID            # A-path trio…
npx wrangler secret put GITHUB_APP_INSTALLATION_ID
npx wrangler secret put GITHUB_APP_PRIVATE_KEY   # …or C-path:
npx wrangler secret put ICON_REQUEST_DISCORD_WEBHOOK_URL
npx wrangler deploy
node smoke.mjs --strict https://corebuilds-icon-request.<subdomain>.workers.dev
```

**CI deploy runs from Core-Builds**, where the Cloudflare credentials
already live (`brevityA/Core-Builds` → Actions → *Deploy icon-request
broker (CoreBuildsApps)*, default staging, never on its own). It checks
this repo out at a chosen ref, runs `node --test`, finds or creates the
environment's `RATE_KV` namespace, deploys and smokes, using the same
secrets and environments as core-builds-cors-proxy. GitHub secrets cannot
cross repositories, so this repo's own workflow below would need its own
copy of the token; it stays as the fallback: `secrets.CF_API_TOKEN`,
`vars.CLOUDFLARE_ACCOUNT_ID`, `vars.WRK_ICONREQ_KV_ID_{STAGING,PRODUCTION}`
and either `vars.WORKERS_DEV_SUBDOMAIN` or the `url` input each run.
Either way CI deploys code only; worker secrets stay with wrangler.

The rate-limit `namespace_id`s (3201 production, 3202 staging) are
account-wide, and this worker shares its account with the cors-proxy,
which holds 3001-3007 and 3101-3107. The Core-Builds workflow refuses to
deploy on any overlap.

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

   # GitHub path (A) — all three:
   npx wrangler secret put GITHUB_APP_ID
   npx wrangler secret put GITHUB_APP_INSTALLATION_ID
   npx wrangler secret put GITHUB_APP_PRIVATE_KEY  # paste the whole PEM block

   # OR Discord-to-start (C) — one secret; upgrade later by adding the trio:
   npx wrangler secret put ICON_REQUEST_DISCORD_WEBHOOK_URL

   npx wrangler deploy
   ```
5. **Smoke-test (never creates an issue):**
   ```bash
   node smoke.mjs --strict https://<name>.<subdomain>.workers.dev
   ```
   Then, if you want the create-path proven once against GitHub:
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

`node --test` — 36 contracts: payload grammar, issue-body parity with
`.github/ISSUE_TEMPLATE/1.new_icon_request.yml` and, for mapping reports,
`2.icon_not_applying.yml` (parsed, so a form edit fails the parity test),
each form's title prefix and label, hourly rate-limit buckets, a real RS256 JWT
sign-and-verify, the full file-or-+1 flows against a stubbed GitHub API,
the Discord fallback, and the workerd entry-export constraint. For the
deployed-runtime proof run `npm run rehearse` (see above).
