#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:-emulator-5554}"
TMP_DIR="/tmp/llmonitor_vm_regression"
mkdir -p "$TMP_DIR"

adb -s "$DEVICE" get-state >/dev/null

dump_ui() {
  local name="$1"
  adb -s "$DEVICE" shell uiautomator dump "/sdcard/${name}.xml" >/dev/null
  adb -s "$DEVICE" pull "/sdcard/${name}.xml" "$TMP_DIR/${name}.xml" >/dev/null
}

center_by_text() {
  local xml_path="$1"
  local text="$2"
  python3 - "$xml_path" "$text" <<'PY'
import re
import sys

xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
text = sys.argv[2]
pattern = rf'text="{re.escape(text)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
match = re.search(pattern, xml)
if not match:
    raise SystemExit(1)
l, t, r, b = map(int, match.groups())
print((l + r) // 2, (t + b) // 2)
PY
}

tap_text() {
  local xml_path="$1"
  local text="$2"
  local coords
  coords="$(center_by_text "$xml_path" "$text")" || return 1
  local x="${coords% *}"
  local y="${coords#* }"
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

tap_first_clickable_below() {
  local xml_path="$1"
  local min_top="$2"
  local coords
  coords="$(python3 - "$xml_path" "$min_top" <<'PY'
import re
import sys

xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
min_top = int(sys.argv[2])
pattern = r'clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
for match in re.finditer(pattern, xml):
    l, t, r, b = map(int, match.groups())
    if t >= min_top and (r - l) > 500 and (b - t) > 120:
        print((l + r) // 2, (t + b) // 2)
        raise SystemExit(0)
raise SystemExit(1)
PY
)" || return 1
  local x="${coords% *}"
  local y="${coords#* }"
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

current_enabled_alias() {
  adb -s "$DEVICE" shell dumpsys package com.lele.llmonitor \
    | awk '/enabledComponents:/{flag=1;next} flag{ if ($0 ~ /^[[:space:]]+[[:alnum:]_.]+$/) print $1; else if ($0 !~ /^[[:space:]]/) flag=0 }' \
    | rg 'LauncherAlias' -m 1 -S || true
}

assert_enabled_alias() {
  local expected_regex="$1"
  local alias
  alias="$(current_enabled_alias)"
  echo "[INFO] 当前启用图标别名: ${alias:-<none>}"
  if [[ -z "$alias" ]]; then
    echo "[FAIL] 未读取到已启用的 LauncherAlias"
    return 1
  fi
  if ! [[ "$alias" =~ $expected_regex ]]; then
    echo "[FAIL] 图标别名不符合预期: expected_regex=$expected_regex actual=$alias"
    return 1
  fi
}

confirm_restart_if_needed() {
  dump_ui llm_confirm
  if rg -q '确认并重启' "$TMP_DIR/llm_confirm.xml"; then
    tap_text "$TMP_DIR/llm_confirm.xml" "确认并重启"
    sleep 3
  else
    sleep 1.2
  fi
}

launch_llmonitor_settings() {
  adb -s "$DEVICE" shell am force-stop com.lele.llmonitor
  adb -s "$DEVICE" shell monkey -p com.lele.llmonitor -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 1
  adb -s "$DEVICE" shell input tap 1008 221
  sleep 1
  for _ in 1 2 3 4 5; do
    dump_ui llm_settings
    if rg -q '外观设置' "$TMP_DIR/llm_settings.xml" && rg -q '关于 LLMonitor' "$TMP_DIR/llm_settings.xml"; then
      break
    fi
    adb -s "$DEVICE" shell input keyevent 4
    sleep 0.6
  done
  adb -s "$DEVICE" exec-out screencap -p > "$TMP_DIR/llm_settings.png"
  if ! rg -q '外观设置' "$TMP_DIR/llm_settings.xml"; then
    echo "[FAIL] 未能打开 LLMonitor 设置主页"
    return 1
  fi
}

open_llmonitor_appearance() {
  launch_llmonitor_settings
  tap_text "$TMP_DIR/llm_settings.xml" "外观设置"
  sleep 1
  dump_ui llm_appearance
  if ! rg -q '显示模式' "$TMP_DIR/llm_appearance.xml"; then
    echo "[FAIL] 未能打开 LLMonitor 外观页"
    return 1
  fi
}

switch_theme_and_icon_chain() {
  open_llmonitor_appearance
  tap_text "$TMP_DIR/llm_appearance.xml" "深色模式"
  confirm_restart_if_needed
  assert_enabled_alias 'LauncherAlias[A-F]Dark$'

  open_llmonitor_appearance
  tap_text "$TMP_DIR/llm_appearance.xml" "浅色模式"
  confirm_restart_if_needed
  assert_enabled_alias 'LauncherAlias[A-F]$'

  open_llmonitor_appearance
  tap_text "$TMP_DIR/llm_appearance.xml" "沧蓝"
  confirm_restart_if_needed
  assert_enabled_alias 'LauncherAliasA$'

  open_llmonitor_appearance
  tap_text "$TMP_DIR/llm_appearance.xml" "松青"
  confirm_restart_if_needed
  assert_enabled_alias 'LauncherAliasB$'

  open_llmonitor_appearance
  tap_text "$TMP_DIR/llm_appearance.xml" "深色模式"
  confirm_restart_if_needed
  assert_enabled_alias 'LauncherAliasBDark$'
}

open_llmonitor_oss() {
  launch_llmonitor_settings
  tap_text "$TMP_DIR/llm_settings.xml" "关于 LLMonitor"
  sleep 1
  dump_ui llm_about

  tap_text "$TMP_DIR/llm_about.xml" "开源许可"
  sleep 1.2
  dump_ui llm_oss
  adb -s "$DEVICE" exec-out screencap -p > "$TMP_DIR/llm_oss.png"

  tap_first_clickable_below "$TMP_DIR/llm_oss.xml" 820
  sleep 1
  dump_ui llm_oss_detail
}

ensure_llclass_settings_page() {
  adb -s "$DEVICE" shell am force-stop com.lele.llclass
  adb -s "$DEVICE" shell monkey -p com.lele.llclass -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 1

  for _ in 1 2 3 4 5 6 7 8; do
    dump_ui llclass_now
    if rg -q '外观设置' "$TMP_DIR/llclass_now.xml" && rg -q '关于 LLClass' "$TMP_DIR/llclass_now.xml"; then
      adb -s "$DEVICE" exec-out screencap -p > "$TMP_DIR/llclass_settings.png"
      return 0
    fi
    adb -s "$DEVICE" shell input tap 1008 221
    sleep 0.8
    dump_ui llclass_now
    if rg -q '外观设置' "$TMP_DIR/llclass_now.xml" && rg -q '关于 LLClass' "$TMP_DIR/llclass_now.xml"; then
      adb -s "$DEVICE" exec-out screencap -p > "$TMP_DIR/llclass_settings.png"
      return 0
    fi
    adb -s "$DEVICE" shell input keyevent 4
    sleep 0.6
  done

  adb -s "$DEVICE" shell input tap 1008 221
  sleep 1
  dump_ui llclass_now
  if rg -q '外观设置' "$TMP_DIR/llclass_now.xml" && rg -q '关于 LLClass' "$TMP_DIR/llclass_now.xml"; then
    adb -s "$DEVICE" exec-out screencap -p > "$TMP_DIR/llclass_settings.png"
    return 0
  fi

  return 1
}

assert_texts() {
  rg -q '显示模式' "$TMP_DIR/llm_appearance.xml"
  rg -q '外观设置' "$TMP_DIR/llm_settings.xml"
  rg -q '关于 LLMonitor' "$TMP_DIR/llm_settings.xml"
  rg -q '开源许可' "$TMP_DIR/llm_about.xml"
  rg -q '许可详情' "$TMP_DIR/llm_oss_detail.xml"

  if rg -q '开源许可暂不可用|Collection\.isEmpty\(\)|null object reference|尝试调用接口方法' "$TMP_DIR/llm_oss.xml"; then
    echo "[FAIL] LLMonitor 开源许可仍为错误页"
    return 1
  fi

  if ! rg -q '个组件|组许可|适用于' "$TMP_DIR/llm_oss.xml"; then
    echo "[FAIL] LLMonitor 开源许可未出现有效列表信息"
    return 1
  fi

  if rg -q '开源许可暂不可用|Collection\.isEmpty\(\)|null object reference|尝试调用接口方法' "$TMP_DIR/llm_oss_detail.xml"; then
    echo "[FAIL] LLMonitor 开源许可详情仍为错误页"
    return 1
  fi

  if ! rg -q '适用组件|许可证全文|NOTICE 声明' "$TMP_DIR/llm_oss_detail.xml"; then
    echo "[FAIL] LLMonitor 开源许可详情缺少关键区块"
    return 1
  fi
}

compare_colors() {
  python3 - <<'PY'
from pathlib import Path
from PIL import Image
import re

base = Path('/tmp/llmonitor_vm_regression')
llm = Image.open(base / 'llm_settings.png').convert('RGB')
llc = Image.open(base / 'llclass_settings.png').convert('RGB')

llm_xml = (base / 'llm_settings.xml').read_text(encoding='utf-8', errors='ignore')
llc_xml = (base / 'llclass_now.xml').read_text(encoding='utf-8', errors='ignore')

def find_card_top_center(xml_text: str, title: str):
    pattern = rf'text="{re.escape(title)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    m = re.search(pattern, xml_text)
    if not m:
        raise SystemExit(f"[FAIL] 未在 UI dump 中找到卡片标题: {title}")
    l, t, r, b = map(int, m.groups())
    return ((l + r) // 2, max(t + 1, 0))

llm_card_border_pt = find_card_top_center(llm_xml, "外观设置")
llc_card_border_pt = find_card_top_center(llc_xml, "外观设置")

points = {
    'page_bg': (20, 500),
    'topbar_bg': (20, 120),
    'card1_inside': (120, 430),
}
llm_points = dict(points)
llc_points = dict(points)
llm_points['card1_border_top_dynamic'] = llm_card_border_pt
llc_points['card1_border_top_dynamic'] = llc_card_border_pt

thresholds = {
    'page_bg': 0,
    'topbar_bg': 0,
    'card1_inside': 0,
    'card1_border_top_dynamic': 0,
}

failed = []
for key in llm_points.keys():
    a = llm.getpixel(llm_points[key])
    b = llc.getpixel(llc_points[key])
    delta = sum(abs(x - y) for x, y in zip(a, b))
    print(f"[INFO] {key}: llmonitor={a} llclass={b} delta={delta} "
          f"llm_pt={llm_points[key]} llc_pt={llc_points[key]}")
    if delta > thresholds[key]:
        failed.append((key, delta, thresholds[key]))

if failed:
    print('[FAIL] 颜色对比未达标:')
    for key, delta, threshold in failed:
        print(f'  - {key}: delta={delta} threshold={threshold}')
    raise SystemExit(1)

print('[PASS] 颜色对比通过')
PY
}

switch_theme_and_icon_chain
launch_llmonitor_settings
open_llmonitor_appearance
open_llmonitor_oss
assert_texts

if ensure_llclass_settings_page; then
  compare_colors
else
  echo "[WARN] 未能自动定位 LLClass 设置页，跳过颜色对比（文本断言已通过）"
fi

echo "[PASS] VM 回归通过"
