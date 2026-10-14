// The old site deployed site/ as it was, so every catalog was also served at /i18n/<tag>.json.
// Those URLs keep working with the same bytes: one static file per catalog in site/i18n/,
// imported as raw text and returned unchanged (no JSON round trip, which would reformat it).
import type { APIRoute, GetStaticPaths } from 'astro';

const files = import.meta.glob<string>('../../../i18n/*.json', {
  query: '?raw',
  import: 'default',
  eager: true,
});
const tagOf = (path: string) => path.slice(path.lastIndexOf('/') + 1, -'.json'.length);

export const getStaticPaths = (() =>
  Object.entries(files).map(([path, body]) => ({
    params: { tag: tagOf(path) },
    props: { body },
  }))) satisfies GetStaticPaths;

export const GET: APIRoute = ({ props }) =>
  new Response(props.body as string, { headers: { 'Content-Type': 'application/json' } });
