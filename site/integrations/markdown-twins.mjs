// The Markdown twins and llms-full.txt, written after the build from the built pages themselves.
//
// Every English page gets a Markdown copy of its <main> next to it: /download/ at
// /download/index.md, and a release page, whose address has no slash (/changelog/1.26.0), at
// /changelog/1.26.0.md. AI assistants and agents read them instead of the HTML (the page links its
// twin with <link rel="alternate" type="text/markdown">, and vercel.json sends a request that asks
// for text/markdown to it). They are made from dist/ rather than from the page sources so that they
// can never say something the page does not: whatever the page shows, the twin shows. Translated
// pages keep their HTML only; the English text is the source the translations follow.
//
// llms-full.txt is every twin joined, in sitemap order, for an assistant that wants the whole site
// in one request. llms.txt, the short hand-written summary, points at both.
//
// No HTML or Markdown library: the pages are Astro's own well-formed output, and the converter
// below needs to know only the handful of elements they use. A library would also bring its own
// idea of what to keep, and what to keep is the point of this file.
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import pages from '../src/data/lastmod.json' with { type: 'json' };

const ORIGIN = 'https://tmplayer.org';

/** The twin's address for a page's canonical URL: index.md inside a directory, .md after a file. */
export function twinUrl(url) {
  return url.endsWith('/') ? url + 'index.md' : url + '.md';
}

// ---------- a small HTML parser ----------

const VOID = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'source', 'track', 'wbr']);
const RAW = new Set(['script', 'style', 'textarea']);
const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', copy: '\u00a9', middot: '\u00b7',
  rarr: '\u2192', larr: '\u2190', uarr: '\u2191', darr: '\u2193', hellip: '\u2026', times: '\u00d7' };

function decode(s) {
  return s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e) => {
    if (e[0] === '#') return String.fromCodePoint(e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : +e.slice(1));
    return ENTITIES[e.toLowerCase()] ?? m;
  });
}

/** A tree of { tag, attrs, children } and strings, enough for Astro's output. */
function parse(html) {
  const root = { tag: '#root', attrs: {}, children: [] };
  const stack = [root];
  const re = /<!--[\s\S]*?-->|<!\w[^>]*>|<\/([a-zA-Z][\w-]*)\s*>|<([a-zA-Z][\w-]*)((?:\s+[^\s"'>/=]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*\/?>/g;
  let at = 0;
  let m;
  while ((m = re.exec(html))) {
    if (m.index > at) stack.at(-1).children.push(decode(html.slice(at, m.index)));
    at = re.lastIndex;
    if (m[1]) {
      // A close tag pops back to its element; a stray one closes nothing.
      const tag = m[1].toLowerCase();
      const i = stack.findLastIndex((n) => n.tag === tag);
      if (i > 0) stack.length = i;
    } else if (m[2]) {
      const tag = m[2].toLowerCase();
      const attrs = {};
      for (const a of m[3].matchAll(/([^\s"'>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)) {
        attrs[a[1].toLowerCase()] = decode(a[2] ?? a[3] ?? a[4] ?? '');
      }
      const node = { tag, attrs, children: [] };
      stack.at(-1).children.push(node);
      if (RAW.has(tag)) {
        const end = html.toLowerCase().indexOf(`</${tag}`, at);
        const stop = end < 0 ? html.length : end;
        node.children.push(html.slice(at, stop));
        re.lastIndex = at = html.indexOf('>', stop) + 1 || html.length;
      } else if (!VOID.has(tag)) {
        stack.push(node);
      }
    }
  }
  if (at < html.length) stack.at(-1).children.push(decode(html.slice(at)));
  return root;
}

function find(node, test) {
  if (typeof node === 'string') return null;
  if (test(node)) return node;
  for (const c of node.children) {
    const hit = find(c, test);
    if (hit) return hit;
  }
  return null;
}

const text = (node) => (typeof node === 'string' ? node : node.children.map(text).join(''));

// ---------- HTML to Markdown ----------

const BLOCK = new Set(['address', 'article', 'aside', 'blockquote', 'dd', 'details', 'div', 'dl', 'dt', 'figcaption',
  'figure', 'footer', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'header', 'hr', 'li', 'main', 'ol', 'p', 'pre', 'section',
  'summary', 'table', 'tbody', 'td', 'tfoot', 'th', 'thead', 'tr', 'ul']);
// Navigation, controls and drawings: what a reader of the text has no use for.
const SKIP = new Set(['script', 'style', 'noscript', 'template', 'svg', 'button', 'nav', 'form', 'input', 'select',
  'textarea', 'iframe', 'canvas', 'video', 'audio', 'source', 'dialog']);

const TOUCH = new Set(['a', 'span']);
const BOLD = new Set(['b', 'strong']);
const isBlock = (n) => typeof n !== 'string' && (BLOCK.has(n.tag) || n.children.some(isBlock));
const hasHeading = (n) => !!find(n, (c) => /^h[1-6]$/.test(c.tag));

function skipped(n) {
  if (SKIP.has(n.tag) || n.attrs['aria-hidden'] === 'true') return true;
  if (/(^|\s)crumbs(\s|$)/.test(n.attrs.class ?? '')) return true; // the breadcrumb trail is navigation
  // A hidden element is a badge or a status line that app.js fills in for the visitor. A hidden one
  // with a heading of its own is a section app.js reveals (the macOS card on /download/), and stays.
  return 'hidden' in n.attrs && !hasHeading(n);
}

/** Markdown's own characters, escaped where they would otherwise start or end formatting. */
function escape(s) {
  return s.replace(/[\\`*[\]<]/g, '\\$&').replace(/(^|\W)_|_(?=\W|$)/g, (m) => m.replace('_', '\\_'));
}

class Converter {
  constructor(base) {
    this.base = base;
    this.level = 1; // the last heading's level, so a FAQ question sits one below its section
  }

  abs(href) {
    try {
      return new URL(href, this.base).href;
    } catch {
      return href;
    }
  }

  inline(n, pre = false) {
    if (typeof n === 'string') return pre ? n : escape(n.replace(/\s+/g, ' '));
    if (skipped(n)) return '';
    const inner = () => this.join(n.children, pre);
    const wrap = (mark) => {
      const s = inner();
      const t = s.trim();
      if (!t) return s;
      return s.slice(0, s.length - s.trimStart().length) + mark + t + mark + s.slice(s.trimEnd().length);
    };
    switch (n.tag) {
      case 'br': return '\u0000';
      case 'strong': case 'b': return wrap('**');
      case 'em': case 'i': return wrap('*');
      case 'code': case 'kbd': case 'samp': {
        const t = text(n).replace(/\s+/g, ' ');
        if (!t.trim()) return '';
        const ticks = t.includes('`') ? '``' : '`';
        return ticks + t + ticks;
      }
      case 'img': {
        const alt = (n.attrs.alt ?? '').trim();
        return alt && n.attrs.src ? `![${escape(alt)}](${this.abs(n.attrs.src)})` : '';
      }
      case 'a': {
        const label = this.tidy(inner()) || escape(n.attrs['aria-label'] ?? n.attrs.title ?? '');
        if (!n.attrs.href || !label) return label;
        return `[${label}](${this.abs(n.attrs.href)})`;
      }
      default: return inner();
    }
  }

  /** Inline siblings in turn. Two spans that touch are two items the stylesheet sets apart
   * ("GPL-3.0, and free for good" and "21 languages"), and so are two links that touch, so they
   * get a separator in the text. */
  join(children, pre = false) {
    let out = '';
    let prev = null;
    for (const c of children) {
      const s = this.inline(c, pre);
      if (TOUCH.has(prev?.tag) && TOUCH.has(c.tag) && s && /\S$/.test(out) && /^\S/.test(s)) out += ' \u00b7 ';
      // A bold title run straight into its text is a caption's title, set on a line of its own.
      else if (BOLD.has(prev?.tag) && out.endsWith('**') && /^[\p{L}\p{N}]/u.test(s)) out += '\u0000';
      out += s;
      if (s || typeof c === 'string') prev = c;
    }
    return out;
  }

  /** One paragraph's worth of inline Markdown: spaces collapsed, <br> kept as a line break. */
  tidy(s) {
    return s.replace(/ {2,}/g, ' ').split('\u0000').map((l) => l.trim()).join('\n').trim();
  }

  /** The blocks inside a container: block children in turn, runs of inline ones as paragraphs. */
  blocks(n) {
    const out = [];
    let run = [];
    const flush = () => {
      const p = this.tidy(this.join(run));
      if (p) out.push(/^(#|>|[-+] |\d+\. )/.test(p) ? '\\' + p : p);
      run = [];
    };
    for (const c of n.children) {
      if (typeof c !== 'string' && skipped(c)) continue;
      if (isBlock(c)) {
        flush();
        out.push(...this.block(c));
      } else {
        run.push(c);
      }
    }
    flush();
    return out;
  }

  block(n) {
    const tag = n.tag;
    if (/^h[1-6]$/.test(tag)) {
      this.level = +tag[1];
      const t = this.tidy(this.inline(n)).replace(/\n/g, ' ');
      return t ? ['#'.repeat(this.level) + ' ' + t] : [];
    }
    switch (tag) {
      case 'p': case 'dt': case 'figcaption': {
        const t = this.tidy(this.inline(n));
        return t ? [t] : [];
      }
      case 'summary': {
        const t = this.tidy(this.inline(n)).replace(/\n/g, ' ');
        return t ? ['#'.repeat(Math.min(6, this.level + 1)) + ' ' + t] : [];
      }
      case 'details': {
        const outer = this.level;
        const out = this.blocks(n);
        this.level = outer;
        return out;
      }
      case 'hr': return ['---'];
      case 'pre': {
        const t = text(n).replace(/^\n|\n\s*$/g, '');
        const fence = t.includes('```') ? '````' : '```';
        return [fence + '\n' + t + '\n' + fence];
      }
      case 'blockquote':
        return [this.blocks(n).join('\n\n').split('\n').map((l) => (l ? '> ' + l : '>')).join('\n')];
      case 'ul': case 'ol': return [this.list(n)].filter(Boolean);
      case 'dl': return [this.definitions(n)].filter(Boolean);
      case 'table': return [this.table(n)].filter(Boolean);
      case 'a': {
        // A card: a link around a heading and a paragraph. Its blocks stay blocks, and its inline
        // part (the "The full list" line) becomes the link; without one, the heading does.
        const href = n.attrs.href ? this.abs(n.attrs.href) : null;
        const out = [];
        let linked = false;
        let run = [];
        const flush = () => {
          const t = this.tidy(this.join(run));
          if (t) {
            out.push(href ? `[${t}](${href})` : t);
            linked = true;
          }
          run = [];
        };
        for (const c of n.children) {
          if (typeof c !== 'string' && skipped(c)) continue;
          if (isBlock(c)) {
            flush();
            out.push(...this.block(c));
          } else {
            run.push(c);
          }
        }
        flush();
        if (!linked && href) {
          const i = out.findIndex((b) => b.startsWith('#'));
          if (i >= 0) out[i] = out[i].replace(/^(#+) (.*)$/, `$1 [$2](${href})`);
          else out.push(`[${href}](${href})`);
        }
        return out;
      }
      default: return this.blocks(n);
    }
  }

  list(n) {
    const ordered = n.tag === 'ol';
    let i = +(n.attrs.start ?? 1);
    const items = [];
    for (const li of n.children) {
      if (typeof li === 'string' || li.tag !== 'li' || skipped(li)) continue;
      const marker = ordered ? `${i++}. ` : '- ';
      const pad = ' '.repeat(marker.length);
      const body = this.blocks(li).join('\n\n');
      if (!body) continue;
      items.push(marker + body.split('\n').map((l, k) => (k === 0 || !l ? l : pad + l)).join('\n'));
    }
    // Items that are one line each stay tight; any longer item spaces the whole list out.
    return items.join(items.some((t) => t.includes('\n')) ? '\n\n' : '\n');
  }

  definitions(n) {
    // <dt> and <dd> pairs, possibly each wrapped in a <div>, as "- **Term**: description".
    const lines = [];
    let term = null;
    const walk = (node) => {
      for (const c of node.children) {
        if (typeof c === 'string' || skipped(c)) continue;
        if (c.tag === 'dt') term = this.tidy(this.inline(c));
        else if (c.tag === 'dd') {
          const d = this.blocks(c).join(' ').replace(/\n/g, ' ');
          lines.push(term ? `- **${term}**: ${d}` : `- ${d}`);
        } else walk(c);
      }
    };
    walk(n);
    return lines.join('\n');
  }

  table(n) {
    const rows = [];
    const walk = (node) => {
      for (const c of node.children) {
        if (typeof c === 'string' || skipped(c)) continue;
        if (c.tag === 'tr') rows.push(c);
        else if (c.tag !== 'caption') walk(c);
      }
    };
    walk(n);
    if (!rows.length) return '';
    const cells = rows.map((r) => r.children
      .filter((c) => typeof c !== 'string' && (c.tag === 'td' || c.tag === 'th') && !skipped(c))
      .map((c) => this.blocks(c).join(' ').replace(/\n/g, ' ').replace(/\|/g, '\\|')));
    const width = Math.max(...cells.map((r) => r.length));
    const line = (r) => '| ' + Array.from({ length: width }, (_, k) => r[k] ?? '').join(' | ') + ' |';
    const out = [line(cells[0]), '|' + ' --- |'.repeat(width), ...cells.slice(1).map(line)];
    const caption = find(n, (c) => c.tag === 'caption');
    return (caption ? this.tidy(this.inline(caption)) + '\n\n' : '') + out.join('\n');
  }
}

/** A built page as its twin, or null for a page that has none (translations, the 404). */
export function pageToMarkdown(html) {
  const doc = parse(html);
  const htmlEl = find(doc, (n) => n.tag === 'html');
  if (!htmlEl || htmlEl.attrs.lang !== 'en') return null;
  const canonical = find(doc, (n) => n.tag === 'link' && n.attrs.rel === 'canonical')?.attrs.href;
  const main = find(doc, (n) => n.tag === 'main');
  if (!canonical || !main) return null;
  const meta = (name) => find(doc, (n) => n.tag === 'meta' && n.attrs.name === name)?.attrs.content ?? '';
  const title = text(find(doc, (n) => n.tag === 'title') ?? '').trim();
  const body = new Converter(canonical).blocks(main).join('\n\n');
  // Front matter in JSON's quoting, which YAML reads as it is.
  const head = ['---', `title: ${JSON.stringify(title)}`, `description: ${JSON.stringify(meta('description'))}`,
    `url: ${canonical}`, '---'].join('\n');
  return { url: canonical, title, body, markdown: `${head}\n\n${body}\n` };
}

function htmlFiles(dir) {
  return readdirSync(dir, { recursive: true }).filter((f) => f.endsWith(`${sep}index.html`) || f === 'index.html');
}

/** Sitemap order: lastmod.json's pages as listed, the releases newest first after the changelog. */
function order(url) {
  const path = url.slice(ORIGIN.length);
  const i = pages.findIndex((p) => p.path === path);
  if (i >= 0) return [i, 0];
  const release = path.match(/^\/changelog\/(\d+)\.(\d+)\.(\d+)$/);
  if (release) {
    const at = pages.findIndex((p) => p.path === '/changelog/');
    return [at, -(+release[1] * 1e6 + +release[2] * 1e3 + +release[3])];
  }
  return [pages.length, 0];
}

const LLMS_FULL_HEAD = `# TMPlayer: the whole site

> TMPlayer is a free, open source, unofficial Telegram media player that plays the videos in your own Telegram chats on Android TV, Fire TV, Android phones, Windows, macOS and Linux, while they are still downloading.

Every English page of ${ORIGIN} as Markdown, joined in sitemap order and written when the site is built, so it says what the pages say. Each page also has its own Markdown copy, named after its URL. The short summary is ${ORIGIN}/llms.txt.
`;

export default function markdownTwins() {
  return {
    name: 'tm-markdown-twins',
    hooks: {
      'astro:build:done': ({ dir, logger }) => {
        const root = fileURLToPath(dir);
        const done = [];
        for (const file of htmlFiles(root)) {
          const twin = pageToMarkdown(readFileSync(join(root, file), 'utf8'));
          if (!twin) continue;
          const out = join(root, twinUrl(twin.url).slice(ORIGIN.length + 1));
          writeFileSync(out, twin.markdown);
          done.push({ ...twin, file: relative(root, out) });
        }
        done.sort((a, b) => {
          const [x, y] = [order(a.url), order(b.url)];
          return x[0] - y[0] || x[1] - y[1] || a.url.localeCompare(b.url);
        });
        const full = done.map((t) => `\n---\n\nURL: ${t.url}\nMarkdown: ${twinUrl(t.url)}\n\n${t.body}\n`);
        writeFileSync(join(root, 'llms-full.txt'), LLMS_FULL_HEAD + full.join(''));
        logger.info(`${done.length} Markdown twins and llms-full.txt`);
      },
    },
  };
}
