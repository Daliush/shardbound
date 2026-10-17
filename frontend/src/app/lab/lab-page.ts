import { Component, OnDestroy, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { DatasetQuestion, Evaluation, EvaluationRun } from '../api/evaluation';
import { Icon } from '../core/icon';
import { Categories } from './categories';
import { CategoryCurves } from './category-curves';
import { Evaluations } from './evaluations';
import { HeadToHead } from './head-to-head';
import { LabHistory } from './history';
import { decodeRun, runKey } from './layer-model';
import { DECISION_METRIC, atPath, differences, formatMetric, generic, humanize, metricName, metrics, metricDescription, lowerIsBetter, isRatio } from './metrics';
import { QuestionDetail } from './question-detail';
import { MetricsGraph } from './metrics-graph';
import { CombinationBuilder } from './combination-builder';
import { LabMotion } from './lab-motion';
import { pipelineName, pipelineKind, rankedRuns, runColor } from './presentation';
import { isContextMetric, candidateSummary } from './metrics';
import { RecallChart } from './recall-chart';
import { recallCurve } from './recall-model';
import { DATASET_VERSIONS, LAB_TABS, LabState, readState, writeState } from './url-state';

@Component({
  selector:'app-lab-page',
  imports:[Icon,Categories,CategoryCurves,HeadToHead,QuestionDetail,LabHistory,MetricsGraph,CombinationBuilder,LabMotion,RecallChart],
  templateUrl:'./lab-page.html',styleUrls:['./lab-page.css','./lab-tables.css','./lab-compare.css','./lab-evidence.css','./lab-page-mobile.css','./lab-exploration.css'],
})
export class LabPage implements OnDestroy {
  private readonly route = inject(ActivatedRoute); private readonly router = inject(Router); private readonly evaluations = inject(Evaluations);
  private readonly query = toSignal(this.route.queryParams,{initialValue:this.route.snapshot.queryParams});
  protected readonly state = computed(() => readState(this.query()));
  protected readonly report = signal<Evaluation | null>(null);
  /** The split's questions by id, for their wording and answer key; empty until loaded, or when the dataset can't be read. */
  protected readonly dataset = signal<ReadonlyMap<string,DatasetQuestion>>(new Map());
  protected readonly splitTimes = signal<Record<string,string[]>>({dev:[],test:[]});
  protected readonly loading = signal(true); protected readonly error = signal(''); protected readonly copied = signal(false);
  protected readonly versions = DATASET_VERSIONS; protected readonly tabs = LAB_TABS;
  protected readonly key = runKey; protected readonly name = pipelineName; protected readonly humanize = humanize; protected readonly metricName = metricName; protected readonly format = formatMetric; protected readonly generic = generic;
  protected readonly at = atPath; protected readonly kind = pipelineKind; protected readonly description = metricDescription; protected readonly lower = lowerIsBetter;
  protected readonly search = signal('');
  protected readonly copyMessage = signal('');
  protected readonly views = [{id:'leaderboard',label:'Leaderboard',icon:'ranking'},{id:'metrics',label:'Metrics',icon:'chart'},{id:'compare',label:'Compare',icon:'compare'},{id:'builder',label:'Build',icon:'build'}];
  protected readonly decisionMetric = DECISION_METRIC;
  protected readonly contextMetric = isContextMetric;
  protected readonly candidateSummary = candidateSummary;
  protected readonly color = runColor;
  protected readonly rankingMetric = computed(() => isContextMetric(this.state().metric) ? DECISION_METRIC : this.state().metric);
  protected readonly scoreMetrics = computed(() => this.metrics().filter(metric => !isContextMetric(metric)));
  protected readonly tableMetrics = computed(() => this.metrics().filter(metric => metric !== 'mean_candidate_count'));
  protected readonly runs = computed(() => this.report()?.runs ?? []);
  protected readonly hasRecallCurves = computed(() => this.runs().some(run => recallCurve(run.scores.overall).length > 0));
  protected readonly selected = computed<EvaluationRun | undefined>(() => this.runs().find(run => runKey(run.layers) === this.state().run) ?? this.runs()[0]);
  protected readonly builderLayers = computed(() => decodeRun(this.state().run) ?? this.selected()?.layers ?? {});
  protected readonly metrics = computed(() => metrics(this.runs()));
  protected readonly contenders = computed(() => this.state().pins.flatMap(key => { const run = this.runs().find(run => runKey(run.layers) === key); return run ? [run] : []; }));
  protected readonly comparison = computed(() => {
    const chosen = this.contenders().slice(0,2);
    for (const row of this.ranked()) { if (chosen.length < 2 && !chosen.includes(row.run)) chosen.push(row.run); }
    return chosen;
  });
  protected readonly ranked = computed(() => rankedRuns(this.runs(),this.rankingMetric()));
  protected readonly displayed = computed(() => this.ranked().filter(row => pipelineName(row.run).toLowerCase().includes(this.search().toLowerCase()) || JSON.stringify(row.run.layers).toLowerCase().includes(this.search().toLowerCase())));
  protected readonly baseline = computed(() => this.runs().find(run => run.layers['retriever'] === 'random'));
  protected readonly graphRuns = computed(() => [...this.runs()].sort((a,b) => Number(a.layers['retriever'] === 'random')-Number(b.layers['retriever'] === 'random')));
  protected readonly activeView = computed(() => this.state().tab === 'head-to-head' ? 'compare' : this.state().tab);
  protected readonly title = computed(() => ({leaderboard:'Better retrieval, in perspective.',metrics:'Understand every metric',compare:'Compare combinations',builder:'Build your combination',questions:'Explore the evidence',categories:'Scores by category',history:'Evaluation history'}[this.activeView()] ?? 'Benchmark'));
  protected readonly subtitle = computed(() => ({leaderboard:'Every evaluated combination, ranked on the same questions.',metrics:'See where each combination performs best.',compare:'Two pipelines. The same questions. A clear comparison.',builder:'Choose your pipeline. See how it measures up.',questions:'Inspect the sources retrieved for each question.',categories:'Understand performance across question types.',history:'Follow recorded results over time.'}[this.activeView()] ?? ''));
  protected readonly categories = computed(() => [...new Set(this.runs().flatMap(run => run.questions.map(question => question.category)))].sort());
  protected readonly count = computed(() => Number(this.runs()[0]?.scores.overall['question_count'] ?? 0));
  protected readonly times = computed(() => this.splitTimes()[this.state().split] ?? []);
  protected readonly questions = computed(() => {
    const unique = [...new Map(this.runs().flatMap(run => run.questions).map(question => [question.question_id,question])).values()];
    const query = this.state().search.toLowerCase();
    const matching = unique.filter(question => (!this.state().category || question.category === this.state().category) && (!query || question.question_id.toLowerCase().includes(query) || this.dataset().get(question.question_id)?.question.toLowerCase().includes(query) || this.runs().some(run => run.questions.find(row => row.question_id === question.question_id)?.top_chunk_ids.some(chunk => chunk.toLowerCase().includes(query)))));
    if (this.state().sort === 'disagreement' && this.contenders().length >= 2) {
      const deltas = new Map(differences(this.contenders()[0],this.contenders()[1],this.state().metric).map(row => [row.id,Math.abs(row.delta)]));
      return matching.sort((a,b) => (deltas.get(b.question_id) ?? -1)-(deltas.get(a.question_id) ?? -1));
    }
    return matching.sort((a,b) => a.question_id.localeCompare(b.question_id));
  });
  protected readonly extraReportFields = computed(() => Object.entries(this.report() ?? {}).filter(([key]) => !['dataset','evaluated_at','code','runs','description'].includes(key)));
  protected readonly extraRunFields = computed(() => {
    const run = this.selected();
    if (!run) return [];
    const fields = Object.entries(run).filter(([key]) => !['layers','scores','questions','description'].includes(key));
    for (const [key,value] of Object.entries(run.scores)) if (!['overall','by_category'].includes(key)) fields.push(['scores.' + key,value]);
    for (const [key,value] of Object.entries(run.scores.overall)) if (key !== 'mean_recall_curve' && typeof value !== 'number' && (typeof value !== 'object' || value === null || Object.values(value).some(child => typeof child !== 'number'))) fields.push(['overall.' + key,value]);
    return fields;
  });
  private generation = 0;
  private readonly reportRequest = computed(() => JSON.stringify([this.state().version,this.state().split,this.state().timestamp]));
  constructor() { effect(() => { this.reportRequest(); untracked(() => void this.load(this.state(),this.query()['pins'] === undefined)); }); }
  ngOnDestroy(): void { this.generation++; }
  protected async load(state = this.state(), defaultPins = false): Promise<void> {
    const generation = ++this.generation;
    this.loading.set(true); this.error.set('');
    try {
      const [dev,test] = await Promise.all(['dev','test'].map(split => firstValueFrom(this.evaluations.timestamps(state.version,split))));
      if (generation !== this.generation) return;
      this.splitTimes.set({dev,test});
      const split = state.split || (test.length ? 'test' : 'dev'), times = split === 'test' ? test : dev;
      if (!times.length) { this.report.set(null); this.loading.set(false); if (state.split !== split) void this.update({split},true); return; }
      const timestamp = state.timestamp && times.includes(state.timestamp) ? state.timestamp : times.at(-1)!;
      const report = await firstValueFrom(this.evaluations.report(state.version,split,timestamp));
      if (generation !== this.generation) return;
      this.report.set(report);
      void this.loadDataset(state.version,split,generation);
      const ranked = [...report.runs].filter(run => run.layers['retriever'] !== 'random').sort((a,b) => (atPath(b.scores.overall,DECISION_METRIC) ?? -1)-(atPath(a.scores.overall,DECISION_METRIC) ?? -1));
      const keys = report.runs.map(run => runKey(run.layers));
      const run = keys.includes(state.run) || (state.tab === 'builder' && decodeRun(state.run)) ? state.run : runKey((ranked[0] ?? report.runs[0])?.layers ?? {});
      const pins = defaultPins ? ranked.slice(0,2).map(run => runKey(run.layers)) : state.pins.filter(key => keys.includes(key));
      const availableMetrics = metrics(report.runs), metric = availableMetrics.includes(state.metric) ? state.metric : availableMetrics[0] ?? DECISION_METRIC;
      const next = {...state,split,timestamp,run,pins,metric};
      if (JSON.stringify(writeState(next)) !== JSON.stringify(this.query())) void this.router.navigate([], {relativeTo:this.route,queryParams:writeState(next),replaceUrl:true});
    } catch { if (generation === this.generation) { this.report.set(null); this.error.set('This evaluation could not be loaded. Check the connection and try again.'); } }
    finally { if (generation === this.generation) this.loading.set(false); }
  }
  private async loadDataset(version: string, split: string, generation: number): Promise<void> {
    try {
      const questions = await firstValueFrom(this.evaluations.dataset(version,split));
      if (generation === this.generation) this.dataset.set(new Map(questions.map(question => [question.id,question])));
    } catch { if (generation === this.generation) this.dataset.set(new Map()); }
  }
  protected update(changes: Partial<LabState>, replaceUrl = false): Promise<boolean> {
    return this.router.navigate([], {relativeTo:this.route,queryParams:writeState({...this.state(),...changes}),replaceUrl});
  }
  protected selectRun(key: string): void { void this.update({run:key}); }
  protected pin(key: string): void {
    const pins = this.state().pins;
    if (pins.includes(key)) void this.update({pins:pins.filter(pin => pin !== key)});
    else if (pins.length < 4) void this.update({pins:[...pins,key]});
  }
  protected changeComparison(index: number, key: string): void {
    const keys = this.comparison().map(run => runKey(run.layers));
    if (keys[1-index] === key) { [keys[0],keys[1]] = [keys[1],keys[0]]; }
    else keys[index] = key;
    void this.update({pins:keys,question:''});
  }
  protected compareRun(key: string): void {
    const other = this.ranked().find(row => row.key !== key)?.key;
    void this.update({tab:'compare',pins:[key,...(other ? [other] : [])],question:''});
  }
  protected buildRun(key: string): void { void this.update({tab:'builder',run:key}); }
  protected scoreWidth(run: EvaluationRun, metric: string): number {
    const value = atPath(run.scores.overall,metric);
    const max = isRatio(metric) ? 1 : Math.max(1,...this.runs().map(run => atPath(run.scores.overall,metric) ?? 0));
    return value === null ? 0 : Math.max(0,Math.min(100,value/max*100));
  }
  protected delta(metric: string): number | null {
    const a = atPath(this.comparison()[0]?.scores.overall,metric), b = atPath(this.comparison()[1]?.scores.overall,metric);
    return a === null || b === null ? null : a-b;
  }
  protected deltaText(metric: string): string {
    const delta = this.delta(metric);
    if (delta === null) return '—';
    if (Math.abs(delta) < 1e-10) return 'Tied';
    return (delta > 0 ? '+' : '−') + (isRatio(metric) ? (Math.abs(delta)*100).toFixed(1) + ' pp' : formatMetric(metric,Math.abs(delta)));
  }
  protected advantage(metric: string): string {
    if (isContextMetric(metric)) return 'context';
    const delta = this.delta(metric);
    return delta === null || Math.abs(delta) < 1e-10 ? 'tie' : ((lowerIsBetter(metric) ? -delta : delta) > 0 ? 'a' : 'b');
  }
  protected openQuestion(id: string): void { void this.update({question:id,tab:'questions'}); }
  protected value(event: Event): string { return (event.target as HTMLInputElement).value; }
  protected date(timestamp: string): string { return new Date(timestamp.replace(/T(\d{2})-(\d{2})-(\d{2})Z/,'T$1:$2:$3Z')).toLocaleString('en-GB',{dateStyle:'medium',timeStyle:'short'}); }
  protected async share(): Promise<void> { try { await navigator.clipboard.writeText(location.href); this.copied.set(true); this.copyMessage.set('Link copied.'); } catch { this.copyMessage.set('Copy the URL from your browser to share this view.'); } }
}


