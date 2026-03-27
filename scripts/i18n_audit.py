#!/usr/bin/env python3
import argparse
import re
import sys
from pathlib import Path


ALLOWED_UNMANAGED_HAN = {
    "简体中文（中国）",
    "繁體中文（中國香港）",
    "繁體中文（中國台灣）",
}

# User-required wording exception.
ALLOWED_S2T_DIFF_VALUES = {
    "繁體中文（中國台灣）",
}


def extract_map_block(text: str, name: str) -> list[tuple[str, str]]:
    match = re.search(
        rf"private val {name}: Map<String, String> = mapOf\((.*?)\n\)",
        text,
        re.S,
    )
    if not match:
        return []
    return re.findall(r"\"([^\"]+)\"\s+to\s+\"([^\"]+)\"", match.group(1))


def sample_expand_dynamic(template: str) -> str:
    s = re.sub(r"\$\{[^}]+\}", "1", template)
    s = re.sub(r"\$[A-Za-z_][A-Za-z0-9_]*", "1", s)
    return s


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit i18n coverage and leaks.")
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Project root path",
    )
    parser.add_argument(
        "--require-opencc",
        action="store_true",
        help="Fail if OpenCC is unavailable",
    )
    args = parser.parse_args()

    root = args.root.resolve()
    src_root = root / "app/src/main/java/com/lele/llmonitor"
    i18n_file = src_root / "i18n/TextI18n.kt"
    if not i18n_file.exists():
        print(f"[ERROR] Missing file: {i18n_file}")
        return 2

    text_i18n = i18n_file.read_text(encoding="utf-8")
    all_kts = sorted(src_root.rglob("*.kt"))

    zh_hant_entries = extract_map_block(text_i18n, "zhHantMap")
    en_entries = extract_map_block(text_i18n, "enMap")
    zh_hk_overrides = extract_map_block(text_i18n, "zhHkOverrides")
    zh_tw_overrides = extract_map_block(text_i18n, "zhTwOverrides")

    zh_hant_keys = {k for k, _ in zh_hant_entries}
    en_keys = {k for k, _ in en_entries}

    all_l10n_calls: set[str] = set()
    static_l10n_calls: set[str] = set()
    dynamic_l10n_calls: set[str] = set()

    l10n_call_pattern = re.compile(r'l10n\("([^"]+)"\)')
    han_literal_pattern = re.compile(r'"[^"\n]*[\u4e00-\u9fff][^"\n]*"')

    unmanaged_han_literals: list[tuple[str, int, str]] = []
    for file_path in all_kts:
        raw = file_path.read_text(encoding="utf-8")

        for match in l10n_call_pattern.finditer(raw):
            content = match.group(1)
            all_l10n_calls.add(content)
            if "$" in content:
                dynamic_l10n_calls.add(content)
            else:
                static_l10n_calls.add(content)

        if file_path == i18n_file:
            continue

        for match in han_literal_pattern.finditer(raw):
            literal = match.group(0)[1:-1]
            if literal in ALLOWED_UNMANAGED_HAN:
                continue
            start_ctx = max(0, match.start() - 20)
            end_ctx = min(len(raw), match.end() + 2)
            if 'l10n("' in raw[start_ctx:end_ctx]:
                continue
            line = raw.count("\n", 0, match.start()) + 1
            unmanaged_han_literals.append((str(file_path.relative_to(root)), line, literal))

    missing_static_in_en = sorted(static_l10n_calls - en_keys)
    missing_static_in_hant = sorted(static_l10n_calls - zh_hant_keys)

    regex_defs = [
        re.compile(m.group(2))
        for m in re.finditer(r'private val (pattern\w+) = Regex\("""(.*?)"""\)', text_i18n)
    ]
    uncovered_dynamic: list[tuple[str, str]] = []
    for raw in sorted(dynamic_l10n_calls):
        sample = sample_expand_dynamic(raw)
        if not any(regex.fullmatch(sample) for regex in regex_defs):
            uncovered_dynamic.append((raw, sample))

    simplified_hits: list[tuple[str, str, str, str]] = []
    opencc_available = True
    try:
        from opencc import OpenCC  # type: ignore

        cc = OpenCC("s2t")
        for group, entries in (
            ("zhHantMap", zh_hant_entries),
            ("zhHkOverrides", zh_hk_overrides),
            ("zhTwOverrides", zh_tw_overrides),
        ):
            for key, value in entries:
                converted = cc.convert(value)
                if converted != value and value not in ALLOWED_S2T_DIFF_VALUES:
                    simplified_hits.append((group, key, value, converted))
    except Exception:
        opencc_available = False

    print("=== I18N AUDIT ===")
    print(f"kotlin_files={len(all_kts)}")
    print(
        f"l10n_calls: all={len(all_l10n_calls)} static={len(static_l10n_calls)} dynamic={len(dynamic_l10n_calls)}"
    )
    print(f"map_keys: en={len(en_keys)} zhHant={len(zh_hant_keys)}")
    print(f"missing_static_in_en={len(missing_static_in_en)}")
    print(f"missing_static_in_zhHant={len(missing_static_in_hant)}")
    print(f"unmanaged_han_literals={len(unmanaged_han_literals)}")
    print(f"uncovered_dynamic_templates={len(uncovered_dynamic)}")
    if opencc_available:
        print(f"simplified_hits_in_traditional_maps={len(simplified_hits)}")
    else:
        print("simplified_check=skipped (opencc unavailable)")

    if missing_static_in_en:
        print("\n-- missing_static_in_en --")
        for item in missing_static_in_en:
            print(item)

    if missing_static_in_hant:
        print("\n-- missing_static_in_zhHant --")
        for item in missing_static_in_hant:
            print(item)

    if unmanaged_han_literals:
        print("\n-- unmanaged_han_literals --")
        for file_path, line, literal in unmanaged_han_literals:
            print(f"{file_path}:{line}: {literal}")

    if uncovered_dynamic:
        print("\n-- uncovered_dynamic_templates --")
        for raw, sample in uncovered_dynamic:
            print(f"{raw} [sample={sample}]")

    if simplified_hits:
        print("\n-- simplified_hits_in_traditional_maps --")
        for group, key, value, converted in simplified_hits:
            print(f"{group}: {key} => {value} || s2t={converted}")

    hard_fail = (
        bool(missing_static_in_en)
        or bool(missing_static_in_hant)
        or bool(unmanaged_han_literals)
        or bool(uncovered_dynamic)
        or bool(simplified_hits)
    )

    if args.require_opencc and not opencc_available:
        print("\n[ERROR] OpenCC unavailable. Install with: pip install opencc-python-reimplemented")
        return 2

    return 1 if hard_fail else 0


if __name__ == "__main__":
    sys.exit(main())
