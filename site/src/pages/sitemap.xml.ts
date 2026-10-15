// /sitemap.xml, the URL Search Console knows, in the shape the old build scripts wrote it.
//
// src/data/lastmod.json lists the English pages in sitemap order with their real lastmod dates
// (the day the page's content last changed, not the build date), changefreq, priority and image
// entries. A localized page gets the hreflang alternates for every published language plus
// x-default, and each translation follows its English entry with the same dates. The changelog
// carries the newest release's date, and one entry per release page follows it.
import type { APIRoute } from 'astro';
import pages from '../data/lastmod.json';
import { ENGLISH, ORIGIN, PAGES, type PageId } from '../i18n/langs';
import { published } from '../i18n/t';
import { pageUrl } from '../i18n/links';
import { PAGE_URL, releaseUrl, releases } from '../lib/releases';

interface Entry {
  path: string;
  lastmod?: string;
  changefreq?: string;
  priority?: string;
  images?: { loc: string; title: string }[];
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const byPath = new Map(Object.entries(PAGES).map(([id, p]) => ['/' + p, id as PageId]));

function alternates(page: PageId): string[] {
  return [
    ...published.map((l) => `    <xhtml:link rel="alternate" hreflang="${l.hreflang}" href="${ORIGIN}${pageUrl(l, page)}"/>`),
    `    <xhtml:link rel="alternate" hreflang="x-default" href="${ORIGIN}${pageUrl(ENGLISH, page)}"/>`,
  ];
}

function url(loc: string, alt: string[], e: Entry, images = false): string {
  const bits = [`    <loc>${loc}</loc>`, ...alt];
  if (e.lastmod) bits.push(`    <lastmod>${e.lastmod}</lastmod>`);
  if (e.changefreq) bits.push(`    <changefreq>${e.changefreq}</changefreq>`);
  if (e.priority) bits.push(`    <priority>${e.priority}</priority>`);
  if (images) {
    for (const img of e.images ?? []) {
      bits.push('    <image:image>', `      <image:loc>${esc(img.loc)}</image:loc>`,
        `      <image:title>${esc(img.title)}</image:title>`, '    </image:image>');
    }
  }
  return `  <url>\n${bits.join('\n')}\n  </url>`;
}

export const GET: APIRoute = () => {
  const out: string[] = [];
  for (const e of pages as Entry[]) {
    const loc = ORIGIN + e.path;
    const page = byPath.get(e.path);
    if (page !== undefined && published.length > 1) {
      const alt = alternates(page);
      out.push(url(loc, alt, e, true));
      for (const l of published) if (l !== ENGLISH) out.push(url(ORIGIN + pageUrl(l, page), alt, e));
    } else if (loc === PAGE_URL && releases.length) {
      out.push(url(loc, [], { ...e, lastmod: releases[0].day }, true));
      for (const r of releases) {
        out.push(url(releaseUrl(r.version), [], { path: '', lastmod: r.day, changefreq: 'yearly', priority: '0.5' }));
      }
    } else {
      out.push(url(loc, [], e, true));
    }
  }
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">
${out.join('\n')}
</urlset>
`;
  return new Response(xml, { headers: { 'Content-Type': 'application/xml; charset=utf-8' } });
};
