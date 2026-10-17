export interface ArtPalette { light: string; mid: string; dark: string; ink: string; }
export function hashCard(id: string): number {
  let hash = 2166136261;
  for (const char of id) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619);
  return hash >>> 0;
}

/** The same SVG is used by the card and as the canvas shatter texture. */
export function drawPlaceholder(id: string, palette: ArtPalette): string {
  let state = hashCard(id);
  const random = () => { state = (Math.imul(state, 1664525) + 1013904223) >>> 0; return state / 4294967296; };
  const shards: string[] = [];
  const count = 3 + Math.floor(random() * 4);
  for (let i = 0; i < count; i++) {
    const x = Math.round(30 + random() * 720), y = Math.round(130 + random() * 350);
    const width = Math.round(25 + random() * 110), height = Math.round(70 + random() * 220);
    shards.push(`<g opacity="${.35 + random() * .6}" transform="translate(${x} ${y}) rotate(${Math.round(random() * 40 - 20)})"><path d="M0 0 ${width} ${-height} ${width * 1.5} ${-height * .25} ${width * .7} 50Z" fill="url(#crystal)"/><path d="M${width} ${-height} ${width * .7} 50 0 0Z" fill="${palette.dark}" opacity=".55"/><path d="M0 0 ${width} ${-height} ${width * 1.5} ${-height * .25}" stroke="${palette.light}" fill="none" stroke-width="2"/></g>`);
  }
  const seed = hashCard(id) % 999;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600"><defs><linearGradient id="sky" x2=".3" y2="1"><stop stop-color="${palette.dark}"/><stop offset="1" stop-color="${palette.ink}"/></linearGradient><linearGradient id="crystal" x2="1" y2="1"><stop stop-color="${palette.light}"/><stop offset=".45" stop-color="${palette.mid}"/><stop offset="1" stop-color="${palette.dark}"/></linearGradient><radialGradient id="glow"><stop stop-color="${palette.mid}" stop-opacity=".4"/><stop offset="1" stop-color="${palette.mid}" stop-opacity="0"/></radialGradient><radialGradient id="vignette"><stop offset=".35" stop-opacity="0"/><stop offset="1" stop-color="${palette.ink}" stop-opacity=".75"/></radialGradient><filter id="grain"><feTurbulence type="fractalNoise" baseFrequency=".7" numOctaves="2" seed="${seed}" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/></filter></defs><path fill="url(#sky)" d="M0 0h800v600H0z"/><ellipse cx="400" cy="230" rx="370" ry="300" fill="url(#glow)"/><path d="M380 0 110 600H270L430 0Z M490 0 600 600h70L510 0Z" fill="${palette.light}" opacity=".06"/><path d="M0 465 140 390 250 460 400 390 520 480 700 410 800 450V600H0Z" fill="${palette.dark}" opacity=".45"/>${shards.join('')}<g transform="translate(400 250)" fill="none" stroke="${palette.light}" stroke-width="2" opacity=".7"><path d="M0-105 66-25 45 75 0 120-45 75-66-25Z"/><path d="M0-105v225 M-66-25 45 75 M66-25-45 75"/></g><path fill="url(#vignette)" d="M0 0h800v600H0z"/><path filter="url(#grain)" opacity=".055" d="M0 0h800v600H0z"/></svg>`;
}
