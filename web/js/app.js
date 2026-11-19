/* Robot lễ tân Nova — Bảo tàng Lịch sử Quân sự Việt Nam · "Mạch nguồn văn hoá quân sự Việt Nam".
 * 4 tính năng: Dẫn đường (5 tư liệu + tham quan lần lượt) · Giao tiếp AI (chỉ di sản văn hoá & quân sự VN, chỉ nguồn chính thống)
 *              · Hỏi đáp (25 câu trắc nghiệm, chọn đáp án → đúng/sai + thông tin bổ sung) · Xem tư liệu (5 standee).
 * Mọi lời robot nói đi qua 1 cửa talk(); người dùng thao tác là hush() cắt tiếng.
 */
(function () {
  var D = window.APP_DATA, TL = window.TULIEU, QZ = window.QUIZ, R = window.Nova;
  var CFG = {
    IDLE_MS: 15000,        // trang chủ: không ai chạm 15 s -> robot đọc câu mời
    GAP_MS: 25000,         // nghỉ giữa 2 câu mời
    BACK_HOME_MS: 90000,   // màn khác: không ai chạm 90 s -> về trang chủ (bài hỏi đáp làm lại từ đầu)
    WAIT_SAY_MS: 2500      // AI chưa trả lời sau 2,5 s -> robot nói "Quý khách chờ Nova một chút"
  };
  var HDR = { nav: 'Dẫn đường', ai: 'Giao tiếp AI', quiz: 'Hỏi đáp', docs: 'Xem tư liệu' };

  var S = {
    screen: 'home', speaking: false, mic: false, thinking: false, turn: 0,
    lastTouch: Date.now(), lastGreet: 0, greetIdx: 0, greeting: false,
    places: null, move: null, returnAt: 0,
    quiz: newQuiz(), doc: -1, reading: false,
    hist: [], aiReq: null
  };
  function newQuiz() { var a = []; for (var i = 0; i < QZ.length; i++) a.push(-1); return { i: 0, ans: a, done: false }; }

  function $(id) { return document.getElementById(id); }
  function norm(s) {
    return String(s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd')
      .replace(/[^a-z0-9]+/g, ' ').trim();
  }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  var toastT = null;
  function toast(msg, ms) { var t = $('toast'); t.textContent = msg; t.className = 'toast on'; clearTimeout(toastT); toastT = setTimeout(function () { t.className = 'toast'; }, ms || 3500); }

  /* ============ CHỮ -> LỜI ĐỌC (ngày tháng, khoảng số, đơn vị, số La Mã) ============ */
  var ROMAN = { I: 1, V: 5, X: 10 };
  function roman(r) { var n = 0, p = 0; for (var i = r.length - 1; i >= 0; i--) { var v = ROMAN[r[i]]; n += v < p ? -v : v; p = Math.max(p, v); } return n; }
  function toSay(s) {
    function n(x) { return String(parseInt(x, 10)); }   // "05" -> "5"
    return String(s || '')
      .replace(/([Tt]ừ ngày |[Nn]gày )?(\d{1,2})\/(\d{1,2})\s*[-–]\s*(\d{1,2})\/(\d{1,2})\/(\d{4})/g, function (m, p, a, b, c, d, y) {
        return (p && p.charAt(0) === p.charAt(0).toUpperCase() ? 'Từ' : 'từ') + ' ngày ' + n(a) + ' tháng ' + n(b) + ' đến ngày ' + n(c) + ' tháng ' + n(d) + ' năm ' + y; })
      .replace(/([Nn]gày )?(\d{1,2})\/(\d{1,2})\/(\d{4})/g, function (m, p, d, mo, y) { return (p || 'ngày ') + n(d) + ' tháng ' + n(mo) + ' năm ' + y; })
      .replace(/([Tt]háng )?\b(\d{1,2})\/(\d{4})/g, function (m, p, mo, y) { return (p || 'tháng ') + n(mo) + ' năm ' + y; })
      .replace(/([Nn]gày )(\d{1,2})\/(\d{1,2})\b/g, function (m, p, d, mo) { return p + n(d) + ' tháng ' + n(mo); })
      .replace(/(\d)\.(\d{3})\b/g, '$1$2')
      .replace(/(\d)\s*[-–]\s*(\d)/g, '$1 đến $2')
      .replace(/(\d)\s*km\b/g, '$1 ki lô mét').replace(/(\d)\s*kg\b/g, '$1 ki lô gam')
      .replace(/(\d)\s*cm\b/g, '$1 xăng ti mét').replace(/(\d)\s*m\b/g, '$1 mét')
      .replace(/(thế kỷ|thế kỉ|khóa|khoá) ([IVX]+)\b/g, function (m, a, r) { return a + ' ' + roman(r); })
      .replace(/LLVTND/g, 'Lực lượng vũ trang nhân dân').replace(/QĐND/g, 'Quân đội nhân dân')
      .replace(/[“”"]/g, '');
  }

  /* ===================== LỜI NÓI: 1 cửa ra duy nhất ===================== */
  var talkTok = 0;
  function talk(text, onEnd) {
    var my = ++talkTok;
    S.speaking = true; setOrb();
    R.speak(text, function () {
      if (my !== talkTok) return;
      S.speaking = false; setOrb();
      if (onEnd) onEnd();
    });
  }
  function hush() {
    talkTok++; S.turn++;
    S.speaking = false; S.thinking = false; S.reading = false; S.greeting = false;
    if (S.aiReq) { R.cancelAI(S.aiReq); S.aiReq = null; }
    clearTimeout(S.waitT);
    $('ribbon').className = 'ribbon';
    markPara(-1); removeTyping(); setOrb();
    R.userAction();
  }

  /* ===================== ĐIỀU HƯỚNG MÀN ===================== */
  function go(name) {
    if (S.mic) R.micStop();
    hush();
    closeLightbox();
    S.screen = name;
    var ss = document.querySelectorAll('.screen');
    for (var i = 0; i < ss.length; i++) ss[i].classList.toggle('on', ss[i].id === 's-' + name);
    document.body.classList.toggle('at-home', name === 'home');
    if (HDR[name]) $('hdrTitle').textContent = HDR[name];
    if (name === 'nav') loadPlaces();
    if (name === 'ai') { setAiStatus(); S.hist = []; renderChips(); if (!$('chat').querySelector('.msg')) talk(D.ai.hello); }
    if (name === 'quiz') { renderQuiz(); var z = S.quiz; if (!z.done && z.ans[z.i] < 0) talk(z.i === 0 && z.ans.join('') === newQuiz().ans.join('') ? D.quiz.start : toSay(QZ[z.i].q)); }
    if (name === 'docs') { showDocList(); }
    S.lastGreet = Date.now();
    R.setScreen(name);
  }

  /* Nhịp 1 giây: câu mời ở trang chủ, tự về vị trí chờ, về trang chủ khi bỏ đi */
  setInterval(function () {
    var now = Date.now();
    var busy = S.speaking || S.mic || S.thinking || S.reading || S.move || $('modal').classList.contains('on');
    if (S.returnAt && now > S.returnAt && !busy) { S.returnAt = 0; goStandby(); return; }
    if (busy) return;
    if (S.screen === 'home') {
      if (now - S.lastTouch > CFG.IDLE_MS && now - S.lastGreet > CFG.GAP_MS) greet();
    } else if (now - S.lastTouch > CFG.BACK_HOME_MS) {
      S.quiz = newQuiz(); $('chat').innerHTML = '';
      go('home');
    }
  }, 1000);

  function greet() {
    var t = D.greet[S.greetIdx % D.greet.length]; S.greetIdx++;
    S.greeting = true;
    $('ribbon').textContent = t; $('ribbon').className = 'ribbon on';
    talk(t, function () { S.greeting = false; S.lastGreet = Date.now(); $('ribbon').className = 'ribbon'; });
  }

  /* ===================== DẪN ĐƯỜNG ===================== */
  function tlOf(no) { for (var i = 0; i < D.tl.length; i++) if (D.tl[i].no === no) return D.tl[i]; return null; }
  function pointOf(t, list) {
    var L = list || [], i, n;
    for (i = 0; i < L.length; i++) {
      n = norm(L[i]);
      for (var p = 0; p < D.nav.prefixes.length; p++) if (n === D.nav.prefixes[p] + ' ' + t.no) return L[i];
    }
    for (i = 0; i < L.length; i++) { n = norm(L[i]); if (n === norm(t.name) || t.keys.indexOf(n) >= 0) return L[i]; }
    return null;
  }
  function standbyPoint(list) {
    var L = list || [];
    for (var i = 0; i < L.length; i++) if (D.nav.standby.indexOf(norm(L[i])) >= 0) return L[i];
    return null;
  }
  function renderNav() {
    var h = '<button class="ncard tour" id="btnTour"><span class="ico">🚩</span><h3>Tham quan lần lượt</h3><p>Nova dẫn Quý khách đi qua cả 5 tư liệu, tới mỗi nơi robot thuyết minh ngắn. Chạm màn hình để dừng.</p></button>';
    D.tl.forEach(function (t) {
      var off = S.places && S.places.length && !pointOf(t, S.places);
      h += '<button class="ncard' + (off ? ' off' : '') + '" data-no="' + t.no + '"><img src="img/tl' + t.no + '-anh.jpg" alt=""><span class="go">' + (off ? 'Chưa có trên bản đồ' : 'Dẫn đường ›') + '</span>' +
        '<div class="cap"><small>Tư liệu ' + t.no + '</small><h3>' + esc(t.name) + '</h3></div></button>';
    });
    $('navGrid').innerHTML = h;
    $('btnTour').onclick = function () { startTour(); };
  }
  function renderPts() {
    var el = $('ptsList');
    if (S.places === null) { el.innerHTML = '<i>Đang đọc bản đồ…</i>'; return; }
    if (!S.places.length) { el.innerHTML = '<i>—</i>'; return; }
    var used = D.tl.map(function (t) { return pointOf(t, S.places); }), sb = standbyPoint(S.places);
    el.innerHTML = S.places.map(function (p) { return '<i' + (used.indexOf(p) >= 0 ? ' class="p"' : p === sb ? ' class="s"' : '') + '>' + esc(p) + '</i>'; }).join('');
  }
  function navMsg(t, err) { var e = $('navMsg'); e.textContent = t || ''; e.className = 'ni-msg' + (err ? ' err' : ''); }
  function loadPlaces(cb) {
    S.places = null; renderPts(); renderNav();
    R.places(function (list) {
      S.places = list || [];
      renderPts(); renderNav();
      if (!list) navMsg('Không đọc được bản đồ của robot. Kiểm tra robot đã có bản đồ và đã định vị.', true);
      else {
        var miss = D.tl.filter(function (t) { return !pointOf(t, S.places); });
        navMsg(miss.length ? 'Bản đồ còn thiếu điểm cho: ' + miss.map(function (t) { return t.no + '. ' + t.name; }).join(', ') + ' — đặt tên điểm là "Điểm 1" … "Điểm 5" (vàng = đã nhận, xanh = vị trí chờ).' : 'Đã nhận đủ 5 điểm tư liệu' + (standbyPoint(S.places) ? ' và vị trí chờ.' : '. Chưa có điểm "Vị trí chờ" — robot sẽ đứng lại tại tư liệu cuối.'), !!miss.length);
      }
      if (cb) cb(S.places);
    });
  }
  function showMoving(title, t) {
    $('mvTitle').textContent = title; $('mvStatus').textContent = 'Đang di chuyển…';
    $('mvCap').textContent = t ? t.short : '';
    var img = $('mvImg'); if (t) { img.src = 'img/tl' + t.no + '.jpg'; img.style.display = ''; } else img.style.display = 'none';
    $('moving').className = 'moving on';
  }
  function hideMoving() { $('moving').className = 'moving'; }

  /* Dẫn tới 1 tư liệu */
  function startGoto(no) {
    hush(); S.returnAt = 0;
    var t = tlOf(no);
    loadPlaces(function (list) {
      var p = pointOf(t, list);
      if (!p) { toast('Bản đồ chưa có điểm cho "' + t.name + '" (cần điểm tên "Điểm ' + t.no + '").', 6000); return; }
      var mv = S.move = { kind: 'one', t: t };
      showMoving('Đang dẫn đường tới: ' + t.name, t);
      talk(D.nav.go(t));
      R.navigate(p, {
        status: function (msg) { if (S.move === mv) $('mvStatus').textContent = msg; },
        arrive: function () {
          if (S.move !== mv) return;
          S.move = null; hideMoving(); R.light('green');
          openDoc(no, true);
          talk(D.nav.arriveSay(t), function () { if (standbyPoint(S.places)) S.returnAt = Math.max(Date.now(), S.lastTouch) + D.nav.returnMs; });
        },
        fail: function (code, msg) {
          if (S.move !== mv) return;
          S.move = null; hideMoving();
          navMsg('Robot chưa đi được: ' + (msg || ('mã ' + code)), true);
          talk(D.nav.fail);
        }
      });
    });
  }

  /* Tham quan lần lượt 5 tư liệu */
  function startTour() {
    hush(); S.returnAt = 0;
    loadPlaces(function (list) {
      var stops = D.tl.filter(function (t) { return pointOf(t, list); });
      if (!stops.length) { toast('Bản đồ chưa có điểm tư liệu nào ("Điểm 1" … "Điểm 5").', 6000); return; }
      var mv = S.move = { kind: 'tour', stops: stops, i: 0, fails: 0 };
      showMoving('Tham quan lần lượt các tư liệu', null);
      $('mvCap').textContent = D.nav.tourStart;
      talk(D.nav.tourStart, function () { tourStep(mv); });
    });
  }
  function tourStep(mv) {
    if (S.move !== mv) return;
    if (mv.i >= mv.stops.length) { tourEnd(mv); return; }
    var t = mv.stops[mv.i];
    showMoving('Tư liệu ' + t.no + ' / ' + D.tl.length + ': ' + t.name, t);
    $('mvStatus').textContent = 'Đang di chuyển tới ' + t.name + '…';
    R.navigate(pointOf(t, S.places), {
      status: function (msg) { if (S.move === mv) $('mvStatus').textContent = msg; },
      arrive: function () {
        if (S.move !== mv) return;
        mv.fails = 0; R.light('green');
        $('mvStatus').textContent = 'Đã tới: ' + t.name;
        talk(D.nav.arriveSay(t), function () { mv.i++; setTimeout(function () { tourStep(mv); }, D.nav.pauseMs); });
      },
      fail: function (code, msg) {
        if (S.move !== mv) return;
        mv.fails++; mv.i++;
        $('mvStatus').textContent = 'Chưa tới được ' + t.name + ': ' + (msg || code);
        if (mv.fails >= 2) { stopMove(); navMsg('Dừng tham quan vì robot liên tục không tới được điểm. ' + (msg || ''), true); talk(D.nav.fail); return; }
        setTimeout(function () { tourStep(mv); }, 2500);
      }
    });
  }
  function tourEnd(mv) {
    $('mvTitle').textContent = 'Kết thúc tham quan'; $('mvCap').textContent = D.nav.tourEnd; $('mvImg').style.display = 'none';
    talk(D.nav.tourEnd, function () {
      if (S.move !== mv) return;
      var sb = standbyPoint(S.places);
      if (!sb) { S.move = null; hideMoving(); go('home'); return; }
      $('mvStatus').textContent = 'Đang về vị trí chờ…';
      R.navigate(sb, { arrive: function () { if (S.move === mv) { S.move = null; hideMoving(); go('home'); } }, fail: function () { if (S.move === mv) { S.move = null; hideMoving(); go('home'); } } });
    });
  }
  /* Về "Vị trí chờ" (sau khi dẫn tới 1 tư liệu mà không ai chạm) */
  function goStandby() {
    var sb = standbyPoint(S.places); if (!sb) return;
    var mv = S.move = { kind: 'back' };
    showMoving('Nova đang về vị trí chờ', null);
    $('mvCap').textContent = 'Quý khách chạm màn hình nếu cần Nova hỗ trợ tiếp.';
    var end = function () { if (S.move === mv) { S.move = null; hideMoving(); go('home'); } };
    R.navigate(sb, { status: function (m) { if (S.move === mv) $('mvStatus').textContent = m; }, arrive: end, fail: end });
  }
  function stopMove() {
    if (!S.move) return;
    S.move = null; S.returnAt = 0;
    R.stopNav(); hush(); hideMoving();
    navMsg('Đã dừng.');
    S.lastTouch = Date.now();
  }
  $('navGrid').addEventListener('click', function (e) { var c = e.target.closest('.ncard[data-no]'); if (c) startGoto(+c.getAttribute('data-no')); });
  $('btnReload').onclick = function () { navMsg(''); loadPlaces(); };
  $('moving').addEventListener('pointerdown', function (e) { e.stopPropagation(); stopMove(); });

  /* ===================== GIAO TIẾP AI ===================== */
  function setOrb() { var o = $('orb'); if (o) o.className = 'orb' + (S.mic ? ' listen' : S.thinking ? ' think' : S.speaking ? ' talk' : ''); }
  function setMicUi() { $('btnMic').classList.toggle('on', S.mic); $('micLbl').textContent = S.mic ? 'Đang nghe… (bấm để dừng)' : 'Bấm để hỏi'; }
  function setAiStatus() {
    var i = R.aiInfo();
    var ok = i.browser || i.ready;
    $('aiStatus').innerHTML = '<span class="dot' + (ok ? ' ok' : '') + '"></span>' + (i.browser ? 'Bản xem trước trên trình duyệt' : i.ready ? 'AI OrionStar sẵn sàng' : 'Đang kết nối AI OrionStar… (mới mở app có thể mất tới 1 phút)');
  }
  function chatAdd(cls, html) {
    var c = $('chat'); var e = c.querySelector('.empty'); if (e) c.innerHTML = '';
    var d = document.createElement('div'); d.className = 'msg ' + cls; d.innerHTML = html; c.appendChild(d);
    while (c.children.length > 30) c.removeChild(c.firstChild);
    c.scrollTop = c.scrollHeight;
    return d;
  }
  function emptyChat() { $('chat').innerHTML = '<div class="empty">Bấm nút <b>🎤 Bấm để hỏi</b> rồi nói câu hỏi,<br>hoặc chạm một câu gợi ý bên dưới.</div>'; }
  function removeTyping() { var t = $('chat').querySelector('.typing'); if (t) t.parentNode.removeChild(t); }
  /* Câu gợi ý = 3 câu NGẪU NHIÊN trong 25 câu của file khách (chỉ câu đủ ngắn để nằm gọn 1 dòng) — Phương Uyên chốt 18/11/2026.
   * Không gợi ý câu ngoài file; khách vẫn hỏi được câu khác bằng giọng nói (AI trả lời). */
  var CHIP_MAX = 102, CHIP_N = 3;   // đo trên khổ 1280px (18/11): câu ≤102 ký tự mới nằm gọn 1 dòng → hiện là câu 9, 19, 22, 24, 25
  function renderChips() {
    var pool = [];
    QZ.forEach(function (q, i) { if (q.q.length <= CHIP_MAX) pool.push(i); });
    for (var k = pool.length - 1; k > 0; k--) { var j = Math.floor(Math.random() * (k + 1)); var x = pool[k]; pool[k] = pool[j]; pool[j] = x; }
    $('chips').innerHTML = pool.slice(0, CHIP_N).map(function (i) { return '<button class="chip" data-q="' + i + '">' + esc(QZ[i].q) + '</button>'; }).join('');
  }
  /* Chạm câu gợi ý: đọc NGUYÊN VĂN đáp án + Thông tin bổ sung trong file (không cần AI / mạng) */
  function answerQuiz(i) {
    var q = QZ[i];
    hush();
    chatAdd('me', esc(q.q));
    var doc = 'Đáp án: ' + q.opts[q.ok] + '. ' + q.why;
    showAnswer({ nhan: 'TU_LIEU_BAO_TANG', doc: doc, nguon: [{ host: 'Bảo tàng Lịch sử Quân sự Việt Nam' }] }, q.q, tlIn(q.q + ' ' + doc), 'Đáp án là: ' + toSay(q.opts[q.ok]) + '. ' + toSay(q.why));
  }

  /* Tư liệu được nhắc tới trong câu (để hiện nút "Xem tư liệu" / dẫn đường) */
  function tlIn(text) {
    var n = ' ' + norm(text) + ' ';
    for (var i = 0; i < D.tl.length; i++) for (var k = 0; k < D.tl[i].keys.length; k++) if (n.indexOf(' ' + D.tl[i].keys[k] + ' ') >= 0) return D.tl[i];
    return null;
  }
  function isGoIntent(text) { return /\b(dan|dua|chi duong|di toi|di den|toi cho|den cho)\b/.test(norm(text)) || /(o dau|cho nao)\b/.test(norm(text)); }
  function isTourIntent(text) { var n = norm(text); return /(tham quan|di mot vong|di vong)/.test(n) && /(dan|dua|cung|di)/.test(n); }

  function confirmIn(msgHtml, say, onYes) {
    var m = chatAdd('bot', esc(msgHtml) + '<div class="act"><button class="btn gold" data-yes>Đồng ý</button><button class="btn" data-no>Không</button></div>');
    talk(say || msgHtml);
    m.querySelector('[data-yes]').onclick = function () { m.querySelector('.act').innerHTML = ''; onYes(); };
    m.querySelector('[data-no]').onclick = function () { m.querySelector('.act').innerHTML = ''; hush(); };
  }

  function ask(text) {
    text = String(text || '').trim();
    hush();
    if (!text) { chatAdd('bot', esc(D.ai.notHeard)); talk(D.ai.notHeard); return; }
    var turn = S.turn;
    chatAdd('me', esc(text));
    var t = tlIn(text);
    // Lệnh DI CHUYỂN bằng giọng nói luôn phải bấm xác nhận (AI nghe nhầm tiếng ồn thành lệnh — bài học NovaPCCC)
    if (t && isGoIntent(text)) { confirmIn('Quý khách muốn Nova dẫn đường tới khu trưng bày ' + t.name + '?', null, function () { go('nav'); startGoto(t.no); }); return; }
    if (isTourIntent(text)) { confirmIn('Quý khách muốn Nova dẫn đi tham quan lần lượt 5 tư liệu?', null, function () { go('nav'); startTour(); }); return; }

    S.thinking = true; setOrb();
    chatAdd('bot typing', 'Nova đang tra cứu…');
    S.waitT = setTimeout(function () { if (turn === S.turn && S.thinking) talk(D.ai.wait); }, CFG.WAIT_SAY_MS);
    S.aiReq = R.askAI(text, S.hist.slice(-2), function (r) {
      if (turn !== S.turn) return;
      S.aiReq = null; clearTimeout(S.waitT);
      S.thinking = false; removeTyping(); setOrb();
      if (window.console) console.log('[AI]', text, '→', JSON.stringify(r));
      if (r.err && !r.doc) {
        // AI OrionStar không trả lời được: còn trả lời được nếu câu hỏi nhắc tới 1 trong 5 tư liệu
        // Báo rõ lý do cho KỸ THUẬT (toast), khách chỉ nghe câu lịch sự
        var why = { timeout: 'AI OrionStar không phản hồi — kiểm tra Wi-Fi/4G; robot mới mở app thì chờ khoảng 1 phút rồi hỏi lại',
          llm_exception: 'Không gọi được AI OrionStar (AgentOS chưa sẵn sàng) — thoát app, mở lại từ màn hình chính robot',
          config: 'App thiếu file cấu hình AI — build lại APK' }[r.err] || ('AI OrionStar lỗi (' + r.err + ') — kiểm tra mạng robot');
        toast(why, 7000);
        if (t) { showAnswer({ nhan: 'TU_LIEU_BAO_TANG', doc: t.short, nguon: [{ host: 'Bảo tàng Lịch sử Quân sự Việt Nam' }] }, text, t, t.say); return; }
        var msg = D.ai.cannot;
        chatAdd('bot', esc(msg)); talk(msg); return;
      }
      showAnswer(r, text, t || tlIn(r.doc));
    });
  }
  function showAnswer(r, q, t, say) {
    var h = esc(r.doc);
    if (r.nguon && r.nguon.length) {
      h += '<span class="src">Nguồn: ' + r.nguon.slice(0, 3).map(function (n) {
        return '<i' + (r.nhan === 'TU_LIEU_BAO_TANG' ? ' class="bt"' : '') + '>' + esc(n.host || n) + '</i>';
      }).join('') + '</span>';
    }
    if (t && (r.nhan === 'TU_LIEU_BAO_TANG' || r.nhan === 'TRONG_PHAM_VI')) h += '<div class="act"><button class="btn gold" data-doc="' + t.no + '">📜 Xem tư liệu ' + esc(t.name) + '</button></div>';
    chatAdd('bot', h);
    if (r.nhan === 'TU_LIEU_BAO_TANG' || r.nhan === 'TRONG_PHAM_VI') { S.hist.push({ q: q, a: r.doc }); if (S.hist.length > 4) S.hist.shift(); }
    talk(say || toSay(r.doc));
  }
  $('chat').addEventListener('click', function (e) { var b = e.target.closest('[data-doc]'); if (b) { go('docs'); openDoc(+b.getAttribute('data-doc')); } });

  $('btnMic').onclick = function () {
    if (S.mic) { R.micStop(); return; }
    hush();
    $('heardLive').textContent = 'Đang mở micro…';
    R.micStart();
  };
  R.on('mic', function (d) { S.mic = !!d.on; setMicUi(); setOrb(); if (S.mic) $('heardLive').textContent = ''; });
  R.on('asr', function (d) { if (S.mic && d.text) $('heardLive').textContent = '“' + d.text + '”'; });
  R.on('heard', function (d) {
    S.mic = false; setMicUi(); setOrb(); $('heardLive').textContent = '';
    if (S.screen !== 'ai') return;
    if (!d.text && d.manual) return;      // khách tự tắt mic khi chưa nói gì
    ask(d.text);
  });
  R.on('ai_ready', function () { if (S.screen === 'ai') setAiStatus(); });
  $('chips').onclick = function (e) { var b = e.target.closest('.chip'); if (b) answerQuiz(+b.getAttribute('data-q')); };

  /* ===================== HỎI ĐÁP (TRẮC NGHIỆM) ===================== */
  function score() { var s = 0; S.quiz.ans.forEach(function (a, i) { if (a === QZ[i].ok) s++; }); return s; }
  function answered() { return S.quiz.ans.filter(function (a) { return a >= 0; }).length; }
  function renderQuiz() {
    var z = S.quiz, h = '';
    QZ.forEach(function (q, i) {
      var a = z.ans[i], c = a < 0 ? '' : a === q.ok ? 'ok' : 'no';
      h += '<button class="' + c + (i === z.i && !z.done ? ' cur' : '') + '" data-n="' + i + '">' + (i + 1) + '</button>';
    });
    $('qzNums').innerHTML = h;
    var el = $('quiz');
    if (z.done) {
      el.innerHTML = '<div class="qz-end"><div class="big">' + score() + ' / ' + QZ.length + '</div><p>' + esc(D.quiz.end(score(), QZ.length)) + '</p><button class="btn gold" data-again>↺  Làm lại từ đầu</button></div>';
      return;
    }
    var q = QZ[z.i], a = z.ans[z.i];
    h = '<div class="qz-top"><span>Câu ' + (z.i + 1) + ' / ' + QZ.length + ' · đã trả lời ' + answered() + '</span><span class="sc">Đúng: ' + score() + '</span></div>';
    h += '<p class="qz-q">' + esc(q.q) + '</p><div class="opts">';
    q.opts.forEach(function (o, i) {
      var cls = '';
      if (a >= 0) cls = i === q.ok ? ' right' : i === a ? ' wrong' : ' dim';
      h += '<button class="opt' + cls + '" data-o="' + i + '"><span class="k">' + 'ABCD'[i] + '</span>' + esc(o) + '</button>';
    });
    h += '</div>';
    if (a >= 0) {
      var good = a === q.ok;
      h += '<div class="qz-res"><div class="qz-why">' + (good ? '<b class="r">✔ Chính xác!</b>' : '<b class="w">✘ Chưa đúng.</b> Đáp án đúng: <b>' + 'ABCD'[q.ok] + '. ' + esc(q.opts[q.ok]) + '</b>') +
        '<small>Thông tin bổ sung</small>' + esc(q.why) + '</div><div class="qz-foot">' +
        '<button class="btn gold" data-next>' + (answered() < QZ.length ? 'Câu tiếp theo  ›' : 'Xem kết quả') + '</button>' +
        '<button class="btn" data-again>↺  Làm lại từ đầu</button></div></div>';
    }
    el.innerHTML = h;
  }
  function nextUnanswered(from) {
    for (var k = 1; k <= QZ.length; k++) { var j = (from + k) % QZ.length; if (S.quiz.ans[j] < 0) return j; }
    return -1;
  }
  function showQ(i) { hush(); S.quiz.i = i; S.quiz.done = false; renderQuiz(); if (S.quiz.ans[i] < 0) talk(toSay(QZ[i].q)); }
  $('quiz').onclick = function (e) {
    var z = S.quiz;
    if (e.target.closest('[data-again]')) { S.quiz = newQuiz(); showQ(0); return; }
    if (e.target.closest('[data-next]')) {
      var n = nextUnanswered(z.i);
      if (n < 0) { hush(); z.done = true; renderQuiz(); R.light(score() * 2 >= QZ.length ? 'green' : 'blue'); talk(D.quiz.end(score(), QZ.length)); }
      else showQ(n);
      return;
    }
    var o = e.target.closest('.opt'); if (!o || z.ans[z.i] >= 0) return;
    hush();
    var q = QZ[z.i]; z.ans[z.i] = +o.getAttribute('data-o');
    var good = z.ans[z.i] === q.ok;
    R.light(good ? 'green' : 'red');
    renderQuiz();
    talk((good ? D.quiz.right : D.quiz.wrong + ' Đáp án đúng là ' + 'ABCD'[q.ok] + ': ' + toSay(q.opts[q.ok]) + '.') + ' ' + toSay(q.why));
  };
  $('qzNums').onclick = function (e) { var b = e.target.closest('[data-n]'); if (b) showQ(+b.getAttribute('data-n')); };

  /* ===================== XEM TƯ LIỆU ===================== */
  function renderDocList() {
    $('docsList').innerHTML = TL.map(function (t) {
      return '<button class="dcard" data-no="' + t.no + '"><img src="img/tl' + t.no + '.jpg" alt=""><div class="cap"><small>Tư liệu ' + t.no + '</small><h3>' + esc(tlOf(t.no).name) + '</h3><span>Xem chi tiết ›</span></div></button>';
    }).join('');
  }
  function showDocList() { S.doc = -1; $('s-docs').classList.remove('detail'); }
  function openDoc(no, silent) {
    if (!silent) hush();
    if (S.screen !== 'docs') { S.screen = 'docs'; var ss = document.querySelectorAll('.screen'); for (var i = 0; i < ss.length; i++) ss[i].classList.toggle('on', ss[i].id === 's-docs'); document.body.classList.remove('at-home'); $('hdrTitle').textContent = HDR.docs; R.setScreen('docs'); }
    var t = TL[no - 1]; S.doc = no;
    $('s-docs').classList.add('detail');
    $('docImg').querySelector('img').src = 'img/tl' + no + '.jpg';
    $('docNo').textContent = 'Tư liệu ' + no + ' / ' + TL.length + ' · Chương trình truyền thông – giáo dục di sản';
    $('docTitle').textContent = tlOf(no).name;
    $('docText').innerHTML = t.paras.map(function (p) { return '<p>' + esc(p) + '</p>'; }).join('');
    $('docText').scrollTop = 0;
    $('docRead').textContent = '🔊 Nova thuyết minh';
  }
  function markPara(i) {
    var ps = $('docText').querySelectorAll('p');
    for (var k = 0; k < ps.length; k++) ps[k].classList.toggle('on', k === i);
    if (i >= 0 && ps[i]) ps[i].scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }
  function readDoc(i) {
    var t = TL[S.doc - 1]; if (!t) return;
    if (i >= t.paras.length) { S.reading = false; markPara(-1); $('docRead').textContent = '🔊 Nova thuyết minh'; return; }
    S.reading = true; markPara(i); $('docRead').textContent = '■  Dừng thuyết minh';
    var tk = talkTok + 1;
    talk(toSay(t.paras[i]), function () { if (tk === talkTok && S.reading) readDoc(i + 1); });
  }
  $('docsList').onclick = function (e) { var c = e.target.closest('.dcard'); if (c) openDoc(+c.getAttribute('data-no')); };
  $('docBack').onclick = function () { hush(); showDocList(); };
  $('docRead').onclick = function () { if (S.reading) { hush(); $('docRead').textContent = '🔊 Nova thuyết minh'; } else { hush(); readDoc(0); } };
  $('docGo').onclick = function () { var no = S.doc; go('nav'); startGoto(no); };
  $('docImg').onclick = function () { var lb = $('lightbox'); lb.querySelector('img').src = 'img/tl' + S.doc + '.jpg'; lb.scrollTop = 0; lb.className = 'lightbox on'; };
  function closeLightbox() { $('lightbox').className = 'lightbox'; }
  $('lightbox').onclick = closeLightbox;

  /* ===================== SỰ KIỆN CHUNG ===================== */
  document.addEventListener('pointerdown', function () {
    S.lastTouch = Date.now();
    if (S.returnAt) S.returnAt = Date.now() + D.nav.returnMs;   // khách còn đang xem -> hoãn về vị trí chờ
    if (S.greeting) hush();                                       // đang đọc câu mời mà có người chạm -> im ngay
  }, true);
  var tiles = document.querySelectorAll('.tile');
  for (var i = 0; i < tiles.length; i++) tiles[i].onclick = function () { go(this.getAttribute('data-go')); };
  $('btnHome').onclick = function () { S.returnAt = 0; go('home'); };
  R.on('back', function () { if (S.move) stopMove(); else if ($('lightbox').className.indexOf('on') > 0) closeLightbox(); else go('home'); });

  var E = D.event;
  $('evOrg').textContent = E.org; $('evMuseum').textContent = E.museum; $('evTitle').textContent = E.title; $('evSub').textContent = E.sub;
  renderNav(); renderChips(); renderDocList(); emptyChat(); renderQuiz();

  // Xem trước trên trình duyệt: index.html#nav | #ai | #chat | #quiz | #quiz2 | #docs | #doc3 | #moving
  var hs = (location.hash || '').slice(1);
  if (!R.native && hs) {
    if (hs === 'chat') { go('ai'); document.querySelector('#chips .chip').click(); }
    else if (hs === 'quiz2') { go('quiz'); S.quiz.ans[0] = 3; S.quiz.ans[1] = 1; S.quiz.i = 1; renderQuiz(); }
    else if (/^doc\d$/.test(hs)) { go('docs'); openDoc(+hs.slice(3)); }
    else if (hs === 'moving') { go('nav'); showMoving('Đang dẫn đường tới: Xe đạp thồ', tlOf(5)); }
    else if (HDR[hs]) go(hs);
  }
  window.BaoTang = { ask: ask, state: S, go: go, toSay: toSay };
})();
