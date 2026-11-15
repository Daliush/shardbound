import { DatasetQuestion, EvaluationQuestion, EvaluationRun, Layers } from '../../api/evaluation';
import { filmChunks, filmExample, known, stepOrders } from './film-model';
import { chapterTitle, filmScript } from './film-script';
import { FILM_TERMS, captionParts } from './film-terms';

const NONE: Layers = {retriever:null,expand:null,rerank:null,context:null,answer:null};
const NEEDED = {'8.1':null,'8.2':null,'9.11':null} as Record<string, number | null>;
function row(id: string, top: string[], ranks: Record<string, number | null>, count: number, recall: number, context?: number): EvaluationQuestion {
  return {question_id:id,category:'interaction',top_chunk_ids:top,relevant_ranks:ranks,candidate_count:count,recall_at:{'10':recall},binary_ndcg_at:{'10':recall/2},reciprocal_rank:recall ? .5 : 0,seconds:.12,...(context === undefined ? {} : {context_recall:context})};
}
function run(layers: Layers, score: number, questions: EvaluationQuestion[]): EvaluationRun {
  return {layers:{...NONE,...layers},scores:{overall:{mean_context_recall:score},by_category:{}},questions};
}
const filler = (count: number, prefix = '6.') => Array.from({length:count},(_,index) => prefix + (index + 1));
/** One question walked through the best pipeline: 1 needed chunk in the first 10, then 2 after the reranker, 3 in the prompt. */
const RUNS = [
  run({retriever:'random'},.1,[row('q',filler(20),{...NEEDED,'9.11':40},191,0)]),
  run({retriever:'dense-qwen3-0.6b'},.6,[row('q',['tide.shoal-sprite','ember.cinderling',...filler(18,'7.')],{...NEEDED,'8.1':50,'8.2':60,'9.11':70},191,0),row('easy',['1.1'],{'1.1':1},191,1)]),
  run({retriever:'rrf-bm25-dense'},.65,[row('q',['tide.shoal-sprite','9.11',...filler(18)],{...NEEDED,'9.11':2,'8.1':30,'8.2':90},191,1/3),row('easy',['1.1'],{'1.1':1},191,1)]),
  run({retriever:'rrf-bm25-dense',expand:'refs'},.7,[row('q',['tide.shoal-sprite','9.11',...filler(18)],{'9.11':2,'8.1':21,'8.2':22},30,1/3),row('easy',['1.1'],{'1.1':1},30,1)]),
  run({retriever:'rrf-bm25-dense',expand:'refs',rerank:'minilm-l6'},.75,[row('q',['9.11','8.1',...filler(18)],{'9.11':1,'8.1':2,'8.2':22},30,2/3),row('easy',['1.1'],{'1.1':1},30,1)]),
  run({retriever:'rrf-bm25-dense',expand:'refs',rerank:'minilm-l6',context:'top10-plus-named-cards-refs'},.85,[row('q',['9.11','8.1',...filler(8),'tide.shoal-sprite','8.2'],{'9.11':1,'8.1':2,'8.2':12},12,2/3,1),row('easy',['1.1'],{'1.1':1},12,1,1)]),
];
const DATASET = new Map<string, DatasetQuestion>([
  ['q',{id:'q',category:'interaction',question:'What happens if I cast Tempest?',answerable:true,key_facts:[],citations:['8.1','8.2','9.11'],cards:['tide.shoal-sprite']}],
  ['easy',{id:'easy',category:'factual',question:'How many HP?',answerable:true,key_facts:[],citations:['1.1'],cards:[]}],
]);

describe('The film', () => {
  it('tells the question the best pipeline serves far better than dense retrieval, step by step as recorded', () => {
    const example = filmExample(RUNS,DATASET)!;
    expect(example.questionId).toBe('q');
    expect(example.needed).toEqual(['9.11','8.1','8.2']);
    expect(example.corpus).toBe(191);
    expect(example.steps.map(step => [step.layer,step.known.length,step.cut])).toEqual([['retriever',191,[]],['expand',30,[]],['rerank',30,[]]]);
    expect(example.prompt.length).toBe(12);
    expect([example.best.held,example.dense.held,example.best.score,example.dense.score]).toEqual([3,0,.85,.6]);
    expect([example.questionCount,example.pipelineCount]).toEqual([2,5]);
  });
  it('has no example without the runs it needs', () => {
    expect(filmExample(RUNS.filter(item => !(item.layers['retriever'] ?? '').startsWith('dense')),DATASET)).toBeNull();
    expect(filmExample(RUNS,new Map())).toBeNull();
  });
  it('puts each named chunk at the rank recorded, and fills the others without repeating one', () => {
    const question = RUNS[2].questions[0];
    expect(known(question)[1]).toBe('9.11');
    expect(known(question)[29]).toBe('8.1');
    expect(known(question)[25]).toBeNull();
    const example = filmExample(RUNS,DATASET)!, chunks = filmChunks(example), orders = stepOrders(example,chunks);
    expect(chunks.length).toBe(191);
    orders.forEach((order,index) => {
      expect(new Set(order).size).toBe(order.length);
      example.steps[index].known.forEach((id,rank) => { if (id) expect(order[rank]).toBe(id); });
    });
    expect(example.prompt.every(id => orders.at(-1)!.includes(id))).toBe(true);
  });
  it('never puts a needed chunk a step cut back in its ranking', () => {
    const example = filmExample(RUNS,DATASET)!;
    const cut = {...example,steps:example.steps.map((step,index) => index === 0 ? step : {...step,known:step.known.map(id => id === '8.2' ? null : id),cut:['8.2']})};
    const orders = stepOrders(cut,filmChunks(cut));
    expect(orders[1].includes('8.2')).toBe(false);
    expect(orders[2].includes('8.2')).toBe(false);
    expect(filmScript(cut,String).filter(line => line.chapter === 'expand').map(line => line.text))
      .toEqual(['Expansion keeps the top 20, then adds the cards the question names and the chunks they all refer to. 1 buried needed chunk comes back that way.']);
  });
  it('tells each chapter in one caption, with the example’s own numbers', () => {
    const example = filmExample(RUNS,DATASET)!, lines = filmScript(example,value => value === null ? '—' : (value*100).toFixed(1) + '%');
    expect(lines.map(line => line.chapter)).toEqual(['question','key','retriever','expand','rerank','context','recall','versus','scores','explore']);
    expect(lines.map(line => line.text)).toEqual([
      'A player asks the LLM a rules question. Its answer is somewhere in the rulebook and the cards, cut into 191 pieces called chunks.',
      'In our example, the LLM needs 3 of them to respond correctly. And right now the correct chunks are spread randomly across the pool. A pipeline’s job: get those 3 in the LLM call.',
      'The retriever ranks all 191 chunks against the question. Ours mixes keyword search and search by meaning. 1 of the 3 makes the top 10.',
      'Expansion keeps the top 20, then adds the cards the question names and the chunks they all refer to. 2 buried needed chunks come back that way.',
      'A reranker reads the question with each candidate, then reorders them. It’s slower, so it only reads the 30. Now 2 of the 3 make the top 10.',
      'The reranker can push a named card below the cut. The context adds the named cards and their rules back. Here it adds 2 chunks to the top 10, 1 of them needed: 3 of the 3 reach the prompt.',
      'Context recall is the share of the needed chunks the prompt holds: 3 of 3 here, 100%, and 85.0% over 2 questions.',
      'On the same question, dense retrieval alone, one step that searches by meaning, gets 0 of the 3. Over 2 questions: 85.0% against 60.0%.',
      'The other scores read the ranking itself: Recall@10 counts the needed chunks in the first 10, nDCG@10 counts the higher ones more, MRR is 1 ÷ the rank of the first needed chunk, and latency is the time to rank one question.',
      '5 pipelines ran this way, on the same 2 questions. Explore them below.',
    ]);
    expect(chapterTitle('rerank',example)).toBe('Step 3 · Reranker');
    expect(chapterTitle('context',example)).toBe('Step 4 · Context');
  });
  it('lets the viewer point at the retrievers the captions name', () => {
    const lines = filmScript(filmExample(RUNS,DATASET)!,String), terms = (chapter: string) => captionParts(lines.find(line => line.chapter === chapter)!.text);
    expect(terms('retriever').filter(part => part.term).map(part => part.text)).toEqual(['keyword search','search by meaning']);
    expect(terms('versus').filter(part => part.term).map(part => part.term!.phrase)).toEqual(['dense retrieval']);
    expect(terms('retriever').map(part => part.text).join('')).toBe(lines[2].text);
    expect(captionParts('Dense retrieval alone.')).toEqual([{text:'Dense retrieval',term:FILM_TERMS[2]},{text:' alone.',term:null}]);
    expect(captionParts('No term here.')).toEqual([{text:'No term here.',term:null}]);
  });
});
