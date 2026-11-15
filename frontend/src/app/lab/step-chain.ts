import { Component, computed, input } from '@angular/core';
import { Layers } from '../api/evaluation';
import { Icon } from '../core/icon';
import { variantName } from './presentation';
import { STEP_NAMES } from './steps';

const ORDER = ['retriever','expand','rerank','context'];

/**
 * A pipeline drawn as its steps in order, the film's icons joined by a line: a step it skips stays dim. Compact, it is
 * four dots a row of the leaderboard reads at a glance; full, each step says which variant it runs.
 */
@Component({
  selector:'app-step-chain', imports:[Icon],
  template:`<ol class="chain" [class.compact]="compact()" [attr.aria-label]="summary()">
    @for (step of steps(); track step.layer) {
      <li [class.off]="!step.on" [title]="compact() ? step.name + ' · ' + step.variant : ''">
        <span class="node"><app-icon [name]="step.icon" /></span>
        @if (!compact()) { <span class="text"><small>{{ step.name }}</small><b>{{ step.variant }}</b></span> }
      </li>
    }
  </ol>`,
  styleUrl:'./step-chain.css',
})
export class StepChain {
  readonly layers = input<Layers>({});
  readonly compact = input(false);
  protected readonly steps = computed(() => ORDER.filter(layer => layer in this.layers()).map(layer => {
    const value = this.layers()[layer] ?? null;
    return {layer,on:value !== null,variant:value === null ? 'Skipped' : variantName(value),...STEP_NAMES[layer]};
  }));
  protected readonly summary = computed(() => this.steps().map(step => step.name + ': ' + step.variant).join(', '));
}
