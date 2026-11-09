import { describe, expect, it } from 'vitest';
import { parseSseBuffer } from '@/services/chatStream';
import { findHighlightRanges, normalizeForMatch, renderHighlightedItem } from '@/utils/pdfHighlight';

describe('parseSseBuffer', () => {
  it('parses complete events and keeps the remainder', () => {
    const { events, rest } = parseSseBuffer('event:token\ndata:{"text":"a"}\n\nevent:tok');
    expect(events).toEqual([{ event: 'token', data: '{"text":"a"}' }]);
    expect(rest).toBe('event:tok');
  });

  it('handles CRLF separators', () => {
    expect(parseSseBuffer('event:done\r\ndata:{}\r\n\r\n').events).toEqual([{ event: 'done', data: '{}' }]);
  });
});

describe('PDF citation highlighting', () => {
  it('normalizes ligatures, case, hyphens and spacing', () => {
    expect(normalizeForMatch('The ﬁrst pre-fix  Phase')).toBe('thefirstprefixphase');
  });

  it('finds a passage spanning several text items despite different spacing', () => {
    const items = ['Intro line. ', 'Lexical analysis is', ' the first ', 'phase of a compiler', '. Next.'];
    const ranges = findHighlightRanges(items, 'Lexical analysis is\nthe first phase of a compiler');

    expect(ranges.has(0)).toBe(false);
    expect(ranges.get(1)).toEqual([0, 19]);
    expect(ranges.has(3)).toBe(true);
    expect(ranges.has(4)).toBe(false);
  });

  it('highlights only the part of an item that matches', () => {
    const ranges = findHighlightRanges(['before TARGET text after'], 'target text');
    expect(ranges.get(0)).toEqual([7, 18]);
  });

  it('survives words joined across a line break', () => {
    const ranges = findHighlightRanges(['trans-', 'lation of source code into machine instructions now'], 'translation of source code into machine instructions');
    expect(ranges.size).toBe(2);
  });

  it('falls back to anchors when the middle of the passage differs', () => {
    const start = 'the beginning of this passage is intact on the page and long enough to act as an anchor here';
    const end = 'the ending of this passage is also intact on the page and long enough to act as the other anchor';
    const ranges = findHighlightRanges([start, 'XXXX garbled middle XXXX', end], `${start} something completely different ${end}`);
    expect(ranges.size).toBe(3);
  });

  it('returns nothing when the passage is not on the page', () => {
    expect(findHighlightRanges(['entirely unrelated content on this page'], 'lexical analysis is the first phase of a compiler').size).toBe(0);
    expect(findHighlightRanges(['x'], '   ').size).toBe(0);
  });

  it('wraps the range in a mark and escapes HTML from the PDF', () => {
    expect(renderHighlightedItem('a<b>c', [1, 4])).toBe('a<mark class="nm-highlight">&lt;b&gt;</mark>c');
    expect(renderHighlightedItem('<img src=x>', undefined)).toBe('&lt;img src=x&gt;');
  });
});
