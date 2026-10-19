// JSON-LD in every language: text fields become ld.* catalog strings, URLs follow the language.
// The keys are derived from the English text, the way the catalogs have always been keyed.
import { createHash } from 'node:crypto';
import { text } from './t';
import { ldUrl } from './links';
import type { Lang } from './langs';
import alternatives from '../data/alternatives.json';

const NO_TRANSLATE = new Set(['TMPlayer', 'Telegram', 'GitHub', 'nevil.dev', 'Nevil', 'dracu-lah',
  'Infoek.cz', 'Android TV', 'Android', 'Windows', 'Linux', 'macOS', 'DMG', 'Fire TV', 'Flatpak',
  'AppImage', 'Telegram group', 'Obtainium', 'Downloader', 'mpv',
  // the apps the alternatives page lists, by their own names
  ...alternatives.apps.map((a) => a.name)]);
const LD_TEXT_KEYS = new Set(['name', 'alternateName', 'description', 'text', 'headline',
  'featureList', 'about', 'abstract', 'acceptedAnswer']);
const LD_URL_KEYS = new Set(['url', 'item', '@id', 'mainEntityOfPage']);
const NAMEISH = /^(?:[\p{L}\p{N}_.+-]+@[\p{L}\p{N}_.-]+|[\p{L}\p{N}_-]+(?:\.[\p{L}\p{N}_-]+)+(?:\/\S*)?|v?\p{N}[\p{L}\p{N}_.+-]*)$/u;

/** Whether a string is words a reader would want translated (not a name, version or address). */
export function translatable(s: string): boolean {
  const t = s.split(/\s+/).filter(Boolean).join(' ');
  if (!/\p{L}/u.test(t)) return false;
  if (NO_TRANSLATE.has(t)) return false;
  if (!/\p{Ll}/u.test(t)) return false;
  return !NAMEISH.test(t);
}

export function slug(s: string, limit = 5): string {
  const words = s.replace(/\{\w+\}/g, ' ').toLowerCase().match(/[a-z0-9]+/g) ?? [];
  return words.slice(0, limit).join('_') || 'text';
}

export function ldKey(s: string): string {
  return `ld.${slug(s, 4)}_${createHash('sha1').update(s, 'utf8').digest('hex').slice(0, 4)}`;
}

export function translateLd(data: unknown, lang: Lang, field?: string): unknown {
  if (Array.isArray(data)) return data.map((v) => translateLd(v, lang, field));
  if (data && typeof data === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(data)) {
      if (k === 'inLanguage' && typeof v === 'string') out[k] = lang.tag;
      else if (LD_URL_KEYS.has(k) && typeof v === 'string') out[k] = ldUrl(v, lang);
      else out[k] = translateLd(v, lang, k);
    }
    return out;
  }
  if (typeof data === 'string' && field && LD_TEXT_KEYS.has(field) && translatable(data)) {
    return text(lang, ldKey(data));
  }
  return data;
}
