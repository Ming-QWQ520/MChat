#!/usr/bin/env bash
# 自动迭代执行脚本：由 auto-iterate.yml 调用。
# 流程：LLM 生成 → 应用 → 自检 → 本地构建修复循环 → skills 审查 →
#       更新 backlog/history → 提交推送 → 触发 CI → CI 修复循环 → 失败回滚。
set -euo pipefail

COMMAND="${1:-run}"
ROUND_TAG="${2:-iter-unknown}"
SKILLS_LOADED="${3:-false}"
SKILL_FILE="${4:-}"

MIN_FEATURES=$(python3 -c "import json;print(json.load(open('.iteration/config.json'))['minFeaturesPerRound'])")
MAX_FIX=$(python3 -c "import json;print(json.load(open('.iteration/config.json'))['maxFixAttempts'])")
MODEL="${LLM_MODEL:-claude-sonnet-4-20250514}"
BASE_URL="${LLM_BASE_URL:-https://api.anthropic.com}"
ROUND_FILE=".iteration/history/$(date +%Y%m%d-%H%M).md"
START_TS=$(date +%s)
LLM_CALLS=0
CI_RUN_URL=""

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

log() { echo "[iterate] $*"; }

write_history() {
  mkdir -p .iteration/history
  cat >> "$ROUND_FILE" <<EOF
$1
EOF
}

elapsed() { echo "$(( ($(date +%s) - START_TS) / 60 )) 分钟"; }

# ---------------- LLM 调用（Anthropic Messages API）----------------
call_llm() {
  local system="$1"
  local user="$2"
  LLM_CALLS=$((LLM_CALLS + 1))
  python3 - "$system" "$user" "$MODEL" <<'PYEOF'
import json
import os
import sys
import urllib.error
import urllib.request

system, user, model = sys.argv[1], sys.argv[2], sys.argv[3]
payload = {
    "model": model,
    "max_tokens": 16000,
    "system": system,
    "messages": [{"role": "user", "content": user}],
}
url = os.environ.get("LLM_BASE_URL", "https://api.anthropic.com").rstrip("/") + "/v1/messages"
request = urllib.request.Request(
    url,
    data=json.dumps(payload).encode(),
    headers={
        "x-api-key": os.environ["LLM_API_KEY"],
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    },
)
try:
    with urllib.request.urlopen(request, timeout=900) as response:
        data = json.load(response)
    texts = [block["text"] for block in data.get("content", []) if block.get("type") == "text"]
    print("\n".join(texts))
except urllib.error.HTTPError as error:
    body = error.read().decode(errors="replace")
    print("LLM_ERROR: HTTP %s %s" % (error.code, body[:2000]), file=sys.stderr)
    sys.exit(2)
except Exception as error:  # noqa: BLE001
    print("LLM_ERROR: %s" % error, file=sys.stderr)
    sys.exit(2)
PYEOF
}

system_prompt() {
  cat .iteration/prompts/iterate.md
  if [ "$SKILLS_LOADED" = "true" ] && [ -n "$SKILL_FILE" ] && [ -f "$SKILL_FILE" ]; then
    echo ""
    echo "===== Kotlin Agent Skills 规则（生成任何 Kotlin/Gradle/Compose 代码前必须遵守）====="
    cat "$SKILL_FILE"
  else
    echo ""
    echo "（本轮 kotlin-agent-skills 未加载，按项目既有约定执行，并在 history 中记录）"
  fi
}

# ---------------- 自检：禁止项扫描 ----------------
selfcheck() {
  git add -A
  if git diff --cached gradle.properties | grep -E '^[+-]VERSION_(NAME|CODE)='; then
    log "自检失败：修改了版本号"; return 1
  fi
  if git diff --cached .github/workflows/android-ci.yml | grep -E "^[+-].*(workflow_dispatch|on:)"; then
    log "自检失败：修改了 android-ci.yml 触发方式"; return 1
  fi
  if git diff --cached --name-only --diff-filter=D | grep -E 'src/test/'; then
    log "自检失败：删除了既有测试"; return 1
  fi
  if grep -rEn '(ghp_|gho_|github_pat_)[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,}' \
      --include='*.kt' --include='*.kts' --include='*.xml' --include='*.md' . \
      | grep -v '.iteration/history/' | grep -v 'iterate.md'; then
    log "自检失败：疑似引入硬编码密钥"; return 1
  fi
  log "自检通过"
}

# ---------------- 回滚 ----------------
rollback() {
  local reason="$1"
  log "回滚：$reason"
  git reset --hard "$ROUND_TAG"
  git push --force-with-lease origin main
  write_history "

## 回滚记录
- 原因：$reason
- 回滚点：$ROUND_TAG
- LLM 调用次数：$LLM_CALLS
- 耗时：$(elapsed)"
  git add -A && git commit -q -m "chore(iter): 记录回滚到 $ROUND_TAG" || true
  git push origin main || true
  gh issue create \
    --title "[auto-iterate] 本轮回滚，需人工介入" \
    --body "回滚原因：$reason

- 回滚 tag：$ROUND_TAG
- 失败日志：${CI_RUN_URL:-见 Actions 运行记录}
- LLM 修复尝试：$LLM_CALLS 次
- skills 审查：$SKILLS_LOADED" || true
  exit 1
}

# ---------------- 主流程 ----------------
select_tasks() {
  grep '^- \[ \]' .iteration/backlog.md | head -n "$MIN_FEATURES" | sed 's/^- \[ \] //'
}

commit_round() {
  local commit_message
  commit_message=$(printf 'feat(iter): 自动迭代 - %s' "$(echo "$SELECTED_TASKS" | head -n 1 | cut -c1-30)")
  git add -A
  git commit -q -F - <<EOF
$commit_message

$(echo "$SELECTED_TASKS" | sed 's/^/- /')

Iteration-Round: $ROUND_TAG
Backlog-Items: $MIN_FEATURES
Skills-Review: $SKILLS_LOADED
EOF
  git rev-parse --short HEAD
}

case "$COMMAND" in
  run)
    SELECTED_TASKS=$(select_tasks)
    if [ -z "$SELECTED_TASKS" ]; then
      write_history "# 迭代记录（$(date '+%Y-%m-%d %H:%M')）

- 结论：backlog 已全部完成，跳过本轮
- skills 加载：$SKILLS_LOADED"
      exit 0
    fi
    write_history "# 迭代记录（$(date '+%Y-%m-%d %H:%M')）

- 触发时间：$(date '+%Y-%m-%d %H:%M:%S %Z')
- 回滚点：$ROUND_TAG
- skills 加载：$SKILLS_LOADED
- 本轮功能：
$(echo "$SELECTED_TASKS" | sed 's/^/  - /')"

    SYSTEM=$(system_prompt)

    # 1. 生成
    log "调用 LLM 生成本轮功能..."
    if ! GENERATE_OUT=$(call_llm "$SYSTEM" "本轮任务（按顺序实现，全部完成）：

$SELECTED_TASKS

请按系统提示词中的输出格式输出完整文件内容。"); then
      write_history "- 结果：LLM 调用失败，本轮终止，未改动代码"
      exit 1
    fi
    echo "$GENERATE_OUT" > /tmp/llm_out.txt
    python3 .iteration/scripts/apply_llm_output.py /tmp/llm_out.txt
    write_history "- LLM 生成调用次数：1"

    # 2. 自检
    if ! selfcheck; then
      rollback "自检失败（版本号/CI 触发方式/测试删除/硬编码密钥）"
    fi

    # 3. 本地构建修复循环
    attempt=0
    until ./gradlew -q assembleDebug --console=plain > /tmp/build.log 2>&1; do
      attempt=$((attempt + 1))
      if [ "$attempt" -gt "$MAX_FIX" ]; then
        rollback "本地构建连续 ${MAX_FIX} 次修复仍失败"
      fi
      log "本地构建失败，第 $attempt 次回传修复..."
      ERRORS=$(tail -n 150 /tmp/build.log)
      if ! FIX_OUT=$(call_llm "$SYSTEM" "编译失败（第 $attempt 次修复）。错误摘要：

$ERRORS

请只输出修复涉及的完整文件（格式同前）。"); then
        rollback "修复模式 LLM 调用失败"
      fi
      echo "$FIX_OUT" > /tmp/llm_out.txt
      python3 .iteration/scripts/apply_llm_output.py /tmp/llm_out.txt || true
      selfcheck || rollback "自检失败"
    done
    log "本地 assembleDebug 通过（修复 $attempt 次）"

    # 4. skills 审查
    REVIEW_OUT=$(call_llm "$SYSTEM" "本地构建已通过。请按 Kotlin Agent Skills 规则审查以下 diff，仅输出一行结论：REVIEW: passed 或 REVIEW: failed - 原因。

$(git diff HEAD --stat | tail -n 30)") || REVIEW_OUT="REVIEW: skipped（LLM 调用失败）"
    REVIEW_RESULT="passed"
    if ! echo "$REVIEW_OUT" | grep -q "REVIEW: passed"; then
      REVIEW_RESULT="failed: $(echo "$REVIEW_OUT" | head -n 1)"
    fi
    write_history "- skills 审查：$REVIEW_RESULT
- 本地构建修复次数：$attempt
- LLM 调用次数：$LLM_CALLS"

    # 5. 更新 backlog + 提交推送
    COMMIT=$(commit_round)
    python3 - "$COMMIT" "$MIN_FEATURES" <<'PYEOF'
import sys

commit, count = sys.argv[1], int(sys.argv[2])
with open(".iteration/backlog.md", encoding="utf-8") as handle:
    lines = handle.readlines()
marked = 0
result = []
for line in lines:
    if line.startswith("- [ ] ") and marked < count:
        result.append(line.replace("- [ ] ", "- [x] ", 1).rstrip("\n") + "（commit: %s）\n" % commit)
        marked += 1
    else:
        result.append(line)
with open(".iteration/backlog.md", "w", encoding="utf-8") as handle:
    handle.writelines(result)
print("marked %d backlog items" % marked)
PYEOF
    write_history "- 功能提交：$COMMIT"
    git add -A
    git commit -q -m "chore(iter): 更新 backlog 与 history（$ROUND_TAG）" || true
    git push origin main

    # 6. 触发 CI 并等待
    ci_attempt=0
    while true; do
      gh workflow run android-ci.yml --repo "$GITHUB_REPOSITORY" --ref main
      sleep 15
      RUN_ID=$(gh run list --workflow=android-ci.yml --repo "$GITHUB_REPOSITORY" --limit 1 --json databaseId --jq '.[0].databaseId')
      CI_RUN_URL="https://github.com/$GITHUB_REPOSITORY/actions/runs/$RUN_ID"
      log "CI 运行中：$CI_RUN_URL"
      if gh run watch "$RUN_ID" --repo "$GITHUB_REPOSITORY" --exit-status > /dev/null 2>&1; then
        log "CI 成功"
        break
      fi
      ci_attempt=$((ci_attempt + 1))
      if [ "$ci_attempt" -gt "$MAX_FIX" ]; then
        write_history "- CI 结果：失败（${MAX_FIX} 次修复后仍失败）
- CI Run：$CI_RUN_URL"
        rollback "CI 连续 ${MAX_FIX} 次修复仍失败"
      fi
      log "CI 失败，拉取日志修复（第 $ci_attempt 次）..."
      CI_ERRORS=$(gh run view "$RUN_ID" --repo "$GITHUB_REPOSITORY" --log-failed 2>/dev/null | tail -n 150 || echo "无法获取日志")
      if ! CI_FIX=$(call_llm "$SYSTEM" "远程 CI（assembleDebug）失败（第 $ci_attempt 次修复）。错误摘要：

$CI_ERRORS

请只输出修复涉及的完整文件（格式同前）。"); then
        rollback "CI 修复模式 LLM 调用失败"
      fi
      echo "$CI_FIX" > /tmp/llm_out.txt
      python3 .iteration/scripts/apply_llm_output.py /tmp/llm_out.txt || true
      selfcheck || rollback "自检失败"
      if ! ./gradlew -q assembleDebug --console=plain > /tmp/build.log 2>&1; then
        log "本地构建失败，继续回传 CI 修复循环"
      fi
      git add -A
      git commit -q -m "fix(iter): CI 修复第 $ci_attempt 轮" || true
      git push origin main
    done

    # 7. 最终记录
    write_history "- CI 结果：success
- CI Run：$CI_RUN_URL
- 总耗时：$(elapsed)
- LLM 调用次数：$LLM_CALLS
- skills 审查：$REVIEW_RESULT
- 产物：app-debug.apk 已推送 latest-ci Release（覆盖）"
    git add -A
    git commit -q -m "chore(iter): 记录 CI 成功（$ROUND_TAG）" || true
    git push origin main
    log "本轮迭代完成"
    ;;

  *)
    echo "用法：iterate.sh run <round_tag> <skills_loaded> <skill_file>"
    exit 64
    ;;
esac
