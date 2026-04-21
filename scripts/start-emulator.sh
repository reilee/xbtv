#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.xiaobaotv.app"
MAIN_ACTIVITY=".MainActivity"
AVD_NAME="${AVD_NAME:-xiaobaotv_tv}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}}"
EMULATOR_BIN="${SDK_ROOT}/emulator/emulator"
ADB_BIN="${ADB_BIN:-${SDK_ROOT}/platform-tools/adb}"
GRADLEW="${ROOT_DIR}/gradlew"

if [[ ! -x "${EMULATOR_BIN}" ]]; then
  echo "找不到 emulator: ${EMULATOR_BIN}" >&2
  exit 1
fi

if [[ ! -x "${ADB_BIN}" ]]; then
  echo "找不到 adb: ${ADB_BIN}" >&2
  exit 1
fi

if [[ ! -x "${GRADLEW}" ]]; then
  echo "找不到 gradlew: ${GRADLEW}" >&2
  exit 1
fi

if ! "${EMULATOR_BIN}" -list-avds | rg -q "^${AVD_NAME}$"; then
  echo "未找到 AVD: ${AVD_NAME}" >&2
  echo "可用 AVD:" >&2
  "${EMULATOR_BIN}" -list-avds >&2 || true
  exit 1
fi

if ! "${ADB_BIN}" devices | rg -q '^emulator-[0-9]+\s+device$'; then
  echo "启动模拟器: ${AVD_NAME}"
  nohup "${EMULATOR_BIN}" \
    -avd "${AVD_NAME}" \
    -no-snapshot \
    -no-audio \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    >/tmp/${AVD_NAME}-emulator.log 2>&1 &
fi

echo "等待模拟器开机完成..."
for _ in $(seq 1 120); do
  if "${ADB_BIN}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | rg -q '^1$'; then
    break
  fi
  sleep 2
done

if ! "${ADB_BIN}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | rg -q '^1$'; then
  echo "模拟器启动超时，请查看日志: /tmp/${AVD_NAME}-emulator.log" >&2
  exit 1
fi

echo "构建 debug 包..."
"${GRADLEW}" :app:assembleDebug

echo "安装 APK..."
"${ADB_BIN}" install -r "${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"

echo "启动应用..."
"${ADB_BIN}" shell am start -n "${APP_ID}/${MAIN_ACTIVITY}"

echo "完成。"
