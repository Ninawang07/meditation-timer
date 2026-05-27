/**
 * 音频管理 - 颂钵（渐弱/渐强）+ 呼吸吸气提示
 */

let bowl = null;
let cue = null;

let _fadeTimer = null;
let _cueFadeTimer = null;

const BOWL_FADE_DURATION = 26000;
const BOWL_FADE_INTERVAL = 100;
const BOWL_SECOND_STRIKE_START = 6800;
const BOWL_SECOND_STRIKE_END = 8800;
const BOWL_SECOND_STRIKE_GAIN = 0.45;

function _clearFade() {
  if (_fadeTimer) { clearInterval(_fadeTimer); _fadeTimer = null; }
}
function _clearCueFade() {
  if (_cueFadeTimer) { clearInterval(_cueFadeTimer); _cueFadeTimer = null; }
}

function _createAudio(src, label) {
  const ctx = wx.createInnerAudioContext();
  ctx.loop = false;
  ctx.src = src;
  ctx.onError((err) => {
    console.error(`[audio] ${label} error:`, err);
  });
  return ctx;
}

function _getBowl() {
  if (!bowl) bowl = _createAudio('/audio/bowl.mp3', 'bowl');
  return bowl;
}

function _getCue() {
  if (!cue) cue = _createAudio('/audio/cue.mp3', 'cue');
  return cue;
}

function _playFromStart(ctx) {
  try { ctx.stop(); } catch(e) {}
  try { ctx.play(); } catch(e) {
    console.error('[audio] play failed:', e);
  }
}

/** 开始：音量从强到弱 */
function playBowlFadeOut() {
  _clearFade();
  const bowl = _getBowl();
  bowl.volume = 1.0;
  _playFromStart(bowl);
  _startFade(1.0, 0.08);
}

/** 结束：音量从弱到强 */
function playBowlFadeIn() {
  _clearFade();
  const bowl = _getBowl();
  bowl.volume = 0.08;
  _playFromStart(bowl);
  _startFade(0.08, 1.0);
}

function _getSecondStrikeGain(elapsed) {
  if (elapsed < BOWL_SECOND_STRIKE_START || elapsed > BOWL_SECOND_STRIKE_END) return 1;

  const progress = (elapsed - BOWL_SECOND_STRIKE_START) /
    (BOWL_SECOND_STRIKE_END - BOWL_SECOND_STRIKE_START);
  const smooth = Math.sin(Math.PI * progress);
  return 1 - (1 - BOWL_SECOND_STRIKE_GAIN) * smooth;
}

function _startFade(from, to) {
  const startedAt = Date.now();

  _fadeTimer = setInterval(() => {
    const elapsed = Date.now() - startedAt;
    const progress = Math.min(elapsed / BOWL_FADE_DURATION, 1);
    const baseVolume = from + (to - from) * progress;
    const volume = baseVolume * _getSecondStrikeGain(elapsed);
    const bowl = _getBowl();

    bowl.volume = Math.max(0, Math.min(1, Math.round(volume * 100) / 100));

    if (progress >= 1) {
      bowl.volume = to;
      _clearFade();
    }
  }, BOWL_FADE_INTERVAL);
}

function playCue() {
  _clearCueFade();
  const cue = _getCue();
  const startVolume = 0.55;
  cue.volume = startVolume;
  _playFromStart(cue);
  // 4.5 秒内从较低音量渐弱到静音，避免音频结尾突然断掉。
  const steps = 18;
  const stepSize = -startVolume / steps;
  let count = 0;
  _cueFadeTimer = setInterval(() => {
    count++;
    if (count >= steps) {
      cue.volume = 0;
      try { cue.stop(); } catch(e) {}
      _clearCueFade();
      return;
    }
    cue.volume = Math.max(0, Math.round((startVolume + stepSize * count) * 100) / 100);
  }, 250);
}

function stopAll() {
  _clearFade();
  _clearCueFade();
  [bowl, cue].filter(Boolean).forEach(ctx => {
    try { ctx.stop(); } catch(e) {}
  });
}

function destroy() {
  _clearFade();
  _clearCueFade();
  [bowl, cue].filter(Boolean).forEach(ctx => {
    try { ctx.destroy(); } catch(e) {}
  });
  bowl = null;
  cue = null;
}

module.exports = { playBowlFadeOut, playBowlFadeIn, playCue, stopAll, destroy };
