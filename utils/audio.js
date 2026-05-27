/**
 * 音频管理 - 颂钵（渐弱/渐强）+ 呼吸吸气提示
 */

const bowl = wx.createInnerAudioContext();
const cue = wx.createInnerAudioContext();

try {
  bowl.src = '/audio/bowl.mp3';
  cue.src  = '/audio/cue.mp3';
} catch(e) {
  console.error('Audio init failed:', e);
}

bowl.loop = false;
cue.loop  = false;

let _fadeTimer = null;
let _cueFadeTimer = null;

function _clearFade() {
  if (_fadeTimer) { clearInterval(_fadeTimer); _fadeTimer = null; }
}
function _clearCueFade() {
  if (_cueFadeTimer) { clearInterval(_cueFadeTimer); _cueFadeTimer = null; }
}

/** 开始：音量从 0.6 渐弱到 0.3 */
function playBowlFadeOut() {
  _clearFade();
  bowl.volume = 0.6;
  bowl.seek(0);
  bowl.play();
  _startFade(0.6, 0.3);
}

/** 结束：音量从 0.3 渐强到 0.6 */
function playBowlFadeIn() {
  _clearFade();
  bowl.volume = 0.3;
  bowl.seek(0);
  bowl.play();
  _startFade(0.3, 0.6);
}

function _startFade(from, to) {
  const steps = 40;        // 26s / 0.65s = 40 步
  const stepSize = (to - from) / steps;
  let current = from;
  let count = 0;

  _fadeTimer = setInterval(() => {
    count++;
    current += stepSize;
    if (count >= steps) {
      bowl.volume = to;
      _clearFade();
      return;
    }
    bowl.volume = Math.round(current * 100) / 100;
  }, 650);
}

cue.volume = 1.0;

function playCue() {
  _clearCueFade();
  cue.volume = 1.0;
  cue.seek(0);
  cue.play();
  // 4.5 秒内从 1.0 渐弱到 0.33
  const steps = 18;
  const stepSize = (0.33 - 1.0) / steps;
  let count = 0;
  _cueFadeTimer = setInterval(() => {
    count++;
    if (count >= steps) { _clearCueFade(); return; }
    cue.volume = Math.round((1.0 + stepSize * count) * 100) / 100;
  }, 250);
}

function stopAll() {
  _clearFade();
  _clearCueFade();
  [bowl, cue].forEach(ctx => {
    try { ctx.stop(); } catch(e) {}
  });
}

function destroy() {
  _clearFade();
  _clearCueFade();
  [bowl, cue].forEach(ctx => {
    try { ctx.destroy(); } catch(e) {}
  });
}

module.exports = { playBowlFadeOut, playBowlFadeIn, playCue, stopAll, destroy };
