// BreadcrumbList JSON-LD for a page one level below home.
export function breadcrumb(name: string, path: string) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Home', item: 'https://tmplayer.org/' },
      { '@type': 'ListItem', position: 2, name, item: `https://tmplayer.org${path}` },
    ],
  };
}
