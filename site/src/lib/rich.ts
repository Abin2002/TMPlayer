// A catalog string as HTML, for the pages built from data rather than markup, where T's named
// slots cannot be written out by hand. Each {placeholder} becomes: the value from `vars`; for
// {a1}, {a2}... a link to `links[name]` whose words are the `<key>__a1` string; for {b1}, {em1},
// {strong1} or {code1} that element around the `<key>__<name>` string. A link off tmplayer.org
// is nofollow, since naming an app or a page is not recommending it.
import { escapeHtml, text } from '../i18n/t';
import type { Lang } from '../i18n/langs';

const TAGS: Record<string, string> = { b: 'b', em: 'em', strong: 'strong', code: 'code' };

export function rich(
  lang: Lang,
  key: string,
  vars: Record<string, string | number> = {},
  links: Record<string, string> = {},
): string {
  const tmpl = text(lang, key);
  let html = '';
  let pos = 0;
  for (const m of tmpl.matchAll(/\{(\w+)\}/g)) {
    html += escapeHtml(tmpl.slice(pos, m.index));
    pos = m.index! + m[0].length;
    const name = m[1];
    const kind = /^([a-z]+)\d+$/.exec(name)?.[1];
    if (name in vars) html += escapeHtml(String(vars[name]));
    else if (kind === 'a' && links[name]) {
      const href = links[name];
      const rel = href.startsWith('/') ? '' : ` rel="${href.startsWith('https://tmplayer.org') ? 'noopener' : 'nofollow noopener'}"`;
      html += `<a href="${escapeHtml(href)}"${rel}>${escapeHtml(text(lang, `${key}__${name}`))}</a>`;
    } else if (kind && TAGS[kind]) {
      html += `<${TAGS[kind]}>${escapeHtml(text(lang, `${key}__${name}`))}</${TAGS[kind]}>`;
    } else throw new Error(`${key}: nothing fills {${name}}`);
  }
  return html + escapeHtml(tmpl.slice(pos));
}
