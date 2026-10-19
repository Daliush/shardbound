import { AdditiveBlending, ShaderMaterial } from 'three';

const vertexShader = `
  varying vec2 vUv;
  varying float vFade;
  varying float vHeat;
  void main() {
    vUv = uv;
    vFade = instanceColor.r;
    vHeat = instanceColor.g;
    gl_Position = projectionMatrix * modelViewMatrix * instanceMatrix * vec4(position, 1.0);
  }
`;

/** Soft light on a shared plane: no opaque blobs or hard particle silhouettes. */
export function elementalMaterial(kind: 'trail' | 'current'): ShaderMaterial {
  return new ShaderMaterial({
    transparent: true, depthWrite: false, blending: AdditiveBlending,
    uniforms: { time: { value: 0 } }, vertexShader,
    fragmentShader: kind === 'trail' ? `
      uniform float time;
      varying vec2 vUv;
      varying float vFade;
      varying float vHeat;
      void main() {
        float r = length(vUv - .5) * 2.0;
        float glow = exp(-r * r * 3.5) * (1.0 - smoothstep(.7, 1.0, r));
        float core = exp(-r * r * 9.0) * vHeat;
        float flicker = .85 + .15 * sin(time * 23.0 + vHeat * 40.0);
        vec3 ember = mix(vec3(.72, .05, .02), vec3(1.0, .4, .07), vHeat);
        vec3 color = mix(ember, vec3(1.0, .82, .5), core);
        gl_FragColor = vec4(color, (glow * .2 + core * .2) * flicker * vFade);
      }
    ` : `
      uniform float time;
      varying vec2 vUv;
      varying float vFade;
      void main() {
        vec2 p = (vUv - .5) * 2.0;
        float angle = atan(p.y, p.x);
        float radius = length(p * vec2(1.0, 1.16));
        float wave = sin(angle * 3.0 + time * 1.1) * .025 + sin(angle * 5.0 - time) * .012;
        float crest = exp(-pow((radius - .66 - wave) * 52.0, 2.0));
        float echo = exp(-pow((radius - .78 - wave * .6) * 70.0, 2.0));
        float light = pow(.5 + .5 * sin(angle * 2.0 - time * .65), 3.0);
        float reflection = exp(-pow((radius - .63 - wave) * 14.0, 2.0)) * .055;
        float alpha = ((crest * .35 + echo * .12) * (.12 + light * .88) + reflection) * vFade;
        gl_FragColor = vec4(mix(vec3(.19, .65, .78), vec3(.75, .96, 1.0), light), alpha);
      }
    `,
  });
}
