#!/usr/bin/env bash
# 启动冒烟测试：在已启动的模拟器上逐个安装并冷启动 debug / release APK，
# 验证 30 秒进程存活且无 FATAL EXCEPTION。
#
# 重要背景：android-emulator-runner 的 script 输入是【逐行】用 sh -c 执行的，
# 多行 if/函数/跨行变量都会失效（曾导致误报与语法错误）。因此所有逻辑
# 必须集中在本文件中，由 workflow 以单行 `bash .github/scripts/smoke_launch_check.sh` 调用。
#
# 产物（写入工作区根目录，供 upload-artifact 上传）：
#   am-start-{debug,release}.log     am start -W 输出
#   logcat-{debug,release}.log       完整 logcat
#   crash-buffer-{debug,release}.log crash buffer（含 FATAL EXCEPTION 堆栈）

set -uo pipefail

check_launch() {
  local variant="$1"
  local apk="app/build/outputs/apk/${variant}/app-${variant}.apk"

  adb uninstall com.ming.mchat >/dev/null 2>&1 || true
  echo "=== [${variant}] 安装 ${apk} ==="
  if ! adb install -r "${apk}"; then
    echo "::error::${variant} APK 安装失败"
    return 1
  fi

  adb logcat -c >/dev/null 2>&1 || true
  echo "=== [${variant}] 冷启动 com.ming.mchat/.MainActivity ==="
  adb shell am start -W -n com.ming.mchat/.MainActivity | tee "am-start-${variant}.log"

  echo "=== [${variant}] 等待 30 秒观察进程存活 ==="
  sleep 30

  local pid=""
  pid=$(adb shell pidof com.ming.mchat 2>/dev/null | tr -d '\r\n ')
  if [ -z "${pid}" ]; then
    # 兜底：部分镜像 pidof 行为差异时用 ps 解析
    pid=$(adb shell "ps -A" 2>/dev/null | grep com.ming.mchat | awk '{print $2}' | head -n 1 | tr -d '\r\n ')
  fi

  adb logcat -d > "logcat-${variant}.log" 2>/dev/null || true
  adb logcat -d -b crash > "crash-buffer-${variant}.log" 2>/dev/null || true

  if [ -z "${pid}" ]; then
    echo "::error::${variant} APK 启动后进程不存在（闪退）"
    cat "crash-buffer-${variant}.log" 2>/dev/null || true
    return 1
  fi
  if grep -q "FATAL EXCEPTION" "crash-buffer-${variant}.log"; then
    echo "::error::${variant} APK 出现 FATAL EXCEPTION（进程当前 pid=${pid}，可能为崩溃后自动重启）"
    cat "crash-buffer-${variant}.log" 2>/dev/null || true
    return 1
  fi
  echo "=== [${variant}] 启动冒烟通过（pid=${pid}，30 秒后仍存活，无 FATAL EXCEPTION） ==="
  return 0
}

FAIL=0
check_launch debug || FAIL=1
if [ "${FAIL}" -eq 0 ]; then
  check_launch release || FAIL=1
fi

if [ "${FAIL}" -eq 0 ]; then
  echo "=== 启动冒烟测试全部通过：debug + release 均可正常进入 App ==="
else
  echo "=== 启动冒烟测试失败 ==="
fi
exit "${FAIL}"
