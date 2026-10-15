// BreadcrumbList JSON-LD: Home, then each [name, url] of the trail in order. A url may be a path
// on tmplayer.org (/faq/) or a whole URL. Written in English; JsonLd translates the names and
// moves the URLs into the page's language.
const ORIGIN = 'https://tmplayer.org';

export function breadcrumb(...trail: [name: string, url: string][]) {
  const crumbs: [string, string][] = [['Home', '/'], ...trail];
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: crumbs.map(([name, url], i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name,
      item: url.startsWith('/') ? ORIGIN + url : url,
    })),
  };
}
