/* Nội dung app Nova · Bảo tàng Lịch sử Quân sự Việt Nam — "Mạch nguồn văn hoá quân sự Việt Nam".
 * SỬA CÂU CHỮ Ở FILE NÀY. (25 câu hỏi = js/quiz.js, 5 tư liệu = js/tulieu.js — 2 file đó TỰ SINH từ file khách,
 * sửa bằng scripts/make_data_nova_baotang.py; phần AI = js/ai-config.json.)
 * [KH] = nguyên văn tài liệu khách gửi · [RBW] = Roboworld soạn thêm (rút từ standee của Bảo tàng), CHƯA được Bảo tàng duyệt.
 * `say` = câu robot ĐỌC (đã viết số/đơn vị theo cách đọc), `text` = chữ hiện trên màn hình.
 */
window.APP_DATA = {
  event: {
    org: 'Tổng cục Chính trị Quân đội nhân dân Việt Nam', museum: 'Bảo tàng Lịch sử Quân sự Việt Nam',          // [KH] key visual
    title: 'Mạch nguồn văn hoá quân sự Việt Nam',
    sub: 'Chào mừng Ngày Di sản Văn hoá Việt Nam (23/11/2005 – 23/11/2026) và Ngày Văn hoá Việt Nam (24/11/2026)',
    program: 'Chương trình truyền thông – giáo dục di sản'
  },

  /* Đứng chờ ở trang chủ: không ai chạm IDLE_MS thì robot đọc lần lượt các câu này (nghỉ GAP_MS giữa 2 câu). [RBW] */
  greet: [
    'Kính chào Quý khách đến với chương trình truyền thông giáo dục di sản Mạch nguồn văn hoá quân sự Việt Nam. Mời Quý khách chạm vào màn hình để trò chuyện cùng Nova.',
    'Quý khách có biết nỏ Liên Châu bắn được nhiều mũi tên trong một lần bắn? Chạm Xem tư liệu để khám phá năm hiện vật tiêu biểu của Bảo tàng.',
    'Mời Quý khách thử sức với hai mươi lăm câu hỏi về lịch sử quân sự Việt Nam trong mục Hỏi đáp.',
    'Nova có thể dẫn Quý khách tới từng tư liệu đang trưng bày. Mời Quý khách chạm Dẫn đường.'
  ],

  /* Lời ngắn cho từng tư liệu — robot đọc khi DẪN ĐƯỜNG tới nơi và khi tham quan lần lượt. [RBW] rút từ standee.
   * keys = từ khoá nhận ra tư liệu khi khách hỏi bằng giọng nói ("dẫn tôi tới xe đạp thồ"). */
  tl: [
    { no: 1, name: 'Nỏ Liên Châu', keys: ['no lien chau', 'lien chau', 'lay no', 'cao lo', 'than no'],
      short: 'Theo truyền thuyết, tướng Cao Lỗ chế tạo nỏ Liên Châu bắn được nhiều mũi tên trong một lần bắn, giúp An Dương Vương bảo vệ nước Âu Lạc. Các lẫy nỏ trưng bày ở đây thuộc văn hoá Đông Sơn, có niên đại khoảng 2.000 – 2.500 năm cách ngày nay.',
      say: 'Theo truyền thuyết, tướng Cao Lỗ chế tạo nỏ Liên Châu bắn được nhiều mũi tên trong một lần bắn, giúp An Dương Vương bảo vệ nước Âu Lạc. Các lẫy nỏ trưng bày ở đây thuộc văn hoá Đông Sơn, có niên đại khoảng hai nghìn đến hai nghìn năm trăm năm cách ngày nay.' },
    { no: 2, name: 'Địa đạo Củ Chi', keys: ['dia dao cu chi', 'dia dao', 'cu chi', 'dat thep thanh dong'],
      short: 'Địa đạo Củ Chi nằm ở phía Tây Bắc Sài Gòn, gồm ba tầng dưới lòng đất với bệnh xá, phòng họp, nơi ăn ở và giếng nước. Dựa vào địa đạo, quân và dân Củ Chi kiên cường bám trụ, được tuyên dương danh hiệu “Đất thép thành đồng”.',
      say: 'Địa đạo Củ Chi nằm ở phía Tây Bắc Sài Gòn, gồm ba tầng dưới lòng đất với bệnh xá, phòng họp, nơi ăn ở và giếng nước. Dựa vào địa đạo, quân và dân Củ Chi kiên cường bám trụ, được tuyên dương danh hiệu Đất thép thành đồng.' },
    { no: 3, name: 'Cọc gỗ Bạch Đằng', keys: ['coc go bach dang', 'coc bach dang', 'coc go', 'bai coc', 'yen giang'],
      short: 'Năm 1288, dưới sự chỉ huy của Quốc công tiết chế Trần Quốc Tuấn, quân dân nhà Trần bố trí bãi cọc trên sông Bạch Đằng, khai thác địa hình và thuỷ triều, tiêu diệt đạo quân thuỷ của địch. Những cọc gỗ này là chứng tích về trí tuệ và nghệ thuật quân sự của quân dân Đại Việt.',
      say: 'Năm 1288, dưới sự chỉ huy của Quốc công tiết chế Trần Quốc Tuấn, quân dân nhà Trần bố trí bãi cọc trên sông Bạch Đằng, khai thác địa hình và thuỷ triều, tiêu diệt đạo quân thuỷ của địch. Những cọc gỗ này là chứng tích về trí tuệ và nghệ thuật quân sự của quân dân Đại Việt.' },
    { no: 4, name: 'La bàn của đồng chí Võ Bẩm', keys: ['la ban', 'vo bam', 'doan 559'],
      short: 'Chiếc la bàn được Thiếu tướng Võ Bẩm, Đoàn trưởng đầu tiên của Đoàn 559, sử dụng từ năm 1959 đến năm 1967 để xác định phương hướng khi khảo sát, mở tuyến đường Trường Sơn – Đường Hồ Chí Minh.',
      say: 'Chiếc la bàn được Thiếu tướng Võ Bẩm, Đoàn trưởng đầu tiên của Đoàn năm trăm năm mươi chín, sử dụng từ năm 1959 đến năm 1967 để xác định phương hướng khi khảo sát, mở tuyến đường Trường Sơn, Đường Hồ Chí Minh.' },
    { no: 5, name: 'Xe đạp thồ', keys: ['xe dap tho', 'xe tho', 'ma van thang', 'dan cong'],
      short: 'Chiếc xe đạp thồ của dân công Ma Văn Thắng, Đoàn dân công Phú Thọ, phục vụ Chiến dịch Điện Biên Phủ. Qua 10 chuyến, anh vận chuyển được 3.700 kg hàng ra mặt trận, bình quân mỗi chuyến 370 kg.',
      say: 'Chiếc xe đạp thồ của dân công Ma Văn Thắng, Đoàn dân công Phú Thọ, phục vụ Chiến dịch Điện Biên Phủ. Qua mười chuyến, anh vận chuyển được ba nghìn bảy trăm ki lô gam hàng ra mặt trận, bình quân mỗi chuyến ba trăm bảy mươi ki lô gam.' }
  ],

  nav: {
    /* Tên điểm trên bản đồ robot (không phân biệt hoa thường / có dấu). Mỗi tư liệu nhận 1 trong các tên:
     * "Điểm N" · "Tư liệu N" · "Hiện vật N" · hoặc tên tư liệu (VD "Xe đạp thồ"). */
    prefixes: ['diem', 'tu lieu', 'hien vat'],
    standby: ['vi tri cho', 'diem cho', 'cho', 'standby'],
    arriveSay: function (t) { return 'Chúng ta đã đến khu trưng bày ' + t.name + '. ' + t.say; },
    tourStart: 'Mời Quý khách đi cùng Nova tham quan năm tư liệu tiêu biểu của chương trình Mạch nguồn văn hoá quân sự Việt Nam.',
    tourEnd: 'Chuyến tham quan đã kết thúc. Cảm ơn Quý khách đã đồng hành cùng Nova. Mời Quý khách tiếp tục tìm hiểu ở mục Xem tư liệu và Hỏi đáp.',
    go: function (t) { return 'Mời Quý khách đi theo Nova tới khu trưng bày ' + t.name + '.'; },
    fail: 'Xin lỗi Quý khách, Nova chưa đi tới đó được. Quý khách vui lòng nhờ cán bộ Bảo tàng hướng dẫn ạ.',
    pauseMs: 3000,            // nghỉ ở mỗi điểm sau khi đọc xong (tham quan lần lượt)
    returnMs: 25000           // dẫn tới 1 tư liệu xong, không ai chạm 25 s thì tự về "Vị trí chờ" (nếu bản đồ có)
  },

  ai: {
    hello: 'Kính chào Quý khách! Nova có thể trả lời các câu hỏi về lịch sử, văn hoá và du lịch Việt Nam, dựa trên tư liệu của Bảo tàng và các trang thông tin chính thống. Mời Quý khách bấm micro và đặt câu hỏi.',
    wait: 'Quý khách chờ Nova một chút ạ.',
    notHeard: 'Xin lỗi, Nova chưa nghe rõ. Quý khách vui lòng bấm micro và nói lại ạ.',
    /* Câu khách nghe khi robot KHÔNG trả lời được (ngoài phạm vi / mô hình không chắc / mất mạng / AI OrionStar lỗi) — Phương Uyên chốt 18/11/2026.
     * Lý do thật (cho kỹ thuật) hiện ở dòng thông báo trên màn hình. Câu này phải TRÙNG cau_ngoai_pham_vi trong ai-config.json. */
    cannot: 'Phần thông tin này tôi chưa được học, để tôi học và sẽ trả lời bạn sau nhé. Xin cảm ơn Quý khách.',
    /* Câu gợi ý: app tự lấy ngẫu nhiên 3 câu trong js/quiz.js (25 câu của file khách) — xem renderChips() trong app.js. */
  },

  quiz: {
    start: 'Mời Quý khách trả lời các câu hỏi về lịch sử quân sự Việt Nam. Chạm vào đáp án mà Quý khách cho là đúng.',
    right: 'Chính xác!', wrong: 'Chưa đúng.',
    end: function (s, n) { return 'Quý khách đã trả lời đúng ' + s + ' trên ' + n + ' câu. Cảm ơn Quý khách đã cùng Nova ôn lại mạch nguồn văn hoá quân sự Việt Nam!'; }
  }
};
