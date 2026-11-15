import { Component, computed, input } from '@angular/core';
import { Layers } from '../api/evaluation';
import { variantName } from './presentation';
import { STEP_NAMES } from './steps';

const ORDER = ['retriever','expand','rerank','context'];

/** A pipeline written as its steps in order, each named above the variant it runs; a step it skips stays dim. */
@Component({
  selector:'app-step-chain',
  template:`<ol class="chain" [attr.aria-label]="summary()">
    @for (step of steps(); track step.layer) { <li [class.off]="!step.on"><small>{{ step.name }}</small><b>{{ step.variant }}</b></li> }
  </ol>`,
  styleUrl:'./step-chain.css',
})
export class StepChain {
  readonly layers = input<Layers>({});
  protected readonly steps = computed(() => ORDER.filter(layer => layer in this.layers()).map(layer => {
    const value = this.layers()[layer] ?? null;
    return {layer,on:value !== null,variant:value === null ? 'Skipped' : variantName(value),...STEP_NAMES[layer]};
  }));
  protected readonly summary = computed(() => this.steps().map(step => step.name + ': ' + step.variant).join(', '));
}
