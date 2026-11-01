/**
 * Render del Plan de Estudio: hoja profesional (misma para la vista previa y
 * para imprimir), fórmulas con KaTeX ya convertidas a HTML y gráficas de
 * funciones dibujadas como SVG.
 *
 * Todo se hace sobre el TEXTO del plan ANTES de mostrarlo (no sobre el DOM ya
 * pintado), así las fórmulas no se "desrenderizan" cuando Angular vuelve a
 * pintar la vista o cuando se hace scroll.
 */

// ─────────────────────────────────────────────────────────────────────────
// Estilos de la hoja (una sola fuente de verdad: vista previa + impresión)
// ─────────────────────────────────────────────────────────────────────────
export const PLAN_PAPER_CSS = `
.plan-paper {
  --pp-ink: #1f2937; --pp-muted: #64748b; --pp-brand: #1e3a8a; --pp-accent: #2563eb; --pp-line: #dbe3f0;
  box-sizing: border-box; width: 210mm; max-width: 100%; min-height: 297mm; margin: 0 auto;
  padding: 16mm 17mm 18mm; background: #fff; color: var(--pp-ink);
  font-family: Georgia, 'Times New Roman', serif; font-size: 11.5pt; line-height: 1.6;
  box-shadow: 0 10px 40px rgba(15, 23, 42, .18), 0 0 0 1px rgba(15, 23, 42, .06);
}
.plan-paper *, .plan-paper *::before, .plan-paper *::after { box-sizing: border-box; }
.plan-paper .pp-head { display: flex; align-items: center; gap: 14px; padding-bottom: 12px; border-bottom: 3px double var(--pp-brand); }
.plan-paper .pp-logo { width: 62px; height: 62px; object-fit: contain; flex: none; }
.plan-paper .pp-inst { flex: 1; font-family: Arial, Helvetica, sans-serif; }
.plan-paper .pp-inst b { display: block; font-size: 10.5pt; letter-spacing: .6px; color: var(--pp-brand); text-transform: uppercase; }
.plan-paper .pp-inst span { font-size: 8.5pt; color: var(--pp-muted); }
.plan-paper .pp-doc { text-align: right; font-family: Arial, Helvetica, sans-serif; }
.plan-paper .pp-doc h1 { margin: 0; font-size: 19pt; color: var(--pp-brand); letter-spacing: .5px; text-transform: uppercase; line-height: 1.1; }
.plan-paper .pp-doc span { font-size: 8.5pt; color: var(--pp-muted); }
.plan-paper .pp-meta { display: grid; grid-template-columns: repeat(2, 1fr); margin: 14px 0 6px; border: 1px solid var(--pp-line); border-radius: 6px; overflow: hidden; font-family: Arial, Helvetica, sans-serif; }
.plan-paper .pp-meta div { padding: 7px 11px; font-size: 9.5pt; border-bottom: 1px solid var(--pp-line); background: #f8fafc; }
.plan-paper .pp-meta div:nth-child(odd) { border-right: 1px solid var(--pp-line); }
.plan-paper .pp-meta div:nth-last-child(-n+2) { border-bottom: 0; }
.plan-paper .pp-meta b { color: var(--pp-brand); margin-right: 4px; }
.plan-paper .plan-text { font-family: inherit; font-size: inherit; line-height: inherit; color: inherit; margin-top: 6px; }
.plan-paper .plan-text h2 { font-family: Arial, Helvetica, sans-serif; font-size: 12.5pt; letter-spacing: .6px; text-transform: uppercase; color: #fff; background: var(--pp-brand); margin: 20px 0 10px; padding: 5px 11px; border-radius: 3px; break-after: avoid; page-break-after: avoid; }
.plan-paper .plan-text h3 { font-family: Arial, Helvetica, sans-serif; font-size: 11.5pt; color: var(--pp-brand); margin: 14px 0 5px; padding-bottom: 2px; border-bottom: 1px solid var(--pp-line); break-after: avoid; page-break-after: avoid; }
.plan-paper .plan-text p { margin: 0 0 8px; text-align: justify; hyphens: auto; orphans: 3; widows: 3; }
.plan-paper .plan-text ul, .plan-paper .plan-text ol { margin: 4px 0 10px; padding-left: 22px; }
.plan-paper .plan-text li { margin: 0 0 5px; padding-left: 2px; break-inside: avoid; page-break-inside: avoid; }
.plan-paper .plan-text li::marker { color: var(--pp-accent); font-weight: 700; }
.plan-paper .plan-text strong { color: var(--pp-brand); }
.plan-paper .plan-text table { width: 100%; border-collapse: collapse; margin: 8px 0 12px; font-size: 10pt; }
.plan-paper .plan-text th, .plan-paper .plan-text td { border: 1px solid var(--pp-line); padding: 5px 8px; }
.plan-paper .plan-text th { background: #eef3fb; color: var(--pp-brand); }
.plan-paper .katex { font-size: 1.08em; }
.plan-paper .katex-display { margin: 8px 0; overflow-x: auto; overflow-y: hidden; break-inside: avoid; page-break-inside: avoid; }
.plan-paper .fn-figure { margin: 12px auto 14px; padding: 8px 10px 8px; border: 1px solid var(--pp-line); border-radius: 6px; background: #fcfdff; text-align: center; break-inside: avoid; page-break-inside: avoid; }
.plan-paper .fn-figure svg { width: 100%; height: auto; max-width: 520px; display: block; margin: 0 auto; }
.plan-paper .fn-figure figcaption { font-family: Arial, Helvetica, sans-serif; font-size: 9pt; color: var(--pp-muted); margin-top: 4px; }
.plan-paper .fn-legend { display: flex; flex-wrap: wrap; gap: 4px 16px; justify-content: center; margin-top: 4px; font-size: 10pt; color: var(--pp-ink); }
.plan-paper .fn-legend i { display: inline-block; width: 18px; height: 3px; border-radius: 2px; margin-right: 6px; vertical-align: middle; }
.plan-paper .pp-foot { margin-top: 22px; padding-top: 8px; border-top: 1px solid var(--pp-line); font-family: Arial, Helvetica, sans-serif; font-size: 8pt; color: var(--pp-muted); text-align: center; }
@media screen and (max-width: 820px) { .plan-paper { padding: 22px 18px; min-height: 0; font-size: 11pt; } .plan-paper .pp-meta { grid-template-columns: 1fr; } .plan-paper .pp-meta div:nth-child(odd) { border-right: 0; } .plan-paper .pp-meta div:nth-last-child(-n+2) { border-bottom: 1px solid var(--pp-line); } .plan-paper .pp-meta div:last-child { border-bottom: 0; } .plan-paper .pp-head { flex-wrap: wrap; } .plan-paper .pp-doc { text-align: left; } }
@media print { .plan-paper { box-shadow: none; width: auto; min-height: 0; padding: 0; } }
`;

/** Inserta una sola vez los estilos de la hoja en la página (vista previa). */
export function ensurePlanPaperStyles(): void {
  if (typeof document === 'undefined') return;
  if (document.getElementById('plan-paper-style')) return;
  const st = document.createElement('style');
  st.id = 'plan-paper-style';
  st.textContent = PLAN_PAPER_CSS;
  document.head.appendChild(st);
}

// ─────────────────────────────────────────────────────────────────────────
// Fórmulas: texto → HTML de KaTeX
// ─────────────────────────────────────────────────────────────────────────
const MATH_RE = /\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\(([\s\S]+?)\\\)|\$([^$\n]+?)\$/g;
// LaTeX "suelto" (sin delimitadores): \int_0^1 x^2\,dx, f'(x)=\frac{1}{2}x, etc.
const BARE_RE = /[A-Za-z0-9()'^_+\-=*\/]*\\(?:int|iint|oint|sum|prod|lim|frac|dfrac|sqrt|infty|partial|cdot|times|div|pm|leq|geq|neq|approx|sin|cos|tan|ln|log|pi|theta|alpha|beta|Delta|to|rightarrow|vec|bar|hat)(?![a-zA-Z])(?:\{(?:[^{}]|\{[^{}]*\})*\}|[\^_](?:\{(?:[^{}]|\{[^{}]*\})*\}|[A-Za-z0-9])|\\[,;!]|\\[a-zA-Z]+|[A-Za-z0-9()'^_+\-=*\/]| (?=(?:[A-Za-z0-9]{1,2}(?![A-Za-z0-9])|[A-Za-z0-9()]*[\^_\\])))*/g;

function katexOf(): any {
  const k = (typeof window !== 'undefined') ? (window as any).katex : null;
  return k && typeof k.renderToString === 'function' ? k : null;
}

function texToHtml(katex: any, tex: string, display: boolean): string | null {
  try {
    return katex.renderToString(tex.trim(), { displayMode: display, throwOnError: false, strict: false, trust: true });
  } catch {
    return null;
  }
}

function mathInText(katex: any, text: string): DocumentFragment | null {
  if (!/[$\\]/.test(text)) return null;
  const tpl = document.createElement('template');
  let changed = false;
  const pushText = (out: string[], s: string) => out.push(escapeHtml(s));

  const out: string[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  MATH_RE.lastIndex = 0;
  const piece = (s: string) => {
    // Dentro del texto sin delimitadores, buscar LaTeX suelto.
    let l = 0; let b: RegExpExecArray | null;
    BARE_RE.lastIndex = 0;
    while ((b = BARE_RE.exec(s)) !== null) {
      const html = texToHtml(katex, b[0], false);
      if (html) { pushText(out, s.slice(l, b.index)); out.push(html); l = b.index + b[0].length; changed = true; }
    }
    pushText(out, s.slice(l));
  };
  while ((m = MATH_RE.exec(text)) !== null) {
    piece(text.slice(last, m.index));
    const display = m[1] !== undefined || m[2] !== undefined;
    const tex = (m[1] ?? m[2] ?? m[3] ?? m[4]) as string;
    const html = texToHtml(katex, tex, display);
    if (html) { out.push(html); changed = true; } else { pushText(out, m[0]); }
    last = m.index + m[0].length;
  }
  piece(text.slice(last));
  if (!changed) return null;
  tpl.innerHTML = out.join('');
  return tpl.content;
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function renderMathIn(root: HTMLElement): void {
  const katex = katexOf();
  if (!katex) return;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes: Text[] = [];
  let n: Node | null;
  while ((n = walker.nextNode())) {
    const el = (n as Text).parentElement;
    if (!el || el.closest('.katex, .katex-display, script, style, pre, code, svg')) continue;
    nodes.push(n as Text);
  }
  for (const t of nodes) {
    const frag = mathInText(katex, t.nodeValue || '');
    if (frag) t.parentNode?.replaceChild(frag, t);
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Evaluador seguro de funciones f(x) (sin eval)
// ─────────────────────────────────────────────────────────────────────────
type Fn = (x: number) => number;

const FUNCS: Record<string, (v: number) => number> = {
  sin: Math.sin, cos: Math.cos, tan: Math.tan, asin: Math.asin, acos: Math.acos, atan: Math.atan,
  sqrt: Math.sqrt, abs: Math.abs, ln: Math.log, log: Math.log10, log10: Math.log10, exp: Math.exp,
  floor: Math.floor, ceil: Math.ceil, sinh: Math.sinh, cosh: Math.cosh, tanh: Math.tanh
};

export function compileFn(src: string): Fn | null {
  const s = src.replace(/\*\*/g, '^').replace(/·|×/g, '*').replace(/÷/g, '/').replace(/\s+/g, '').toLowerCase();
  const tokens: { t: 'n' | 'x' | 'f' | 'o'; v: string | number }[] = [];
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    if (/[0-9.]/.test(c)) {
      let j = i; while (j < s.length && /[0-9.]/.test(s[j])) j++;
      const v = parseFloat(s.slice(i, j)); if (isNaN(v)) return null;
      tokens.push({ t: 'n', v }); i = j;
    } else if (/[a-z]/.test(c)) {
      let j = i; while (j < s.length && /[a-z0-9]/.test(s[j])) j++;
      let word = s.slice(i, j);
      if (word === 'x') { tokens.push({ t: 'x', v: 'x' }); i = j; }
      else if (word === 'pi') { tokens.push({ t: 'n', v: Math.PI }); i = j; }
      else if (word === 'e' && s[j] !== '(') { tokens.push({ t: 'n', v: Math.E }); i = j; }
      else if (FUNCS[word] && s[j] === '(') { tokens.push({ t: 'f', v: word }); i = j; }
      else if (word.length > 1 && /^[0-9]*x$/.test(word)) { tokens.push({ t: 'n', v: parseFloat(word) || 1 }); tokens.push({ t: 'x', v: 'x' }); i = j; }
      else if (word.length > 1 && word.startsWith('x')) { tokens.push({ t: 'x', v: 'x' }); i = i + 1; }
      else return null;
    } else if ('+-*/^(),'.includes(c)) { tokens.push({ t: 'o', v: c }); i++; }
    else return null;
  }
  // multiplicación implícita: 2x, 3(x+1), )(, x( , 2sin(x)
  const t2: typeof tokens = [];
  for (let k = 0; k < tokens.length; k++) {
    const a = t2[t2.length - 1]; const b = tokens[k];
    const aEnds = a && (a.t === 'n' || a.t === 'x' || (a.t === 'o' && a.v === ')'));
    const bStarts = b.t === 'n' || b.t === 'x' || b.t === 'f' || (b.t === 'o' && b.v === '(');
    if (aEnds && bStarts) t2.push({ t: 'o', v: '*' });
    t2.push(b);
  }
  let p = 0;
  const peek = () => t2[p];
  const eat = () => t2[p++];
  type Node = (x: number) => number;
  const parseExpr = (): Node | null => {
    let left: Node | null = parseTerm(); if (!left) return null;
    while (peek() && peek().t === 'o' && (peek().v === '+' || peek().v === '-')) {
      const op = eat().v; const right = parseTerm(); if (!right) return null;
      const l: Node = left; const r: Node = right;
      left = op === '+' ? (x: number) => l(x) + r(x) : (x: number) => l(x) - r(x);
    }
    return left;
  };
  const parseTerm = (): Node | null => {
    let left: Node | null = parseUnary(); if (!left) return null;
    while (peek() && peek().t === 'o' && (peek().v === '*' || peek().v === '/')) {
      const op = eat().v; const right = parseUnary(); if (!right) return null;
      const l: Node = left; const r: Node = right;
      left = op === '*' ? (x: number) => l(x) * r(x) : (x: number) => l(x) / r(x);
    }
    return left;
  };
  const parseUnary = (): Node | null => {
    if (peek() && peek().t === 'o' && (peek().v === '-' || peek().v === '+')) {
      const op = eat().v; const inner = parseUnary(); if (!inner) return null;
      return op === '-' ? (x) => -inner(x) : inner;
    }
    return parsePow();
  };
  const parsePow = (): Node | null => {
    const base = parseAtom(); if (!base) return null;
    if (peek() && peek().t === 'o' && peek().v === '^') {
      eat(); const exp = parseUnary(); if (!exp) return null; // derecha-asociativa
      return (x) => Math.pow(base(x), exp(x));
    }
    return base;
  };
  const parseAtom = (): Node | null => {
    const tk = eat(); if (!tk) return null;
    if (tk.t === 'n') { const v = tk.v as number; return () => v; }
    if (tk.t === 'x') return (x) => x;
    if (tk.t === 'f') {
      const f = FUNCS[tk.v as string];
      const open = eat(); if (!open || open.v !== '(') return null;
      const inner = parseExpr(); const close = eat();
      if (!inner || !close || close.v !== ')') return null;
      return (x) => f(inner(x));
    }
    if (tk.t === 'o' && tk.v === '(') {
      const inner = parseExpr(); const close = eat();
      if (!inner || !close || close.v !== ')') return null;
      return inner;
    }
    return null;
  };
  const root = parseExpr();
  if (!root || p !== t2.length) return null;
  return root;
}

// ─────────────────────────────────────────────────────────────────────────
// Gráficas: <div class="fn-plot" data-fn="x^2;2x" data-label="f(x)=x^2;f'(x)=2x" ...>
// ─────────────────────────────────────────────────────────────────────────
const COLORS = ['#2563eb', '#dc2626', '#16a34a', '#d97706', '#7c3aed'];

function niceStep(range: number, target: number): number {
  const raw = range / target;
  const pow = Math.pow(10, Math.floor(Math.log10(raw)));
  const f = raw / pow;
  return (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * pow;
}

function fmtTick(v: number): string {
  const r = Math.abs(v) < 1e-9 ? 0 : v;
  return String(parseFloat(r.toFixed(4)));
}

function buildPlot(el: Element, katex: any): string | null {
  const fnSrc = (el.getAttribute('data-fn') || '').split(';').map(s => s.trim()).filter(Boolean).slice(0, 4);
  const fns = fnSrc.map(compileFn);
  if (!fnSrc.length || fns.some(f => !f)) return null;
  const labels = (el.getAttribute('data-label') || '').split(';').map(s => s.trim());
  const caption = el.getAttribute('data-caption') || '';
  let xmin = parseFloat(el.getAttribute('data-xmin') || '-5');
  let xmax = parseFloat(el.getAttribute('data-xmax') || '5');
  if (!isFinite(xmin) || !isFinite(xmax) || xmin >= xmax) { xmin = -5; xmax = 5; }

  const N = 480;
  const series: { x: number; y: number }[][] = fns.map(f => {
    const pts: { x: number; y: number }[] = [];
    for (let i = 0; i <= N; i++) {
      const x = xmin + (xmax - xmin) * i / N;
      let y = (f as Fn)(x);
      if (!isFinite(y) || Math.abs(y) > 1e6) y = NaN;
      pts.push({ x, y });
    }
    return pts;
  });

  let ymin = parseFloat(el.getAttribute('data-ymin') || 'NaN');
  let ymax = parseFloat(el.getAttribute('data-ymax') || 'NaN');
  if (!isFinite(ymin) || !isFinite(ymax) || ymin >= ymax) {
    const ys = series.flat().map(p => p.y).filter(v => isFinite(v)).sort((a, b) => a - b);
    if (!ys.length) return null;
    const lo = ys[Math.floor(ys.length * 0.02)], hi = ys[Math.ceil(ys.length * 0.98) - 1];
    const pad = ((hi - lo) || 2) * 0.12;
    ymin = lo - pad; ymax = hi + pad;
  }

  const W = 520, H = 300, ml = 38, mr = 14, mt = 14, mb = 28;
  const sx = (x: number) => ml + (x - xmin) / (xmax - xmin) * (W - ml - mr);
  const sy = (y: number) => H - mb - (y - ymin) / (ymax - ymin) * (H - mt - mb);
  const clampY = (y: number) => Math.max(mt - 40, Math.min(H - mb + 40, y));

  let svg = `<svg viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Gráfica de función" font-family="Arial, Helvetica, sans-serif">`;
  svg += `<rect x="${ml}" y="${mt}" width="${W - ml - mr}" height="${H - mt - mb}" fill="#ffffff" stroke="#cbd5e1"/>`;
  svg += `<defs><clipPath id="cp${Math.abs(hashCode(fnSrc.join()))}"><rect x="${ml}" y="${mt}" width="${W - ml - mr}" height="${H - mt - mb}"/></clipPath></defs>`;
  const cid = `cp${Math.abs(hashCode(fnSrc.join()))}`;

  const xs = niceStep(xmax - xmin, 8), ys2 = niceStep(ymax - ymin, 6);
  for (let v = Math.ceil(xmin / xs) * xs; v <= xmax + 1e-9; v += xs) {
    svg += `<line x1="${sx(v)}" y1="${mt}" x2="${sx(v)}" y2="${H - mb}" stroke="#e2e8f0" stroke-width="1"/>`;
    svg += `<text x="${sx(v)}" y="${H - mb + 14}" font-size="10" fill="#475569" text-anchor="middle">${fmtTick(v)}</text>`;
  }
  for (let v = Math.ceil(ymin / ys2) * ys2; v <= ymax + 1e-9; v += ys2) {
    svg += `<line x1="${ml}" y1="${sy(v)}" x2="${W - mr}" y2="${sy(v)}" stroke="#e2e8f0" stroke-width="1"/>`;
    svg += `<text x="${ml - 5}" y="${sy(v) + 3.5}" font-size="10" fill="#475569" text-anchor="end">${fmtTick(v)}</text>`;
  }
  if (ymin < 0 && ymax > 0) svg += `<line x1="${ml}" y1="${sy(0)}" x2="${W - mr}" y2="${sy(0)}" stroke="#334155" stroke-width="1.4"/>`;
  if (xmin < 0 && xmax > 0) svg += `<line x1="${sx(0)}" y1="${mt}" x2="${sx(0)}" y2="${H - mb}" stroke="#334155" stroke-width="1.4"/>`;

  // Área bajo la primera curva (integrales): data-area="a,b"
  const area = (el.getAttribute('data-area') || '').split(',').map(parseFloat);
  if (area.length === 2 && isFinite(area[0]) && isFinite(area[1]) && area[0] < area[1]) {
    const pts = series[0].filter(p => p.x >= area[0] && p.x <= area[1] && isFinite(p.y));
    if (pts.length > 1) {
      const y0 = sy(Math.max(ymin, Math.min(ymax, 0)));
      const d = `M ${sx(pts[0].x)} ${y0} ` + pts.map(p => `L ${sx(p.x)} ${clampY(sy(p.y))}`).join(' ') + ` L ${sx(pts[pts.length - 1].x)} ${y0} Z`;
      svg += `<path d="${d}" fill="${COLORS[0]}" fill-opacity=".18" stroke="none" clip-path="url(#${cid})"/>`;
    }
  }

  series.forEach((pts, idx) => {
    let d = ''; let pen = false;
    for (const p of pts) {
      if (!isFinite(p.y)) { pen = false; continue; }
      d += `${pen ? 'L' : 'M'} ${sx(p.x).toFixed(1)} ${clampY(sy(p.y)).toFixed(1)} `;
      pen = true;
    }
    svg += `<path d="${d}" fill="none" stroke="${COLORS[idx % COLORS.length]}" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round" clip-path="url(#${cid})"/>`;
  });
  svg += `<text x="${W - mr}" y="${sy(0) > H - mb || sy(0) < mt ? H - mb - 4 : sy(0) - 5}" font-size="11" fill="#334155" text-anchor="end" font-style="italic">x</text>`;
  svg += `<text x="${(xmin < 0 && xmax > 0 ? sx(0) : ml) + 6}" y="${mt + 11}" font-size="11" fill="#334155" font-style="italic">y</text>`;
  svg += `</svg>`;

  const legend = fnSrc.map((src, idx) => {
    const lab = labels[idx] || `f(x)=${src}`;
    const html = katex ? (texToHtml(katex, lab.replace(/\*/g, '\\cdot '), false) || escapeHtml(lab)) : escapeHtml(lab);
    return `<span><i style="background:${COLORS[idx % COLORS.length]}"></i>${html}</span>`;
  }).join('');
  return `<figure class="fn-figure">${svg}<div class="fn-legend">${legend}</div>${caption ? `<figcaption>${escapeHtml(caption)}</figcaption>` : ''}</figure>`;
}

function hashCode(s: string): number {
  let h = 0; for (let i = 0; i < s.length; i++) { h = (h << 5) - h + s.charCodeAt(i); h |= 0; } return h;
}

// ─────────────────────────────────────────────────────────────────────────
// API principal
// ─────────────────────────────────────────────────────────────────────────

/** Convierte el HTML del plan: gráficas → SVG y fórmulas → HTML de KaTeX. */
export function renderPlanHtml(html: string): string {
  if (!html || typeof document === 'undefined') return html;
  try {
    const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
    const body = doc.body;
    const katex = katexOf();

    // 1) Gráficas (si el modelo no las pudo armar, se quita el marcador)
    body.querySelectorAll('.fn-plot, [data-fn]').forEach(el => {
      const fig = buildPlot(el, katex);
      if (fig) {
        const tpl = document.createElement('template'); tpl.innerHTML = fig;
        el.replaceWith(tpl.content);
      } else {
        el.remove();
      }
    });

    // 2) Fórmulas (solo cuando KaTeX ya cargó; si no, queda el flujo anterior)
    renderMathIn(body as unknown as HTMLElement);
    return body.innerHTML;
  } catch {
    return html;
  }
}
