/**
 * Locates a cited passage inside a PDF page's text-layer items.
 *
 * The passage came from PDFBox on the server and the items come from PDF.js in the browser, so
 * spacing, hyphenation and ligatures differ. Matching therefore compares letters and digits only,
 * and keeps a map back to each character's original item and offset.
 */
export type HighlightRanges = Map<number, [start: number, end: number]>;

const KEEP = /[\p{L}\p{N}]/u;
const MIN_ANCHOR = 24;
const ANCHOR_SIZES = [80, 48, MIN_ANCHOR];

interface Position {
  item: number;
  offset: number;
}

export function normalizeForMatch(text: string): string {
  let out = '';
  for (const ch of text) {
    for (const part of ch.normalize('NFKC').toLowerCase()) {
      if (KEEP.test(part)) out += part;
    }
  }
  return out;
}

function indexPage(items: string[]): { text: string; positions: Position[] } {
  let text = '';
  const positions: Position[] = [];
  items.forEach((str, item) => {
    let offset = 0;
    for (const ch of str) {
      for (const part of ch.normalize('NFKC').toLowerCase()) {
        if (KEEP.test(part)) {
          text += part;
          positions.push({ item, offset });
        }
      }
      offset += ch.length;
    }
  });
  return { text, positions };
}

function locate(page: string, needle: string): [number, number] | null {
  const exact = page.indexOf(needle);
  if (exact >= 0) return [exact, exact + needle.length];

  // Extraction differed somewhere: anchor on both ends, shrinking until each end is found.
  if (needle.length < MIN_ANCHOR) return null;
  let start = -1;
  let end = -1;
  for (const size of ANCHOR_SIZES) {
    if (start < 0) start = page.indexOf(needle.slice(0, size));
    if (end < 0) {
      const at = page.lastIndexOf(needle.slice(-size));
      if (at >= 0) end = at + Math.min(size, needle.length);
    }
  }
  if (start >= 0 && end > start) return [start, end];
  if (start >= 0) return [start, Math.min(page.length, start + ANCHOR_SIZES[ANCHOR_SIZES.length - 1])];
  if (end >= 0) return [Math.max(0, end - ANCHOR_SIZES[ANCHOR_SIZES.length - 1]), end];

  // Last resort: the first short window of the passage that exists on the page.
  for (let i = 0; i + MIN_ANCHOR <= needle.length; i += MIN_ANCHOR) {
    const at = page.indexOf(needle.slice(i, i + MIN_ANCHOR));
    if (at >= 0) return [at, at + MIN_ANCHOR];
  }
  return null;
}

export function findHighlightRanges(items: string[], passage: string): HighlightRanges {
  const ranges: HighlightRanges = new Map();
  const needle = normalizeForMatch(passage);
  if (!needle) return ranges;

  const { text, positions } = indexPage(items);
  const span = locate(text, needle);
  if (!span) return ranges;

  for (let i = span[0]; i < span[1]; i++) {
    const { item, offset } = positions[i];
    const current = ranges.get(item);
    const charEnd = offset + (String.fromCodePoint(items[item].codePointAt(offset) ?? 0).length || 1);
    ranges.set(item, current ? [current[0], Math.max(current[1], charEnd)] : [offset, charEnd]);
  }
  return ranges;
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** HTML for one text-layer item, with its highlighted portion wrapped in a mark element. */
export function renderHighlightedItem(str: string, range: [number, number] | undefined): string {
  if (!range) return escapeHtml(str);
  const [start, end] = range;
  return (
    escapeHtml(str.slice(0, start)) +
    `<mark class="nm-highlight">${escapeHtml(str.slice(start, end))}</mark>` +
    escapeHtml(str.slice(end))
  );
}
