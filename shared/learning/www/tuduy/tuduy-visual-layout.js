/* View geometry shared by the Tu Duy renderer and its layout tests. */
(function (root) {
  'use strict';
  const trianglePoints = [[50, 12], [10, 85], [90, 85], [30, 45], [50, 85], [70, 45]];
  function numberLine(v) {
    // Ten labelled ticks give a 29.3-unit hop even for 0..20. Keep the
    // whole hop in view, including near the two ends of the range.
    const intervals = Math.min(v.max, v.max === 20 ? 9 : 10);
    const low = Math.min(v.from, v.to), high = Math.max(v.from, v.to);
    const start = Math.max(0, Math.min(v.max - intervals, low - Math.floor((intervals - (high - low)) / 2)));
    const step = 264 / intervals;
    const x = (n) => 18 + (n - start) * step;
    return {
      start, end: start + intervals, step, fontSize: 14,
      ticks: Array.from({ length: intervals + 1 }, (_, i) => ({ value: start + i, x: x(start + i) })),
      fromX: x(v.from), toX: x(v.to), controlX: (x(v.from) + x(v.to)) / 2,
    };
  }
  const pips = {
    1: [[24, 24]],
    2: [[14, 14], [34, 34]],
    3: [[14, 14], [24, 24], [34, 34]],
    4: [[14, 14], [34, 14], [14, 34], [34, 34]],
    5: [[14, 14], [34, 14], [24, 24], [14, 34], [34, 34]],
    6: [[14, 12], [34, 12], [14, 24], [34, 24], [14, 36], [34, 36]],
  };
  function dieFace(value) {
    if (!pips[value]) throw new Error('dice face must be 1..6');
    return `<svg class="td-die" viewBox="0 0 48 48" role="img" aria-label="Xúc xắc ${value}"><rect x="2" y="2" width="44" height="44" rx="8" fill="white" stroke="#477be8" stroke-width="3"/>${pips[value].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="4" fill="#2d3561"/>`).join('')}</svg>`;
  }
  const api = { trianglePoints, numberLine, dieFace };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TuDuyVisualLayout = api;
})(typeof window !== 'undefined' ? window : this);
