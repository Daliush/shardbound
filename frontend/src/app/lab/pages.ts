/** Every list of pipelines in the lab shows ten at a time. */
export const PAGE_SIZE = 10;

/** What a list shows of a ranking: every key in rank order, and the page it shows of them. */
export interface RowWindow { readonly keys: readonly string[]; readonly start: number; readonly size: number; }

export function pageCount(total: number, size = PAGE_SIZE): number { return Math.max(1,Math.ceil(total/size)); }
/** The page, kept inside the list: a list that shrinks under a page shows its last one. */
export function clampPage(page: number, total: number, size = PAGE_SIZE): number { return Math.min(Math.max(0,page),pageCount(total,size)-1); }
export function pageOf<T>(items: readonly T[], page: number, size = PAGE_SIZE): T[] {
  const start = clampPage(page,items.length,size)*size;
  return items.slice(start,start+size);
}
/** "11–20": the ranks a page shows, from 1. */
export function pageRange(page: number, total: number, size = PAGE_SIZE): string {
  if (!total) return '0';
  const start = clampPage(page,total,size)*size;
  return (start+1) + '–' + Math.min(total,start+size);
}
export function windowOf(keys: readonly string[], page: number, size = PAGE_SIZE): RowWindow {
  return {keys,start:clampPage(page,keys.length,size)*size,size};
}

export type Arrival = { kind: 'intro' | 'fade'; index: number } | { kind: 'page'; side: 1 | -1; index: number } | { kind: 'rank'; side: 1 | -1; index: number };

/**
 * How a row comes onto the page shown, or leaves it: the first rows rise in turn, a page turned slides in from the
 * side it was asked from, and a row whose rank crosses the page's edge comes in or goes out through that edge, from
 * above when it fell from a better page, from below when it climbed from a worse one.
 */
export function arrivalOf(key: string, now: RowWindow, before: RowWindow | null, leaving: boolean): Arrival {
  const index = Math.max(0,(leaving ? before ?? now : now).keys.indexOf(key) - (leaving ? before ?? now : now).start);
  if (!before) return {kind:'intro',index};
  if (now.start !== before.start) {
    const forward = now.start > before.start;
    return {kind:'page',side:(forward !== leaving ? 1 : -1),index};
  }
  const other = (leaving ? now : before).keys.indexOf(key), start = now.start, end = now.start + now.size;
  if (other === -1) return {kind:'fade',index};
  if (other < start) return {kind:'rank',side:-1,index};
  if (other >= end) return {kind:'rank',side:1,index};
  return {kind:'fade',index};
}
