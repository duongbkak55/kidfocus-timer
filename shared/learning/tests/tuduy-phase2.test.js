'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('../www/tuduy/tuduy-core');
const N = Number(process.env.TUDUY_N || 100);
const all = (n) => Array.from({ length: n }, (_, i) => i);
const digits = (x) => typeof x === 'number' && Number.isInteger(x);
const inRange = (n, max) => !digits(n) || (n >= 0 && n <= max);
const canStep = (a, op, b) => op === '+' ? a % 10 + b % 10 <= 9 : a % 10 >= b % 10;
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const sign = (a, b) => a === b ? '=' : a > b ? '>' : '<';
const ev = (e) => e.length === 1 ? e[0] : e[1] === '+' ? e[0] + e[2] : e[0] - e[2];
const tileEq = (a,b) => a.shape === b.shape && a.color === b.color;
const permutations = (() => {
  const out = [];
  const rec = (p, unused) => {
    if (!unused.length) { out.push(p); return; }
    unused.forEach((v, i) => rec(p.concat(v), unused.filter((_, j) => j !== i)));
  };
  rec([], [1,2,3,4,5,6]);
  return out;
})();
function independent(item, max) {
  const v = item.visual;
  switch (item.type) {
    case 'compare': {
      for (const e of [v.left, v.right]) if (max === 20 && e.length > 1) assert(canStep(e[0], e[1], e[2]));
      return sign(ev(v.left), ev(v.right));
    }
    case 'order': {
      assert.equal(new Set(v.nums).size, v.nums.length);
      assert(v.nums.length >= 4 && v.nums.length <= 6);
      return v.nums.slice().sort((a,b) => v.ascending ? a-b : b-a);
    }
    case 'neighbor': {
      const first = v.values.findIndex((x) => x != null), anchor = v.values[first];
      const full = v.values.map((_, i) => anchor + i - first);
      assert(full.every((x) => x >= 0 && x <= max));
      if(max===20) assert(!full.includes(9)||!full.includes(10));
      return v.values.map((x,i) => x == null ? full[i] : null).filter((x) => x != null);
    }
    case 'position': {
      assert.equal(new Set(v.icons).size, v.icons.length);
      if(v.mode==='middle') assert.equal(v.icons.length%2,1);
      return v.mode === 'middle' ? Math.floor(v.icons.length / 2) :
        v.mode === 'left' ? v.k - 1 : v.mode === 'right' ? v.icons.length - v.k : v.icons.length - v.k - 1;
    }
    case 'condition': {
      assert.equal(new Set(v.nums).size, v.nums.length);
      assert(v.nums.length >= 6 && v.nums.length <= 8);
      const match = (n) => (v.rule.lo == null || (n > v.rule.lo && n < v.rule.hi)) &&
        (v.rule.parity == null || n % 2 === v.rule.parity);
      const set = v.nums.filter(match).sort((a,b) => a-b);
      assert(set.length >= 1 && set.length <= 4);
      return set;
    }
    case 'shapePattern': {
      const valid = item.choices.filter((choice) => all(Math.floor(v.prefix.length/2)).some((i) => {
        const p = i+1;
        return v.prefix.every((x,j) => x === v.prefix[j%p]) && choice === v.prefix[v.prefix.length%p];
      }));
      assert(v.prefix.length >= 4);
      assert.equal(valid.length, 1);
      return valid[0];
    }
    case 'grid': {
      const fits = item.choices.map((candidate, choice) => {
        const full = v.tiles.slice(); full[v.missing] = candidate;
        if (v.n === 2) {
          const good = all(2).every((row) => full[row*2].shape === full[row*2+1].shape) &&
            all(2).every((col) => full[col].color === full[col+2].color);
          return good ? choice : null;
        }
        const good = all(3).every((i) => {
          const row = full.slice(i*3,i*3+3), col=[full[i],full[i+3],full[i+6]];
          return new Set(row.map((x)=>x.shape)).size===3 && new Set(row.map((x)=>x.color)).size===3 &&
            new Set(col.map((x)=>x.shape)).size===3 && new Set(col.map((x)=>x.color)).size===3;
        });
        return good ? choice : null;
      }).filter((x)=>x!=null);
      assert.equal(fits.length,1);
      return fits[0];
    }
    case 'oddOne': {
      const unique = new Set();
      for(const attr of ['shape','color','size']) for(let i=0;i<4;i++)
        if(v.items.filter((x)=>x[attr]===v.items[i][attr]).length===1) unique.add(i);
      assert.equal(unique.size,1);
      return [...unique][0];
    }
    case 'insideOut': {
      assert.equal(v.rect.w,v.rect.h);
      assert(v.circle.x-v.circle.r>=0&&v.circle.x+v.circle.r<=260);
      assert(v.circle.y-v.circle.r>=0&&v.circle.y+v.circle.r<=220);
      assert(v.rect.x>=0&&v.rect.x+v.rect.w<=260&&v.rect.y>=0&&v.rect.y+v.rect.h<=220);
      const circle=(p)=>Math.hypot(p.x-v.circle.x,p.y-v.circle.y)<v.circle.r;
      const rect=(p)=>p.x>v.rect.x&&p.x<v.rect.x+v.rect.w&&p.y>v.rect.y&&p.y<v.rect.y+v.rect.h;
      const regions=v.objects.map((p)=>(circle(p)?1:0)+(rect(p)?2:0));
      assert.equal(new Set(regions).size,v.objects.length);
      v.objects.forEach((p)=>{
        assert(Math.abs(Math.hypot(p.x-v.circle.x,p.y-v.circle.y)-v.circle.r)>=12);
        for(const d of [Math.abs(p.x-v.rect.x),Math.abs(p.x-v.rect.x-v.rect.w),Math.abs(p.y-v.rect.y),Math.abs(p.y-v.rect.y-v.rect.h)]) assert(d>=12);
      });
      return regions.indexOf(v.target);
    }
    case 'code': {
      const decoded=v.row.map((icon)=>v.legend[icon]);
      assert.equal(new Set(Object.values(v.legend)).size,Object.values(v.legend).length);
      if(item.input==='numpad') return decoded;
      const matching=item.choices.map((c,i)=>c===decoded.join('')?i:null).filter((x)=>x!=null);
      assert.equal(matching.length,1);
      return matching[0];
    }
    case 'triSum': {
      assert.equal(max,20);
      assert.equal(v.sum,9); // a side target above 9 would require a carry with 1..6
      const sols=permutations.filter((p)=>{
        const [A,B,C,AB,BC,CA]=p;
        return A+AB+B===v.sum && B+BC+C===v.sum && C+CA+A===v.sum &&
          v.values.every((x,i)=>x==null||x===p[i]);
      });
      assert.equal(sols.length,1);
      return v.values.map((x,i)=>x==null?sols[0][i]:null).filter((x)=>x!=null);
    }
  }
  throw new Error('missing independent check '+item.type);
}
function inspectNumbers(value,max) {
  if (digits(value)) { assert(inRange(value,max)); return; }
  if (Array.isArray(value)) return value.forEach((x)=>inspectNumbers(x,max));
  if (value && typeof value==='object') Object.values(value).forEach((x)=>inspectNumbers(x,max));
}
for (const type of T.PHASE2_TYPES) for (const level of [2,3]) {
  test(`${type} level ${level}: ${N} independent items at both bands`, () => {
    const stats={ equal:0 };
    for(const max of (type==='triSum'?[20]:[10,20])) {
      const rng=T.mkRng((level*100+max)*17 + T.PHASE2_TYPES.indexOf(type));
      for(let i=0;i<N;i++) {
        const item=T.generate(type,level,rng,{max});
        assert.equal(item.type,type);
        assert.equal(item.level,level);
        assert(Array.isArray(item.explain)&&item.explain.length>=2);
        assert(item.explain[0].length>0);
        const expected=independent(item,max);
        assert(same(item.answer,expected), `${type} ${level} max=${max} #${i}: ${JSON.stringify(item)}`);
        inspectNumbers(item.visual.kind==='insideOut'?[]:item.answer,max);
        // No answer value is revealed verbatim by the first hint.
        const answerText=typeof expected==='string'?expected:(Array.isArray(expected)?expected.join(', '):String(expected));
        assert(!item.explain[0].includes(answerText));
        assert(item.explain.slice(1).join(' ').length>0);
        if(type==='compare'&&expected==='=') stats.equal++;
        if(type==='grid') assert(item.choices.every((x)=>!item.choices.some((y)=>x!==y&&tileEq(x,y))));
      }
    }
    if(type==='compare') assert(stats.equal>=N*.2);
  });
}
test('triSum is hidden from age 5–6 generation',()=>assert.throws(()=>T.generate('triSum',2,T.mkRng(1),{max:10})));
test('old type opts max 20 remains in range and avoids carry or borrow',()=>{
  const rng=T.mkRng(9182);
  for(const type of T.TYPES) for(const level of [2,3]) for(let i=0;i<100;i++){
    const x=T.generate(type,level,rng,{max:20});
    x.blanks.forEach((b)=>assert(b.answer>=0&&b.answer<=20));
    if(type==='chain') x.visual.ops.forEach((d,j)=>{
      const left=x.anim.values[j];assert(canStep(left,d>0?'+':'-',Math.abs(d)));
    });
    if(type==='pyramid') {
      const c=x.anim.cells;
      assert(canStep(c[0],'+',c[1])&&canStep(c[1],'+',c[2])&&canStep(c[3],'+',c[4]));
    }
    if(type==='balance') for(const side of [x.visual.left,x.visual.right]){
      const e=side.map((v)=>v==='□'?x.blanks[0].answer:v==='−'?'-':v);
      let acc=e[0];
      for(let j=1;j<e.length;j+=2){assert(canStep(acc,e[j],e[j+1])); acc=e[j]==='+'?acc+e[j+1]:acc-e[j+1];}
    }
    if(type==='sequence'&&x.rule==='step') x.anim.values.forEach((v,j)=>{
      if(j){const prev=x.anim.values[j-1];assert(canStep(prev,v>=prev?'+':'-',Math.abs(v-prev)));}
    });
  }
});
test('phase 2 viewport geometry stays within a 360 px phone',()=>{
  const fs=require('node:fs');
  const css=fs.readFileSync(require.resolve('../www/tuduy/tuduy-core').replace('tuduy-core.js','tuduy.css'),'utf8');
  const ui=fs.readFileSync(require.resolve('../www/tuduy/tuduy-core').replace('tuduy-core.js','tuduy-ui.js'),'utf8');
  assert.match(css,/\.td-region\{[^}]*width:min\(300px,95%\)/);
  assert.match(css,/\.td-tile-grid\{[^}]*width:min\(258px,90%\)/);
  assert.match(css,/\.td-triangle\{[^}]*width:min\(300px,90vw\)/);
  assert.match(css,/\.td-pattern\{[^}]*width:min\(100%,340px\)/);
  assert.match(css,/\.td-position \.td-choice\{[^}]*min-width:35px/);
  assert.match(css,/\.td-order-source \.td-choice\{[^}]*min-width:42px/);
  assert.match(ui,/viewBox="0 0 64 64"/); // grid and odd-one SVG tile
  assert.match(ui,/viewBox="0 0 260 220"/); // circle + square SVG
  assert.match(ui,/viewBox="0 0 300 250"/); // triangle SVG
  const c=/<circle cx="(\d+)" cy="(\d+)" r="(\d+)"/.exec(ui).slice(1).map(Number);
  assert(c[0]-c[2]>=0&&c[0]+c[2]<=64&&c[1]-c[2]>=0&&c[1]+c[2]<=64);
  const rect=/<rect x="(\d+)" y="(\d+)" width="(\d+)" height="(\d+)"/.exec(ui).slice(1).map(Number);
  assert(rect[0]>=0&&rect[0]+rect[2]<=64&&rect[1]>=0&&rect[1]+rect[3]<=64);
  const pts=JSON.parse(/const pts = (\[\[[^;]+\]);/.exec(ui)[1]);
  pts.forEach(([x,y])=>assert(x*3-23>=0&&x*3+23<=300&&y*2.5-23>=0&&y*2.5+23<=250));
  for(const viewport of [360,390,412]) {
    const content=viewport-24-28; // hub padding + card padding
    assert(7*35+6*5<=content); // widest position row
    assert(6*42+5*5<=content); // widest order row
    assert(Math.min(300,viewport*.9)<=content); // triangle figure
  }
});
