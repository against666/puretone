#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""lrclib_fetch.py —— 为本地音频抓取同步歌词，生成同名 .lrc

零第三方依赖，只用标准库。做三件事：
  1. 读 MP3 的 ID3 标签（歌名/歌手/专辑）和精确时长（优先 Xing/Info 帧数）
  2. 拿这些元数据去 LRCLIB 检索，按「时长差 + 专辑匹配」挑最合适的那版同步歌词
  3. 写成 <音频同名>.lrc，UTF-8 无 BOM / CRLF，可加 offset

用法示例：
  python lrclib_fetch.py "D:\\下载的文件\\搁浅-周杰伦.mp3"
  python lrclib_fetch.py -d "D:\\Music" --dry-run
  python lrclib_fetch.py song.mp3 --offset 1500 --force
  python lrclib_fetch.py song.flac --title 搁浅 --artist 周杰伦 --duration 238

匹配到的歌词来自 LRCLIB（https://lrclib.net），一个社区维护的公开歌词库。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API_BASE = "https://lrclib.net/api"
USER_AGENT = "PureTone-lrclib_fetch/1.0 (local lyrics tool)"
AUDIO_EXTS = {".mp3", ".flac", ".m4a", ".ogg", ".opus", ".wav", ".aac", ".wma"}

# ---------------------------------------------------------------- 文本工具


def out(msg: str = "") -> None:
    print(msg)


def decode_text(raw: bytes) -> str:
    """ID3 文本帧：首字节是编码标记，其后才是正文。"""
    if not raw:
        return ""
    enc, body = raw[0], raw[1:]
    # 去掉结尾的 \x00 填充
    body = body.rstrip(b"\x00")
    table = {0: "latin-1", 2: "utf-16-be", 3: "utf-8"}
    if enc == 1:
        text = body.decode("utf-16", "replace")
    else:
        text = body.decode(table.get(enc, "utf-8"), "replace")
    # ID3v2.4 允许多值，用 \x00 分隔
    return text.replace("\x00", " / ").strip()


def syncsafe(b: bytes, i: int = 0) -> int:
    return ((b[i] & 0x7F) << 21) | ((b[i + 1] & 0x7F) << 14) | ((b[i + 2] & 0x7F) << 7) | (b[i + 3] & 0x7F)


# ---------------------------------------------------------------- MP3 解析

ID3_FRAME_MAP = {
    "TIT2": "title",
    "TPE1": "artist",
    "TALB": "album",
    "TRCK": "track",
    "TDRC": "year",
    "TYER": "year",
}

LAYER3_BITRATES = {
    1: [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320],
    2: [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
}
SAMPLE_RATES = {1: [44100, 48000, 32000], 2: [22050, 24000, 16000], 25: [11025, 12000, 8000]}


def read_id3v2(data: bytes) -> tuple[dict, int]:
    """返回 (标签字典, 音频起始偏移)。不是 ID3v2 则偏移为 0。"""
    tags: dict[str, str] = {}
    if len(data) < 10 or data[:3] != b"ID3":
        return tags, 0
    major = data[3]
    flags = data[5]
    size = syncsafe(data, 6)
    total = 10 + size + (10 if (major == 4 and flags & 0x10) else 0)
    off = 10
    if flags & 0x40:  # 扩展头
        if major >= 4:
            off += syncsafe(data, off)
        else:
            off += 4 + int.from_bytes(data[off:off + 4], "big")
    end = min(10 + size, len(data))
    while off + 10 <= end:
        fid = data[off:off + 4]
        try:
            fsid = fid.decode("ascii")
        except UnicodeDecodeError:
            break
        if not re.fullmatch(r"[A-Z0-9]{4}", fsid):
            break
        fsize = syncsafe(data, off + 4) if major >= 4 else int.from_bytes(data[off + 4:off + 8], "big")
        if fsize <= 0 or off + 10 + fsize > end:
            break
        key = ID3_FRAME_MAP.get(fsid)
        if key and key not in tags:
            tags[key] = decode_text(data[off + 10:off + 10 + fsize])
        off += 10 + fsize
    return tags, total


def read_id3v1(data: bytes) -> dict:
    """尾部 128 字节的老式标签，GBK/latin1 混用，仅作兜底。"""
    tags: dict[str, str] = {}
    if len(data) < 128 or data[-128:-125] != b"TAG":
        return tags
    fields = {"title": (3, 33), "artist": (33, 63), "album": (63, 93)}
    for key, (a, b) in fields.items():
        raw = data[-128 + a:-128 + b].split(b"\x00")[0]
        if not raw:
            continue
        for enc in ("utf-8", "gbk", "latin-1"):
            try:
                tags[key] = raw.decode(enc).strip()
                break
            except UnicodeDecodeError:
                continue
    return tags


def tail_tag_size(data: bytes) -> int:
    size = 0
    if len(data) >= 128 and data[-128:-125] == b"TAG":
        size += 128
    idx = data.rfind(b"APETAGEX", max(0, len(data) - 1024))
    if idx >= 0:
        try:
            tag_size = int.from_bytes(data[idx + 12:idx + 16], "little")
            size += tag_size + 32
        except Exception:
            pass
    return size


def parse_frame_header(h: bytes):
    if len(h) < 4 or h[0] != 0xFF or (h[1] & 0xE0) != 0xE0:
        return None
    ver_bits = (h[1] >> 3) & 0x03
    layer_bits = (h[1] >> 1) & 0x03
    if ver_bits == 1 or layer_bits != 1:  # 只认 Layer III
        return None
    ver = {3: 1, 2: 2, 0: 25}[ver_bits]
    br_idx = (h[2] >> 4) & 0x0F
    sr_idx = (h[2] >> 2) & 0x03
    if br_idx in (0, 15) or sr_idx == 3:
        return None
    br_table = LAYER3_BITRATES[1] if ver == 1 else LAYER3_BITRATES[2]
    return {
        "version": ver,
        "bitrate": br_table[br_idx] * 1000,
        "samplerate": SAMPLE_RATES[ver][sr_idx],
        "padding": (h[2] >> 1) & 0x01,
        "spf": 1152 if ver == 1 else 576,
    }


def mp3_duration(path: Path) -> tuple[float, dict]:
    """返回 (秒, 信息字典)。优先用 Xing/Info 的帧数，退化为 CBR 估算。"""
    data = path.read_bytes()
    tags, audio_off = read_id3v2(data)
    tags.update({k: v for k, v in read_id3v1(data).items() if k not in tags})

    info: dict = {"tags": tags, "vbr": False, "mode": "unknown"}
    # 在前 64KB 内找第一个合法帧
    first = None
    for i in range(audio_off, min(len(data) - 4, audio_off + 65536)):
        if data[i] != 0xFF:
            continue
        hdr = parse_frame_header(data[i:i + 4])
        if hdr:
            first = (i, hdr)
            break
    if not first:
        return 0.0, info

    off, hdr = first
    info.update({k: hdr[k] for k in ("bitrate", "samplerate", "version")})
    frame_len = (144 if hdr["version"] == 1 else 72) * hdr["bitrate"] // hdr["samplerate"] + hdr["padding"]

    # Xing / Info / VBRI：位于首帧的辅助数据区
    head = data[off:off + max(frame_len, 200)]
    for marker in (b"Xing", b"Info"):
        p = head.find(marker)
        if p >= 0 and p + 8 <= len(head):
            flags = int.from_bytes(head[p + 4:p + 8], "big")
            if flags & 0x01:
                frames = int.from_bytes(head[p + 8:p + 12], "big")
                if frames > 0:
                    info["vbr"] = marker == b"Xing"
                    info["mode"] = "VBR" if info["vbr"] else "CBR(Info头)"
                    return frames * hdr["spf"] / hdr["samplerate"], info
    p = head.find(b"VBRI")
    if p >= 0 and p + 18 <= len(head):
        frames = int.from_bytes(head[p + 14:p + 18], "big")
        if frames > 0:
            info["vbr"] = True
            info["mode"] = "VBR(VBRI)"
            return frames * hdr["spf"] / hdr["samplerate"], info

    # 退化：按码率估算
    audio_bytes = len(data) - off - tail_tag_size(data)
    info["mode"] = "CBR估算"
    return max(0.0, audio_bytes * 8 / hdr["bitrate"]), info


# ---------------------------------------------------------------- LRCLIB


def api_get(endpoint: str, params: dict, timeout: int = 15):
    url = f"{API_BASE}/{endpoint}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_candidates(title: str, artist: str, album: str) -> list[dict]:
    queries = []
    if title and artist and album:
        queries.append({"track_name": title, "artist_name": artist, "album_name": album})
    if title and artist:
        queries.append({"track_name": title, "artist_name": artist})
    if title:
        queries.append({"q": f"{artist} {title}".strip()})

    seen: dict[int, dict] = {}
    for q in queries:
        try:
            for item in api_get("search", q) or []:
                seen.setdefault(item.get("id"), item)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                continue
            raise
        if len(seen) >= 3:
            break
    return list(seen.values())


def pick_best(cands: list[dict], duration: float, album: str, tolerance: float) -> tuple[dict | None, list[str]]:
    """按时长差排序；专辑/歌名越接近越优先。返回 (最佳条目, 落选说明)。"""
    notes: list[str] = []
    scored = []
    for c in cands:
        if not c.get("syncedLyrics"):
            notes.append(f"  - id={c.get('id')} {c.get('artistName')} / {c.get('trackName')} "
                         f"({c.get('albumName')}) 只有纯文本，跳过")
            continue
        d = c.get("duration") or 0
        delta = abs(d - duration) if duration > 0 and d > 0 else 999
        if duration > 0 and delta > tolerance:
            notes.append(f"  - id={c.get('id')} {c.get('artistName')} / {c.get('trackName')} 时长 {d:.0f}s "
                         f"与本地差 {delta:.0f}s，超出容差")
            continue
        album_ok = bool(album) and album.strip() == (c.get("albumName") or "").strip()
        scored.append((delta, 0 if album_ok else 1, len(c.get("syncedLyrics") or ""), c))
    if not scored:
        return None, notes
    scored.sort(key=lambda x: (x[0], x[1], -x[2]))
    return scored[0][3], notes


# ---------------------------------------------------------------- LRC 写出

TS_RE = re.compile(r"\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]")


def ts_to_ms(m: str, s: str, frac: str | None) -> int:
    """[mm:ss.xx] 的 xx 是百分秒；也兼容 [mm:ss.xxx]。"""
    ms = int(m) * 60000 + int(s) * 1000
    if frac:
        ms += int((frac + "000")[:3]) if len(frac) >= 3 else int(frac.ljust(2, "0")) * 10
    return ms


def check_timeline(text: str) -> tuple[int, list[str]]:
    stamps = []
    for line in text.splitlines():
        for m in TS_RE.finditer(line):
            stamps.append(ts_to_ms(*m.groups()))
    problems = []
    for a, b in zip(stamps, stamps[1:]):
        if b < a - 50:
            problems.append(f"时间戳回退：{a}ms → {b}ms")
    return len(stamps), problems


def build_lrc(synced: str, tags: dict, offset_ms: int, with_header: bool) -> str:
    normalized = synced.strip("\ufeff").replace("\r\n", "\n").replace("\r", "\n")
    body = [ln.rstrip() for ln in normalized.strip("\n").split("\n")]
    lines: list[str] = []
    if with_header:
        for key, tag in (("title", "ti"), ("artist", "ar"), ("album", "al")):
            if tags.get(key):
                lines.append(f"[{tag}:{tags[key]}]")
        if offset_ms:
            lines.append(f"[offset:{offset_ms}]")
        lines.append("")
    lines.extend(body)
    # 全文统一 CRLF，避免个别播放器/车机解析到混用的换行
    return "\r\n".join(lines) + "\r\n"


def write_lrc(path: Path, text: str, encoding: str) -> None:
    path.write_bytes(text.encode(encoding, "replace"))


# ---------------------------------------------------------------- 主流程


def process(audio: Path, args) -> bool:
    title = args.title or ""
    artist = args.artist or ""
    album = args.album or ""
    duration = args.duration or 0.0

    if audio.suffix.lower() == ".mp3":
        duration, info = mp3_duration(audio)
        t = info.get("tags", {})
        title = title or t.get("title", "")
        artist = artist or t.get("artist", "")
        album = album or t.get("album", "")
        detail = f"{info.get('bitrate', 0)//1000} kbps / {info.get('samplerate', 0)} Hz / {info.get('mode', '?')}"
    else:
        detail = "非 MP3，时长靠参数或标签"

    out(f"\n文件  {audio}")
    out(f"标签  {artist or '?'} — {title or '?'}  《{album or '?'}》")
    out(f"时长  {duration:.1f}s    {detail}")

    if not title:
        out("× 拿不到歌名。用 --title/--artist 手工指定。")
        return False

    lrc_path = audio.with_suffix(".lrc")
    if lrc_path.exists() and not args.force:
        out(f"= 已存在 {lrc_path.name}，跳过（加 --force 覆盖）")
        return False

    cands = fetch_candidates(title, artist, album)
    out(f"检索  LRCLIB 命中 {len(cands)} 条")
    best, notes = pick_best(cands, duration, album, args.tolerance)
    for n in notes[:6]:
        out(n)
    if not best:
        out("× 没有可用的同步歌词")
        return False

    delta = abs((best.get("duration") or 0) - duration) if duration else 0
    out(f"选优  id={best['id']}  {best.get('artistName')} / {best.get('trackName')} "
        f"《{best.get('albumName')}》 {best.get('duration')}s  相差 {delta:.1f}s")

    offset = args.offset
    if not args.offset and delta > 1.5 and duration > 0:
        diff = duration - (best.get("duration") or 0)
        suggest = int(round(diff * 1000))
        out(f"提示  两者时长差 {diff:+.1f}s，若歌词整体偏移可加 --offset {suggest}")

    text = build_lrc(best["syncedLyrics"], {"title": title, "artist": artist, "album": album},
                     offset, not args.no_header)
    count, problems = check_timeline(text)
    out(f"时间轴 {count} 个时间标签" + (f"，发现 {len(problems)} 处异常" if problems else "，递增正常"))
    for p in problems[:3]:
        out(f"  ! {p}")

    if args.dry_run:
        preview = [ln for ln in text.splitlines() if TS_RE.search(ln)][:3]
        out("预演  未写盘。前 3 行时间标签：")
        for ln in preview:
            out(f"      {ln}")
        return True

    write_lrc(lrc_path, text, args.encoding)
    out(f"写入  {lrc_path}  ({lrc_path.stat().st_size} B, {args.encoding})")
    return True


def collect(paths, dirs) -> list[Path]:
    files: list[Path] = []
    for d in dirs:
        p = Path(d)
        if p.is_dir():
            files += sorted(f for f in p.rglob("*") if f.suffix.lower() in AUDIO_EXTS)
    for p in map(Path, paths):
        if p.is_dir():
            files += sorted(f for f in p.rglob("*") if f.suffix.lower() in AUDIO_EXTS)
        elif p.exists():
            files.append(p)
        else:
            out(f"× 路径不存在：{p}")
    return files


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    ap = argparse.ArgumentParser(
        description="从 LRCLIB 抓取同步歌词，生成同名 .lrc",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="示例：python lrclib_fetch.py -d \"D:\\Music\" --dry-run",
    )
    ap.add_argument("paths", nargs="*", help="音频文件或目录")
    ap.add_argument("-d", "--dir", action="append", default=[], help="批量扫描的目录，可重复")
    ap.add_argument("--dry-run", action="store_true", help="只查询和校验，不写文件")
    ap.add_argument("--force", action="store_true", help="覆盖已存在的 .lrc")
    ap.add_argument("--offset", type=int, default=0, help="写入 [offset:N] 毫秒，正值让歌词延后")
    ap.add_argument("--tolerance", type=float, default=8.0, help="允许的时长差（秒），默认 8")
    ap.add_argument("--encoding", default="utf-8", choices=["utf-8", "gbk", "utf-8-sig"],
                    help="输出编码，老车机可试 gbk")
    ap.add_argument("--no-header", action="store_true", help="不写 [ti:][ar:][al:] 头部")
    ap.add_argument("--title"), ap.add_argument("--artist"), ap.add_argument("--album")
    ap.add_argument("--duration", type=float, default=0.0, help="手工指定时长（秒）")
    args = ap.parse_args()

    files = collect(args.paths, args.dir)
    if not files:
        ap.print_help()
        return 1

    out(f"待处理 {len(files)} 个文件")
    ok = 0
    for i, f in enumerate(files):
        try:
            if process(f, args):
                ok += 1
        except urllib.error.URLError as e:
            out(f"× 网络失败：{e}")
        except Exception as e:  # 单个文件出错不影响整批
            out(f"× {type(e).__name__}: {e}")
        if i < len(files) - 1:
            time.sleep(0.4)

    out(f"\n完成：{ok}/{len(files)} 成功")
    return 0 if ok == len(files) else 2


if __name__ == "__main__":
    raise SystemExit(main())
