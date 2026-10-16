/* Luyện tư duy — UI for the Learning Hub (5–6 tuổi and up).
 * Depends on tuduy-core.js (window.TuDuy). Uses the hub's helpers when present:
 * sndTap/sndRight/sndWrong/confetti/speakText/recordLearningResult.
 */
(function () {
  'use strict';
  const T = window.TuDuy;
  const Layout = window.TuDuyVisualLayout;
  const KEY = 'tuduy.v1';
  const SESSION_N = 8;
  const $ = (id) => document.getElementById(id);
  const call = (name, ...a) => { try { if (typeof window[name] === 'function') return window[name](...a); } catch (e) { /* host helper failed */ } return undefined; };
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /* ---------- persistence (per-viewer convenience; never required) ---------- */
  function load() { try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; } }
  function save(st) { try { localStorage.setItem(KEY, JSON.stringify(st)); } catch (e) { /* storage unavailable */ } }
  let store = load();
  store.stats = store.stats || {};
  store.review = store.review || [];

  /* ---------- session state ---------- */
  let items = [], idx = 0, score = 0, tries = 0, active = null, answers = {}, mode = 'mix', locked = false;
  const allTypes = () => T.TYPES.concat(T.PHASE2_TYPES, T.PHASE2B_TYPES).filter((t) =>
    (t !== 'triSum' && t !== 'addTable') || (typeof ageBand !== 'undefined' && ageBand !== '5-6'));
  const SHELVES = [
    ['Số & phép tính', ['balance', 'chain', 'compare', 'order', 'neighbor', 'condition', 'signs', 'sumSplit', 'addTable', 'numberLine']],
    ['Quy luật', ['sequence', 'shapePattern', 'oddOne', 'ring', 'match']],
    ['Hình & vị trí', ['shapes', 'position', 'insideOut', 'grid']],
    ['Câu đố ô số', ['pyramid', 'scale', 'code', 'triSum', 'latin', 'magic', 'triCenter']],
  ];
  const currentMax = () => (typeof ageBand !== 'undefined' && ageBand === '5-6' ? 10 : 20);

  function open() {
    $('tdQuiz').hidden = true; $('tdDone').hidden = true; $('tdChooser').hidden = false;
    const box = $('tdPick'); box.innerHTML = '';
    const mk = (emoji, name, sub, onClick, cls) => {
      const b = document.createElement('button');
      b.className = 'td-pick ' + (cls || '');
      b.innerHTML = `<span class="td-em">${emoji}</span><span class="td-name">${esc(name)}</span><span class="td-sub">${esc(sub)}</span>`;
      b.onclick = onClick; box.appendChild(b);
    };
    mk('🎲', 'Trộn các dạng', SESSION_N + ' câu', () => start('mix'), 'mix');
    for (const [title, types] of SHELVES) {
      const heading = document.createElement('h3'); heading.className = 'td-shelf'; heading.textContent = title; box.appendChild(heading);
      for (const t of types.filter((x) => allTypes().includes(x))) {
        const s = store.stats[t] || {};
        const stars = s.seen ? '⭐'.repeat(Math.min(3, Math.floor((s.right || 0) / 4))) || '·' : 'mới';
        mk(T.TYPE_INFO[t].emoji, T.TYPE_INFO[t].name, `Mức ${s.level || 2} · ${stars}`, () => start(t));
      }
    }
    if (store.review.length) mk('🔁', 'Làm lại câu chưa đúng', store.review.length + ' câu', () => start('review'), 'review');
  }

  function levelFor(t) { return (store.stats[t] && store.stats[t].level) || 2; }
  function start(m) {
    call('sndTap');
    mode = m; idx = 0; score = 0;
    if (m === 'review') items = store.review.slice(0, SESSION_N);
    else {
      const mixed = allTypes();
      if (m === 'mix') for (let i = mixed.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1)); [mixed[i], mixed[j]] = [mixed[j], mixed[i]];
      }
      items = Array.from({ length: SESSION_N }, (_, i) => {
        const t = m === 'mix' ? mixed[i % mixed.length] : m;
        return T.generate(t, levelFor(t), undefined, { max: currentMax(), phase2b: true });
      });
    }
    $('tdChooser').hidden = true; $('tdDone').hidden = true; $('tdQuiz').hidden = false;
    show();
  }

  /* ---------- rendering ---------- */
  function blankBox(id, cls) { return `<button class="td-box td-blank ${cls || ''}" data-blank="${id}" aria-label="ô trống">?</button>`; }
  function numBox(v, cls) { return `<span class="td-box ${cls || ''}">${v}</span>`; }
  function tileSvg(t) {
    const color = t.color || '#477be8';
    const shape = t.shape === 'circle' ? `<circle cx="32" cy="32" r="22" fill="${color}"/>` :
      t.shape === 'triangle' ? `<path d="M32 8 L57 55 H7 Z" fill="${color}"/>` :
      `<rect x="10" y="10" width="44" height="44" rx="5" fill="${color}"/>`;
    return `<svg class="td-tile-svg" viewBox="0 0 64 64" aria-hidden="true">${shape}</svg>`;
  }
  function dotTile(position) {
    const coords = [[16,16],[48,16],[48,48],[16,48]];
    return `<svg class="td-tile-svg" viewBox="0 0 64 64" aria-hidden="true"><rect x="3" y="3" width="58" height="58" rx="8" fill="#f2f7ff" stroke="#477be8" stroke-width="2"/>${coords.map(([x,y], i) => `<circle cx="${x}" cy="${y}" r="${i === position ? 8 : 4}" fill="${i === position ? '#ff6b9d' : '#bfd0ef'}"/>`).join('')}</svg>`;
  }
  function dotShapeTile(t) {
    return `<span class="td-dot-shape">${esc(t.shape)}<small>${'•'.repeat(t.dots)}</small></span>`;
  }
  function ringArrow(index, count) {
    const tangent = 2 * Math.PI * (index + .5) / count;
    return ['→', '↘', '↓', '↙', '←', '↖', '↑', '↗'][Math.round(tangent / (Math.PI / 4)) % 8];
  }

  function renderVisual(item) {
    const v = item.visual, B = (i) => item.blanks.find((b) => b.cell === i);
    if (v.kind === 'pyramid') {
      const cell = (i) => (B(i) ? blankBox(B(i).id) : numBox(v.cells[i]));
      return `<div class="td-pyr"><svg viewBox="0 0 300 200" class="td-pyr-bg" aria-hidden="true"><path d="M150 8 L292 192 L8 192 Z" /></svg>
        <div class="td-row">${cell(5)}</div><div class="td-row">${cell(3)}${cell(4)}</div><div class="td-row">${cell(0)}${cell(1)}${cell(2)}</div></div>`;
    }
    if (v.kind === 'equation') {
      const side = (toks) => toks.map((t) => (t === '□' ? blankBox(item.blanks[0].id) : (typeof t === 'number' ? numBox(t, 'td-plain') : `<span class="td-op">${t}</span>`))).join('');
      return `<div class="td-eq">${side(v.left)}<span class="td-op td-eqsign">=</span>${side(v.right)}</div>`;
    }
    if (v.kind === 'chain') {
      let h = '<div class="td-chain"><span class="td-train" aria-hidden="true">🚂</span>';
      v.values.forEach((val, i) => {
        h += '<span class="td-link">';
        if (i > 0) { const d = v.ops[i - 1]; h += `<span class="td-arrow"><b>${d > 0 ? '+' + d : '−' + -d}</b><i>→</i></span>`; }
        h += (B(i) ? blankBox(B(i).id, 'td-car') : numBox(val, 'td-car')) + '</span>';
      });
      return h + '</div>';
    }
    if (v.kind === 'sequence') {
      return '<div class="td-seq">' + v.values.map((val, i) => (B(i) ? blankBox(B(i).id) : numBox(val))).join('') + '</div>';
    }
    if (v.kind === 'scales') {
      const pan = (arr) => arr.map((x) => (/^\d+$/.test(x) ? `<span class="td-weight">${x}</span>` : `<span class="td-fruit">${x}</span>`)).join('<span class="td-plus">+</span>');
      let h = '<div class="td-scales">';
      for (const f of v.facts) h += `<div class="td-scale"><div class="td-pans"><div class="td-pan">${pan(f.left)}</div><div class="td-pan">${pan(f.right)}</div></div><div class="td-beam"></div><div class="td-pivot"></div></div>`;
      h += `<div class="td-ask">${pan(v.ask.left)}<span class="td-op td-eqsign">=</span>${blankBox(item.blanks[0].id)}</div>`;
      return h + '</div>';
    }
    if (v.kind === 'fan' || v.kind === 'grid') return `<div class="td-fig">${figureSvg(v)}</div><div class="td-eq">Có ${blankBox(item.blanks[0].id)} hình</div>`;
    if (v.kind === 'compare') {
      const fmt = (e) => e.join(' ').replace('-', '−');
      return `<div class="td-compare"><span class="td-side">${esc(fmt(v.left))}</span><span class="td-question">?</span><span class="td-side">${esc(fmt(v.right))}</span></div>`;
    }
    if (v.kind === 'order') return `<div class="td-order-slots">${v.nums.map((_, i) => `<button class="td-order-slot" data-undo="${i}" aria-label="Vị trí ${i + 1}">?</button>`).join('')}</div><div class="td-order-source">${v.nums.map((n) => `<button class="td-choice" data-order="${n}">${n}</button>`).join('')}</div>`;
    if (v.kind === 'neighbor') return `<div class="td-neighbor">${v.values.map((n, i) => B(i) ? blankBox(B(i).id) : numBox(n)).join('<span>→</span>')}</div>`;
    if (v.kind === 'position') return `<div class="td-position">${v.icons.map((icon, i) => `<button class="td-choice" data-choice="${i}" aria-label="Con thứ ${i + 1}">${icon}</button>`).join('')}</div>`;
    if (v.kind === 'condition') return `<div class="td-number-choices">${v.nums.map((n) => `<button class="td-choice" data-multi="${n}">${n}</button>`).join('')}</div>`;
    if (v.kind === 'shapePattern') return `<div class="td-pattern">${v.prefix.map((s) => `<span class="td-symbol">${esc(s)}</span>`).join('')}<span class="td-symbol td-missing">?</span></div>`;
    if (v.kind === 'tileGrid') return `<div class="td-tile-grid" style="--n:${v.n}">${v.tiles.map((t) => `<div class="td-grid-cell">${t ? tileSvg(t) : '?'}</div>`).join('')}</div>`;
    if (v.kind === 'oddOne') return `<div class="td-odd">${v.items.map((t, i) => `<button class="td-choice" data-choice="${i}">${tileSvg(t)}</button>`).join('')}</div>`;
    if (v.kind === 'insideOut') return `<svg class="td-region" viewBox="0 0 260 220" role="img" aria-label="Vật trong và ngoài hai hình"><circle cx="${v.circle.x}" cy="${v.circle.y}" r="${v.circle.r}" class="td-region-line"/><rect x="${v.rect.x}" y="${v.rect.y}" width="${v.rect.w}" height="${v.rect.h}" class="td-region-line"/>${v.objects.map((o, i) => `<text x="${o.x}" y="${o.y}" text-anchor="middle" dominant-baseline="central" data-choice="${i}" class="td-region-object" role="button">${o.icon}</text>`).join('')}</svg>`;
    if (v.kind === 'code') return `<div class="td-code-legend">${Object.entries(v.legend).map(([icon, n]) => `<span>${icon} = ${n}</span>`).join('')}</div><div class="td-code-row">${v.row.map((icon) => `<span>${icon}</span>`).join('')}</div>${item.blanks ? `<div class="td-code-row">${item.blanks.map((b) => blankBox(b.id)).join('')}</div>` : ''}`;
    if (v.kind === 'triSum') {
      const pts = Layout.trianglePoints;
      return `<div class="td-triangle"><svg viewBox="0 0 300 250" aria-hidden="true"><path d="M150 12 L10 220 L290 220 Z" fill="none" stroke="#7c5cff" stroke-width="5"/></svg>${v.values.map((n, i) => `<span class="td-tri-cell" style="left:${pts[i][0]}%;top:${pts[i][1]}%">${B(i) ? blankBox(B(i).id) : numBox(n)}</span>`).join('')}</div>`;
    }
    if (v.kind === 'latin' || v.kind === 'magic') return `<div class="td-digit-grid" style="--n:${v.n}" role="group" aria-label="Bảng số ${v.n} hàng">${v.cells.map((n, i) => `<div class="td-digit-cell">${B(i) ? blankBox(B(i).id) : esc(n)}</div>`).join('')}</div>${v.kind === 'magic' ? `<div class="td-grid-note">Mỗi hàng và cột = ${v.sum}</div>` : ''}`;
    if (v.kind === 'signs') return `<div class="td-eq td-signs">${v.nums.map((n, i) => i ? `${blankBox(B(i - 1).id)}${numBox(n, 'td-plain')}` : numBox(n, 'td-plain')).join('')}<span class="td-op">=</span>${numBox(v.target, 'td-plain')}</div>`;
    if (v.kind === 'triCenter') return `<div class="td-triangle td-center-triangle"><svg viewBox="0 0 300 250" aria-hidden="true"><path d="M150 12 L10 220 L290 220 Z" fill="none" stroke="#7c5cff" stroke-width="5"/></svg>${[[50,10],[10,86],[90,86],[50,58]].map(([x,y], i) => `<span class="td-tri-cell" style="left:${x}%;top:${y}%">${B(i) ? blankBox(B(i).id) : numBox(v.values[i])}</span>`).join('')}</div>`;
    if (v.kind === 'sumSplit') return `<div class="td-number-choices td-split-numbers">${v.nums.map((n) => numBox(n)).join('')}</div><div class="td-grid-note">□ + □ = □ + □</div>`;
    if (v.kind === 'addTable') return `<div class="td-add-table"><span class="td-add-head">+</span>${v.cols.map((n) => `<span class="td-add-head">${n}</span>`).join('')}${v.rows.map((row, r) => `${row == null ? blankBox('row0') : `<span class="td-add-head">${row}</span>`}${v.cols.map((_, c) => { const i = r * 2 + c; return B(i) ? blankBox(B(i).id) : numBox(v.cells[i]); }).join('')}`).join('')}</div>`;
    if (v.kind === 'numberLine') {
      const line = Layout.numberLine(v);
      return `<div class="td-number-line"><svg viewBox="0 0 300 100" role="img" aria-label="Ếch nhảy từ ${v.from} đến ${v.to}"><path d="M18 70 H282" stroke="#477be8" stroke-width="3"/>${line.ticks.map(({ value, x }) => `<g><path d="M${x} 63 V77" stroke="#477be8" stroke-width="2"/><text x="${x}" y="92" text-anchor="middle" font-size="${line.fontSize}">${value}</text></g>`).join('')}<path d="M${line.fromX} 63 Q${line.controlX} 8 ${line.toX} 63" fill="none" stroke="#ff9f43" stroke-width="4"/><text class="td-frog" x="${line.fromX}" y="55" text-anchor="middle" font-size="24">🐸</text></svg></div><div class="td-eq td-line-equation">${blankBox('n0')}<span class="td-op">${v.op === '-' ? '−' : '+'}</span>${blankBox('n1')}<span class="td-op">=</span>${blankBox('n2')}</div>`;
    }
    if (v.kind === 'ring') return `<div class="td-ring">${v.values.map((n, i) => { const angle = -Math.PI / 2 + 2 * Math.PI * i / v.values.length, x = 50 + 38 * Math.cos(angle), y = 50 + 38 * Math.sin(angle), d = v.ops[i]; return `<div class="td-ring-node" style="left:${x}%;top:${y}%">${B(i) ? blankBox(B(i).id) : numBox(n)}</div><span class="td-ring-op" style="left:${50 + 38 * Math.cos(angle + Math.PI / v.values.length)}%;top:${50 + 38 * Math.sin(angle + Math.PI / v.values.length)}%">${d > 0 ? '+' + d : '−' + -d} ${ringArrow(i, v.values.length)}</span>`; }).join('')}</div>`;
    if (v.kind === 'match') return `<div class="td-match">${[v.left, v.right].map((col, side) => `<div class="td-match-col">${col.map((e, i) => `<button class="td-match-item" data-pair-side="${side}" data-pair-index="${i}">${e.join(' ').replace('-', '−')}</button>`).join('')}</div>`).join(`<span class="td-match-links">${v.left.map(() => '↔').join('<br>')}</span>`)}<svg class="td-match-lines" aria-hidden="true"></svg></div><div class="td-grid-note" id="tdPairStatus">Chạm một phép bên trái, rồi phép bằng nó bên phải.</div>`;
    if (v.kind === 'queue') return `<div class="td-queue-front">🚩 Đầu hàng ở bên trái</div><div class="td-queue">${v.names.map((name, i) => `<button class="td-choice" data-choice="${i}"><span>🧒</span><small>${esc(name)}</small></button>`).join('')}</div>`;
    if (v.kind === 'venn3') return `<svg class="td-region td-venn" viewBox="0 0 260 230" role="img" aria-label="Ba hình tròn và bạn kangaroo">${v.circles.map((c) => `<circle cx="${c.x}" cy="${c.y}" r="${c.r}" class="td-region-line"/>`).join('')}<text x="${v.kangaroo.x}" y="${v.kangaroo.y + 8}" text-anchor="middle" font-size="27">🦘</text></svg>`;
    if (v.kind === 'expressionCondition') return `<div class="td-expressions">${v.expressions.map((e, i) => `<button class="td-choice" data-multi="${i}">${esc(e.join(' ').replace('-', '−'))}</button>`).join('')}</div>`;
    if (v.kind === 'dotPattern') return `<div class="td-dot-pattern">${v.prefix.map(dotTile).join('')}<span class="td-symbol td-missing">?</span></div>`;
    if (v.kind === 'dotGrid') return `<div class="td-tile-grid" style="--n:3">${v.cells.map((t) => `<div class="td-grid-cell">${t ? dotShapeTile(t) : '?'}</div>`).join('')}</div>`;
    if (v.kind === 'diceCompare') return `<div class="td-dice-pairs">${v.pairs.map(([a,b], i) => `<div><b>Bạn ${i + 1}</b><span class="td-dice-faces">${Layout.dieFace(a)}<span>+</span>${Layout.dieFace(b)}</span></div>`).join('')}</div>`;
    if (v.kind === 'pyramid4') {
      const cell = (i) => B(i) ? blankBox(B(i).id) : numBox(v.cells[i]);
      return `<div class="td-pyr td-pyr-four"><div class="td-row">${cell(9)}</div><div class="td-row">${cell(7)}${cell(8)}</div><div class="td-row">${cell(4)}${cell(5)}${cell(6)}</div><div class="td-row">${cell(0)}${cell(1)}${cell(2)}${cell(3)}</div></div>`;
    }
    return '';
  }

  /* SVG geometry shared by the figure and the counting animation */
  function fanGeom(n) { const ax = 150, ay = 12, by = 188, x0 = 14, x1 = 286; const xs = Array.from({ length: n + 1 }, (_, i) => x0 + ((x1 - x0) * i) / n); return { ax, ay, by, xs }; }
  function gridGeom(v) { const aspect = v.aspect || 1; const cw = Math.min(aspect === 1 ? 90 : 120, 280 / v.w, (180 / v.h) * aspect); const chh = cw / aspect; const W = cw * v.w, H = chh * v.h; return { cw, chh, W, H, ox: (300 - W) / 2, oy: (200 - H) / 2 }; }
  function figureSvg(v) {
    if (v.kind === 'fan') {
      const g = fanGeom(v.segments);
      let s = `<svg viewBox="0 0 300 200" class="td-svg"><path class="td-line" d="M${g.ax} ${g.ay} L${g.xs[0]} ${g.by} L${g.xs[g.xs.length - 1]} ${g.by} Z"/>`;
      g.xs.slice(1, -1).forEach((x) => { s += `<line class="td-line" x1="${g.ax}" y1="${g.ay}" x2="${x}" y2="${g.by}"/>`; });
      return s + '<g class="td-hl"></g></svg>';
    }
    const g = gridGeom(v);
    let s = `<svg viewBox="0 0 300 200" class="td-svg">`;
    for (let x = 0; x < v.w; x++) for (let y = 0; y < v.h; y++) s += `<rect class="td-cell" x="${g.ox + x * g.cw}" y="${g.oy + y * g.chh}" width="${g.cw}" height="${g.chh}"/>`;
    s += '<g class="td-hl"></g>';
    if (v.ant) s += `<text x="${g.ox + (v.ant.x + 0.5) * g.cw}" y="${g.oy + (v.ant.y + 0.5) * g.chh + 12}" text-anchor="middle" font-size="34">🐜</text>`;
    return s + '</svg>';
  }

  function show() {
    const item = items[idx];
    tries = 0; locked = false; answers = {};
    $('tdCount').textContent = `Câu ${idx + 1}/${items.length} · ${T.TYPE_INFO[item.type].emoji} ${T.TYPE_INFO[item.type].name}`;
    $('tdStars').textContent = '⭐'.repeat(score);
    $('tdPrompt').textContent = item.prompt;
    $('tdVisual').innerHTML = renderVisual(item);
    $('tdExplain').hidden = true; $('tdExplain').innerHTML = '';
    $('tdFb').textContent = ''; $('tdFb').className = 'fb';
    $('tdNext').hidden = true; $('tdCheck').hidden = false; $('tdPad').hidden = false;
    $('tdVisual').querySelectorAll('.td-blank').forEach((b) => { b.onclick = () => select(b.dataset.blank); });
    if (item.input === 'numpad' || !item.input || item.input === 'signs') {
      if (item.input === 'signs') buildSignsPad();
      else buildPad(item.type === 'code' ? 9 : item.type === 'latin' ? item.visual.n : currentMax(), item.type === 'latin' ? 1 : 0);
      select(item.blanks[0].id);
    } else {
      active = null;
      buildChoicePad(item);
      $('tdVisual').querySelectorAll('[data-choice]').forEach((b) => { b.onclick = () => choose(Number(b.dataset.choice)); });
      $('tdVisual').querySelectorAll('[data-multi]').forEach((b) => { b.onclick = () => toggleNumber(Number(b.dataset.multi)); });
      $('tdVisual').querySelectorAll('[data-order]').forEach((b) => { b.onclick = () => placeNumber(Number(b.dataset.order)); });
      $('tdVisual').querySelectorAll('[data-undo]').forEach((b) => { b.onclick = () => undoOrder(Number(b.dataset.undo)); });
      $('tdVisual').querySelectorAll('[data-pair-side]').forEach((b) => { b.onclick = () => pair(Number(b.dataset.pairSide), Number(b.dataset.pairIndex)); });
    }
    speakPrompt();
  }

  function select(id) {
    active = id;
    $('tdVisual').querySelectorAll('.td-blank').forEach((b) => b.classList.toggle('sel', b.dataset.blank === id));
  }
  function press(n) {
    if (locked || active == null) return;
    call('sndTap');
    answers[active] = n;
    const el = $('tdVisual').querySelector(`[data-blank="${active}"]`);
    el.textContent = n; el.classList.add('filled'); el.classList.remove('bad');
    // move to the next empty blank
    const item = items[idx];
    const nextEmpty = item.blanks.find((b) => answers[b.id] == null);
    if (nextEmpty) select(nextEmpty.id);
  }
  function clearActive() {
    if (locked || active == null) return;
    delete answers[active];
    const el = $('tdVisual').querySelector(`[data-blank="${active}"]`);
    el.textContent = '?'; el.classList.remove('filled', 'bad');
  }
  function choose(value) {
    if (locked) return;
    answers.choice = value;
    call('sndTap');
    document.querySelectorAll('#tuduy [data-choice]').forEach((b) => b.classList.toggle('chosen', Number(b.dataset.choice) === value));
  }
  function pair(side, index) {
    if (locked) return;
    if (side === 0) answers.pairLeft = index;
    else if (answers.pairLeft != null) {
      const pairs = (answers.pairs || []).slice();
      pairs[answers.pairLeft] = index;
      // Each right-hand expression can have only one line.
      pairs.forEach((v, i) => { if (i !== answers.pairLeft && v === index) pairs[i] = null; });
      answers.pairs = pairs;
      answers.pairLeft = null;
    }
    call('sndTap');
    $('tdVisual').querySelectorAll('[data-pair-side]').forEach((b) => {
      const s = Number(b.dataset.pairSide), i = Number(b.dataset.pairIndex);
      b.classList.toggle('chosen', s === 0 && i === answers.pairLeft);
      b.textContent = s === 0 && answers.pairs && answers.pairs[i] != null ?
        `${items[idx].visual.left[i].join(' ').replace('-', '−')} → ${answers.pairs[i] + 1}` :
        (s === 0 ? items[idx].visual.left[i] : items[idx].visual.right[i]).join(' ').replace('-', '−');
    });
    $('tdPairStatus').textContent = (answers.pairs || []).filter((x) => x != null).length + '/' + items[idx].answer.length + ' cặp đã nối';
    drawPairs();
  }
  function drawPairs() {
    const root = $('tdVisual').querySelector('.td-match'), svg = root && root.querySelector('.td-match-lines');
    if (!svg) return;
    const bounds = root.getBoundingClientRect();
    svg.setAttribute('viewBox', `0 0 ${bounds.width} ${bounds.height}`);
    svg.innerHTML = (answers.pairs || []).map((right, left) => {
      if (right == null) return '';
      const a = root.querySelector(`[data-pair-side="0"][data-pair-index="${left}"]`).getBoundingClientRect();
      const b = root.querySelector(`[data-pair-side="1"][data-pair-index="${right}"]`).getBoundingClientRect();
      return `<path d="M${a.right - bounds.left} ${a.top + a.height / 2 - bounds.top} L${b.left - bounds.left} ${b.top + b.height / 2 - bounds.top}" stroke="#ff9f43" stroke-width="3" fill="none"/>`;
    }).join('');
  }
  function toggleNumber(n) {
    if (locked) return;
    const selected = new Set(answers.multi || []);
    if (selected.has(n)) selected.delete(n); else selected.add(n);
    answers.multi = [...selected];
    call('sndTap');
    document.querySelectorAll('#tuduy [data-multi]').forEach((b) => b.classList.toggle('chosen', selected.has(Number(b.dataset.multi))));
  }
  function placeNumber(n) {
    if (locked) return;
    const selected = answers.order || [];
    if (selected.includes(n) || selected.length >= items[idx].answer.length) return;
    answers.order = selected.concat(n);
    call('sndTap'); updateOrder();
  }
  function undoOrder(i) {
    if (locked || !answers.order || i >= answers.order.length) return;
    answers.order = answers.order.slice(0, i);
    call('sndTap'); updateOrder();
  }
  function updateOrder() {
    const selected = answers.order || [];
    $('tdVisual').querySelectorAll('[data-undo]').forEach((b, i) => { b.textContent = selected[i] == null ? '?' : selected[i]; b.classList.toggle('chosen', selected[i] != null); });
    $('tdVisual').querySelectorAll('[data-order]').forEach((b) => { b.disabled = selected.includes(Number(b.dataset.order)); });
  }
  function showCorrect(item) {
    if (item.input === 'pairs') {
      answers.pairs = item.answer.slice(); answers.pairLeft = null;
      $('tdVisual').querySelectorAll('[data-pair-side="0"]').forEach((b, i) => { b.textContent = `${item.visual.left[i].join(' ').replace('-', '−')} → ${item.answer[i] + 1}`; b.classList.add('shown'); });
      $('tdPairStatus').textContent = 'Các cặp đúng đã được nối.';
      drawPairs();
      return;
    }
    if (item.input === 'order') { answers.order = item.answer.slice(); updateOrder(); return; }
    if (item.input === 'multi') {
      answers.multi = item.answer.slice();
      $('tdVisual').querySelectorAll('[data-multi]').forEach((b) => b.classList.toggle('shown', item.answer.includes(Number(b.dataset.multi))));
      return;
    }
    const v = item.answer;
    if (item.input === 'sign' || item.type === 'shapePattern') {
      $('tdPad').querySelectorAll('.td-choice').forEach((b, i) => b.classList.toggle('shown', (item.input === 'sign' ? ['>', '<', '='][i] : item.choices[i]) === v));
    } else {
      document.querySelectorAll('#tuduy [data-choice]').forEach((b) => b.classList.toggle('shown', Number(b.dataset.choice) === v));
      $('tdPad').querySelectorAll('.td-choice').forEach((b, i) => b.classList.toggle('shown', i === v));
    }
  }
  function buildChoicePad(item) {
    const pad = $('tdPad'); pad.innerHTML = ''; pad.dataset.built = '';
    pad.className = 'td-pad';
    if (item.input === 'order' || item.input === 'multi' || item.input === 'pairs' || item.type === 'position' || item.type === 'oddOne' || (item.type === 'insideOut' && item.visual.kind !== 'venn3')) {
      pad.hidden = true; return;
    }
    pad.hidden = false;
    pad.classList.add('td-choice-pad');
    const labels = item.input === 'sign' ? ['>', '<', '='] : item.choices;
    labels.forEach((choice, i) => {
      const b = document.createElement('button');
      b.className = 'td-key td-choice';
      b.dataset.choice = item.input === 'sign' ? i : i;
      b.innerHTML = item.visual.kind === 'dotPattern' ? dotTile(choice) : item.visual.kind === 'dotGrid' ? dotShapeTile(choice) : item.type === 'grid' ? tileSvg(choice) : esc(choice);
      b.onclick = () => {
        answers.choice = item.input === 'sign' ? choice : item.type === 'shapePattern' ? choice : i;
        call('sndTap');
        pad.querySelectorAll('.td-choice').forEach((x) => x.classList.toggle('chosen', x === b));
      };
      pad.appendChild(b);
    });
  }

  function check() {
    if (locked) return;
    const item = items[idx];
    const input = item.input || 'numpad';
    const blankInput = input === 'numpad' || input === 'signs';
    const filled = blankInput ? item.blanks.every((b) => answers[b.id] != null) :
      input === 'order' ? Array.isArray(answers.order) && answers.order.length === item.answer.length :
      input === 'multi' ? Array.isArray(answers.multi) && answers.multi.length > 0 :
      input === 'pairs' ? Array.isArray(answers.pairs) && answers.pairs.filter((x) => x != null).length === item.answer.length : answers.choice != null;
    if (!filled) { $('tdFb').textContent = 'Con chọn hoặc điền đủ đáp án nhé!'; $('tdFb').className = 'fb'; return; }
    tries += 1;
    const numberLineOK = item.type === 'numberLine' && T.numberLineCorrect(item, item.blanks.map((b) => answers[b.id]));
    const wrong = blankInput && !numberLineOK ? item.blanks.filter((b) => answers[b.id] !== b.answer) : [];
    const correct = blankInput ? numberLineOK || !wrong.length : input === 'order' ? JSON.stringify(answers.order) === JSON.stringify(item.answer) :
      input === 'multi' ? JSON.stringify(answers.multi.slice().sort((a,b) => a-b)) === JSON.stringify(item.answer) :
      input === 'pairs' ? JSON.stringify(answers.pairs) === JSON.stringify(item.answer) :
      answers.choice === item.answer;
    if (correct) {
      locked = true;
      if (tries === 1) score += 1;
      record(item, tries === 1);
      call('sndRight'); if (tries === 1) call('confetti');
      $('tdFb').textContent = tries === 1 ? 'Giỏi quá! 🎉' : 'Đúng rồi! 👍';
      $('tdFb').className = 'fb ok';
      $('tdVisual').querySelectorAll('.td-blank').forEach((b) => b.classList.add('ok'));
      revealExplain(item, false);
      finishStep();
      return;
    }
    call('sndWrong');
    wrong.forEach((b) => $('tdVisual').querySelector(`[data-blank="${b.id}"]`).classList.add('bad'));
    if (tries === 1) {
      $('tdFb').textContent = 'Chưa đúng, con thử lại nhé. Gợi ý: ' + item.explain[0];
      $('tdFb').className = 'fb no';
      return;
    }
    // second miss: show the answer, the steps and the animation; queue for review
    locked = true;
    record(item, false);
    if (blankInput) item.blanks.forEach((b) => { const el = $('tdVisual').querySelector(`[data-blank="${b.id}"]`); el.textContent = b.answer === '-' ? '−' : b.answer; el.classList.remove('bad'); el.classList.add('shown'); });
    else showCorrect(item);
    $('tdFb').textContent = 'Mình cùng xem cách làm nhé 👇';
    $('tdFb').className = 'fb';
    revealExplain(item, true);
    finishStep();
  }

  function record(item, firstTry) {
    store.stats[item.type] = T.updateStat(store.stats[item.type], firstTry);
    if (!firstTry && mode !== 'review') store.review = [item].concat(store.review).slice(0, 12);
    if (firstTry && mode === 'review') store.review = store.review.filter((x) => x !== item && JSON.stringify(x) !== JSON.stringify(item));
    save(store);
  }

  function finishStep() {
    $('tdCheck').hidden = true; $('tdPad').hidden = true; $('tdNext').hidden = false;
    $('tdNext').textContent = idx + 1 < items.length ? 'Câu tiếp ▶' : 'Xem kết quả ▶';
  }
  function next() {
    call('sndTap');
    if (idx + 1 < items.length) { idx += 1; show(); return; }
    $('tdQuiz').hidden = true; $('tdDone').hidden = false;
    $('tdDoneStars').textContent = '⭐'.repeat(score) || '💪';
    $('tdDoneText').textContent = `Con làm đúng ngay lần đầu ${score}/${items.length} câu!`;
    call('recordLearningResult', 'tuduy', score, items.length);
    if (score >= items.length - 1) call('confetti');
  }

  /* ---------- explanations + animations ---------- */
  function revealExplain(item, auto) {
    const box = $('tdExplain');
    box.hidden = false;
    box.innerHTML = '<div class="td-ex-title">Cách làm</div><ol class="td-steps">' +
      item.explain.map((s) => `<li>${esc(s)}</li>`).join('') +
      '</ol><div class="td-ex-btns"><button class="rbtn say" id="tdSayEx">🔊 Nghe giải thích</button><button class="rbtn prev" id="tdPlay">▶ Xem hoạt hình</button></div>';
    $('tdSayEx').onclick = () => speakLines(item.explain);
    $('tdPlay').onclick = () => animate(item);
    const lis = [...box.querySelectorAll('li')];
    lis.forEach((li, i) => { li.style.animationDelay = (i * 0.45) + 's'; });
    if (auto) setTimeout(() => animate(item), 400);
  }

  let animTimers = [];
  function after(ms, fn) { animTimers.push(setTimeout(fn, ms)); }
  function stopAnim() { animTimers.forEach(clearTimeout); animTimers = []; }

  function animate(item) {
    stopAnim();
    const vis = $('tdVisual');
    const a = item.anim;
    if (a.kind === 'numberLine') {
      const frog = vis.querySelector('.td-frog'), v = item.visual, direction = v.to > v.from ? 1 : -1;
      const line = Layout.numberLine(v);
      if (!frog) return;
      const renderedStep = line.step * frog.ownerSVGElement.getBoundingClientRect().width / 300;
      frog.style.transform = 'translateX(0px)';
      for (let hop = 1; hop <= Math.abs(v.to - v.from); hop++) {
        after(300 + hop * 350, () => { frog.style.transform = `translateX(${renderedStep * direction * hop}px)`; call('sndTap'); });
      }
      return;
    }
    if (a.kind === 'ring') {
      const nodes = [...vis.querySelectorAll('.td-ring-node')];
      nodes.forEach((node, i) => after(300 + i * 500, () => { nodes.forEach((x) => x.classList.remove('glow')); node.classList.add('glow'); call('sndTap'); }));
      after(500 + nodes.length * 500, () => nodes.forEach((x) => x.classList.remove('glow')));
      return;
    }
    if (['compare', 'order', 'position', 'insideOut', 'triSum'].includes(a.kind)) {
      const selectors = { compare: '.td-side', order: '.td-order-slot', position: '.td-position .td-choice', insideOut: '.td-region-object', triSum: '.td-tri-cell' };
      const nodes = [...vis.querySelectorAll(selectors[a.kind])];
      nodes.forEach((node) => node.classList.remove('glow'));
      nodes.forEach((node, i) => after(300 + i * 500, () => { node.classList.add('glow'); call('sndTap'); }));
      after(700 + nodes.length * 500, () => nodes.forEach((node) => node.classList.remove('glow')));
      return;
    }
    if (a.kind === 'pyramid') {
      const kids = { 3: [0, 1], 4: [1, 2], 5: [3, 4] };
      const order = item.blanks.map((b) => b.cell).sort((x, y) => (x < 3 && y >= 3 ? 1 : 0));
      // show solution order from the explanation: cells explained in the order the solver filled them
      const boxes = () => [...vis.querySelectorAll('.td-pyr .td-box')];
      const cellEl = (i) => { const all = boxes(); const map = [all[3], all[4], all[5], all[1], all[2], all[0]]; return map[i]; };
      item.blanks.forEach((b) => { const el = cellEl(b.cell); el.textContent = '?'; el.classList.remove('ok', 'shown', 'filled'); });
      let t = 300;
      const filled = new Set(item.visual.cells.map((v, i) => (v != null ? i : -1)).filter((i) => i >= 0));
      let guard = 0;
      while (filled.size < 6 && guard++ < 6) {
        for (const [s, k] of Object.entries(kids)) {
          const si = Number(s), [x, y] = k;
          const unknown = [si, x, y].filter((i) => !filled.has(i));
          if (unknown.length !== 1) continue;
          const u = unknown[0];
          const parts = [si, x, y].filter((i) => i !== u);
          after(t, () => parts.forEach((p) => cellEl(p).classList.add('glow')));
          after(t + 700, () => { const el = cellEl(u); el.textContent = a.cells[u]; el.classList.add('shown', 'pop'); call('sndTap'); });
          after(t + 1300, () => parts.forEach((p) => cellEl(p).classList.remove('glow')));
          filled.add(u); t += 1500;
        }
      }
      void order;
      return;
    }
    if (a.kind === 'hops') {
      const cars = [...vis.querySelectorAll('.td-car')];
      const arrows = [...vis.querySelectorAll('.td-arrow')];
      const train = vis.querySelector('.td-train');
      item.blanks.forEach((b) => { cars[b.cell].textContent = '?'; cars[b.cell].classList.remove('ok', 'shown', 'filled'); });
      let t = 300;
      const backwards = item.blanks.some((b) => b.cell === 0);
      const seqIdx = backwards
        ? [...Array(a.values.length).keys()].reverse()
        : [...Array(a.values.length).keys()];
      seqIdx.forEach((i, k) => {
        after(t, () => {
          cars.forEach((c) => c.classList.remove('glow'));
          cars[i].classList.add('glow');
          if (train) { const cr = cars[i].getBoundingClientRect(), pr = train.parentElement.getBoundingClientRect(); train.style.transform = `translate(${cr.left - pr.left}px, ${cr.top - pr.top - 30}px)`; }
          if (k > 0) { const ai = backwards ? i : i - 1; if (arrows[ai]) arrows[ai].classList.add('glow'); }
          if (cars[i].textContent === '?') { cars[i].textContent = a.values[i]; cars[i].classList.add('shown', 'pop'); call('sndTap'); }
        });
        t += 800;
      });
      after(t, () => { cars.forEach((c) => c.classList.remove('glow')); arrows.forEach((x) => x.classList.remove('glow')); });
      return;
    }
    if (a.kind === 'highlight') {
      const g = vis.querySelector('.td-hl');
      const v = a.figure;
      const counter = document.createElement('div');
      counter.className = 'td-counter';
      vis.querySelector('.td-fig').appendChild(counter);
      let t = 300;
      a.shapes.forEach((s, i) => {
        after(t, () => {
          let shape;
          if (v.kind === 'fan') {
            const fg = fanGeom(v.segments);
            shape = `<path class="td-hl-shape" d="M${fg.ax} ${fg.ay} L${fg.xs[s.from]} ${fg.by} L${fg.xs[s.to]} ${fg.by} Z"/>`;
          } else {
            const gg = gridGeom(v);
            shape = `<rect class="td-hl-shape" x="${gg.ox + s.x0 * gg.cw + 3}" y="${gg.oy + s.y0 * gg.chh + 3}" width="${(s.x1 - s.x0) * gg.cw - 6}" height="${(s.y1 - s.y0) * gg.chh - 6}" rx="6"/>`;
          }
          g.innerHTML = shape;
          counter.textContent = i + 1;
          call('speakText', String(i + 1), 'vi-VN', null, 'en-US', 1, 1.2);
        });
        t += 1000;
      });
      after(t, () => { g.innerHTML = ''; counter.textContent = `Tất cả: ${a.shapes.length}`; });
      return;
    }
    // equation / sequence / scales: step through the explanation lines
    const lis = [...$('tdExplain').querySelectorAll('li')];
    lis.forEach((li) => li.classList.remove('lit'));
    lis.forEach((li, i) => after(300 + i * 1200, () => { lis.forEach((x) => x.classList.remove('lit')); li.classList.add('lit'); }));
    after(300 + lis.length * 1200, () => lis.forEach((x) => x.classList.remove('lit')));
  }

  /* ---------- speech ---------- */
  const spoken = (s) => s.replace(/□/g, 'ô trống').replace(/−/g, ' trừ ').replace(/\+/g, ' cộng ').replace(/=/g, ' bằng ');
  function speakPrompt() { call('speakText', spoken(items[idx].prompt), 'vi-VN', null, 'en-US', 0.9, 1.1); }
  async function speakLines(lines) { for (const l of lines) { await call('speakText', spoken(l), 'vi-VN', null, 'en-US', 0.9, 1.1); } }

  /* ---------- keypad ---------- */
  function buildPad(max, min) {
    const start = min || 0, key = `${start}-${max}`;
    const pad = $('tdPad'); if (!pad || pad.dataset.built === key) return;
    pad.innerHTML = ''; pad.className = 'td-pad'; pad.dataset.built = key;
    for (let n = start; n <= max; n++) { const b = document.createElement('button'); b.className = 'td-key'; b.textContent = n; b.onclick = () => press(n); pad.appendChild(b); }
    const del = document.createElement('button'); del.className = 'td-key del'; del.textContent = '⌫'; del.setAttribute('aria-label', 'Xoá'); del.onclick = clearActive; pad.appendChild(del);
  }
  function buildSignsPad() {
    const pad = $('tdPad'); pad.innerHTML = ''; pad.className = 'td-pad td-choice-pad'; pad.dataset.built = 'signs';
    for (const op of ['+', '-']) { const b = document.createElement('button'); b.className = 'td-key'; b.textContent = op === '-' ? '−' : op; b.onclick = () => press(op); pad.appendChild(b); }
    const del = document.createElement('button'); del.className = 'td-key del'; del.textContent = '⌫'; del.onclick = clearActive; pad.appendChild(del);
  }

  window.TuDuyUI = {
    open() { buildPad(currentMax()); stopAnim(); open(); },
    backToChooser() {
      if ($('tdQuiz').hidden && $('tdDone').hidden) return false;
      stopAnim(); open(); return true;
    },
    check, next, speakPrompt, start,
  };
})();
