import { Component, computed, input, model } from '@angular/core';
import { Icon } from '../core/icon';
import { PAGE_SIZE, clampPage, pageCount, pageRange } from './pages';
import { RollingNumber } from './rolling-number';

/** "‹ 11–20 of 59 ›": the page of a ranking shown, ten at a time; nothing when the ranking fits on one page. */
@Component({
  selector:'app-pager', imports:[Icon,RollingNumber],
  host:{'[hidden]':'total() <= size()'},
  template:`
    <button class="quiet step back" [disabled]="current() === 0" (click)="turn(-1)" [attr.aria-label]="'Previous ' + size()"><app-icon name="chevron" /></button>
    <span class="range" aria-live="polite"><app-rolling-number [text]="range()" [value]="current()" [better]="null" /><small>of {{ total() }}</small></span>
    <button class="quiet step" [disabled]="current() === last()" (click)="turn(1)" [attr.aria-label]="'Next ' + size()"><app-icon name="chevron" /></button>
  `,
  styles:`
    :host { display: inline-flex; align-items: center; gap: .2rem; }
    :host([hidden]) { display: none; }
    .range { display: inline-flex; align-items: baseline; gap: .35rem; min-width: 5.6rem; justify-content: center; font-size: .78rem; font-variant-numeric: tabular-nums; }
    .range small { font-size: .7rem; }
    .step { min-height: 34px; min-width: 34px; padding: .3rem; border-radius: 999px; color: var(--text); transition: background 160ms ease, scale var(--spring-bouncy-duration, .4s) var(--spring-bouncy, ease); }
    .step:hover:not(:disabled) { background: var(--lab-subtle); border-color: transparent; }
    .step:active:not(:disabled) { scale: .88; }
    .step app-icon { width: 16px; height: 16px; }
    .back app-icon { rotate: 180deg; }
  `,
})
export class Pager {
  readonly total = input(0);
  readonly size = input(PAGE_SIZE);
  readonly page = model(0);
  protected readonly last = computed(() => pageCount(this.total(),this.size()) - 1);
  protected readonly current = computed(() => clampPage(this.page(),this.total(),this.size()));
  protected readonly range = computed(() => pageRange(this.current(),this.total(),this.size()));
  protected turn(step: number): void { this.page.set(clampPage(this.current()+step,this.total(),this.size())); }
}
