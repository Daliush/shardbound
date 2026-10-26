import { Component, ElementRef, OnDestroy, afterRenderEffect, computed, inject, input, model, signal, viewChild } from '@angular/core';
import { gsap } from 'gsap';
import { EvaluationRun } from '../api/evaluation';
import { Settings } from '../core/settings';
import { isFloor, runKey } from './layer-model';
import { atPath, candidateSummary, humanize } from './metrics';
import { MetricCatalog } from './metric-catalog';
import { isVariant, pipelineName, runColor } from './presentation';
import { budgetFill, curvePath, rankAtBudget, recallAt, recallCurve } from './recall-model';
import { LabMotion } from './lab-motion';

@Component({
  selector:'app-recall-chart', imports:[LabMotion],
  templateUrl:'./recall-chart.html', styleUrls:['./recall-chart.css','./recall-chart-responsive.css'],
})
export class RecallChart implements OnDestroy {
  readonly runs = input<EvaluationRun[]>([]);
  readonly category = input('');
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  /** A small multiple: the budget and the focused pipeline come from a shared control, so it draws neither. */
  readonly compact = input(false);
  protected readonly full = signal(false);
  readonly budget = model(20);
  readonly focused = model<string | null>(null);
  protected readonly humanize = humanize;
  protected readonly ticks = [0,.25,.5,.75,1];
  protected readonly plotWidth = signal(790);
  private readonly canvas = viewChild<ElementRef<HTMLDivElement>>('canvas');
  private readonly readoutPanel = viewChild<ElementRef<HTMLElement>>('readoutPanel');
  private readonly settings = inject(Settings);
  /** Where each readout row sat in the panel after the last render, so that a row the new order moves slides from there. */
  private readonly rowTops = new Map<string,number>();
  private observer?: ResizeObserver;
  constructor() {
    afterRenderEffect(() => {
      const canvas = this.canvas()?.nativeElement;
      this.observer?.disconnect();
      if (!canvas || typeof ResizeObserver === 'undefined') return;
      this.observer = new ResizeObserver(entries => { const width = Math.round(entries[0].contentRect.width); if (width > 0) this.plotWidth.set(Math.max(280,width)); });
      this.observer.observe(canvas);
    });
    afterRenderEffect(() => {
      this.readout();
      const panel = this.readoutPanel()?.nativeElement;
      if (panel) this.slideRows(panel); else this.rowTops.clear();
    });
  }
  ngOnDestroy(): void { this.observer?.disconnect(); }
  /** Recall@k at the budget: a point of the recall curve, written as the curve is. */
  protected formatRecall(value: number | null): string { return this.catalog().format('mean_recall_curve',value); }
  protected readonly all = computed(() => this.runs().map(run => {
    const scores = this.category() ? run.scores.by_category[this.category()] : run.scores.overall;
    return {key:runKey(run.layers),name:pipelineName(run),color:runColor(run),curve:recallCurve(scores),summary:candidateSummary(scores,this.catalog()),description:run.description,random:isFloor(run),variant:isVariant(run)};
  }));
  protected readonly maximum = computed(() => Math.max(1,...this.all().map(series => series.curve.length)));
  protected readonly limit = computed(() => this.full() ? this.maximum() : Math.min(70,this.maximum()));
  protected readonly k = computed(() => Math.min(this.limit(),this.budget()));
  protected readonly progress = computed(() => budgetFill(this.k(),this.limit()));
  protected readonly sample = computed(() => {
    const scores = this.category() ? this.runs()[0]?.scores.by_category[this.category()] : this.runs()[0]?.scores.overall;
    return atPath(scores,'question_count');
  });
  protected readonly series = computed(() => this.all().map(series => {
    const last = Math.min(this.limit(),series.curve.length), ceiling = series.curve.at(-1) ?? 0;
    return {...series,path:curvePath(series.curve,this.limit(),k => this.x(k),value => this.y(value)),
      extension:last < this.limit() ? `M${this.x(last)},${this.y(ceiling)}L${this.x(this.limit())},${this.y(ceiling)}` : '',
      endX:this.x(last),endY:this.y(ceiling),ended:series.curve.length <= this.limit(),value:recallAt(series.curve,this.k())};
  }));
  protected readonly xTicks = computed(() => [...new Set([1,...(this.plotWidth() < 460 ? (this.limit() > 70 ? [50,100,150] : [20,40,60]) : this.limit() > 70 ? [20,50,100,150] : [10,20,30,40,50,60]),this.limit()])].filter(k => k <= this.limit()));
  /** The pipelines on the right of the chart, ranked by their recall at the budget. */
  protected readonly readout = computed(() => rankAtBudget(this.series()));
  /** What a small multiple reads out: the focused pipeline, or else the best at the budget and how many tie with it. */
  protected readonly lead = computed(() => {
    const scored = this.series().filter(item => !item.random);
    const focused = scored.find(item => item.key === this.focused());
    if (focused) return {item:focused,label:focused.name};
    const best = [...scored].sort((a,b) => b.value-a.value)[0];
    if (!best) return null;
    const tied = scored.filter(item => item.value === best.value).length - 1;
    return {item:best,label:'Best: ' + best.name + (tied ? ' and ' + tied + ' tied' : '')};
  });
  protected readonly motionKey = computed(() => [this.category(),this.limit(),...this.all().map(series => series.key)].join(':'));
  protected x(k: number): number { return 44 + (k-1)/Math.max(1,this.limit()-1)*(this.plotWidth()-60); }
  protected y(value: number): number { return 270-value*224; }
  protected inspect(event: PointerEvent, element: Element): void {
    const svg = element as SVGSVGElement;
    const point = svg.createSVGPoint(); point.x = event.clientX; point.y = event.clientY;
    const matrix = svg.getScreenCTM();
    if (!matrix) return;
    const local = point.matrixTransform(matrix.inverse());
    this.budget.set(Math.max(1,Math.min(this.limit(),Math.round(1+(local.x-44)/(this.plotWidth()-60)*(this.limit()-1)))));
  }
  protected setBudget(event: Event): void { this.budget.set(Number((event.target as HTMLInputElement).value)); }
  protected highlight(key: string): void { this.focused.update(current => current === key ? null : key); }
  /** Each row the new order moves glides from where it was seen, a slide in progress included, to its new place; the farther it travels, the higher it passes. */
  private slideRows(panel: HTMLElement): void {
    const animate = !this.settings.reducedMotion() && this.settings.preferences().speed !== 0;
    for (const row of panel.querySelectorAll<HTMLElement>('[data-readout-key]')) {
      const key = row.dataset['readoutKey']!, top = row.offsetTop, before = this.rowTops.get(key);
      this.rowTops.set(key,top);
      if (!animate || before === undefined || before === top) continue;
      const from = before + Number(gsap.getProperty(row,'y')) - top;
      gsap.fromTo(row,{y:from,zIndex:1+Math.round(Math.abs(from))},{y:0,duration:.5,ease:'power3.out',overwrite:true,clearProps:'transform,zIndex'});
    }
  }
}
