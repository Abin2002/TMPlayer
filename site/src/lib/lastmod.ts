// A page's lastmod from src/data/lastmod.json (the day its content last changed), for the
// "Updated" line and the guides' dateModified, so the reader, the sitemap and the JSON-LD all
// give the same date.
import pages from '../data/lastmod.json';
import type { Lang } from '../i18n/langs';

/** The lastmod of an English path, such as /faq/. */
export function lastmod(path: string): string {
  const day = (pages as { path: string; lastmod?: string }[]).find((p) => p.path === path)?.lastmod;
  if (!day) throw new Error(`no lastmod for ${path} in src/data/lastmod.json`);
  return day;
}

/** A YYYY-MM-DD day as the page's language writes a date, read in UTC like app.js does. */
export function formatDay(lang: Lang, day: string): string {
  return new Intl.DateTimeFormat(lang.tag === 'en' ? 'en-GB' : lang.tag, {
    day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${day}T00:00:00Z`));
}
