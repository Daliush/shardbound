import { Component, ElementRef, OnDestroy, afterRenderEffect, computed, effect, inject, input, linkedSignal, output, signal } from '@angular/core';
import { gsap } from 'gsap';
import { EvaluationRun, Layers } from '../api/evaluation';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { StageChoice, bestRun, fixedStages, flowCounts, rankOf, stageNodes } from './builder-model';
import { isFloor, runKey } from './layer-model';
import { DECISION_METRIC, atPath, metrics } from './metrics';
import { MetricCatalog } from './metric-catalog';
import { zoomedAxis } from './metrics-graph';
import { matchingRun, pipelineName, variantName } from './presentation';
import { RollingNumber } from './rolling-number';
import { SPRINGS, placeMarker, still } from './springs';

/** How far apart, in percent of the strip, two dots of one lane must sit. */
const DOT_GAP = 1.4, LANES = 4;
/** The width under which the pipeline runs top to bottom (pipeline-builder-mobile.css). */
const VERTICAL = '(max-width: 1060px)';

/**
 * The pipeline drawn as it runs, left to right: the question, each step with every variant it was evaluated with, the
 * prompt. A click changes a step and the recorded score of the new pipeline rolls in; each choice says how the score
 * would move. Only recorded runs are shown, never an estimate: a combination that was not run says so.
 */
@Component({
  selector:'app-pipeline-builder', imports:[Icon,RollingNumber],
  templateUrl:'./pipeline-builder.html', styleUrls:['./pipeline-builder-flow.css','./pipeline-builder.css','./pipeline-builder-output.css','./pipeline-builder-scores.css','./pipeline-builder-mobile.css'],
})
export class PipelineBuilder implements OnDestroy {
  readonly runs = input<EvaluationRun[]>([]);
  readonly initial = input<Layers>({});
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  readonly changed = output<Layers>();
  readonly compared = output<string>();
  protected readonly decision = DECISION_METRIC;
  protected readonly variant = variantName;
  protected readonly draft = signal<Layers>({});
  protected readonly nodes = computed(() => stageNodes(this.runs(),this.draft()));
  protected readonly fixed = computed(() => fixedStages(this.runs()));
  protected readonly match = computed(() => matchingRun(this.runs(),this.draft()));
  /** The pipeline picked before this one, which each score's change is measured from. */
  protected readonly previous = linkedSignal<EvaluationRun | undefined,EvaluationRun | undefined>({source:this.match,computation:(_,before) => before?.source});
  protected readonly counts = computed(() => flowCounts(this.runs(),this.draft(),this.nodes().map(node => node.layer)));
  protected readonly pipelines = computed(() => this.runs().filter(run => !isFloor(run)));
  protected readonly best = computed(() => bestRun(this.runs()));
  protected readonly tiles = computed(() => metrics(this.runs()).filter(metric => metric !== DECISION_METRIC && !this.catalog().isContext(metric)));
  protected readonly rank = computed(() => { const run = this.match(); return run ? rankOf(this.runs(),run,DECISION_METRIC,false) : null; });
  /** Every pipeline on one strip by the decision metric, spread over lanes so that close scores stay apart. */
  protected readonly strip = computed(() => {
    const scored = this.pipelines().flatMap(run => { const value = atPath(run.scores.overall,DECISION_METRIC); return value === null ? [] : [{run,value}]; });
    if (!scored.length) return null;
    const axis = zoomedAxis(scored.map(item => item.value),true), ends: number[] = [];
    const dots = [...scored].sort((a,b) => a.value-b.value).map(({run,value}) => {
      const x = axis.x(value);
      let lane = ends.findIndex(end => x - end >= DOT_GAP);
      if (lane === -1) lane = ends.length < LANES ? ends.length : ends.indexOf(Math.min(...ends));
      ends[lane] = x;
      return {key:runKey(run.layers),layers:run.layers,name:pipelineName(run),text:this.format(DECISION_METRIC,value),x,lane};
    });
    const floor = atPath(this.runs().find(isFloor)?.scores.overall,DECISION_METRIC);
    return {axis,dots,lanes:Math.min(LANES,ends.length),floor,floorX:floor !== null && floor >= axis.domain[0] ? axis.x(floor) : null};
  });
  protected readonly marker = computed(() => {
    const run = this.match(), strip = this.strip(), value = run ? atPath(run.scores.overall,DECISION_METRIC) : null;
    return strip && value !== null && !isFloor(run!) ? strip.axis.x(Math.max(strip.axis.domain[0],value)) : null;
  });
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly settings = inject(Settings);
  private readonly motion = gsap.context(() => {});
  private sparked: string | null = null;

  constructor() {
    effect(() => this.draft.set({...this.initial()}));
    afterRenderEffect(() => { this.nodes(); this.placePills(); });
    afterRenderEffect(() => { const run = this.match(); this.spark(run ? runKey(run.layers) : 'none'); });
  }
  ngOnDestroy(): void { this.motion.revert(); }

  protected choose(layer: string, value: string | null): void {
    if ((this.draft()[layer] ?? null) === value) return;
    const draft = {...this.draft(),[layer]:value};
    this.draft.set(draft); this.changed.emit(draft);
  }
  protected load(layers: Layers): void { this.draft.set({...layers}); this.changed.emit({...layers}); }
  protected startFromBest(): void { const best = this.best(); if (best) this.load(best.layers); }
  protected isBest(): boolean { const best = this.best(), run = this.match(); return !!best && best === run; }

  protected at(run: EvaluationRun | undefined, metric: string): number | null { return atPath(run?.scores.overall,metric); }
  protected format(metric: string, value: number | null): string { return this.catalog().format(metric,value); }
  protected metricName(metric: string): string { return this.catalog().name(metric); }
  protected description(metric: string): string { return this.catalog().description(metric)?.what ?? ''; }
  protected lower(metric: string): boolean { return this.catalog().lowerIsBetter(metric); }
  protected key(run: EvaluationRun): string { return runKey(run.layers); }
  protected chunks(count: number | null): string { return count === null ? '—' : Math.round(count).toString(); }
  protected rankText(rank: number | null): string { return rank === null ? '—' : String(rank); }

  /** What a choice would do to the decision score: "+4.2", "−3.1", or the score itself for the choice made. */
  protected effect(choice: StageChoice): string {
    const current = this.at(this.match(),DECISION_METRIC);
    if (choice.score === null) return 'not run';
    if (choice.chosen) return this.format(DECISION_METRIC,choice.score);
    if (current === null) return this.format(DECISION_METRIC,choice.score);
    const delta = (choice.score - current) * 100;
    return Math.abs(delta) < .05 ? '±0' : (delta > 0 ? '+' : '−') + Math.abs(delta).toFixed(1);
  }
  protected effectTone(choice: StageChoice): string {
    const current = this.at(this.match(),DECISION_METRIC);
    if (choice.score === null) return 'missing';
    if (choice.chosen || current === null) return 'current';
    const delta = choice.score - current;
    return Math.abs(delta) < .0005 ? 'same' : delta > 0 ? 'up' : 'down';
  }
  /** A score's change since the pipeline picked before: "+4.2 pts" for a share, better or worse by the metric's direction. */
  protected change(metric: string): { text: string; tone: string } | null {
    const now = this.at(this.match(),metric), before = this.at(this.previous(),metric);
    if (now === null || before === null || this.previous() === this.match()) return null;
    const delta = now - before;
    if (Math.abs(delta) < 1e-9) return {text:'No change',tone:'same'};
    const better = this.lower(metric) ? delta < 0 : delta > 0;
    const size = this.catalog().isShare(metric) ? (Math.abs(delta)*100).toFixed(1) + ' pts' : this.format(metric,Math.abs(delta));
    return {text:(delta > 0 ? '+' : '−') + size,tone:better ? 'up' : 'down'};
  }
  /** A tile's bar: always longer for a better value, on the scale of every pipeline. */
  protected width(metric: string): number {
    const ceiling = this.catalog().ceiling(metric,this.pipelines().map(run => atPath(run.scores.overall,metric)));
    return this.catalog().barLength(metric,this.at(this.match(),metric),ceiling) * 100;
  }
  protected tileRank(metric: string): number | null { const run = this.match(); return run ? rankOf(this.runs(),run,metric,this.lower(metric)) : null; }

  /** Each step's pill glides to the choice made, growing or shrinking to its size. */
  private placePills(): void {
    for (const choices of this.host.nativeElement.querySelectorAll<HTMLElement>('[data-choices]')) {
      const pill = choices.querySelector<HTMLElement>('[data-pill]'), chosen = choices.querySelector<HTMLElement>('[data-chosen]');
      if (!pill) continue;
      if (!chosen) { pill.style.opacity = '0'; continue; }
      placeMarker(pill,{x:0,y:chosen.offsetTop,height:chosen.offsetHeight},this.settings);
    }
  }
  /** A spark runs down the wires each time the pipeline changes, as if the question went through it again. */
  private spark(key: string): void {
    const first = this.sparked === null;
    if (this.sparked === key) return;
    this.sparked = key;
    if (first || still(this.settings)) return;
    const host = this.host.nativeElement;
    this.motion.add(() => {
      const along = typeof matchMedia === 'function' && matchMedia(VERTICAL).matches ? 'top' : 'left';
      gsap.fromTo(host.querySelectorAll('[data-spark]'),{[along]:'0%',opacity:0},{keyframes:[{opacity:1,duration:.08},{[along]:'100%',duration:.32,ease:'power1.in'},{opacity:0,duration:.08}],stagger:.12});
      const output = host.querySelector('[data-output]');
      if (output) gsap.fromTo(output,{scale:.97},{scale:1,delay:.38,duration:SPRINGS.bouncy.duration,ease:SPRINGS.bouncy.ease,clearProps:'transform'});
    });
  }
}
