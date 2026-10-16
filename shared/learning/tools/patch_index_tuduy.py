"""Idempotent patch that adds the 5–6 band and the Luyện tư duy module to index.html.
Works on both main's index.html and the audit (P0-gate) version. Usage: python3 patch_index_tuduy.py <index.html>"""
import re, sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
if 'data-game="tuduy"' in s:
    print('already patched'); sys.exit(0)

def rep(old, new):
    global s
    if old not in s: raise SystemExit('anchor missing: ' + old[:70])
    s = s.replace(old, new, 1)

rep('<link rel="apple-touch-icon" href="icons/icon-192.png">',
    '<link rel="apple-touch-icon" href="icons/icon-192.png">\n<link rel="stylesheet" href="tuduy/tuduy.css">')
rep('<span class="age-em">🎈</span>4–5 tuổi</button>',
    '<span class="age-em">🎈</span>4–5 tuổi</button>\n'
    '        <button class="age-chip" data-age="5-6" aria-pressed="false" onclick="setAge(\'5-6\')">\n'
    '          <span class="age-em">🌟</span>5–6 tuổi</button>')
rep('    <div class="subject-section" id="examSection">', '''    <div class="subject-section" id="thinkSection">
      <div class="subject-title grade2"><span class="em">🧠</span> Luyện tư duy · Thinking Puzzles</div>
      <div class="menu">
        <button class="tile c25" data-game="tuduy" onclick="go('tuduy')">
          <div class="ic">🧠</div>
          <div><div class="t-vi">Luyện tư duy</div><div class="t-en">Tháp số, ô trống, quy luật, đếm hình</div></div>
        </button>
      </div>
    </div>
    <div class="subject-section" id="examSection">''')
rep('  <div id="exam" class="screen">', '''  <div id="tuduy" class="screen">
    <div class="scr-head"><button class="back" onclick="go('home')">← Về</button>
      <div class="scr-title">Luyện tư duy <small>Chơi mà nghĩ, nghĩ mà chơi</small></div></div>
    <div class="card" id="tdChooser">
      <div class="qprompt">Hôm nay con muốn giải đố gì?</div>
      <div class="td-intro">Làm sai cũng không sao: con được thử lại và xem cách làm.</div>
      <div class="td-pickgrid" id="tdPick"></div>
    </div>
    <div class="card" id="tdQuiz" hidden>
      <div class="td-top"><span id="tdCount"></span><span id="tdStars"></span></div>
      <div class="td-prompt"><span id="tdPrompt"></span><button class="td-say" onclick="TuDuyUI.speakPrompt()" aria-label="Nghe đề">🔊</button></div>
      <div class="td-visual" id="tdVisual"></div>
      <div class="td-pad" id="tdPad" role="group" aria-label="Bàn phím số"></div>
      <div class="td-actions"><button class="rbtn next" id="tdCheck" onclick="TuDuyUI.check()">✔ Xong rồi</button>
        <button class="rbtn next" id="tdNext" onclick="TuDuyUI.next()" hidden>Câu tiếp ▶</button></div>
      <div class="fb" id="tdFb" role="status" aria-live="polite"></div>
      <div class="td-explain" id="tdExplain" hidden></div>
    </div>
    <div class="card td-done" id="tdDone" hidden>
      <div class="td-big" id="tdDoneStars"></div>
      <div class="qprompt" id="tdDoneText"></div>
      <div class="td-actions"><button class="rbtn next" onclick="TuDuyUI.open()">Chơi tiếp 🧠</button>
        <button class="rbtn prev" onclick="go('home')">Về trang chính</button></div>
    </div>
  </div>

  <div id="exam" class="screen">''')

# 5–6 band: derived from the 4–5 band, minus exam/pre-Grade-1 reading, plus tuduy first.
m = re.search(r"  '4-5':\{\n    label:'4–5 tuổi',\n(?:    /\*[\s\S]*?\*/\n)?    visible:\[([^\]]*)\],", s)
if not m: raise SystemExit('4-5 band not found')
base = [x.strip().strip("'") for x in m.group(1).split(',') if x.strip()]
withheld = {'exam', 'vna', 'vph', 'vread'}
v56 = ['tuduy'] + [g for g in base if g not in withheld and g != 'tuduy']
band = ("  '5-6':{\n    label:'5–6 tuổi',\n"
        "    /* Mẫu giáo lớn. `tuduy` (Luyện tư duy) starts here: puzzles within 10, no exam framing.\n"
        "       `exam`, `vna`, `vph`, `vread` are not shown in this band. */\n"
        "    visible:[" + ",".join("'" + g + "'" for g in v56) + "],\n"
        "    profile:{\n"
        "      icon:'🌟',description:'Mẫu giáo lớn: số đến 20, giải đố tư duy, quy luật và khám phá.',\n"
        "      numberMax:20,additionMax:10,subtractionMax:10,compareMax:20,countMax:20,sequenceMax:20,bondMax:10,\n"
        "      defaultMax:10,questionTotal:5,ranges:[10,20],questionChoices:[5,8,10]\n"
        "    }\n  },\n")
i = s.index("  l1:{\n    label:'Lớp 1',")
s = s[:i] + band + s[i:]
for lab in ["label:'Lớp 1'", "label:'Lớp 2'", "label:'Lớp 3'"]:
    j = s.index(lab); k = s.index("visible:['", j)
    s = s[:k] + "visible:['tuduy','" + s[k + len("visible:['"):]
rep("  if(id==='exam') openExam();", "  if(id==='tuduy'&&window.TuDuyUI) TuDuyUI.open();\n  if(id==='exam') openExam();")
k = s.rindex('</body>')
s = s[:k] + '<script src="tuduy/tuduy-core.js"></script>\n<script src="tuduy/tuduy-ui.js"></script>\n' + s[k:]
open(p, 'w', encoding='utf-8').write(s)
print('patched; 5-6 visible =', v56)
