// The RSS feed of the releases, written out by hand in the format scripts/build-changelog.py
// used, so feed readers see the same GUIDs, titles, links and dates and no item comes back new.
import type { APIRoute } from 'astro';
import {
  DESCRIPTION, FEED_ITEMS, FEED_URL, PAGE_URL, escAttr, markdown, plain, releaseUrl, releases, rfc822,
} from '../../lib/releases';

export const GET: APIRoute = () => {
  const items = releases.slice(0, FEED_ITEMS).map((r) => {
    const summary = plain(r.notes) || 'No notes were written for this release.';
    const body = r.notes ? markdown(r.notes, '') : `<p>${escAttr(summary)}</p>`;
    return `    <item>
      <title>TMPlayer ${escAttr(r.version)}</title>
      <link>${escAttr(releaseUrl(r.version))}</link>
      <guid isPermaLink="false">tmplayer-v${escAttr(r.version)}</guid>
      <pubDate>${rfc822(r.date)}</pubDate>
      <description>${escAttr(body)}</description>
    </item>`;
  });
  const built = releases.length ? rfc822(releases[0].date) : 'Thu, 01 Jan 1970 00:00:00 GMT';
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>TMPlayer releases</title>
    <link>${PAGE_URL}</link>
    <description>${escAttr(DESCRIPTION)}</description>
    <language>en-gb</language>
    <lastBuildDate>${built}</lastBuildDate>
    <atom:link href="${FEED_URL}" rel="self" type="application/rss+xml"/>
${items.join('\n')}
  </channel>
</rss>
`;
  return new Response(xml, { headers: { 'Content-Type': 'application/xml; charset=utf-8' } });
};
