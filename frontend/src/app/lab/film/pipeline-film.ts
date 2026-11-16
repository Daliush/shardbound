import { Component, ElementRef, OnDestroy, afterRenderEffect, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { gsap } from 'gsap';
import { DatasetQuestion, EvaluationRun } from '../../api/evaluation';
import { Icon } from '../../core/icon';
import { Settings } from '../../core/settings';
import { CardBook } from '../../game/card-book';
import { ruleSection } from '../chunk-chip';
import { MetricCatalog } from '../metric-catalog';
import { DECISION_METRIC } from '../metrics';
import { variantName } from '../presentation';
import { still } from '../springs';
import { Box, NUMBERED_TILES, scatter, stageLayout } from './film-layout';
import { filmChunks, filmExample, stepOrders } from './film-model';
import { ChapterId, FilmLine, STEP_NAMES, broughtBack, chapterTitle, filmScript, readTime } from './film-script';
import { FilmTerm, captionParts } from './film-terms';

/** What the bench shows during a caption: the archive, a step's ranking, the prompt being filled, or something else. */
interface View { kind: 'intro' | 'step' | 'context' | 'after'; step: number; }
/** A caption, where it starts on the timeline, when its motion is over (`done`) and when the next one starts (`end`). */
interface Beat extends FilmLine { at: number; done: number; end: number; bench: string; view: View; }
/** A chapter: its captions, from the first's start to the end of the last one's motion (`done`). Next plays one. */
interface Chapter { id: ChapterId; title: string; start: number; end: number; done: number; }
interface SlipState { x: number; y: number; w: number; h: number; o: number; label: number; }
/** A term's definition, placed in the player: its box's left edge, its bottom edge from the player's, its arrow's x. */
interface Hint { term: FilmTerm; left: number; bottom: number; arrow: number; }

/** Under this width, in pixels, the film plays on its tall stage. */
const NARROW = 620;
const MOVE = 'power3.inOut', ARRIVE = 'power3.out';
/** nDCG's weight of each of the first 10 ranks: 1 / log2(rank + 1). */
const WEIGHTS = Array.from({length:10},(_,index) => 1/Math.log2(index + 2));

/**
 * How a pipeline works, played on one real question of the evaluation: the chunks it searches, the ones its answer key
 * cites, each step of the best pipeline as recorded, what the prompt holds, context recall, dense retrieval alone on
 * the same question, and the scores that read a ranking. The viewer moves through it one chapter at a time, each told
 * in one caption: Next plays a whole chapter on one GSAP timeline and stops; Previous shows the chapter before as it
 * ends. Every chunk on screen sits where the report recorded it, and the needed ones say their rank. The captions'
 * terms (`film-terms.ts`) show what they mean when pointed at.
 */
@Component({
  selector:'app-pipeline-film', imports:[Icon],
  templateUrl:'./pipeline-film.html',
  styleUrls:['./pipeline-film.css','./pipeline-film-stage.css','./pipeline-film-panels.css','./pipeline-film-slips.css','./pipeline-film-phone.css','./pipeline-film-terms.css'],
  host:{'(keydown)':'key($event)','(pointerdown)':'away($event)'},
})
export class PipelineFilm implements OnDestroy {
  readonly runs = input<EvaluationRun[]>([]);
  readonly dataset = input<ReadonlyMap<string,DatasetQuestion>>(new Map());
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  readonly closed = output<void>();
  readonly explored = output<void>();
  readonly ended = output<void>();

  protected readonly book = inject(CardBook);
  private readonly settings = inject(Settings);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly player = viewChild<ElementRef<HTMLElement>>('player');
  private readonly screen = viewChild<ElementRef<HTMLElement>>('screen');
  private readonly stage = viewChild<ElementRef<HTMLElement>>('stage');
  private readonly definition = viewChild<ElementRef<HTMLElement>>('definition');

  protected readonly example = computed(() => filmExample(this.runs(),this.dataset()));
  protected readonly chunks = computed(() => { const example = this.example(); return example ? filmChunks(example) : []; });
  private readonly orders = computed(() => { const example = this.example(); return example ? stepOrders(example,this.chunks()) : []; });
  /** Each step's rank of every chunk it hands on, from 1. */
  private readonly ranks = computed(() => this.orders().map(order => new Map(order.map((key,index) => [key,index + 1]))));
  protected readonly tall = signal(false);
  /** The stage fitted to its screen, centered: on a phone the screen is what the caption and the controls leave. */
  protected readonly fit = signal({scale:1,x:0,y:0});
  protected readonly layout = computed(() => stageLayout(this.tall()));
  private readonly lines = computed(() => { const example = this.example(); return example ? filmScript(example,value => this.catalog().format(DECISION_METRIC,value)) : []; });
  protected readonly beats = signal<Beat[]>([]);
  protected readonly current = signal(0);
  /** Whether the viewer has pressed Next once: until then, Next calls for it. */
  protected readonly advanced = signal(false);
  protected readonly line = computed(() => this.beats()[this.current()] ?? null);
  protected readonly bench = computed(() => this.line()?.bench ?? '');
  private readonly view = computed<View>(() => this.line()?.view ?? {kind:'intro',step:-1});
  /** Whether the tiles under the cut are few enough to carry their rank. */
  protected readonly numbered = computed(() => { const view = this.view(); return view.kind === 'step' && (this.orders()[view.step]?.length ?? 0) - 10 <= NUMBERED_TILES; });
  /** The rank each tile under the cut shows: every tile's when there are few, else only a needed chunk's. */
  protected readonly tags = computed(() => {
    const view = this.view(), ranks = this.ranks()[view.step], numbered = this.numbered();
    return this.chunks().map(chunk => {
      const rank = view.kind === 'step' ? ranks?.get(chunk.key) : undefined;
      return rank !== undefined && rank > 10 && (numbered || chunk.needed) ? (numbered ? '' : '#') + rank : '';
    });
  });
  /** The needed chunks cut by a step shown so far: they wait in the tray. */
  protected readonly cutKeys = computed(() => {
    const view = this.view(), steps = this.example()?.steps ?? [];
    const until = view.kind === 'step' ? view.step : view.kind === 'context' ? steps.length - 1 : -1;
    return new Set(steps.slice(0,until + 1).flatMap(step => step.cut));
  });
  /** Where the needed chunks are at this caption: in the top 10, lower, cut; or in the prompt, missed. */
  protected readonly tally = computed(() => {
    const view = this.view(), example = this.example(), ranks = this.ranks()[view.step];
    if (!example) return [];
    if (view.kind === 'context') return [{count:example.best.held,text:'in the prompt',tone:'on'},{count:example.needed.length - example.best.held,text:'missed',tone:'off'}];
    if (view.kind !== 'step' || !ranks) return [];
    const placed = example.needed.map(id => ranks.get(id)), cut = this.cutKeys().size;
    return [{count:placed.filter(rank => rank !== undefined && rank <= 10).length,text:'in the top 10',tone:'on'},
      {count:placed.filter(rank => rank !== undefined && rank > 10).length,text:'lower',tone:'low'},
      ...(cut ? [{count:cut,text:'cut',tone:'off'}] : [])];
  });
  /** "Ranks 11–191": what the tiles under the cut stand for. */
  protected readonly range = computed(() => { const view = this.view(), total = this.orders()[view.step]?.length ?? 0; return view.kind === 'step' && total > 10 ? 'Ranks 11–' + total : ''; });
  protected readonly chapters = computed<Chapter[]>(() => {
    const example = this.example(), chapters: Chapter[] = [];
    for (const beat of this.beats()) {
      const last = chapters.at(-1);
      if (last?.id === beat.chapter) { last.end = beat.end; last.done = beat.done; }
      else chapters.push({id:beat.chapter,title:example ? chapterTitle(beat.chapter,example) : beat.chapter,start:beat.at,end:beat.end,done:beat.done});
    }
    return chapters;
  });
  protected readonly chapterIndex = computed(() => this.chapters().findIndex(chapter => chapter.id === this.line()?.chapter));
  protected readonly last = computed(() => this.chapters().length > 0 && this.chapterIndex() === this.chapters().length - 1);
  /** The captions shown, cut around their terms: the one the time has reached; without motion, every caption of the chapter at once. */
  protected readonly captions = computed(() => {
    const line = this.line();
    if (!line) return [];
    return (this.motionless() ? this.beats().filter(beat => beat.chapter === line.chapter) : [line]).map(beat => ({...beat,parts:captionParts(beat.text)}));
  });
  /** The last term pointed at, and whether its definition shows: the box keeps its text while it fades out. */
  protected readonly hint = signal<Hint | null>(null);
  protected readonly hintOpen = signal(false);
  /** The best pipeline's steps, drawn down the stage's left side: its ranking steps, then its context. */
  protected readonly steps = computed(() => {
    const example = this.example();
    if (!example) return [];
    return [...example.steps.map(step => ({layer:step.layer,variant:variantName(step.value),...STEP_NAMES[step.layer]})),
      ...(example.best.layers['context'] ? [{layer:'context',variant:variantName(example.best.layers['context']),...STEP_NAMES['context']}] : [])];
  });
  protected readonly missed = computed(() => { const example = this.example(); return example ? example.needed.filter(id => !example.prompt.includes(id)).slice(0,4) : []; });
  /** The first 10 of the last ranking, needed or not: what the scores chapter reads. */
  protected readonly lastTop = computed(() => { const example = this.example(); return example ? example.steps.at(-1)!.known.slice(0,10).map(id => !!id && example.needed.includes(id)) : []; });
  protected readonly firstHit = computed(() => this.lastTop().indexOf(true));
  protected readonly metricCards = computed(() => {
    const example = this.example();
    if (!example) return [];
    const scores = example.scores, top = this.lastTop().filter(Boolean).length, cards = [
      {name:'Recall@10',value:Math.round((scores.recallAt10 ?? 0)*100) + '%',rule:top + ' of ' + example.needed.length + ' needed in the first 10'},
      {name:'nDCG@10',value:(scores.ndcgAt10 ?? 0).toFixed(2),rule:'Higher ranks count more'},
    ];
    if (scores.reciprocalRank) cards.push({name:'MRR',value:scores.reciprocalRank.toFixed(2),rule:'1 ÷ rank of the first needed'});
    if (scores.seconds !== null) cards.push({name:'Latency',value:Math.round(scores.seconds*1000) + ' ms',rule:'To rank one question'});
    return cards;
  });
  protected readonly denseName = computed(() => variantName(this.example()?.dense.value ?? null));
  protected readonly weights = WEIGHTS.map(weight => weight.toFixed(2).replace(/^0/,''));
  protected readonly motionless = computed(() => still(this.settings));

  private timeline?: gsap.core.Timeline;
  private playhead?: gsap.core.Tween;
  private motion?: gsap.Context;
  private resize?: ResizeObserver;

  constructor() {
    afterRenderEffect(() => {
      const screen = this.screen()?.nativeElement;
      this.resize?.disconnect();
      if (!screen || typeof ResizeObserver === 'undefined') return;
      this.resize = new ResizeObserver(entries => {
        const {width,height} = entries[0].contentRect;
        if (!width) return;
        const layout = stageLayout(width < NARROW), scale = height ? Math.min(width/layout.width,height/layout.height) : width/layout.width;
        this.tall.set(layout.tall);
        this.fit.set({scale,x:(width - layout.width*scale)/2,y:height ? (height - layout.height*scale)/2 : 0});
      });
      this.resize.observe(screen);
    });
    afterRenderEffect(() => {
      this.layout(); this.chunks(); this.lines();
      untracked(() => this.build());
    });
    effect(() => { if (this.last()) untracked(() => this.ended.emit()); });
    effect(() => { this.line(); untracked(() => this.hintOpen.set(false)); });
  }
  ngOnDestroy(): void { this.resize?.disconnect(); this.playhead?.kill(); this.motion?.revert(); }

  protected title(chapter: ChapterId): string { const example = this.example(); return example ? chapterTitle(chapter,example) : ''; }
  protected at(box: Box, height?: number): Record<string,string> { return {left:box.x + 'px',top:box.y + 'px',width:box.w + 'px',height:(height ?? box.h) + 'px'}; }
  protected sideSlot(side: number, index: number, count: number): Box { return this.layout().slot(index,count,this.sideArea(side)); }
  protected rule(id: string): string { return ruleSection(id)?.name ?? ''; }
  protected term(id: string): string { return id.replace(/^vocabulary\./,'').replaceAll('-',' '); }
  protected faction(id: string): string | null { return /^(ember|tide|root|neutral)\./.exec(id)?.[1] ?? null; }
  protected percent(held: number, of: number): string { return of ? Math.round(held/of*100) + '%' : '—'; }
  protected format(value: number | null): string { return this.catalog().format(DECISION_METRIC,value); }
  protected rowAt(rank: number): Box { return this.layout().rank(rank,10); }

  /** Plays the next chapter; on the last one, nothing. */
  protected next(): void { if (!this.last()) this.openChapter(this.chapterIndex() + 1); }
  /** Shows the chapter before as it ends: going back replays nothing. */
  protected previous(): void { if (this.chapterIndex() > 0) this.settle(this.chapterIndex() - 1); }
  protected openChapter(index: number): void { if (index > 0) this.advanced.set(true); this.play(index); }
  protected replay(): void { this.play(0); }
  protected close(): void { this.playhead?.kill(); this.closed.emit(); }
  protected explore(): void { this.playhead?.kill(); this.explored.emit(); }
  protected key(event: KeyboardEvent): void {
    const target = event.target as HTMLElement;
    if (target.closest('input,select,textarea')) return;
    if ((event.key === ' ' || event.key === 'Enter') && target.closest('button')) return;
    if (event.key === 'ArrowRight' || event.key === ' ') { event.preventDefault(); this.next(); }
    else if (event.key === 'ArrowLeft') { event.preventDefault(); this.previous(); }
    else if (event.key === 'Escape') { if (this.hintOpen()) this.hintOpen.set(false); else this.close(); }
  }

  /**
   * Shows a term's definition above it, inside the player. A mouse shows it on hover; a finger, as it lifts, since a touch
   * enters and leaves at once and iOS may turn a tap into a hover that sends no click.
   */
  protected define(term: FilmTerm, event: Event): void {
    const pointer = (event as PointerEvent).pointerType;
    if ((event.type === 'pointerenter' && pointer !== 'mouse') || (event.type === 'pointerup' && pointer === 'mouse')) return;
    const player = this.player()?.nativeElement, box = this.definition()?.nativeElement, word = (event.currentTarget as HTMLElement).getClientRects()[0];
    if (!player || !box || !word) return;
    const frame = player.getBoundingClientRect(), width = box.offsetWidth, middle = word.left + word.width/2 - frame.left;
    const left = Math.max(12,Math.min(frame.width - width - 12,middle - width/2));
    this.hint.set({term,left,bottom:frame.bottom - word.top + 8,arrow:Math.max(16,Math.min(width - 16,middle - left))});
    this.hintOpen.set(true);
  }
  protected leave(event: PointerEvent): void { if (event.pointerType === 'mouse') this.hintOpen.set(false); }
  /** Focus leaving a term hides its definition, but not the one of the term tapped since. */
  protected blurred(term: FilmTerm): void { if (this.hint()?.term === term) this.hintOpen.set(false); }
  /** A press anywhere but on a term hides its definition. */
  protected away(event: PointerEvent): void { if (!(event.target as HTMLElement).closest('.term')) this.hintOpen.set(false); }

  /** Plays a chapter from its first frame to the end of its last motion; without motion, shows its last frame. */
  private play(index: number): void {
    const timeline = this.timeline, chapter = this.chapters()[index];
    if (!timeline || !chapter) return;
    this.playhead?.kill();
    if (this.motionless()) { this.seek(chapter.done); return; }
    this.seek(chapter.start);
    this.playhead = timeline.tweenTo(chapter.done,{onUpdate:() => this.sync(),onComplete:() => this.sync()});
  }
  private settle(index: number): void { const chapter = this.chapters()[index]; if (!chapter) return; this.playhead?.kill(); this.seek(chapter.done); }
  private seek(time: number): void {
    const timeline = this.timeline;
    if (!timeline) return;
    timeline.pause();
    timeline.seek(Math.max(0,Math.min(timeline.duration(),time)));
    this.sync();
  }

  /** The caption, and each chapter's bar, follow the timeline's time. */
  private sync(): void {
    const timeline = this.timeline;
    if (!timeline) return;
    const time = timeline.time(), beats = this.beats();
    let index = 0;
    for (let at = 0; at < beats.length; at++) if (beats[at].at <= time + 1e-4) index = at;
    if (index !== this.current()) this.current.set(index);
    const fills = this.host.nativeElement.querySelectorAll<HTMLElement>('[data-fill]');
    this.chapters().forEach((chapter,at) => {
      const progress = at < this.chapterIndex() ? 1 : at > this.chapterIndex() ? 0 : Math.max(0,Math.min(1,(time - chapter.start)/Math.max(.001,chapter.done - chapter.start)));
      if (fills[at]) fills[at].style.transform = 'scaleX(' + progress + ')';
    });
  }

  private sideArea(side: number): Box { const box = this.layout().sides[side]; return {x:box.x + 14,y:box.y + 34,w:box.w - 28,h:box.h}; }

  /** Builds the timeline for the stage as drawn, then opens it at the chapter shown, or plays the first. */
  private build(): void {
    const example = this.example(), stage = this.stage()?.nativeElement, chunks = this.chunks(), lines = this.lines();
    const resume = this.timeline ? this.chapterIndex() : null;
    this.playhead?.kill(); this.motion?.revert(); this.timeline = undefined;
    if (!example || !stage || !lines.length) { this.beats.set([]); return; }
    const layout = this.layout(), orders = this.orders(), cells = scatter(chunks.length);
    const indexOf = new Map(chunks.map((chunk,index) => [chunk.key,index]));
    const beats: Beat[] = [];
    this.motion = gsap.context(() => {
      const slips = gsap.utils.toArray<HTMLElement>('[data-slip]',stage);
      const labels = slips.map(slip => slip.querySelector<HTMLElement>('[data-label]'));
      const pick = (name: string) => gsap.utils.toArray<HTMLElement>('[data-f="' + name + '"]',stage);
      const timeline = gsap.timeline({paused:true});
      const states: SlipState[] = chunks.map((_,index) => ({...layout.archive(cells[index]),o:0,label:0}));
      const needed = chunks.flatMap((chunk,index) => chunk.needed ? [index] : []);
      const prompt = example.prompt.map(id => indexOf.get(id)!).filter(index => index !== undefined);
      const tray: string[] = [];
      let line = 0;

      const initial = [...states];
      timeline.set(slips,{x:index => initial[index].x,y:index => initial[index].y,width:index => initial[index].w,height:index => initial[index].h,opacity:0,scale:.4},0);
      timeline.set(labels.filter(label => label !== null),{opacity:0},0);
      timeline.set(gsap.utils.toArray<HTMLElement>('[data-f]',stage),{autoAlpha:0},0);
      timeline.set(stage,{'--reveal':0},0);

      const beat = (bench: string, view: View, animate: (at: number) => void) => {
        const text = lines[line++], at = timeline.duration();
        if (!text) return;
        animate(at);
        const done = Math.max(at,timeline.duration());
        timeline.to({},{duration:readTime(text.text)},at);
        beats.push({...text,at,done,end:0,bench,view});
      };
      /** Moves each slip given a state to it; a label shows once its slip has arrived, and leaves as it sets off. */
      const move = (next: (SlipState | null)[], at: number, duration: number, delay: (index: number) => number = () => 0, ease = MOVE) => {
        const moving = next.flatMap((state,index) => state ? [index] : []);
        if (!moving.length) return;
        timeline.to(moving.map(index => slips[index]),{x:k => next[moving[k]]!.x,y:k => next[moving[k]]!.y,width:k => next[moving[k]]!.w,height:k => next[moving[k]]!.h,
          opacity:k => next[moving[k]]!.o,scale:1,duration,ease,stagger:k => delay(moving[k])},at);
        const relabel = moving.filter(index => labels[index] && next[index]!.label !== states[index].label);
        if (relabel.length) timeline.to(relabel.map(index => labels[index]!),{opacity:k => next[relabel[k]]!.label,duration:.3,
          stagger:k => delay(relabel[k]) + (next[relabel[k]]!.label ? duration*.65 : 0)},at);
        moving.forEach(index => states[index] = next[index]!);
      };
      const show = (name: string, at: number, from: gsap.TweenVars = {y:10}, stagger = 0) => {
        const targets = pick(name);
        if (targets.length) timeline.fromTo(targets,{autoAlpha:0,...from},{autoAlpha:1,x:0,y:0,scale:1,filter:'blur(0px)',duration:.6,ease:ARRIVE,stagger},at);
      };
      const hide = (names: string[], at: number) => {
        const targets = names.flatMap(pick);
        if (targets.length) timeline.to(targets,{autoAlpha:0,duration:.35,ease:'power1.out'},at);
      };
      const pulse = (indices: number[], at: number, size = 1.12) => {
        if (indices.length) timeline.to(indices.map(index => slips[index]),{scale:size,duration:.22,ease:'power2.out',yoyo:true,repeat:1,stagger:.07},at);
      };
      const activate = (step: number, at: number) => {
        const items = pick('step');
        if (!items.length) return;
        timeline.to(items,{autoAlpha:k => k === step ? 1 : .4,duration:.4},at);
        timeline.to(items.map(item => item.querySelector('[data-glow]')),{opacity:k => k === step ? 1 : 0,duration:.4},at);
      };
      /** A step's ranking: rows, tiles, and the needed chunks it cut in the tray; the others it drops fall away. */
      const ranked = (order: string[], cut: string[]) => {
        const rank = new Map(order.map((key,at) => [indexOf.get(key)!,at + 1]));
        for (const id of cut) if (!tray.includes(id)) tray.push(id);
        return chunks.map((chunk,index): SlipState => {
          const place = rank.get(index), waiting = tray.indexOf(chunk.key);
          if (waiting >= 0) return {...layout.tray(waiting),o:1,label:1};
          if (place === undefined) return {...states[index],y:states[index].y + 14,o:0,label:0};
          return {...layout.rank(place,order.length),o:1,label:place <= 10 ? 1 : 0};
        });
      };

      const intro: View = {kind:'intro',step:-1}, after: View = {kind:'after',step:-1};
      // One beat per chapter, as one caption tells it: within it, things show in the order the caption names them.
      beat(example.corpus + ' chunks',intro,at => {
        show('question',at + .2,{y:16,filter:'blur(6px)'});
        show('bench-label',at + 1.4);
        const center = layout.archive(Math.floor(chunks.length/2));
        const far = Math.max(...chunks.map((_,index) => Math.hypot(states[index].x - center.x,states[index].y - center.y)));
        move(chunks.map((_,index) => ({...states[index],o:1})),at + 1.6,.7,index => Math.hypot(states[index].x - center.x,states[index].y - center.y)/far*.9,ARRIVE);
      });
      beat(example.corpus + ' chunks · ' + needed.length + ' needed',intro,at => {
        timeline.to(stage,{'--reveal':1,duration:.8,ease:'power2.out'},at + .3);
        timeline.to(slips.filter((_,index) => !chunks[index].needed),{opacity:.32,duration:.6},at + .3);
        pulse(needed,at + 2.7,1.9);
        show('prompt',at + 5.5,{x:24});
      });

      example.steps.forEach((step,index) => {
        const order = orders[index], total = order.length, view: View = {kind:'step',step:index};
        const label = index === 0 ? 'Ranked · ' + total + ' chunks' : step.layer === 'expand' ? total + ' candidates' : 'Reranked · ' + total + ' candidates';
        /** Once the ranking is in place, the needed chunks the caption counts pulse: brought back by expansion, else in the top 10. */
        const counted = step.layer === 'expand' ? broughtBack(example,index).map(id => indexOf.get(id)!)
          : needed.filter(slip => { const place = order.indexOf(chunks[slip].key); return place >= 0 && place < 10; });
        beat(label,view,at => {
          if (index === 0) { show('step',at,{x:-12},.08); show('tally',at + 1.2,{y:4}); }
          activate(index,at + (index === 0 ? .5 : 0));
          const hadTray = tray.length;
          move(ranked(order,step.cut),at + .4,1.2,slip => Math.min(order.indexOf(chunks[slip].key) + 1 || 200,200)*.004);
          if (!hadTray && tray.length) show('tray',at + 1.4,{x:-6});
          if (index === 0) { show('cut',at + 1.3,{y:0}); show('ranks',at + 1,{x:-6},.03); }
          pulse(counted,at + 2.6,1.08);
        });
      });

      const context = example.steps.length, filling: View = {kind:'context',step:context - 1};
      beat('Left out of the prompt',filling,at => {
        activate(context,at);
        timeline.to(pick('cut'),{autoAlpha:.25,duration:.4},at);
        hide(['ranks'],at);
        move(chunks.map((_,index) => { const slot = prompt.indexOf(index); return slot >= 0 ? {...layout.slot(slot,prompt.length),o:1,label:1} : states[index].o ? {...states[index],o:.2} : null; }),
          at + .3,1.1,index => Math.max(0,prompt.indexOf(index))*.06);
        pulse(prompt.filter(index => chunks[index].needed),at + 1.6 + prompt.length*.06,1.07);
      });
      beat('Left out of the prompt',after,at => {
        timeline.to(slips.filter((_,index) => !prompt.includes(index)),{opacity:0,duration:.5},at);
        hide(['cut','bench-label','tally','tray'],at);
        show('result',at + .3,{y:12});
        show('missed',at + 1,{y:8},.1);
        show('average',at + 2.4);
      });

      beat('',after,at => {
        hide(['result','missed','average','prompt','step'],at);
        const area = this.sideArea(0);
        move(chunks.map((_,index) => { const slot = prompt.indexOf(index); return slot >= 0 ? {...layout.slot(slot,prompt.length,area),o:1,label:1} : null; }),at + .3,1);
        show('side',at + .2,{y:14},.15);
        show('dense-slip',at + 1,{x:12},.06);
        show('side-foot',at + 2.4,{y:6},.2);
        show('side-score',at + 4,{y:6},.2);
      });

      const top = orders.at(-1)!.slice(0,10).map(key => indexOf.get(key)!), topLabel = STEP_NAMES[example.steps.at(-1)!.layer].name + '’s top 10';
      beat(topLabel,after,at => {
        hide(['side','side-foot','side-score','dense-slip'],at);
        move(chunks.map((_,index) => { const place = top.indexOf(index); return place >= 0 ? {...layout.rank(place + 1,10),o:1,label:1} : states[index].o ? {...states[index],o:0} : null; }),at + .3,1);
        show('bench-label',at + .8);
        show('ranks',at + .9,{x:-6},.03);
        show('metric-0',at + 1.1,{x:16});
        let next = at + 3;
        show('weight',next,{x:6},.05); show('metric-1',next + .3,{x:16});
        if (example.scores.reciprocalRank) { next += 2.2; hide(['weight'],next); show('first',next + .2,{scale:1.06}); show('metric-2',next + .4,{x:16}); }
        if (example.scores.seconds !== null) { next += 2.2; hide(['first'],next); show('metric-' + (example.scores.reciprocalRank ? 3 : 2),next + .2,{x:16}); }
      });

      beat('',after,at => {
        timeline.to(slips,{opacity:0,duration:.4},at);
        hide(['question','bench-label','ranks','weight','first',...this.metricCards().map((_,index) => 'metric-' + index)],at);
        show('outro',at + .4,{scale:.96,filter:'blur(8px)'});
      });

      beats.forEach((beat,index) => beat.end = beats[index+1]?.at ?? timeline.duration());
      this.timeline = timeline;
    },stage);
    this.beats.set(beats);
    if (resume === null || resume < 0) this.play(0); else this.settle(Math.min(resume,this.chapters().length - 1));
  }
}
