// Setzt echte Geräte-Screenshots (raw/*.png) in Store-Bilder 1080×1920 mit Bildunterschrift.
// Nötig, weil Play höchstens 2:1 erlaubt – der Pixel 9 Pro XL liefert ~20:9.
// Aufruf: node frame.mjs [de|en]   → out/<lang>/*.png
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, existsSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const lang = process.argv[2] || 'de';
const shots = JSON.parse(readFileSync(join(here, 'captions.json'), 'utf8'));
const fonts = pathToFileURL(join(here, '..', 'fonts')).href;
const ENSO = 'M61.74,32.73L60.75,31.17L59.48,30.15L58.09,29.31L56.61,28.62L55.06,28.07L53.45,27.67L51.82,27.65L50.20,27.85L48.61,28.18L47.04,28.63L45.52,29.18L44.04,29.84L42.62,30.59L41.25,31.43L39.94,32.34L38.68,33.32L37.48,34.36L36.34,35.46L35.26,36.61L34.24,37.80L33.27,39.05L32.36,40.34L31.51,41.67L30.73,43.05L30.03,44.48L29.40,45.94L28.86,47.45L28.42,49.00L28.08,50.57L27.85,52.17L27.74,53.79L27.74,55.41L27.87,57.02L28.12,58.63L28.49,60.20L28.99,61.74L29.59,63.24L30.30,64.68L31.11,66.07L32.01,67.39L32.98,68.64L34.04,69.82L35.15,70.93L36.32,71.97L37.54,72.93L38.81,73.83L40.11,74.65L41.45,75.39L42.82,76.07L44.22,76.67L45.65,77.20L47.10,77.65L48.57,78.02L50.06,78.31L51.57,78.51L53.08,78.63L54.60,78.65L56.12,78.59L57.63,78.36L59.10,77.97L60.53,77.50L61.93,76.95L63.30,76.33L64.62,75.64L65.89,74.88L67.12,74.05L68.30,73.16L69.43,72.22L70.50,71.21L71.51,70.14L72.46,69.02L73.33,67.84L74.12,66.61L74.84,65.33L75.46,64.01L75.99,62.65L76.43,61.26L76.77,59.85L77.01,58.42L77.14,56.98L77.18,55.54L77.13,54.11L76.98,52.69L76.75,51.29L76.44,49.91L76.05,48.56L75.59,47.23L75.07,45.94L74.48,44.68L73.85,43.45A0.80,0.80 0 0 0 72.44,44.20L72.96,45.37L73.40,46.58L73.76,47.81L74.05,49.05L74.25,50.31L74.37,51.57L74.41,52.84L74.37,54.09L74.25,55.35L74.05,56.58L73.78,57.80L73.42,58.99L72.99,60.14L72.48,61.27L71.89,62.34L71.23,63.37L70.50,64.34L69.70,65.24L68.85,66.08L67.93,66.84L66.97,67.52L65.96,68.13L64.93,68.64L63.86,69.08L62.79,69.43L61.70,69.70L60.62,69.90L59.54,70.03L58.48,70.10L57.43,70.12L56.40,70.10L55.39,70.11L54.39,70.16L53.40,70.18L52.40,70.16L51.39,70.12L50.38,70.03L49.36,69.90L48.34,69.72L47.32,69.49L46.31,69.19L45.30,68.82L44.32,68.38L43.37,67.87L42.45,67.28L41.58,66.62L40.77,65.89L40.01,65.09L39.31,64.23L38.69,63.32L38.14,62.36L37.66,61.37L37.25,60.34L36.92,59.29L36.65,58.22L36.45,57.14L36.31,56.05L36.24,54.95L36.23,53.86L36.27,52.76L36.38,51.67L36.54,50.58L36.76,49.51L37.04,48.45L37.38,47.40L37.78,46.37L38.25,45.37L38.78,44.39L39.38,43.45L40.03,42.56L40.75,41.70L41.53,40.90L42.35,40.15L43.23,39.46L44.15,38.84L45.11,38.27L46.11,37.77L47.13,37.34L48.18,36.97L49.25,36.66L50.33,36.42L51.42,36.24L52.52,36.13L53.62,35.98L54.75,35.66L55.91,35.38L57.12,35.15L58.37,34.96L59.68,34.78L61.19,34.23A0.80,0.80 0 0 0 61.74,32.73Z';
const outDir = join(here, 'out', lang);
mkdirSync(outDir, { recursive: true });

const page = (img, caption) => `<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{font-family:C;src:url(${fonts}/CormorantGaramond-Light.ttf)}
@font-face{font-family:I;src:url(${fonts}/Inter-Light.ttf)}
html,body{margin:0;width:1080px;height:1920px;background:#15130F;overflow:hidden}
.enso{position:absolute;left:50%;top:70px;width:64px;margin-left:-32px;opacity:.9}
h1{position:absolute;top:150px;left:60px;right:60px;margin:0;text-align:center;
   font:300 76px/1.1 C,serif;color:#F2EDE4;letter-spacing:.01em}
.shot{position:absolute;left:50%;bottom:0;transform:translateX(-50%);height:1560px;
   border-radius:48px 48px 0 0;border:3px solid #2E2A24;border-bottom:0;overflow:hidden;background:#000}
.shot img{height:100%;display:block}
</style></head><body>
<svg class="enso" viewBox="0 0 108 108"><path fill="#F2EDE4" d="${ENSO}"/></svg>
<h1>${caption}</h1>
<div class="shot"><img src="${img}"></div>
</body></html>`;

const browser = await chromium.launch();
const p = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
let n = 0;
for (const s of shots) {
  const src = join(here, 'raw', s.file);
  if (!existsSync(src)) { console.log('fehlt:', s.file); continue; }
  // Über eine Datei laden – setContent (about:blank) darf keine file://-Bilder und Schriften nachladen.
  const tmp = join(outDir, '.frame.html');
  writeFileSync(tmp, page(pathToFileURL(src).href, s[lang] || s.de));
  await p.goto(pathToFileURL(tmp).href, { waitUntil: 'load' });
  rmSync(tmp);
  await p.evaluate(() => document.fonts.ready);
  await p.screenshot({ path: join(outDir, s.file) });
  n++;
}
await browser.close();
console.log(`${n} Bilder → ${outDir}`);
