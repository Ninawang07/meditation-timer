/**
 * pages/timer/timer.js - 冥想计时主页
 *
 * 核心设计：
 * - 不依赖 setInterval 做计时（后台会冻结）
 * - 用「开始时间戳 + 当前时间」差值计算
 * - onHide 暂停，onShow 根据时间差恢复
 * - 圆环用 SVG stroke-dasharray，通过 setData 更新
 */
const audio = require('../../utils/audio');
const storage = require('../../utils/storage');

const RING_CIRCUMFERENCE = 2 * Math.PI * 122; // ~766.5
const PRESET_MINUTES = [1, 3, 5, 10, 15, 20, 30];

const BREATH_PATTERNS = {
  box:  { phases: ['吸气','屏息','呼气','屏息'], durations: [4,4,4,4] },
  '478': { phases: ['吸气','屏息','呼气'], durations: [4,7,8] }
};

Page({
  data: {
    mode: 'countdown',
    duration: 300,
    running: false,
    paused: false,
    displayTime: '05:00',
    totalSeconds: 300,
    ringOffset: 0,

    breathing: false,
    breathMode: 'box',
    breathLabel: '',

    audioPlaying: false,
    customSelected: false,
    customSeconds: 300,
  },

  _startTime: 0,
  _pausedRemaining: 0,
  _tickTimer: null,
  _breathTimer: null,
  _breathPhase: 0,

  onLoad() {},
  onReady() { this._updateRing(1); },

  onShow() {
    // 从后台切回：根据时间差恢复状态
    if (this.data.running && !this.data.paused) {
      this._recoverFromBackground();
      if (!this.data.running || this.data.paused) return;
      this._startTickLoop();
      if (this.data.breathing) this._startBreathCycle();
    }
  },

  onHide() {
    // 切到后台：停止动画循环（JS 线程会被冻结，setInterval 不可靠）
    this._clearAllTimers();
    // 记录当前剩余/已过作为快照
    if (this.data.running && !this.data.paused) {
      this._pausedRemaining = this._calcCurrent();
    }
  },

  onUnload() {
    this._clearAllTimers();
    audio.stopAll();
  },

  _updateRing(fraction) {
    this.setData({ ringOffset: RING_CIRCUMFERENCE * (1 - fraction) });
  },

  // ── 计时核心 ──

  /** 获取当前的剩余秒数（倒计时）或已过秒数（正计时） */
  _calcCurrent() {
    const elapsed = Math.floor((Date.now() - this._startTime) / 1000);
    if (this.data.mode === 'countdown') {
      return Math.max(0, this.data.duration - elapsed);
    }
    return elapsed;
  },

  /** 从后台恢复 */
  _recoverFromBackground() {
    const current = this._calcCurrent();
    if (this.data.mode === 'countdown') {
      if (current <= 0) {
        this._finish();
        return;
      }
    }
    this._pausedRemaining = current;
    this._updateDisplay(current);
  },

  /** 启动显示刷新循环（不负责计时逻辑，只刷新 UI） */
  _startTickLoop() {
    this._clearTickLoop();
    this._tickTimer = setInterval(() => {
      const current = this._calcCurrent();
      this._pausedRemaining = current;

      if (this.data.mode === 'countdown' && current <= 0) {
        this._finish();
        return;
      }
      this._updateDisplay(current);
    }, 250); // 250ms 刷新足够平滑，省性能
  },

  _clearTickLoop() {
    if (this._tickTimer) { clearInterval(this._tickTimer); this._tickTimer = null; }
  },

  _clearAllTimers() {
    this._clearTickLoop();
    if (this._breathTimer) { clearTimeout(this._breathTimer); this._breathTimer = null; }
  },

  _updateDisplay(total) {
    const displayTime = this._formatDisplayTime(total);

    let fraction;
    if (this.data.mode === 'countdown') {
      fraction = total / this.data.duration;
    } else {
      fraction = Math.min(total / 7200, 1); // 正计时用 120 分钟作为满格
    }

    this.setData({ displayTime, totalSeconds: total });
    this._updateRing(fraction);
  },

  _formatDisplayTime(total) {
    const safeTotal = Math.max(0, Math.floor(total));
    const mins = Math.floor(safeTotal / 60);
    const secs = safeTotal % 60;
    return `${String(mins).padStart(2,'0')}:${String(secs).padStart(2,'0')}`;
  },

  // ── 开始 / 暂停 / 结束 ──

  onStart() {
    if (this.data.running && this.data.paused) {
      // 恢复
      const now = Date.now();
      if (this.data.mode === 'countdown') {
        this._startTime = now - (this.data.duration - this._pausedRemaining) * 1000;
      } else {
        this._startTime = now - this._pausedRemaining * 1000;
      }
      this.setData({ paused: false });
      this._startTickLoop();
      if (this.data.breathing) this._startBreathCycle();
      return;
    }

    // 全新开始
    const now = Date.now();
    this._startTime = now;
    this._breathPhase = 0;

    this.setData({
      running: true,
      paused: false
    });
    this._startTickLoop();
    audio.playBowlFadeOut();
    this._flashAudioIndicator();
    // 呼吸引导等颂钵播完再开始（颂钵 26 秒 + 1 秒缓冲）
    if (this.data.breathing) {
      setTimeout(() => {
        if (this.data.running && !this.data.paused) this._startBreathCycle();
      }, 27000);
    }
  },

  onPause() {
    if (!this.data.running || this.data.paused) return;
    this._clearAllTimers();
    this._pausedRemaining = this._calcCurrent();
    this.setData({ paused: true });
  },

  onStop() {
    const wasRunning = this.data.running;
    this._clearAllTimers();

    // 记录本次冥想
    let actualMin = 0;
    if (wasRunning) {
      const elapsed = this.data.paused
        ? this._getMeditatedSecondsFromSnapshot()
        : Math.floor((Date.now() - this._startTime) / 1000);
      if (this.data.mode === 'countdown') {
        const meditated = Math.min(elapsed, this.data.duration);
        actualMin = Math.round(meditated / 6) / 10; // 一位小数
      } else {
        actualMin = Math.round(elapsed / 6) / 10;
      }
    }

    this.setData({
      running: false,
      paused: false,
      breathLabel: ''
    });

    // 重置显示
    if (this.data.mode === 'countdown') {
      this._updateDisplay(this.data.duration);
      this._updateRing(1);
    } else {
      this._updateDisplay(0);
      this._updateRing(0);
    }

    audio.stopAll();
    audio.playBowlFadeIn();
    this._flashAudioIndicator();

    // 超过 6 秒才记录
    if (actualMin > 0.1) {
      storage.add({
        date: storage.formatDate(new Date()),
        time: storage.formatTime(new Date()),
        duration: actualMin,
        mode: this._getModeLabel()
      });
    }
  },

  _finish() {
    // 定时到时自动结束
    this._clearAllTimers();
    this.setData({
      running: false,
      paused: false,
      displayTime: '00:00',
      totalSeconds: 0,
      breathLabel: ''
    });
    this._updateRing(0);
    audio.playBowlFadeIn();
    this._flashAudioIndicator();

    storage.add({
      date: storage.formatDate(new Date()),
      time: storage.formatTime(new Date()),
      duration: Math.round(this.data.duration / 60 * 10) / 10,
      mode: this._getModeLabel()
    });

    // 重置
    setTimeout(() => {
      if (!this.data.running) {
        this._updateDisplay(this.data.duration);
        this._updateRing(1);
      }
    }, 2000);
  },

  _getModeLabel() {
    if (this.data.mode === 'stopwatch') return '不定时';
    if (this.data.breathing) return '定时+呼吸';
    return '定时';
  },

  _getMeditatedSecondsFromSnapshot() {
    if (this.data.mode === 'countdown') {
      return Math.max(0, this.data.duration - this._pausedRemaining);
    }
    return Math.max(0, this._pausedRemaining);
  },

  // ── 呼吸引导 ──

  _startBreathCycle() {
    this._runPhase();
  },

  _runPhase() {
    if (!this.data.running || this.data.paused) return;

    const pattern = BREATH_PATTERNS[this.data.breathMode] || BREATH_PATTERNS.box;
    const phaseName = pattern.phases[this._breathPhase];
    const phaseSec = pattern.durations[this._breathPhase];

    this.setData({ breathLabel: phaseName });

    // 播放提示音
    if (phaseName === '吸气') audio.playCue();

    this._breathTimer = setTimeout(() => {
      this._breathPhase = (this._breathPhase + 1) % pattern.phases.length;
      this._runPhase();
    }, phaseSec * 1000);
  },

  // ── 交互 ──

  onModeTap(e) {
    if (this.data.running) return;
    const mode = e.currentTarget.dataset.mode;
    this.setData({
      mode,
      displayTime: mode === 'countdown'
        ? `${String(Math.floor(this.data.duration/60)).padStart(2,'0')}:00`
        : '00:00',
      totalSeconds: mode === 'countdown' ? this.data.duration : 0
    });
    this._updateRing(mode === 'countdown' ? 1 : 0);
  },

  onDurationTap(e) {
    if (this.data.running) return;
    const min = parseInt(e.currentTarget.dataset.min);
    this.setData({
      duration: min * 60,
      displayTime: this._formatDisplayTime(min * 60),
      totalSeconds: min * 60,
      customSelected: false,
      customSeconds: min * 60
    });
    this._updateRing(1);
  },

  onCustomTap() {
    if (this.data.running) return;
    this.setData({ customSelected: !this.data.customSelected });
  },

  onCustomInput(e) {
    const val = parseInt(e.detail.value);
    if (Number.isNaN(val)) {
      this.setData({ customSeconds: '' });
      return;
    }
    const clamped = Math.max(1, Math.min(7200, val));
    this.setData({
      customSeconds: clamped,
      duration: clamped,
      displayTime: this._formatDisplayTime(clamped),
      totalSeconds: clamped
    });
    this._updateRing(1);
  },

  onBreathToggle() {
    if (this.data.running) return;
    this.setData({ breathing: !this.data.breathing });
  },

  onBreathModeTap(e) {
    if (this.data.running) return;
    this.setData({ breathMode: e.currentTarget.dataset.mode });
  },

  onGoHistory() {
    if (this.data.running) return;
    wx.navigateTo({ url: '/pages/history/history' });
  },

  // ── 音频指示 ──

  _flashAudioIndicator() {
    this.setData({ audioPlaying: true });
    setTimeout(() => {
      this.setData({ audioPlaying: false });
    }, 1500);
  }
});
