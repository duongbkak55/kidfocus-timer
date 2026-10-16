/* Shared arithmetic boundary for all Tu Duy engines. Pure and DOM-free. */
(function (root) {
  'use strict';
  function step(a, op, b, max) {
    if (!Number.isInteger(a) || !Number.isInteger(b) || a < 0 || b < 0 || a > max || b > max) return false;
    const value = op === '+' ? a + b : op === '-' ? a - b : NaN;
    if (!Number.isInteger(value) || value < 0 || value > max) return false;
    if (max === 10) return true;
    if (max !== 20) return false;
    return op === '+' ? value <= 10 || a % 10 + b % 10 <= 9 : a <= 10 || a % 10 >= b % 10;
  }
  const api = { step };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TuDuyArithmetic = api;
})(typeof window !== 'undefined' ? window : this);
