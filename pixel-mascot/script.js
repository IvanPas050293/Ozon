/* ============================================================
 * Clawd — пиксельный маскот с подсказками про Claude Code
 * Файлы: index.html, styles.css, script.js (без сборки)
 * ============================================================ */

"use strict";

/* ------------------------------------------------------------------
 * 1. Подсказки (30 шт). Каждая — одна строка, не длиннее 140 символов.
 *    Команды сверены с официальной документацией Claude Code.
 * ------------------------------------------------------------------ */
const TIPS = [
  "shift+tab переключает режимы разрешений: default → acceptEdits → plan и другие.",
  "! npm test — shell-режим: команда выполнится сразу, а её вывод попадёт в контекст.",
  "@src/utils/auth.js — @-упоминание файла добавит его в контекст без ожидания.",
  "ctrl+r — обратный поиск по истории промптов: начните печатать и подхватите прошлый запрос.",
  "Вставьте скриншот бага через ctrl+v (на Windows — alt+v): Claude изучит картинку.",
  "esc прерывает ответ, не отменяя сделанное: Claude остановится и сохранит прогресс.",
  "Двойной esc на пустом вводе открывает rewind-меню: откат кода и диалога к чекпоинту.",
  "ctrl+b — перевести долгую bash-команду в фон и продолжить общение без блокировки.",
  "ctrl+o — транскрипт: увидите все вызовы инструментов, включая MCP, с таймстампами.",
  "alt+p (Windows/Linux) — сменить модель в текущей сессии, не очищая промпт.",
  "alt+t — extended thinking: глубокая цепочка рассуждений для сложных задач.",
  "ctrl+t — чек-лист задач: Claude ведёт to-do список по многошаговым планам.",
  "Не уверены в актуальных данных? Claude сам запустит WebSearch и сошлётся на источники.",
  "/compact — освободить контекст: Claude резюмирует диалог и продолжит с того же места.",
  "/clear — новое сообщение с чистого контекста. Прошлый диалог вернётся через /resume.",
  "/init — создать CLAUDE.md: файл памяти проекта, который читает каждая сессия.",
  "/model — выбрать модель и уровень усилий (effort) для текущей сессии.",
  "/config — меню настроек: тема, редактор, суффиксы, режимы и опции сессии.",
  "/permissions — правила одобрения: что спрашивать, а что разрешать без подтверждений.",
  "/memory — CLAUDE.md и auto memory: закрепите правила, конвенции и личные предпочтения.",
  "/review — проверить диф на баги и чистоту кода. Уровни от low до max, есть --fix.",
  "/plan — plan mode: Claude предложит план изменений и не тронет файлы до вашего OK.",
  "/usage (алиас /cost) — расход токенов и стоимость текущей сессии.",
  "/resume — вернуться к прошлому диалогу. claude --continue продолжит последнюю сессию.",
  "/rewind — откатиться к чекпоинту: вернуть код и диалог в состояние до ошибки.",
  "/mcp — подключить MCP-серверы (GitHub, Sentry, базы данных) и расширить возможности.",
  "/hooks — скрипты на события Claude Code: форматирование, проверки, авто-одобрение.",
  "/subtask — поручить субагенту побочную задачу: отдельный контекст, краткий отчёт обратно.",
  "/btw вопрос — быстрый ответ без записи в историю и без прерывания текущей работы.",
  "claude -p \"промпт\" — неинтерактивный режим для CI и скриптов: stdout работает как в юниксе.",
];

/* ------------------------------------------------------------------
 * 2. Элементы страницы
 * ------------------------------------------------------------------ */
const mascot = document.getElementById("mascot");
const tipEl = document.getElementById("tip");
const counterEl = document.getElementById("count");
const STORAGE_KEY = "clawd-tips-opened";

/* ------------------------------------------------------------------
 * 3. Счётчик открытых подсказок (localStorage)
 * ------------------------------------------------------------------ */
let opened = 0;
try {
  opened = parseInt(localStorage.getItem(STORAGE_KEY), 10) || 0;
} catch (e) {
  /* localStorage недоступен (например, file://) — работаем в памяти */
}
function renderCount() {
  counterEl.textContent = opened;
}
renderCount();

function bumpCount() {
  opened += 1;
  renderCount();
  try {
    localStorage.setItem(STORAGE_KEY, String(opened));
  } catch (e) {
    /* ignore */
  }
}

/* ------------------------------------------------------------------
 * 4. Выбор подсказки: одна и та же подряд не выпадает
 * ------------------------------------------------------------------ */
let lastIndex = -1;

function pickTip() {
  let i;
  do {
    i = Math.floor(Math.random() * TIPS.length);
  } while (i === lastIndex);
  lastIndex = i;
  return TIPS[i];
}

/* ------------------------------------------------------------------
 * 5. Показ подсказки в точке клика
 * ------------------------------------------------------------------ */
let hideTimer = null;
const TIP_LIFETIME = 4000; // мс

function showTipAt(x, y) {
  const text = pickTip();
  tipEl.textContent = text;

  // Показываем, затем корректируем позицию, чтобы не вылезать за край
  tipEl.hidden = false;
  const pad = 10;
  const left = Math.max(pad, Math.min(x, window.innerWidth - tipEl.offsetWidth - pad));
  const top = Math.max(pad, Math.min(y, window.innerHeight - tipEl.offsetHeight - pad));
  tipEl.style.left = left + "px";
  tipEl.style.top = top + "px";

  bumpCount();

  clearTimeout(hideTimer);
  hideTimer = setTimeout(() => {
    tipEl.hidden = true;
  }, TIP_LIFETIME);
}

document.addEventListener("click", (e) => {
  showTipAt(e.clientX, e.clientY);
});

/* ------------------------------------------------------------------
 * 6. Маскот: следует за курсором, при простое — плавает по экрану
 * ------------------------------------------------------------------ */
const IDLE_MS = 2500; // сколько без движения мыши до режима «плавания»
const FOLLOW_LERP = 0.25; // скорость догона курсора
const WANDER_SPEED = 0.9; // px/кадр в режиме плавания

const state = {
  mode: "wander", // "follow" | "wander"
  cursor: { x: window.innerWidth / 2, y: window.innerHeight / 2 },
  lastMove: performance.now(),
  pos: { x: window.innerWidth / 2, y: window.innerHeight / 2 },
  wanderTarget: null,
  wanderWait: 0, // пауза между точками плавания
};

function clampToViewport(p) {
  const mw = mascot.offsetWidth || 128;
  const mh = mascot.offsetHeight || 100;
  return {
    x: Math.max(0, Math.min(p.x, window.innerWidth - mw)),
    y: Math.max(0, Math.min(p.y, window.innerHeight - mh)),
  };
}

function randomPoint() {
  return {
    x: Math.random() * Math.max(1, window.innerWidth),
    y: Math.random() * Math.max(1, window.innerHeight),
  };
}

document.addEventListener("mousemove", (e) => {
  state.cursor = { x: e.clientX, y: e.clientY };
  state.lastMove = performance.now();
  state.mode = "follow";
});

function tick(now) {
  // В режиме follow держим маскота рядом с курсором (со смещением),
  // чтобы он не перекрывал место клика.
  if (state.mode === "follow") {
    if (now - state.lastMove > IDLE_MS) {
      state.mode = "wander";
    } else {
      const target = clampToViewport({
        x: state.cursor.x + 28,
        y: state.cursor.y + 22,
      });
      state.pos.x += (target.x - state.pos.x) * FOLLOW_LERP;
      state.pos.y += (target.y - state.pos.y) * FOLLOW_LERP;
    }
  }

  // В режиме wander маскот плавает по экрану, останавливаясь на паузы.
  if (state.mode === "wander") {
    if (!state.wanderTarget) {
      state.wanderTarget = randomPoint();
      state.wanderWait = 400 + Math.random() * 900; // пауза перед стартом
    }
    if (state.wanderWait > 0) {
      state.wanderWait -= 16;
    } else {
      const target = clampToViewport(state.wanderTarget);
      const dx = target.x - state.pos.x;
      const dy = target.y - state.pos.y;
      const dist = Math.hypot(dx, dy);
      if (dist < 2) {
        state.wanderTarget = null;
        state.wanderWait = 1200 + Math.random() * 1500; // пауза на месте
      } else {
        const step = Math.min(WANDER_SPEED, dist);
        state.pos.x += (dx / dist) * step;
        state.pos.y += (dy / dist) * step;
      }
    }
  }

  mascot.style.transform = `translate(${state.pos.x}px, ${state.pos.y}px)`;
  requestAnimationFrame(tick);
}

requestAnimationFrame(tick);
