/* Lớp kết nối giao diện <-> robot Nova.
 * - Trong app Android: gọi window.NovaNative (Kotlin @JavascriptInterface), robot trả kết quả qua window.NovaEvents.dispatch(type, json).
 * - Trên trình duyệt (xem trước): tự MÔ PHỎNG giọng nói, micro, AI, dẫn đường.
 * Cú pháp ES2017 (WebView robot là Chromium cũ): không dùng ?. ?? , flex gap, inset.
 */
(function () {
  var N = window.NovaNative || null;
  var seq = 1;
  var waiters = {};      // id -> callback
  var listeners = {};    // type -> [fn]
  var FINAL = { tts_end: 1, places: 1, ai: 1 };

  window.NovaEvents = {
    dispatch: function (type, data) {
      var d = data;
      if (typeof d === 'string') { try { d = JSON.parse(d); } catch (e) { d = { raw: data }; } }
      if (d && d.id && waiters[d.id]) {
        var w = waiters[d.id];
        if (FINAL[type] || (type === 'nav' && (d.kind === 'arrive' || d.kind === 'fail'))) delete waiters[d.id];
        try { w(type, d); } catch (e) { console.error(e); }
      }
      (listeners[type] || []).forEach(function (fn) { try { fn(d); } catch (e) { console.error(e); } });
    }
  };
  function emit(type, d) { window.NovaEvents.dispatch(type, d); }

  function viVoice() {
    if (!('speechSynthesis' in window)) return null;
    var vs = speechSynthesis.getVoices();
    for (var i = 0; i < vs.length; i++) if (/^vi/i.test(vs[i].lang)) return vs[i];
    return null;
  }
  var simNav = { timer: null, n: 0 }, navId = null, simMicTimer = null;

  /* Mô phỏng AI trên trình duyệt (chỉ để xem giao diện) */
  function simAI(q) {
    var n = String(q).toLowerCase();
    var D = window.APP_DATA;
    for (var i = 0; i < D.tl.length; i++) {
      var t = D.tl[i];
      if (n.indexOf(t.name.toLowerCase()) >= 0) return { nhan: 'TU_LIEU_BAO_TANG', doc: t.short, nguon: [{ host: 'Bảo tàng Lịch sử Quân sự Việt Nam' }] };
    }
    if (/xin chào|chào nova|hello/.test(n)) return { nhan: 'CHAO_HOI', doc: 'Nova xin kính chào Quý khách! Quý khách muốn tìm hiểu về di sản văn hoá hay di sản quân sự Việt Nam, cứ hỏi Nova nhé.', nguon: [] };
    if (/thời tiết|giá vàng|bóng đá|world cup|thơ/.test(n)) return { nhan: 'NGOAI_PHAM_VI', doc: window.APP_DATA.ai.cannot, nguon: [] };
    return { nhan: 'TRONG_PHAM_VI', doc: '(Bản xem trước trên trình duyệt — trên robot, AI OrionStar sẽ trả lời câu này.)', nguon: [] };
  }

  window.Nova = {
    native: !!N,
    on: function (type, fn) { (listeners[type] = listeners[type] || []).push(fn); },

    /* Robot nói. onEnd gọi đúng 1 lần khi nói xong / bị ngắt / lỗi. */
    speak: function (text, onEnd) {
      if (!text) { if (onEnd) onEnd(); return; }
      if (N) {
        var id = 't' + seq++;
        if (onEnd) waiters[id] = function () { onEnd(); };
        N.speak(id, text);
        return;
      }
      var v = viVoice();
      if (v) {
        speechSynthesis.cancel();
        var u = new SpeechSynthesisUtterance(text);
        u.voice = v; u.lang = v.lang; u.rate = 1.05;
        var done = false;
        u.onend = u.onerror = function () { if (!done) { done = true; if (onEnd) onEnd(); } };
        speechSynthesis.speak(u);
      } else {
        setTimeout(function () { if (onEnd) onEnd(); }, Math.min(15000, 900 + text.length * 45));
      }
    },
    stopSpeak: function () { if (N) N.stopSpeak(); else if ('speechSynthesis' in window) speechSynthesis.cancel(); },
    /* Người dùng vừa thao tác: cắt mọi tiếng nói (SkillApi + AgentOS) */
    userAction: function () { if (N && N.userAction) N.userAction(); else this.stopSpeak(); },
    light: function (color) { if (N) N.light(color); },

    /* Danh sách điểm trên bản đồ -> cb([tên...]) (null = không đọc được) */
    places: function (cb) {
      if (N) {
        var id = 'p' + seq++;
        waiters[id] = function (t, d) { cb(Array.isArray(d.list) ? d.list : null); };
        N.getPlaces(id);
        setTimeout(function () { if (waiters[id]) { delete waiters[id]; cb(null); } }, 6000);
      } else setTimeout(function () { cb(['Điểm 1', 'Điểm 2', 'Điểm 3', 'Điểm 4', 'Điểm 5', 'Vị trí chờ']); }, 150);
    },

    /* Dẫn đường tới điểm "name". h = {status(msg), arrive(), fail(code,msg)} */
    navigate: function (name, h) {
      if (N) {
        var id = 'n' + seq++;
        if (navId) delete waiters[navId];
        navId = id;
        waiters[id] = function (t, d) {
          if (d.kind === 'arrive') { if (h.arrive) h.arrive(); }
          else if (d.kind === 'fail') { if (h.fail) h.fail(d.code, d.msg); }
          else if (h.status) h.status(d.msg, d.code);
        };
        N.navigate(id, name);
        return id;
      }
      clearTimeout(simNav.timer);
      var my = ++simNav.n;
      if (h.status) h.status('Đang di chuyển tới "' + name + '" (mô phỏng)');
      simNav.timer = setTimeout(function () { if (my === simNav.n && h.arrive) h.arrive(); }, 4000);
      return 'sim' + my;
    },
    stopNav: function () {
      if (navId) { delete waiters[navId]; navId = null; }
      if (N) N.stopNav();
      clearTimeout(simNav.timer); simNav.n++;
    },

    /* Micro bấm-mới-nghe. Sự kiện về: 'mic' {on}, 'asr' {text} (đang nghe), 'heard' {text} (cả câu, 1 lần). */
    micStart: function () {
      if (N) { N.micStart(); return; }
      emit('mic', { on: true });
      clearTimeout(simMicTimer);
      simMicTimer = setTimeout(function () {
        var t = window.prompt('Gõ câu khách nói (mô phỏng trên trình duyệt):', '');
        emit('mic', { on: false });
        emit('heard', { text: t || '' });
      }, 600);
    },
    micStop: function () { if (N) { N.micStop(); return; } clearTimeout(simMicTimer); emit('mic', { on: false }); },

    /* Hỏi AI (OrionStar trên robot, giới hạn chủ đề).
     * history = [{q, a}] (tối đa 2 lượt gần nhất). cb({nhan, doc, nguon:[{host,url,title}], err}) */
    askAI: function (q, history, cb) {
      if (N && N.askAI) {
        var id = 'a' + seq++;
        waiters[id] = function (t, d) { cb(d); };
        N.askAI(id, q, JSON.stringify(history || []));
        return id;
      }
      setTimeout(function () { cb(simAI(q)); }, 1400);
      return null;
    },
    cancelAI: function (id) { if (id) delete waiters[id]; },

    /* Trạng thái AI: {ready, config, aiMs, aiErr, ...} */
    aiInfo: function () { if (N && N.aiInfo) { try { return JSON.parse(N.aiInfo()); } catch (e) { } } return { ready: true, browser: !N }; },

    setScreen: function (name) { if (N && N.setScreen) N.setScreen(name, ''); },
    exitApp: function () { if (N) N.exitApp(); }
  };
})();
