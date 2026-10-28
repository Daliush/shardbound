import { TestBed } from '@angular/core/testing';
import { EvaluationRun, Layers } from '../api/evaluation';
import { Settings } from '../core/settings';
import { bestRun, fixedStages, flowCounts, rankOf, stageNodes } from './builder-model';
import { PipelineBuilder } from './pipeline-builder';

function run(layers: Layers, recall: number, candidates: number): EvaluationRun {
  return {layers:{retriever:layers['retriever'] ?? null,expand:layers['expand'] ?? null,rerank:layers['rerank'] ?? null,answer:null},questions:[],
    scores:{overall:{mean_recall_at:{'10':recall},mean_reciprocal_rank:recall/2,mean_candidate_count:candidates,question_count:61},by_category:{}}};
}
const RUNS = [
  run({retriever:'random'},.09,191), run({retriever:'bm25'},.53,118), run({retriever:'bm25-robertson'},.57,112),
  run({retriever:'bm25',expand:'refs'},.55,38), run({retriever:'bm25-robertson',expand:'refs'},.57,38),
  run({retriever:'bm25',rerank:'minilm-l6'},.58,20), run({retriever:'bm25',expand:'refs',rerank:'minilm-l6'},.59,38),
];

describe('Builder model', () => {
  it('draws each step with every variant evaluated, BM25 settings as one family, each scored with the other steps as drafted', () => {
    const [retriever,expand,rerank] = stageNodes(RUNS,{retriever:'bm25',expand:'refs',rerank:null,answer:null});

    expect(retriever.families.map(family => [family.family,family.members.map(member => [member.label,member.score])])).toEqual([
      ['Random baseline',[['',null]]],['BM25',[['Default',.55],['Robertson',.57]]]]);
    expect(retriever.families[1].chosen).toBe(true);
    expect(expand.families.map(family => [family.family,family.pick.score,family.chosen])).toEqual([['None',.53,false],['References',.55,true]]);
    expect(rerank.skipped).toBe(true);
    expect(rerank.families.map(family => family.pick.score)).toEqual([.55,.59]);
  });
  it('keeps a step evaluated with one variant out of the choices, as what comes next', () => {
    expect(stageNodes(RUNS,{}).map(node => node.layer)).toEqual(['retriever','expand','rerank']);
    expect(fixedStages(RUNS)).toEqual([{layer:'answer',name:'Answer model',value:null}]);
  });
  it('counts the chunks leaving each step from the recorded pipeline cut after it', () => {
    expect(flowCounts(RUNS,{retriever:'bm25',expand:null,rerank:'minilm-l6'},['retriever','expand','rerank'])).toEqual([118,118,20]);
  });
  it('ranks a pipeline among the others, ties sharing a rank and the floor left out, and starts from the best', () => {
    expect(rankOf(RUNS,RUNS[4],'mean_recall_at.10',false)).toBe(3);
    expect(rankOf(RUNS,RUNS[2],'mean_recall_at.10',false)).toBe(3);
    expect(rankOf(RUNS,RUNS[0],'mean_recall_at.10',false)).toBeNull();
    expect(bestRun(RUNS)).toBe(RUNS[6]);
  });
});

describe('Pipeline builder', () => {
  beforeEach(() => { TestBed.configureTestingModule({imports:[PipelineBuilder]}); TestBed.inject(Settings).update({motion:'on'}); });
  async function builder(initial: Layers) {
    const fixture = TestBed.createComponent(PipelineBuilder);
    let changed: Layers | null = null;
    fixture.componentInstance.changed.subscribe(layers => changed = layers);
    fixture.componentRef.setInput('runs',RUNS); fixture.componentRef.setInput('initial',initial);
    await fixture.whenStable();
    return {fixture,host:fixture.nativeElement as HTMLElement,changed:() => changed};
  }
  const score = (host: HTMLElement) => host.querySelector('.output .big .sr-only')?.textContent;

  it('shows the score of the pipeline drawn at once, and each choice says how it would move it', async () => {
    const {host} = await builder({retriever:'bm25',expand:'refs',rerank:null,answer:null});

    expect(score(host)).toBe('55.0%');
    expect([...host.querySelectorAll('.node')].map(node => node.querySelector('h3')?.textContent)).toEqual(['Retriever','Expansion','Reranker']);
    const rerank = host.querySelectorAll('.node')[2];
    expect([...rerank.querySelectorAll('.effect')].map(effect => effect.textContent)).toEqual(['55.0%','+4.0']);
    expect(host.querySelector('.output .standing')?.textContent).toContain('of 6 pipelines');
  });
  it('moves the score as soon as a step changes, and says when a combination was never run', async () => {
    const {fixture,host,changed} = await builder({retriever:'bm25',expand:'refs',rerank:null,answer:null});

    (host.querySelectorAll<HTMLInputElement>('input[name="stage-rerank"]')[1]).click(); await fixture.whenStable();
    expect(score(host)).toBe('59.0%');
    expect(changed()).toEqual({retriever:'bm25',expand:'refs',rerank:'minilm-l6',answer:null});
    expect(host.querySelector('.output .change')?.textContent).toContain('+4.0 pts');

    [...host.querySelectorAll<HTMLButtonElement>('.member')].find(member => member.textContent?.startsWith('Robertson'))!.click(); await fixture.whenStable();
    expect(host.querySelector('.output .not-run')?.textContent).toContain('Not run yet');
    expect(host.querySelector('.builder-actions .primary')).toBeNull();
  });
});
