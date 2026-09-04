/**
 * "Watch" integration — assign any installed sports/streaming app (or the web)
 * to open a game, so a viewer can jump from the ticker into the app that
 * actually plays the broadcast. Pure logic shared by the UI; the Android shell
 * does the real package query + launch (LineBridge.kt + manifest <queries>).
 */

import { LEAGUES } from './scoreboard.mjs';

/** Sentinel meaning "open the game page in the web browser". */
export const WATCH_WEB = 'web';

/**
 * Curated, best-effort list of well-known sports/streaming apps. Used only to
 * sort the picker (familiar apps first); the native shell also returns the
 * device's full installed-app list so a user can assign ANY app.
 * [UNVERIFIED] package ids drift across storefronts/versions — never treat this
 * list as authoritative.
 */
export const SPORTS_APPS = [
  { pkg: 'com.espn.score_center', label: 'ESPN' },
  { pkg: 'com.foxsports.videogo', label: 'Fox Sports' },
  { pkg: 'com.nbcuni.nbcsports', label: 'NBC Sports' },
  { pkg: 'com.peacocktv.peacockandroid', label: 'Peacock' },
  { pkg: 'com.cbs.ott', label: 'Paramount+' },
  { pkg: 'com.paramountplus.android', label: 'Paramount+' },
  { pkg: 'com.dazn', label: 'DAZN' },
  { pkg: 'com.google.android.apps.youtube.unplugged', label: 'YouTube TV' },
  { pkg: 'com.hulu.livingroomplus', label: 'Hulu' },
  { pkg: 'com.sling', label: 'Sling TV' },
  { pkg: 'tv.fubo.mobile', label: 'FuboTV' },
  { pkg: 'com.amazon.amazonvideo.livingroom', label: 'Prime Video' },
  { pkg: 'com.wbd.stream', label: 'Max' },
  { pkg: 'com.bellmedia.tsn', label: 'TSN' },
  { pkg: 'com.rogers.sportsnet', label: 'Sportsnet' },
  { pkg: 'com.apple.atve.android.appletv', label: 'Apple TV' },
  // Niche / league-specific apps [UNVERIFIED]
  { pkg: 'com.sync.tv', label: 'SYNC Sports' },
];

const CURATED = new Map(SPORTS_APPS.map((a) => [a.pkg, a.label]));

/**
 * Order an installed-app list for the picker: curated sports apps first (in
 * curated order), then everything else alphabetically. Dedupes by package.
 * @param {Array<{pkg:string,label:string}>} apps raw list from the shell
 */
export function sortAppsForPicker(apps = []) {
  const byPkg = new Map();
  for (const a of apps) {
    if (!a || typeof a.pkg !== 'string' || !a.pkg) continue;
    const label = String(a.label || a.pkg);
    if (!byPkg.has(a.pkg)) byPkg.set(a.pkg, { pkg: a.pkg, label });
  }
  const curated = [];
  const rest = [];
  for (const a of byPkg.values()) {
    if (CURATED.has(a.pkg)) {
      a.label = CURATED.get(a.pkg);
      curated.push(a);
    } else {
      rest.push(a);
    }
  }
  const curatedOrder = new Map(SPORTS_APPS.map((a, i) => [a.pkg, i]));
  curated.sort((x, y) => (curatedOrder.get(x.pkg) ?? 99) - (curatedOrder.get(y.pkg) ?? 99));
  rest.sort((x, y) => x.label.localeCompare(y.label));
  return [...curated, ...rest];
}

/** Map a league LABEL (event.league, e.g. "NFL") back to its LEAGUES id. */
export function leagueIdForLabel(label) {
  for (const [id, l] of Object.entries(LEAGUES)) {
    if (l.label === label) return id;
  }
  return null;
}

/**
 * Build the best web URL for a game. ESPN-sourced events get league-specific
 * routes: soccer uses the match page, UFC uses the MMA fightcenter, F1 uses
 * race results; all others use the generic game page. Falls back to an ESPN
 * search for non-numeric ids or unknown leagues.
 */
export function espnWebUrl(event) {
  const leagueId = leagueIdForLabel(event?.league);
  const league = leagueId ? LEAGUES[leagueId] : null;
  const sport = league?.sport || '';
  const gameId = /^\d+$/.test(String(event?.id || '')) ? event.id : null;

  if (league) {
    if (sport === 'soccer' && gameId) {
      // Soccer uses the match route: /soccer/match/_/gameId/{id}
      const espnPath = league.espn || '';
      return `https://www.espn.com/soccer/match/_/gameId/${gameId}`;
    }
    if (sport === 'mma' && gameId) {
      // UFC uses the fightcenter route: /mma/fightcenter/_/fightId/{id}
      return `https://www.espn.com/mma/fightcenter/_/fightId/${gameId}`;
    }
    if (sport === 'racing' && gameId) {
      // F1 uses the race results route: /f1/race/_/raceId/{id}
      return `https://www.espn.com/f1/race/_/raceId/${gameId}`;
    }
    if (gameId) {
      const espnPath = (league.espn || '').split('/').pop();
      if (espnPath) {
        return `https://www.espn.com/${espnPath}/game/_/gameId/${gameId}`;
      }
    }
  }
  const q = [event?.away?.name, event?.home?.name].filter(Boolean).join(' vs ') || event?.rawTitle || event?.league || '';
  return `https://www.espn.com/search/_/q/${encodeURIComponent(q)}`;
}

/**
 * Resolve what should open a given event: an app package id, or 'web'.
 * Assignment is keyed by league id; falls back to the league label, then web.
 */
export function watchChoiceFor(watchApps, leagueLabel) {
  const id = leagueIdForLabel(leagueLabel);
  const choice = (watchApps && (watchApps[id] || watchApps[leagueLabel])) || WATCH_WEB;
  return choice === WATCH_WEB ? WATCH_WEB : String(choice);
}
