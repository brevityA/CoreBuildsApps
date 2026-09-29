/**
 * Channel name normalizer.
 * Turns the messy tokens people actually write — "epn", "tsn 4", "sn3",
 * "sportsnet ontario" — into the short bugs a ticker should show.
 */

const ALIAS_PAIRS = [
  ['espn news', 'ESPNEWS'],
  ['espnews', 'ESPNEWS'],
  ['espn plus', 'ESPN+'],
  ['espn+', 'ESPN+'],
  ['espn2', 'ESPN2'],
  ['espnu', 'ESPNU'],
  ['espn 2', 'ESPN2'],
  ['espn u', 'ESPNU'],
  ['epn', 'ESPN'],
  ['espn', 'ESPN'],
  ['abc', 'ABC'],
  ['nbc sports', 'NBCSN'],
  ['nbcsn', 'NBCSN'],
  ['nbc', 'NBC'],
  ['cbs sports', 'CBSSN'],
  ['cbssn', 'CBSSN'],
  ['cbs', 'CBS'],
  ['fox sports 1', 'FS1'],
  ['fox sports 2', 'FS2'],
  ['fox sports', 'FS1'],
  ['fox', 'FOX'],
  ['fs1', 'FS1'],
  ['fs2', 'FS2'],
  ['fsn', 'FSN'],
  ['btn', 'BTN'],
  ['sec network', 'SECN'],
  ['secn', 'SECN'],
  ['accn', 'ACCN'],
  ['pac-12', 'P12N'],
  ['longhorn', 'LHN'],
  ['tnt sports', 'TNT'],
  ['tnt', 'TNT'],
  ['tbs', 'TBS'],
  ['usa network', 'USA'],
  ['usa', 'USA'],
  ['nba tv', 'NBA TV'],
  ['nbatv', 'NBA TV'],
  ['nhl network', 'NHLN'],
  ['nhln', 'NHLN'],
  ['nhl net', 'NHLN'],
  ['mlb network', 'MLBN'],
  ['mlbn', 'MLBN'],
  ['mlb.tv', 'MLB.TV'],
  ['mlb tv', 'MLB.TV'],
  ['nfl network', 'NFLN'],
  ['nfln', 'NFLN'],
  ['nfl redzone', 'NFL RZ'],
  ['redzone', 'NFL RZ'],
  ['nfl+', 'NFL+'],
  ['prime video', 'PRIME'],
  ['amazon prime', 'PRIME'],
  ['amazon', 'PRIME'],
  ['prime', 'PRIME'],
  ['apple tv+', 'APPLE'],
  ['apple tv', 'APPLE'],
  ['appletv', 'APPLE'],
  ['peacock', 'PEACOCK'],
  ['paramount+', 'P+'],
  ['paramount plus', 'P+'],
  ['youtube tv', 'YTTV'],
  ['youtube', 'YT'],
  ['max', 'MAX'],
  ['hbo max', 'MAX'],
  ['fubo', 'FUBO'],
  ['sling', 'SLING'],
  ['dazn', 'DAZN'],
  ['bein sports', 'BEIN'],
  ['bein', 'BEIN'],
  ['tnt sports 1', 'TNT 1'],
  ['sky sports main event', 'SKY ME'],
  ['sky sports premier league', 'SKY PL'],
  ['sky sports', 'SKY'],
  ['bt sport', 'TNT SP'],
  ['now tv', 'NOW'],
  ['tsn 1', 'TSN1'],
  ['tsn 2', 'TSN2'],
  ['tsn 3', 'TSN3'],
  ['tsn 4', 'TSN4'],
  ['tsn 5', 'TSN5'],
  ['tsn1', 'TSN1'],
  ['tsn2', 'TSN2'],
  ['tsn3', 'TSN3'],
  ['tsn4', 'TSN4'],
  ['tsn5', 'TSN5'],
  ['tsn', 'TSN'],
  ['sportsnet one', 'SN 1'],
  ['sportsnet 360', 'SN360'],
  ['sportsnet ontario', 'SN ONT'],
  ['sportsnet west', 'SN W'],
  ['sportsnet east', 'SN E'],
  ['sportsnet pacific', 'SN PAC'],
  ['sportsnet world', 'SN WLD'],
  ['sportsnet+', 'SN+'],
  ['sportsnet plus', 'SN+'],
  ['sn ontario', 'SN ONT'],
  ['sn ont', 'SN ONT'],
  ['sn west', 'SN W'],
  ['sn east', 'SN E'],
  ['sn pacific', 'SN PAC'],
  ['sn360', 'SN360'],
  ['sn 360', 'SN360'],
  ['sn1', 'SN 1'],
  ['sn 1', 'SN 1'],
  ['sn2', 'SN 2'],
  ['sn 2', 'SN 2'],
  ['sn3', 'SN 3'],
  ['sn 3', 'SN 3'],
  ['sn4', 'SN 4'],
  ['sn 4', 'SN 4'],
  ['sn5', 'SN 5'],
  ['sn 5', 'SN 5'],
  ['sn+', 'SN+'],
  ['sportsnet', 'SN'],
  ['rds', 'RDS'],
  ['tva sports', 'TVA'],
  ['tva', 'TVA'],
  ['cbc', 'CBC'],
  ['citytv', 'CITY'],
  ['ctv', 'CTV'],
  ['globaltv', 'GLOBAL'],
  ['the sports network', 'TSN'],
  ['willow', 'WILLOW'],
  ['golf channel', 'GOLF'],
  ['tennis channel', 'TENNIS'],
  ['olympic channel', 'OLY'],
  ['univision', 'UNI'],
  ['unimas', 'UNIMAS'],
  ['telemundo', 'TMD'],
  ['galavision', 'GALA'],
  ['fox deportes', 'FXD'],
  ['espn deportes', 'ESPND'],
  ['tvazteca', 'AZTECA'],
  // ---- Streaming tiers and niche college carriers -------------------------
  // Supporter feedback, 2026-09-29: "It's easy to get the main sports on the
  // main channels. It's the niche sports and niche channels that's hard —
  // ESPN unlimited, ESPN+, SEC+ etc. If someone can pull those you get all
  // college sports." The tier feeds below are how those channels actually
  // arrive from a provider: numbered event feeds, branded with a "+".
  // Spelled-out forms of brands the table only knew by their short bug. The
  // same defect as the tiers above: "CBS Sports Network" resolved to
  // "CBS SPORTS NETWORK", which is not the bug any slate publishes.
  ['cbs sports network', 'CBSSN'],
  ['acc network', 'ACCN'],
  ['nbc sports network', 'NBCSN'],
  ['longhorn network', 'LHN'],
  ['pac-12 network', 'P12N'],
  ['espn unlimited', 'ESPN+'],
  ['espn unlimited +', 'ESPN+'],
  ['sec network+', 'SEC+'],
  ['sec network plus', 'SEC+'],
  ['secn+', 'SEC+'],
  ['sec+', 'SEC+'],
  ['sec plus', 'SEC+'],
  ['acc network extra', 'ACC+'],
  ['accnx', 'ACC+'],
  ['accn+', 'ACC+'],
  ['acc+', 'ACC+'],
  ['acc plus', 'ACC+'],
  ['big ten network', 'BTN'],
  ['big ten+', 'BTN+'],
  ['btn+', 'BTN+'],
  ['espn3', 'ESPN3'],
  ['espn 3', 'ESPN3'],
  ['espn college extra', 'ESPNE'],
  ['flosports', 'FLO'],
  ['flo sports', 'FLO'],
  ['flo', 'FLO'],
  ['stadium', 'STAD'],
  ['trutv', 'TRU'],
  ['tru tv', 'TRU'],
];

const ALIAS_MAP = new Map(ALIAS_PAIRS.map(([k, v]) => [k, v]));

/** Longest-first so "espn 2" wins over "espn". */
const ALIAS_KEYS = [...ALIAS_MAP.keys()].sort((a, b) => b.length - a.length);

/**
 * Alias keys that a provider fans out into numbered event feeds, where the
 * number identifies an event rather than a channel. Only these collapse; a
 * numbered *channel* ("ESPN2", "TSN1", "SN 3") has its own alias and is
 * matched before this runs.
 */
const TIER_KEYS = new Set([
  'espn+', 'espn plus', 'sec+', 'sec plus', 'sec network+', 'sec network plus', 'secn+',
  'acc+', 'acc plus', 'acc network extra', 'accnx', 'accn+',
  'btn+', 'big ten+', 'nfl+', 'flo', 'espnu',
]);

const TOKEN_RE = new RegExp(
  [
    '\\btsn\\s*[1-5]\\b',
    '\\bsn\\s*[1-5]\\b',
    '\\bsn\\s*360\\b',
    '\\bespn\\+?',
    '\\bespn[2u]\\b',
    '\\bfs[12]\\b',
    '\\bnfl\\s*rz\\b',
    '\\bnba\\s*tv\\b',
    '\\bnhl\\s*n(?:etwork)?\\b',
    '\\bmlb\\s*n(?:etwork)?\\b',
    '\\bnfl\\s*n(?:etwork)?\\b',
    '\\bprime(?:\\s*video)?\\b',
    '\\bapple\\s*tv\\+?',
    '\\bparamount\\+?',
    '\\bpeacock\\b',
    '\\bdazn\\b',
    '\\bbein\\b',
    '\\bsky\\s*sports(?:\\s+\\w+)?\\b',
    '\\bsportsnet(?:\\s+\\w+)?\\b',
  ].join('|'),
  'gi',
);

export function normalizeChannel(raw) {
  if (!raw) return '';
  const key = String(raw).trim().toLowerCase().replace(/[_./]+/g, ' ').replace(/\s+/g, ' ');
  if (ALIAS_MAP.has(key)) return ALIAS_MAP.get(key);
  const compact = key.replace(/\s+/g, '');
  if (ALIAS_MAP.has(compact)) return ALIAS_MAP.get(compact);
  // A provider splits a streaming tier into numbered event feeds — "ESPN+ 1",
  // "ESPN+ 12", "SEC Network+ 3", "BTN+ 2". The slate calls the tier by name
  // ("ESPN+"), so without this the whole tier fails the Tier 1 network match
  // and every niche college game falls through to the team-name tiers or to
  // nothing at all. Numbered *national* channels are their own aliases above
  // ("ESPN2", "TSN1"), so this only fires for the plus-tiers, where the number
  // is an event counter and carries no identity.
  const tier = key.match(/^(.+?)\s*\d+$/);
  if (tier && TIER_KEYS.has(tier[1].trim())) return ALIAS_MAP.get(tier[1].trim());
  const tsn = key.match(/^tsn\s*([1-5])$/);
  if (tsn) return `TSN${tsn[1]}`;
  const sn = key.match(/^(?:sn|sportsnet)\s*([1-5])$/);
  if (sn) return `SN ${sn[1]}`;
  return String(raw).trim().toUpperCase().replace(/\s+/g, ' ');
}

export function extractChannels(text) {
  if (!text) return [];
  const source = String(text);
  const found = [];
  const seen = new Set();

  // Longest-first, and each hit is consumed so a shorter alias cannot re-match
  // the same word: "SEC Network+" is SEC+, not SEC+ *and* SECN. The trailing
  // class must not exclude "+" — the tier brands are written with it and
  // excluding it meant "SEC Network+" matched nothing at all — while a
  // following digit still blocks the match, so "espn" never swallows "espn2".
  let work = source;
  const lowerWork = () => work.toLowerCase();
  for (const alias of ALIAS_KEYS) {
    if (!lowerWork().includes(alias)) continue;
    const re = new RegExp(`(?:^|[^a-z0-9+])(${escapeRe(alias)})(?=[^a-z0-9]|$)`, 'gi');
    let hit = false;
    work = work.replace(re, (match, group) => {
      hit = true;
      // Blank the alias itself, keeping length and separators so neighbouring
      // aliases still see their own boundaries.
      return match.replace(group, '\u0000'.repeat(group.length));
    });
    if (!hit) continue;
    const label = ALIAS_MAP.get(alias);
    if (label && !seen.has(label)) {
      seen.add(label);
      found.push(label);
    }
  }

  // Pattern leftovers that the alias list might have missed (TSN4 glued, etc.)
  for (const match of source.matchAll(TOKEN_RE)) {
    const label = normalizeChannel(match[0]);
    if (label && !seen.has(label) && label.length >= 2 && label.length <= 12) {
      seen.add(label);
      found.push(label);
    }
  }

  return found;
}

const LEAGUE_WORDS = new Set(['nhl', 'nba', 'nfl', 'mlb', 'wnba', 'mls', 'epl', 'ufc', 'f1', 'ncaaf', 'ncaab', 'soccer', 'hockey', 'football', 'baseball', 'basketball']);

export function splitChannelsBlob(text) {
  if (!text) return [];
  const parts = String(text)
    .split(/\s*(?:,|\/|\||•|·|;|\band\b)\s*/i)
    .map((p) => p.trim())
    .filter(Boolean);
  const out = [];
  const seen = new Set();
  for (const part of parts) {
    const wholeKey = part.toLowerCase().replace(/[_./]+/g, ' ').replace(/\s+/g, ' ');
    // A part that is exactly a curated alias is trusted as-is. The length and
    // dot filters below exist to reject *guesses* (a brand smashed together
    // with a team name, a stray dotted token) — applying them to a curated
    // label was silently dropping real channels: "MLB.TV" is an alias, carried
    // a dot, and so could never reach a card no matter how it was written.
    const curated = ALIAS_MAP.has(wholeKey) || ALIAS_MAP.has(wholeKey.replace(/\s+/g, ''));
    const labels = curated ? [normalizeChannel(part)] : extractChannels(part);
    for (const label of labels) {
      if (!label || seen.has(label) || LEAGUE_WORDS.has(label.toLowerCase())) continue;
      if (!curated && (label.length > 12 || /[.]/.test(label))) continue;
      seen.add(label);
      out.push(label);
    }
  }
  return out;
}

export function peelChannels(text) {
  const source = String(text || '').trim();
  if (!source) return { text: '', channels: [], index: -1 };
  const lower = source.toLowerCase();
  let index = -1;
  for (const alias of ALIAS_KEYS) {
    const re = new RegExp(`(?:^|[\\s,;/|—(])(${escapeRe(alias)})\\b`, 'i');
    const match = lower.match(re);
    if (!match) continue;
    const at = match.index + (match[0].length - match[1].length);
    if (index === -1 || at < index) index = at;
  }
  const comma = source.search(/\s*[,;/|]\s*/);
  if (comma > 0 && (index === -1 || comma < index)) {
    return {
      text: source.slice(0, comma).trim(),
      channels: splitChannelsBlob(source.slice(comma + 1)),
      index: comma,
    };
  }
  if (index > 0) {
    return {
      text: source.slice(0, index).trim(),
      channels: splitChannelsBlob(source.slice(index)),
      index,
    };
  }
  if (index === 0) {
    return { text: '', channels: splitChannelsBlob(source), index: 0 };
  }
  return { text: source, channels: [], index: -1 };
}

export function formatChannelList(channels, sep = ', ') {
  return (channels || []).filter(Boolean).join(sep);
}

function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
