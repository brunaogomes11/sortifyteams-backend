/* RACHA — landing: bola do hero gira com o scroll e troca de esporte
   em sincronia com a palavra do título (crossfade ease-in-out). */
(function () {
  'use strict';

  var CYCLE_MS = 2600;
  var GLOW_COLORS = ['#EEF2E4', '#4FB8FF', '#35E0D0', '#FF8A3D'];

  var rotor = document.getElementById('ball-rotor');
  var glow = document.getElementById('hero-glow');
  var layers = document.querySelectorAll('.ball-layer');
  var words = document.querySelectorAll('.hero-word');
  if (!rotor || !glow || layers.length !== 4 || words.length !== 4) return;

  var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var phase = 0;

  function applyPhase() {
    for (var i = 0; i < 4; i++) {
      layers[i].classList.toggle('is-active', i === phase);
      words[i].classList.remove('is-active', 'is-leaving');
      if (i === phase) {
        words[i].classList.add('is-active');
      } else if (i === (phase + 3) % 4) {
        // palavra que acabou de sair sobe; as demais esperam embaixo
        words[i].classList.add('is-leaving');
      }
    }
    glow.style.background =
      'radial-gradient(circle, color-mix(in oklab, ' + GLOW_COLORS[phase] + ' 14%, transparent) 0%, transparent 62%)';
  }

  applyPhase();
  setInterval(function () {
    phase = (phase + 1) % 4;
    applyPhase();
  }, CYCLE_MS);

  // Rotação contínua (12°/s) somada à rotação guiada pelo scroll.
  if (!reducedMotion) {
    var spin = 0;
    var lastT = 0;
    var tick = function (t) {
      // cap no dt: sem ele, voltar de uma aba em segundo plano salta a rotação
      var dt = lastT ? Math.min((t - lastT) / 1000, 0.1) : 0;
      lastT = t;
      spin = (spin + dt * 12) % 360;
      rotor.style.transform = 'rotate(' + ((window.scrollY || 0) * 0.18 + spin).toFixed(2) + 'deg)';
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }
})();
