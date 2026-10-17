import { Directive, ElementRef, Injectable, OnDestroy, effect, inject, input } from '@angular/core';

@Injectable()
export class Anchors {
  readonly elements = new Map<string, HTMLElement>();
  find(key: string | null): HTMLElement | undefined { return key ? this.elements.get(key) : undefined; }
  rect(key: string | null): DOMRect | undefined { return this.find(key)?.getBoundingClientRect(); }
}
@Directive({ selector: '[appAnchor]' })
export class AnchorDirective implements OnDestroy {
  readonly appAnchor = input.required<string>();
  private readonly registry = inject(Anchors);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private key = '';
  constructor() {
    effect(() => {
      this.remove();
      this.key = this.appAnchor();
      this.registry.elements.set(this.key, this.element);
    });
  }
  private remove(): void { if (this.registry.elements.get(this.key) === this.element) this.registry.elements.delete(this.key); }
  ngOnDestroy(): void { this.remove(); }
}
