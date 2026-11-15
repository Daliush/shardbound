import { STEP_NAMES } from '../steps';
import { FilmExample } from './film-model';

export { STEP_NAMES };

/** The film's chapters, in order; a step the best pipeline skips has no chapter. */
export type ChapterId = 'question' | 'key' | 'retriever' | 'expand' | 'rerank' | 'context' | 'recall' | 'versus' | 'scores' | 'explore';
export interface FilmLine { chapter: ChapterId; text: string; }


const RETRIEVERS: [string, string][] = [
  ['rrf','Ours mixes keyword search and search by meaning.'],
  ['dense','It compares meanings, not words.'],
  ['bm25','It counts the words a chunk shares with the question.'],
];
const CONTEXTS: Record<string,string> = {
  'top10-plus-named-cards-refs':'The reranker can push a named card below the cut. The context adds the named cards and their rules back.',
  'top10-plus-named-cards':'The reranker can push a named card below the cut. The context adds the named cards back.',
};
/** How many chunks a retriever hands the next step: `CANDIDATE_COUNT` in ai/rag/retrievers. */
export const HANDED_ON = 20;

/**
 * The film's captions, one per chapter, with the example's own numbers. `share` writes a score the way the lab
 * writes context recall ("84.9%").
 */
export function filmScript(example: FilmExample, share: (value: number | null) => string): FilmLine[] {
  const needed = example.needed.length, of = (count: number) => count + ' of the ' + needed;
  const inTop = (known: (string | null)[]) => known.slice(0,10).filter(id => id !== null && example.needed.includes(id)).length;
  const lines: FilmLine[] = [
    {chapter:'question',text:'A player asks the LLM a rules question. Its answer is somewhere in the rulebook and the cards, cut into ' + example.corpus + ' pieces called chunks.'},
    {chapter:'key',text:'In our example, the LLM needs ' + needed + ' of them to respond correctly. And right now the correct chunks are spread randomly across the pool. A pipeline’s job: get those '
      + needed + ' in the LLM call.'},
  ];
  example.steps.forEach((step,index) => {
    const total = step.known.length, top = inTop(step.known), makes = top === 1 ? ' makes the top 10.' : ' make the top 10.';
    if (step.layer === 'retriever') {
      const kind = RETRIEVERS.find(([prefix]) => step.value.startsWith(prefix))?.[1] ?? '';
      lines.push({chapter:'retriever',text:'The retriever ranks all ' + example.corpus + ' chunks against the question. ' + (kind ? kind + ' ' : '') + of(top).replace(/^./,c => c.toUpperCase()) + makes});
    } else if (step.layer === 'expand') {
      const pulled = broughtBack(example,index).length;
      lines.push({chapter:'expand',text:'Expansion keeps the top ' + HANDED_ON + ', then adds the cards the question names and the chunks they all refer to. '
        + (pulled ? pulled + (pulled === 1 ? ' buried needed chunk comes' : ' buried needed chunks come') + ' back that way.' : total + ' candidates move on.')});
    } else {
      lines.push({chapter:'rerank',text:'A reranker reads the question with each candidate, then reorders them. It’s slower, so it only reads the ' + total + '. Now ' + of(top) + makes});
    }
  });
  const context = example.best.layers['context'] ?? null, held = example.best.held, prompt = example.prompt.length;
  const added = example.prompt.slice(10), addedNeeded = added.filter(id => example.needed.includes(id)).length;
  lines.push({chapter:'context',text:(context ? CONTEXTS[context] ?? 'The context: what the LLM reads.' : 'The LLM reads the top 10.') + ' '
    + (context && added.length ? 'Here it adds ' + added.length + ' chunks to the top 10, ' + addedNeeded + ' of them needed: ' : prompt + ' chunks: ') + of(held) + (held === 1 ? ' reaches the prompt.' : ' reach the prompt.')});
  lines.push({chapter:'recall',text:'Context recall is the share of the needed chunks the prompt holds: ' + held + ' of ' + needed + ' here, ' + percent(held/needed)
    + ', and ' + share(example.best.score) + ' over ' + example.questionCount + ' questions.'});
  const cardsOnly = example.dense.top.every(id => /^(ember|tide|root|neutral)\./.test(id));
  lines.push({chapter:'versus',text:'On the same question, dense retrieval alone, one step that searches by meaning, gets ' + of(example.dense.held)
    + (cardsOnly ? ': only look-alike cards, no rule.' : '.') + ' Over ' + example.questionCount + ' questions: ' + share(example.best.score) + ' against ' + share(example.dense.score) + '.'});
  /** The scores chapter's cards show each value: the caption says what each score reads. */
  const scores = example.scores, reads = ['Recall@10 counts the needed chunks in the first 10','nDCG@10 counts the higher ones more',
    ...(scores.reciprocalRank ? ['MRR is 1 ÷ the rank of the first needed chunk'] : []),...(scores.seconds !== null ? ['latency is the time to rank one question'] : [])];
  lines.push({chapter:'scores',text:'The other scores read the ranking itself: ' + reads.slice(0,-1).join(', ') + ', and ' + reads.at(-1) + '.'});
  lines.push({chapter:'explore',text:example.pipelineCount + ' pipelines ran this way, on the same ' + example.questionCount + ' questions. Explore them below.'});
  return lines;
}

/** A chapter's title, for its marker and the caption's eyebrow: "Step 2 · Expansion". */
export function chapterTitle(chapter: ChapterId, example: FilmExample): string {
  const step = ['retriever','expand','rerank'].indexOf(chapter);
  if (step >= 0) return 'Step ' + (example.steps.findIndex(item => item.layer === chapter) + 1) + ' · ' + STEP_NAMES[chapter].name;
  if (chapter === 'context') return 'Step ' + (example.steps.length + 1) + ' · Context';
  const titles: Record<string,string> = {question:'The question',key:'The answer key',recall:'Context recall',versus:'Best vs dense',scores:'Other scores',explore:'Explore'};
  return titles[chapter] ?? chapter;
}

/** Seconds a caption stays: long enough to read it while the stage moves. */
export function readTime(text: string): number { return Math.max(2.8, 1.1 + text.length * .045); }

/** The needed chunks a step brings back from past the top 20 the step before handed on. */
export function broughtBack(example: FilmExample, index: number): string[] {
  const before = example.steps[index-1]?.known, step = example.steps[index];
  return example.needed.filter(id => rankIn(before,id) > HANDED_ON && rankIn(step.known,id) <= step.known.length);
}

function percent(value: number): string { return Math.round(value * 100) + '%'; }
function rankIn(known: (string | null)[] | undefined, id: string): number { const index = known?.indexOf(id) ?? -1; return index === -1 ? Infinity : index + 1; }
