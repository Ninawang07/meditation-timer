/**
 * pages/history/history.js - 冥想记录页
 */
const storage = require('../../utils/storage');

Page({
  data: {
    stats: { totalCount: 0, totalMinutes: 0, streak: 0 },
    sessions: []
  },

  onShow() {
    this._refresh();
  },

  _refresh() {
    const stats = storage.getStats();
    const sessions = storage.getRecent(50);
    this.setData({ stats, sessions });
  }
});
