/**
 * Favorite match is an exact abbreviation, or a whole word in the team name.
 * CHI must not match CHIEFS. NY must not match NYY.
 */
export function matchesFavorite(event, favs) {
  const needles = (Array.isArray(favs) ? favs : String(favs || '').split(/[,\s]+/))
    .map((s) => String(s).trim().toUpperCase())
    .filter(Boolean);
  if (!needles.length || !event) return false;
  const abbrs = [event.home?.abbr, event.away?.abbr]
    .filter(Boolean)
    .map((s) => String(s).toUpperCase());
  const names = [event.home?.name, event.away?.name]
    .filter(Boolean)
    .map((s) => String(s).toUpperCase());
  return needles.some((fav) => {
    if (abbrs.includes(fav)) return true;
    return names.some((name) => name.split(/[^A-Z0-9]+/).includes(fav));
  });
}
