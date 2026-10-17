import { AdditiveBlending, ShaderMaterial } from 'three';

const vertexShader = `
  varying vec2 vUv;
  varying float vFade;
  void main() {
    vUv = uv;
    vFade = instanceColor.r;
    gl_Position = projectionMatrix * modelViewMatrix * instanceMatrix * vec4(position, 1.0);
  }
`;

/** Soft light on a shared plane: no opaque blobs or hard particle silhouettes. */
export function elementalMaterial(kind: 'heat' | 'current'): ShaderMaterial {
  return new ShaderMaterial({
    transparent: true, depthWrite: false, blending: AdditiveBlending,
    uniforms: { time: { value: 0 } }, vertexShader,
    fragmentShader: kind === 'heat' ? `
      uniform float time;
      varying vec2 vUv;
      varying float vFade;
      void main() {
        vec2 p = vUv;
        float height = smoothstep(0.0, .15, p.y) * (1.0 - smoothstep(.35, 1.0, p.y));
        float sway = sin(p.y * 6.0 - time * 1.7) * .09 * p.y;
        float center = p.x - .5 - sway;
        float warmth = exp(-center * center * 24.0) * height;
        float ribbon = exp(-pow((center - sin(p.y * 9.0 - time * 2.0) * .13) * 45.0, 2.0));
        float edge = exp(-pow((center + .18 - p.y * .19) * 55.0, 2.0));
        float alpha = (warmth * .085 + (ribbon + edge * .5) * height * .24) * vFade;
        vec3 color = mix(vec3(1.0, .25, .045), vec3(1.0, .73, .35), ribbon);
        gl_FragColor = vec4(color, alpha);
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
