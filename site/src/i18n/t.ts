// Catalog lookups: fallback to English per string, {placeholders} filled from slots or values.
//
// site/i18n/en.json is the source of every string; site/i18n/<tag>.json holds the translations
// with the same keys and {placeholders}. A translation is used only when its placeholders match
// English, so a string that lost a {a1} falls back to English rather than dropping a link.
import { ENGLISH, LANGS, PUBLISH_THRESHOLD, type Lang } from './langs';

type Flat = Record<string, string>;

const files = import.meta.glob<Record<string, Record<string, unknown>>>('../../i18n/*.json', {
  eager: true,
  import: 'default',
});

function unnest(data: Record<string, unknown>, prefix = ''): Flat {
  const out: Flat = {};
  for (const [k, v] of Object.entries(data)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') Object.assign(out, unnest(v as Record<string, unknown>, key));
    else if (typeof v === 'string') out[key] = v;
  }
  return out;
}

function load(tag: string): Flat {
  const data = files[`../../i18n/${tag}.json`];
  return data ? unnest(data) : {};
}

export const english: Flat = load('en');
const catalogs = new Map<string, Flat>([['en', english]]);
for (const lang of LANGS) catalogs.set(lang.tag, load(lang.tag));

export function catalog(lang: Lang): Flat {
  return catalogs.get(lang.tag) ?? {};
}

/** Share of the English keys (minus the build.* ones) a catalog fills. */
export function coverage(lang: Lang): number {
  const cat = catalog(lang);
  const keys = Object.keys(english).filter((k) => !k.startsWith('build.'));
  return keys.length ? keys.filter((k) => cat[k]).length / keys.length : 0;
}

/** English first, then every language whose catalog is complete enough, in picker order. */
export const published: Lang[] = [ENGLISH, ...LANGS.filter((l) => coverage(l) >= PUBLISH_THRESHOLD)];

const PH = /\{(\w+)\}/g;
const placeholders = (s: string) => [...s.matchAll(PH)].map((m) => m[1]).sort().join(',');

/** Every key a build looked up, written to .astro/used-keys.json for scripts/site-i18n-check.py. */
const used: Set<string> = ((globalThis as Record<string, unknown>).__tmUsedKeys ??= new Set<string>()) as Set<string>;

/** The translation, or English when it is missing or lost a placeholder. */
export function text(lang: Lang, key: string): string {
  used.add(key);
  const en = english[key];
  const tr = catalog(lang)[key];
  if (tr && en !== undefined && placeholders(tr) === placeholders(en)) return tr;
  if (en === undefined && tr === undefined) throw new Error(`no string for key ${key}`);
  return en ?? tr;
}

/** A build.* string: the translation when there is one, English otherwise. */
export function buildString(lang: Lang, key: string): string {
  used.add(key);
  return catalog(lang)[key] || english[key];
}

/** text() with {name} placeholders filled from vars. */
export function format(lang: Lang, key: string, vars: Record<string, string | number>): string {
  return text(lang, key).replace(PH, (m, name) => (name in vars ? String(vars[name]) : m));
}

/** The js.* strings app.js reads from <script id="tm-strings">, for a translated page. */
export function jsStrings(lang: Lang): Flat {
  const cat = catalog(lang);
  const out: Flat = {};
  for (const k of Object.keys(english)) if (k.startsWith('js.') && cat[k]) out[k.slice(3)] = cat[k];
  return out;
}

export function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
