// Localized URLs, as scripts/build-site-i18n.py's page_url() and localize_links() made them.
import { ENGLISH, ORIGIN, PAGES, type Lang, type PageId } from './langs';

export function pageUrl(lang: Lang, page: PageId): string {
  return (lang.path ? `/${lang.path}/` : '/') + PAGES[page];
}

const LOCALIZED_PATHS = new Set(Object.values(PAGES).map((p) => '/' + p));

/** An internal link to a localized page stays in the reader's language; anything else (the
 * changelog, the legal pages, assets, other sites) is left as it is. */
export function l(lang: Lang, href: string): string {
  if (!lang.path || !href.startsWith('/') || href.startsWith('//')) return href;
  const m = /^([^#?]*)([#?]?)(.*)$/.exec(href)!;
  const norm = m[1].endsWith('/') ? m[1] : m[1] + '/';
  return LOCALIZED_PATHS.has(norm) ? `/${lang.path}${norm}${m[2]}${m[3]}` : href;
}

/** An absolute tmplayer.org URL in JSON-LD, moved into the language when its page is localized. */
export function ldUrl(value: string, lang: Lang): string {
  if (!value.startsWith(ORIGIN + '/')) return value;
  const m = /^([^#?]*)([#?]?)(.*)$/.exec(value.slice(ORIGIN.length))!;
  return LOCALIZED_PATHS.has(m[1]) ? `${ORIGIN}/${lang.path}${m[1]}${m[2]}${m[3]}` : value;
}

export { ENGLISH };
