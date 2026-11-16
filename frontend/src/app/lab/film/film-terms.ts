/** A phrase of the captions the viewer can point at to read what it means. */
export interface FilmTerm { phrase: string; definition: string; }
/** A piece of a caption: plain text, or one of the terms. */
export interface CaptionPart { text: string; term: FilmTerm | null; }

export const FILM_TERMS: readonly FilmTerm[] = [
  {phrase:'keyword search',definition:'BM25, a lexical search: a chunk scores on the stemmed words it shares with the question. Each word’s term frequency is weighted by its IDF, so a rare word like a card’s name counts more, and normalized by the chunk’s length.'},
  {phrase:'search by meaning',definition:'An embedding model turns the question and each chunk into a vector that holds its meaning. The chunks closest to the question by cosine similarity come first, even with no word in common. In the leaderboard: dense.'},
  {phrase:'dense retrieval',definition:'Search by meaning: an embedding model turns the question and each chunk into a vector, and the chunks closest to the question by cosine similarity come first.'},
];

const PATTERN = new RegExp('\\b(?:' + FILM_TERMS.map(term => term.phrase).join('|') + ')\\b','gi');

/** A caption cut around the terms it names, in any case: joined back, the parts are the caption. */
export function captionParts(text: string): CaptionPart[] {
  const parts: CaptionPart[] = [];
  let from = 0;
  for (const match of text.matchAll(PATTERN)) {
    if (match.index > from) parts.push({text:text.slice(from,match.index),term:null});
    parts.push({text:match[0],term:FILM_TERMS.find(term => term.phrase === match[0].toLowerCase()) ?? null});
    from = match.index + match[0].length;
  }
  if (from < text.length) parts.push({text:text.slice(from),term:null});
  return parts;
}
