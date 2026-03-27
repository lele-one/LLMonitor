#!/usr/bin/env bash
set -euo pipefail

LLCLASS_ROOT="/Users/lele/AndroidStudioProjects/LLClass2"
LLMONITOR_ROOT="/Users/lele/AndroidStudioProjects/LLPower"
TMP_DIR="/tmp/llmonitor_isomorphic_guard"
mkdir -p "$TMP_DIR"

normalize() {
  sed \
    -e 's/com\.lele\.llclass/com.lele.llmonitor/g' \
    -e 's/package com\.lele\.llmonitor\.feature\.settings/package com.lele.llmonitor.ui.settings/g' \
    -e 's/com\.lele\.llmonitor\.feature\.settings/com.lele.llmonitor.ui.settings/g' \
    -e 's/LLClass/LLMonitor/g' \
    -e 's/ic_llclass_exact/ic_llmonitor_exact/g' \
    -e 's/Theme\.LLClassApp/Theme.LLMonitor/g' \
    -e 's/Base\.Theme\.LLClassApp/Base.Theme.LLMonitor/g' \
    -e 's/com\.lele\.llmonitor\.ui\.components\.NavigationBarBottomInsetSpacer/com.lele.llclass.ui.components.NavigationBarBottomInsetSpacer/g'
}

check_pair() {
  local src="$1"
  local dst="$2"
  local name="$3"
  local src_norm="$TMP_DIR/${name}.src.norm"
  local dst_norm="$TMP_DIR/${name}.dst.norm"

  normalize < "$src" > "$src_norm"
  normalize < "$dst" > "$dst_norm"

  if ! diff -u "$src_norm" "$dst_norm" > "$TMP_DIR/${name}.diff"; then
    echo "[FAIL] $name differs after normalization"
    cat "$TMP_DIR/${name}.diff"
    exit 1
  fi
  echo "[PASS] $name"
}

check_pair \
  "$LLCLASS_ROOT/app/src/main/java/com/lele/llclass/feature/settings/SettingsUiKit.kt" \
  "$LLMONITOR_ROOT/app/src/main/java/com/lele/llmonitor/ui/settings/SettingsUiKit.kt" \
  "settings_uikit"

check_pair \
  "$LLCLASS_ROOT/app/src/main/java/com/lele/llclass/feature/settings/AboutScreenPage.kt" \
  "$LLMONITOR_ROOT/app/src/main/java/com/lele/llmonitor/ui/settings/AboutScreenPage.kt" \
  "about_screen"

check_pair \
  "$LLCLASS_ROOT/app/src/main/java/com/lele/llclass/feature/settings/OpenSourceLicensesScreenPage.kt" \
  "$LLMONITOR_ROOT/app/src/main/java/com/lele/llmonitor/ui/settings/OpenSourceLicensesScreenPage.kt" \
  "open_source_screen"

check_pair \
  "$LLCLASS_ROOT/app/src/main/java/com/lele/llclass/feature/settings/OpenSourceLicensesRepository.kt" \
  "$LLMONITOR_ROOT/app/src/main/java/com/lele/llmonitor/ui/settings/OpenSourceLicensesRepository.kt" \
  "open_source_repo"

# Hard guard for manual tuning in shared card kit.
if rg -n "accentColor\.copy\\(alpha = 0\\.36f\\)" "$LLMONITOR_ROOT/app/src/main/java/com/lele/llmonitor/ui/settings/SettingsUiKit.kt" >/dev/null; then
  echo "[FAIL] Detected non-isomorphic tuning markers in SettingsUiKit"
  exit 1
fi

echo "[PASS] Isomorphic guard passed"
