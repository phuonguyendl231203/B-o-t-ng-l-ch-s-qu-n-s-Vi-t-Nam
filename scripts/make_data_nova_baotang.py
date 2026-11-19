"""Sinh dữ liệu cho app Nova · Bảo tàng Lịch sử Quân sự Việt Nam ("Mạch nguồn văn hoá quân sự Việt Nam").

Đọc THẲNG file khách gửi, không chép tay:
  - 25 câu trắc nghiệm: 02_BaoGia_HopDong/BaoTang-LSQS-VN/04.11.2026 Nội dung câu hỏi tích hợp robot. sửa.docx
    (đáp án đúng = phương án khách IN ĐẬM; mỗi câu phải có đúng 1 phương án in đậm, sai là script dừng)
  - 5 tư liệu standee:  03_Media/Hinh-anh/BaoTang-LSQS/Standee điện DS quân sự.pdf  (mỗi trang 1 hiện vật)
Ghi ra: website-nova-baotang-app/js/quiz.js  và  js/tulieu.js  (KHÔNG sửa tay 2 file này — sửa file gốc hoặc bảng SUA_LOI rồi chạy lại).
Ảnh: website-nova-baotang-app/img/tlN.jpg (cả trang standee) + tlN-anh.jpg (vùng ảnh hiện vật) + tlN-nho.jpg.

  python make_data_nova_baotang.py
"""
import json, re, sys
from pathlib import Path

import docx
import pymupdf
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]          # D:/Phương Uyên Robot
WEB = ROOT / "05_Templates_CongCu/Roboworld-Design-Kit/website-nova-baotang-app"
DOCX = ROOT / "02_BaoGia_HopDong/BaoTang-LSQS-VN/04.11.2026 Nội dung câu hỏi tích hợp robot. sửa.docx"
PDF = ROOT / "03_Media/Hinh-anh/BaoTang-LSQS/Standee điện DS quân sự.pdf"

# Lỗi gõ RÕ RÀNG trong file khách — sửa khi hiển thị, liệt kê trong README để báo khách.
SUA_LOI = [
    ("Tuyền truyền", "Tuyên truyền"),
    ("tổ chức  của", "tổ chức của"),
    ("Quân đội nhân dân Việt Nam..", "Quân đội nhân dân Việt Nam."),
    ("bắn. theo truyền thuyết", "bắn. Theo truyền thuyết"),
    ("Súng thần cơ do Hồ Nguyên Trừng", "Súng thần công do Hồ Nguyên Trừng"),   # Câu 16 — Phương Uyên chốt 11/11/2026 (khớp phần Thông tin bổ sung)
    ("Lạc. theo truyền thuyết", "Lạc. Theo truyền thuyết"),
]


def sach(s: str) -> str:
    s = s.replace("\u00a0", " ")
    for a, b in SUA_LOI:
        s = s.replace(a, b)
    s = re.sub(r"[ \t]+", " ", s).strip()
    return s


def doc_quiz():
    d = docx.Document(str(DOCX))
    rows = d.tables[0].rows[1:]
    out = []
    for r in rows:
        so = r.cells[0].text.strip()
        # Tách theo DÒNG (có câu khách để câu hỏi + phương án A chung 1 đoạn, ngắt dòng mềm — Câu 9)
        dong = []   # [(chữ, có_in_đậm)]
        for p in r.cells[1].paragraphs:
            cur, bold = "", False
            for run in p.runs:
                parts = run.text.split("\n")
                for k, part in enumerate(parts):
                    if k > 0:
                        dong.append((cur, bold)); cur, bold = "", False
                    cur += part
                    if run.bold and part.strip():
                        bold = True
            dong.append((cur, bold))
        q, opts, ok, why, in_why = [], [], [], [], False
        for raw, bold in dong:
            if not raw.strip():
                continue
            t = sach(raw)
            m = re.match(r"^([A-D])\.\s*(.*)$", t)
            if in_why:
                why.append(t)
            elif re.match(r"^Thông tin bổ sung", t, re.I):
                in_why = True
                rest = re.sub(r"^Thông tin bổ sung\s*:?\s*", "", t, flags=re.I)
                if rest:
                    why.append(rest)
            elif m and len(opts) < 4:
                txt = m.group(2).strip()
                txt = txt[0].upper() + txt[1:] if txt else txt
                if bold:
                    ok.append(len(opts))
                opts.append(txt.rstrip("."))
            else:
                q.append(t)
        if len(opts) != 4 or len(ok) != 1:
            sys.exit(f"{so}: tìm thấy {len(opts)} phương án, {len(ok)} phương án in đậm — kiểm tra lại file docx")
        out.append({"no": int(re.sub(r"\D", "", so)), "q": " ".join(q), "opts": opts, "ok": ok[0], "why": " ".join(why)})
    return out


TEN_TL = ["NỎ LIÊN CHÂU", "ĐỊA ĐẠO CỦ CHI", "CỌC GỖ BẠCH ĐẰNG", "LA BÀN CỦA ĐỒNG CHÍ VÕ BẨM", "XE ĐẠP THỒ"]
BO_DONG = ("CHƯƠNG TRÌNH TRUYỀN THÔNG", "MẠCH NGUỒN VĂN HOÁ QUÂN SỰ")


def doc_tulieu():
    d = pymupdf.open(str(PDF))
    out = []
    for i, p in enumerate(d):
        lines = [l.rstrip() for l in p.get_text().split("\n")]
        ten = next((t for t in TEN_TL if any(l.strip() == t for l in lines)), None)
        if not ten:
            sys.exit(f"Trang {i+1}: không thấy tên tư liệu")
        paras, cur = [], []
        for l in lines:
            s = l.strip()
            if not s or s == ten or s.startswith(BO_DONG):
                continue
            # Đoạn mới bắt đầu bằng 3 dấu cách thụt đầu dòng (cách standee dàn trang)
            if l.startswith("   ") and cur:
                paras.append(" ".join(cur)); cur = []
            cur.append(s)
        if cur:
            paras.append(" ".join(cur))
        # "Hộp lẫy có mặt…" trên standee 1 không thụt đầu dòng nhưng là đoạn riêng
        fixed = []
        for para in paras:
            fixed += [x for x in re.split(r"(?<=\.) (?=Hộp lẫy có mặt)", para)]
        out.append({"no": i + 1, "title": ten, "paras": [sach(x) for x in fixed]})

        # Ảnh: cả trang (xem phóng to) + vùng ảnh hiện vật (thẻ danh sách)
        pix = p.get_pixmap(matrix=pymupdf.Matrix(1000 / p.rect.width, 1000 / p.rect.width))
        im = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        im.save(WEB / f"img/tl{i+1}.jpg", quality=82, optimize=True)
        w, h = im.size
        anh = im.crop((0, int(h * 0.165), w, int(h * 0.60)))
        anh.thumbnail((760, 760)); anh.save(WEB / f"img/tl{i+1}-anh.jpg", quality=82, optimize=True)
        nho = im.copy(); nho.thumbnail((300, 600)); nho.save(WEB / f"img/tl{i+1}-nho.jpg", quality=80)
    return out


def ghi(name, var, data, nguon):
    js = (f"/* TỰ SINH bằng scripts/make_data_nova_baotang.py từ {nguon} — KHÔNG sửa tay. */\n"
          f"window.{var} = " + json.dumps(data, ensure_ascii=False, indent=1) + ";\n")
    (WEB / "js" / name).write_text(js, encoding="utf-8")


if __name__ == "__main__":
    qz = doc_quiz()
    ghi("quiz.js", "QUIZ", qz, DOCX.name)
    tl = doc_tulieu()
    ghi("tulieu.js", "TULIEU", tl, PDF.name)
    print(f"{len(qz)} câu trắc nghiệm · {len(tl)} tư liệu")
    for q in qz:
        print(f"  Câu {q['no']:>2}: đáp án {'ABCD'[q['ok']]} — {q['opts'][q['ok']][:50]}")
    for t in tl:
        print(f"  TL{t['no']} {t['title']}: {len(t['paras'])} đoạn, {sum(len(x) for x in t['paras'])} ký tự")
