import { TestBed } from '@angular/core/testing';
import { Settings } from '../core/settings';
import { arrivalOf, clampPage, pageOf, pageRange, windowOf } from './pages';
import { RollingNumber, countText, countable } from './rolling-number';
import { SPRINGS, spring } from './springs';

describe('Springs', () => {
  it('start at rest, settle on their target, and bounce past it only when damped below 1', () => {
    for (const curve of Object.values(SPRINGS)) expect([curve.ease(0),curve.ease(1)]).toEqual([0,1]);
    const samples = (curve: typeof SPRINGS.bouncy) => Array.from({length:101},(_,index) => curve.ease(index/100));
    expect(Math.max(...samples(SPRINGS.bouncy))).toBeGreaterThan(1.03);
    expect(Math.max(...samples(SPRINGS.gentle))).toBeLessThanOrEqual(1);
  });
  it('write the same curve as a CSS easing, and last longer when they swing slower', () => {
    expect(SPRINGS.snappy.css).toMatch(/^linear\(0, .*, 1\)$/);
    expect(spring(.8,.7).duration).toBeGreaterThan(spring(.4,.7).duration);
  });
});

describe('Pages of a ranking', () => {
  const keys = Array.from({length:23},(_,index) => 'p' + (index+1));
  it('show ten at a time and keep a page inside the list', () => {
    expect(pageOf(keys,1)).toEqual(keys.slice(10,20));
    expect(pageOf(keys,7)).toEqual(keys.slice(20));
    expect([clampPage(-1,23),clampPage(9,23),clampPage(3,0)]).toEqual([0,2,0]);
    expect([pageRange(0,23),pageRange(2,23),pageRange(0,0)]).toEqual(['1–10','21–23','0']);
  });
  it('bring the first rows in turn, and a turned page from the side it was asked from', () => {
    const first = windowOf(keys,0), second = windowOf(keys,1);
    expect(arrivalOf('p3',first,null,false)).toEqual({kind:'intro',index:2});
    expect(arrivalOf('p12',second,first,false)).toEqual({kind:'page',side:1,index:1});
    expect(arrivalOf('p2',second,first,true)).toEqual({kind:'page',side:-1,index:1});
    expect(arrivalOf('p2',first,second,false)).toEqual({kind:'page',side:-1,index:1});
  });
  it('replace the 11th by the 10th through the top edge when they swap, and move a dropped row out through the bottom', () => {
    const before = windowOf(keys,1);
    const swapped = [...keys]; [swapped[9],swapped[10]] = [swapped[10],swapped[9]];
    const after = windowOf(swapped,1);
    expect(arrivalOf('p10',after,before,false)).toEqual({kind:'rank',side:-1,index:0});
    expect(arrivalOf('p11',after,before,true)).toEqual({kind:'rank',side:-1,index:0});
    const dropped = windowOf([...keys.slice(0,15),...keys.slice(16),'p16'],1);
    expect(arrivalOf('p16',dropped,before,true)).toEqual({kind:'rank',side:1,index:5});
    expect(arrivalOf('p21',dropped,before,false)).toEqual({kind:'rank',side:1,index:9});
  });
});

describe('Counting numbers', () => {
  it('count a text that writes one number in one unit, and roll any other', () => {
    expect(countable('71.1%')).toEqual({prefix:'',number:71.1,decimals:1,width:0,suffix:'%'});
    expect(countable('114 ms')).toEqual({prefix:'',number:114,decimals:0,width:0,suffix:' ms'});
    expect(countable('07')?.width).toBe(2);
    expect(['—','11–20','1,234',''].map(countable)).toEqual([null,null,null,null]);
  });
  it('write each step of a count as its target is written', () => {
    const target = countable('56.3%')!;
    expect([countText(target,71.06),countText(target,63.649),countText(target,56.3)]).toEqual(['71.1%','63.6%','56.3%']);
    expect(countText(countable('01')!,41.6)).toBe('42');
    expect(countText(countable('05')!,4.6)).toBe('05');
  });
});

describe('Rolling number', () => {
  beforeEach(() => { TestBed.configureTestingModule({imports:[RollingNumber]}); TestBed.inject(Settings).update({motion:'on'}); });
  it('reads its value whole, and renews only the glyphs that change, counted from the right', async () => {
    const fixture = TestBed.createComponent(RollingNumber);
    fixture.componentRef.setInput('text','9.5%'); fixture.componentRef.setInput('value',.095);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const glyphs = () => [...host.querySelectorAll('.glyph')];
    const percent = glyphs().at(-1);
    expect(host.querySelector('.sr-only')?.textContent).toBe('9.5%');
    expect(glyphs().map(glyph => glyph.textContent).join('')).toBe('9.5%');

    fixture.componentRef.setInput('text','10.5%'); fixture.componentRef.setInput('value',.105);
    await fixture.whenStable();

    expect(glyphs().map(glyph => glyph.textContent).join('')).toBe('10.5%');
    expect(glyphs().at(-1)).toBe(percent);
    expect(host.classList).toContain('better');
  });
});
