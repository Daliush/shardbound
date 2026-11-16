import { Component, computed, input } from '@angular/core';
import { Layers } from '../api/evaluation';
import { Icon } from '../core/icon';
import { variantName } from './presentation';
import { STEP_NAMES } from './steps';

const ORDER = ['retriever','expand','rerank','context'];

/**
 * A pipeline drawn as the Build tab draws it: its steps down a wire, each a node with the step's icon, its name above the
 * variant it runs. A step it skips stays on the wire, dim.
 */
@Component({
  selector:'app-step-chain',
  imports:[Icon],
  template:`<ol class="chain" [attr.aria-label]="summary()">
    @for (step of steps(); track step.layer) { <li [class.off]="!step.on"><span class="node"><app-icon [name]="step.icon" /></span><span class="text"><small>{{ step.name }}</small><b>{{ step.variant }}</b></span></li> }
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
