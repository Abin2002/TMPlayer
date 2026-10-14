// The JSON-LD every guide carries, written in English: a breadcrumb under the home page, the
// guide as an Article, and for a guide with numbered steps a HowTo whose steps link to their
// anchors on the page. JsonLd translates the text fields and moves the URLs into the language.

const ORIGIN = 'https://tmplayer.org';

export interface GuideStep {
  /** the step's id on the page, such as tv-1 */
  id: string;
  name: string;
  text: string;
}

export interface Guide {
  /** the guide's path, such as /guides/telegram-videos-on-android-tv/ */
  path: string;
  /** the breadcrumb's name for the guide */
  crumb: string;
  headline: string;
  description: string;
  datePublished: string;
  dateModified: string;
  /** a HowTo with these steps follows the Article */
  howTo?: { totalTime: string; steps: GuideStep[] };
}

export function guideLd(g: Guide): object[] {
  const url = ORIGIN + g.path;
  const blocks: object[] = [
    {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: [
        { '@type': 'ListItem', position: 1, name: 'Home', item: `${ORIGIN}/` },
        { '@type': 'ListItem', position: 2, name: g.crumb, item: url },
      ],
    },
    {
      '@context': 'https://schema.org',
      '@type': 'Article',
      headline: g.headline,
      description: g.description,
      url,
      mainEntityOfPage: url,
      image: `${ORIGIN}/og.png?v=4`,
      inLanguage: 'en',
      datePublished: g.datePublished,
      dateModified: g.dateModified,
      author: { '@type': 'Person', name: 'dracu-lah', url: 'https://nevil.dev' },
      publisher: {
        '@type': 'Organization',
        name: 'TMPlayer',
        url: `${ORIGIN}/`,
        logo: { '@type': 'ImageObject', url: `${ORIGIN}/icon-512.png` },
      },
      about: { '@type': 'SoftwareApplication', name: 'TMPlayer', url: `${ORIGIN}/` },
    },
  ];
  if (g.howTo) {
    blocks.push({
      '@context': 'https://schema.org',
      '@type': 'HowTo',
      name: g.headline,
      description: g.description,
      inLanguage: 'en',
      totalTime: g.howTo.totalTime,
      step: g.howTo.steps.map((s) => ({
        '@type': 'HowToStep',
        name: s.name,
        url: `${url}#${s.id}`,
        text: s.text,
      })),
    });
  }
  return blocks;
}
