/**
 * 本地存储工具 - 冥想记录 CRUD
 */

const STORAGE_KEY = 'meditation_sessions';

function getAll() {
  try {
    return wx.getStorageSync(STORAGE_KEY) || [];
  } catch (e) {
    return [];
  }
}

function add(session) {
  const sessions = getAll();
  sessions.unshift(session);
  if (sessions.length > 100) sessions.length = 100;
  wx.setStorageSync(STORAGE_KEY, sessions);
  return sessions;
}

function getStats() {
  const sessions = getAll();
  const totalCount = sessions.length;
  const totalMinutes = Math.round(sessions.reduce((s, i) => s + i.duration, 0));

  // 连续天数
  let streak = 0;
  const today = formatDate(new Date());
  const dates = [...new Set(sessions.map(s => s.date))].sort().reverse();

  if (dates.length > 0 && dates[0] === today) {
    streak = 1;
    for (let i = 1; i < dates.length; i++) {
      const prev = new Date(dates[i - 1]);
      const curr = new Date(dates[i]);
      const diff = (prev - curr) / (1000 * 60 * 60 * 24);
      if (Math.abs(diff - 1) < 0.1) streak++;
      else break;
    }
  }

  return { totalCount, totalMinutes, streak };
}

function getRecent(limit = 30) {
  return getAll().slice(0, limit);
}

function formatDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function formatTime(d) {
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${h}:${min}`;
}

module.exports = { getAll, add, getStats, getRecent, formatDate, formatTime };
