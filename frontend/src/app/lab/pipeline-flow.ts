import { Component, ElementRef, OnDestroy, afterRenderEffect, computed, inject, input, signal, viewChild } from '@angular/core';
import { linkHorizontal } from 'd3-shape';
import { gsap } from 'gsap';
import { EvaluationRun } from '../api/evaluation';
import { Settings } from '../core/settings';
import { ChunkChip } from './chunk-chip';
import { PipelineColumn, PipelineSlot, citedChunks, columnSummary, pipelineColumns } from './pipeline-model';

/** Pixels of the drawing: a rank every STEP, columns GAP apart after a GUTTER for the cut labels. */
const STEP = 7, BAR = 5, TOP = 52, GAP = 40, GUTTER = 28, LINE = 15, MIN_WIDTH = 600;
const link = linkHorizontal();

/** One question through the pipeline's stages: every chunk at its rank, cited chunks joined from stage to stage. */
@Component({
  selector:'app-pipeline-flow', imports:[ChunkChip],
  templateUrl:'./pipeline-flow.html', styleUrl:'./pipeline-flow.css',
})
export class PipelineFlow implements OnDestroy {
  readonly id = input.required<string>(); readonly runs = input<EvaluationRun[]>([]); readonly cards = input<readonly string[]>([]);
  protected readonly focused = signal<string | null>(null);
  protected readonly width = signal(760);
  protected readonly gutter = GUTTER; protected readonly bar = BAR;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly settings = inject(Settings);
  private readonly canvas = viewChild<ElementRef<HTMLDivElement>>('canvas');
  private observer?: ResizeObserver; private motion?: gsap.Context;
  protected readonly columns = computed(() => pipelineColumns(this.runs(),this.id(),this.cards()));
  protected readonly cited = computed(() => citedChunks(this.columns()));
  protected readonly columnWidth = computed(() => (this.width()-GUTTER-GAP*(this.columns().length-1))/Math.max(1,this.columns().length));
  protected readonly layout = computed(() => this.columns().map((column,index) => {
    const summary = this.cited().length ? columnSummary(column) : [];
    return {column,summary,x:GUTTER+index*(this.columnWidth()+GAP),boxY:TOP+column.slots.length*STEP+10,boxHeight:summary.length*LINE+12};
  }));
  protected readonly height = computed(() => Math.max(TOP,...this.layout().map(item => item.boxY+item.boxHeight))+4);
  protected readonly cuts = computed(() => [10,20].filter(rank => this.columns().some(column => column.slots.length > rank)).map(rank => ({rank,y:TOP+rank*STEP-1})));
  /** One link per cited chunk between two stages, from its bar, or from the summary below when it ranks past what is drawn. */
  protected readonly links = computed(() => this.layout().slice(1).flatMap((to,index) => {
    const from = this.layout()[index];
    return this.cited().flatMap(chunk => {
      const start = this.anchor(from,chunk,from.x+this.columnWidth()), end = this.anchor(to,chunk,to.x);
      return start && end ? [{key:index+':'+chunk,chunk,path:link({source:start,target:end}) ?? ''}] : [];
    });
  }));
  protected readonly description = computed(() => 'Ranks of every chunk at ' + this.columns().map(column => column.stage.label).join(', ') + '. ' + this.layout().map(item => item.column.stage.label + ': ' + item.summary.join(', ')).join('. '));
  private readonly motionKey = computed(() => this.id() + ':' + this.columns().length);
  constructor() {
    afterRenderEffect(() => {
      const canvas = this.canvas()?.nativeElement;
      this.observer?.disconnect();
      if (!canvas || typeof ResizeObserver === 'undefined') return;
      this.observer = new ResizeObserver(entries => { const width = Math.round(entries[0].contentRect.width); if (width > 0) this.width.set(Math.max(MIN_WIDTH,width)); });
      this.observer.observe(canvas);
    });
    afterRenderEffect(() => {
      this.motionKey();
      this.motion?.revert();
      if (this.settings.reducedMotion() || this.settings.preferences().speed === 0) return;
      const host = this.host.nativeElement;
      this.motion = gsap.context(() => this.play(host),host);
    });
  }
  ngOnDestroy(): void { this.observer?.disconnect(); this.motion?.revert(); }
  protected y(rank: number): number { return TOP+(rank-1)*STEP; }
  protected subtitle(column: PipelineColumn): string {
    return column.stage.role + ' · ' + (column.slots.length === column.total ? column.total + ' chunks' : 'top ' + column.slots.length + ' of ' + column.total);
  }
  protected slotLabel(slot: PipelineSlot): string { return '#' + slot.rank + ' ' + (slot.chunk ?? 'chunk not cited'); }
  protected focus(chunk: string | null): void { this.focused.set(chunk); }
  protected rankText(column: PipelineColumn, chunk: string): string { const rank = column.ranks[chunk] ?? null; return rank === null ? '—' : '#' + rank; }
  /** In the prompt, among the ranks the stage hands on, past them, or never returned. */
  protected rankPlace(column: PipelineColumn, chunk: string): string {
    const rank = column.ranks[chunk] ?? null;
    return rank === null ? 'missing' : rank <= 10 ? 'in-prompt' : rank <= column.slots.length ? 'handed-on' : 'beyond';
  }
  private anchor(item: {column: PipelineColumn; boxY: number; boxHeight: number}, chunk: string, x: number): [number,number] | null {
    const rank = item.column.ranks[chunk] ?? null;
    if (rank === null) return null;
    return rank <= item.column.slots.length ? [x,this.y(rank)+BAR/2] : [x,item.boxY+item.boxHeight/2];
  }
  /** Stage after stage, the bars grow from the left, then the cited chunks' links draw towards the next stage. */
  private play(host: HTMLElement): void {
    const timeline = gsap.timeline();
    host.querySelectorAll('[data-flow-stage]').forEach((stage,index) => {
      const bars = stage.querySelectorAll('[data-flow-bar]'), summary = stage.querySelectorAll('[data-flow-summary]');
      if (bars.length) timeline.from(bars,{scaleX:0,transformOrigin:'left center',duration:.4,stagger:.004,ease:'power3.out',clearProps:'transform'},index*.22);
      if (summary.length) timeline.from(summary,{opacity:0,y:4,duration:.3,clearProps:'opacity,transform'},index*.22+.2);
    });
    const links = [...host.querySelectorAll<SVGPathElement>('[data-flow-link]')].filter(path => typeof path.getTotalLength === 'function');
    if (links.length) timeline.fromTo(links,{strokeDasharray:(_: number,path: SVGPathElement) => path.getTotalLength(),strokeDashoffset:(_: number,path: SVGPathElement) => path.getTotalLength()},{strokeDashoffset:0,duration:.6,stagger:.02,ease:'power2.out',clearProps:'strokeDasharray,strokeDashoffset'},'-=.25');
  }
}
