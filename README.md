# MChat

MChat 是一个仅面向 Android 的原生 LLM 聊天客户端：Jetpack Compose + Material3 打造，基于 [openai-kotlin](https://github.com/Aallam/openai-kotlin) 的流式传输，多 Provider（DeepSeek / ChatGPT / 智谱 GLM / Grok）开箱即用，本地优先、零遥测。

- **版本**：0.1.0（versionCode 1，全程锁定，CI 强制校验）
- **包名**：`com.ming.mchat`
- **协议**：MIT

## 截图（占位）

| 聊天页（DeepSeek 风格） | 历史抽屉 | 液态玻璃（默认关闭，可开启） |
| :---: | :---: | :---: |
| ![chat](docs/placeholder-chat.png) | ![drawer](docs/placeholder-drawer.png) | ![glass](docs/placeholder-glass.png) |

## 功能列表

- **DeepSeek 风格 UI**：品牌蓝 #4D6BFE 三色体系（浅 #F7F7F8 / 深 #0F1014）；助手消息扁平无气泡 + 徽标，用户消息蓝色气泡；空会话居中欢迎页；输入区 28dp 大圆角悬浮容器，内嵌当前模型名 chip / 上下文环 / 深度思考三档；流式输出 90ms 节流 + 底部智能跟随（上滑阅读不打断）。
- **流式聊天**：基于 openai-kotlin `chatCompletions` Flow 的逐字输出，可随时停止（Job.cancel，不吞 CancellationException），末帧获取服务端 usage。
- **四 Provider**：DeepSeek、ChatGPT、智谱 GLM、Grok，全部通过 baseUrl 切换接入；能力字段驱动参数分支（temperature / max_completion_tokens / system role=developer / reasoning_content / stream usage）。
- **Token 统计双轨**：服务端 usage 优先，本地估算（JTokkit + 字符兜底）补偿；消息气泡展示 输入/输出/总数/模型/时延，流式中显示 `≈`，结束后切换真实值；按天按模型聚合，标题生成等辅助消耗单独记账。
- **附件**：Photo Picker 多选图片（最多 9 张，不申请存储权限）、txt/md/csv/json 文本、PDF（PdfBox-Android 按页提取）；图片自动压缩（长边 1568 / JPEG 85 / 单图 4MB / 剥离 EXIF / 透明铺白底 / HEIC 兼容）；附件文本以 `<untrusted_file>` 包裹注入并附安全声明。
- **提示词系统**：内置 4 个预设（翻译助手 / 文档总结 / 代码审查 / 写作润色）+ 自定义 CRUD + 导入导出；Mustache 子集模板引擎（`{{var}}`，未定义保留原样），内置变量 date/time/model/app_version/attachment_names/locale；注入优先级 单条 > 会话 > 全局；注入扫描只标记不删除，发送前确认。
- **会话标题状态机**：DEFAULT → AUTO_TRUNCATE（首条用户消息兜底）→ AUTO_LLM（首轮结束异步生成）；MANUAL 之后任何自动流程不可覆盖（SQL 层守卫）；长按/重新生成，按 convId Mutex 防并发。
- **历史抽屉**：左边缘右滑打开（+ 顶栏菜单冗余入口）；三段布局（模型卡片+新建对话 / 按今天-昨天-7天-30天-更早分组 sticky 列表 / 设置）；长按重命名、滑动删除、级联清理消息与附件文件。
- **思考强度 & 输出上限**：思考强度三档（低/高/最高，默认最高，不支持自动禁用）；输出上限三模式（默认不发送 max_tokens / 手动 / 自动=上下文 50%）。
- **上下文环 & DeepSeek 余额**：环形指示器内嵌于输入框功能行；环身=下一轮请求上下文占用/contextWindow（<80% 品牌蓝 / 80~95% 橙 / >95% 红，服务端 usage 优先、本地估算前缀 ≈）；DeepSeek 环心显示余额（>999 缩写 1.2K），点击展开 token 拆分与余额明细；刷新时机=进页/回前台/对话结束/测试连接/手动，失败静默保留缓存。
- **完整设置页**：九大分组（外观 / 模型与 Provider / 提示词 / 会话与标题 / 附件 / 用量统计 / 安全与隐私 / 数据管理 / 关于），全部 DataStore 持久化即时生效。
- **错误透传**：HTTP 400/413 等状态码与 Provider 错误信息完整透传，不吞错。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 2.0 · Jetpack Compose · Material 3（BOM 2024.10.01） |
| 构建 | Gradle 8.10.2 Kotlin DSL · Version Catalog · AGP 8.7.3 |
| 架构 | 多模块 · MVVM · Repository |
| 依赖注入 | Hilt 2.52（KSP） |
| 异步 | Coroutines + Flow |
| LLM 客户端 | openai-kotlin 4.2.0（Ktor 3 + OkHttp 引擎） |
| 序列化 | Kotlinx Serialization |
| 本地存储 | Room 2.6.1（KSP）· DataStore Preferences |
| 图片 | Coil |
| PDF | PdfBox-Android |
| Token 估算 | JTokkit + 字符近似兜底 |
| SDK | minSdk 26 · targetSdk 35 · compileSdk 35 · JDK 17 |

## 模块结构

```
app              入口、导航、主题装配、DI 聚合
core-common      工具、AppResult、设置枚举
core-data        Room 实体/DAO/数据库、DataStore、Keystore 封装、种子数据
core-llm         openai-kotlin 客户端工厂、统一流式事件映射、Token 估算、DeepSeek 余额
core-prompt      提示词预设、模板引擎、注入解析、注入扫描
core-attachment  图片压缩、文本/PDF 提取、附件仓库
core-theme       MD3 主题、动态取色、液态玻璃视觉层、外观设置
feature-chat     聊天页、消息列表、输入区、附件条、上下文环
feature-history  左侧历史抽屉、会话列表、模型选择
feature-settings 完整设置页（九大分组）+ Provider 编辑
feature-title    会话标题生成器（状态机）
```

## 外观说明

- **默认**：克制的 MD3——Material3 组件、色调表面、圆角，无多余装饰；颜色跟随系统（Android 12+ 动态取色，以下回退默认 M3）；四档主题模式：跟随系统 / 浅色 / 深色 / 动态取色（仅 12+）。
- **液态玻璃（默认关闭）**：设置 → 外观开启后，在语义色之上为 TopAppBar、底部输入栏、抽屉、对话框、设置分组卡片叠加半透明渐变 + 边缘高光描边（`LiquidGlassSurface`）；抽屉打开时对背后内容做真实背景模糊（Android 12+ RenderEffect，低版本自动降级为半透明+渐变高光，不崩溃）；强度三档（低/中/高）；列表项与长列表滚动区域不做实时模糊；文本统一 onSurface 保证对比度；“减少动画”开关同时尊重系统“移除动画”无障碍设置。
- 两套视觉共用同一份导航与功能，仅悬浮层绘制不同。

## Provider 配置

| Provider | Base URL | 模型 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com/v1` | DeepSeek-4.1Flash · DeepSeek-4.1Pro（reasoning_content，131072 上下文，旧模型自动 remap） |
| ChatGPT | `https://api.openai.com/v1` | gpt-4o · gpt-4o-mini · o3-mini（max_completion_tokens / 不支持 temperature / developer role） |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | glm-4-plus · glm-4v-plus（视觉） · glm-4-flash |
| Grok | `https://api.x.ai/v1` | grok-2-latest · grok-2-vision-latest（视觉） · grok-beta（流式 usage 不稳定→本地估算） |

首次启动自动 seed（只插不覆盖用户 Key）。在 设置 → 模型与 Provider 中填入 API Key（Keystore 加密存储）、可测试连接、切换默认模型、调整 Base URL。

## 构建步骤

```bash
git clone https://github.com/Ming-QWQ520/MChat.git
cd MChat
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（未配置正式 keystore 时使用 debug keystore 签名，可直接安装）
```

要求：JDK 17、Android SDK（compileSdk 35）。版本号在 `gradle.properties` 集中锁定（`VERSION_NAME=0.1.0`、`VERSION_CODE=1`），`app/build.gradle.kts` 读取，CI 校验，任何改动版本号的提交都会使构建失败。

## CI 说明（android-ci.yml）

- 触发方式：**仅 workflow_dispatch**（手动），防止递归与意外扣分钟。
- 发布门禁：先运行 `smoke` job（复用 [smoke-test.yml](.github/workflows/smoke-test.yml)），在 GitHub 托管 runner 的 KVM 模拟器（API 30 + API 34 矩阵）上安装并冷启动 debug / release 两个 APK，验证 30 秒进程存活且无 FATAL EXCEPTION，**任一闪退则整条 CI 失败、不会发布 Release**。
- 步骤：JDK 17 → Gradle 缓存 → 版本锁定校验（0.1.0/1）→ `./gradlew assembleDebug assembleRelease`（同时编译 debug 与 release 两个 APK）→ 产物存在性校验。
- 成功后：`latest-ci` tag 移动到当前提交，[softprops/action-gh-release@v2](https://github.com/softprops/action-gh-release) 将 `app-debug.apk` 与 `app-release.apk` 一并推送到 `latest-ci` Release（prerelease，`overwrite_files: true`，同名 APK 直接替换、不保留历史）。
- release 签名：未配置正式 keystore 时回退使用 debug keystore 签名（可通过 `MCHAT_KEYSTORE_FILE` / `MCHAT_KEYSTORE_PASSWORD` / `MCHAT_KEY_ALIAS` / `MCHAT_KEY_PASSWORD` 环境变量注入正式签名）。
- 失败时：不上传 Release，仅上传 `build.log` 失败日志 artifact；若冒烟测试失败，`smoke-logs-api*` artifact 中包含完整 logcat 与 crash buffer（含 FATAL EXCEPTION 堆栈）。
- 修复循环：手动触发 → 检测状态 → 拉取 `--log-failed` 日志 → 修复推送 → 再次触发，直到 conclusion=success。

## 启动冒烟测试（smoke-test.yml）

- 触发：workflow_dispatch 手动，或被 android-ci.yml 以 `workflow_call` 作为发布门禁调用。
- 矩阵：API 30（default 镜像）+ API 34（google_apis 镜像），x86_64，KVM 硬件加速。
- 流程：构建 debug + release APK → [.github/scripts/smoke_launch_check.sh](.github/scripts/smoke_launch_check.sh) 逐个安装 → `am start` 冷启动 → 等待 30 秒 → `pidof`（`ps` 兜底）检查进程存活 + crash buffer 无 `FATAL EXCEPTION`，否则失败并上传 logcat / crash buffer / am-start 日志。
- 背景：第 2 轮修复的启动闪退（`TemplateEngine` 正则 `}` 未转义，Android ICU 不容忍）在桌面 JVM 单测中无法暴露（JVM 容忍未转义 `}`），构建也全绿——只有真实 Android 运行时能发现。此测试即为堵住该类「单测绿、真机炸」的漏洞而设。

## 自动迭代说明（auto-iterate.yml）

- 触发：`cron '0 */5 * * *'`（每 5 小时）+ workflow_dispatch 手动调试；`concurrency: auto-iterate`，不并发。
- 每轮流程：版本校验（不符直接失败并开 issue）→ 打回滚 tag `iter-YYYYMMDDHHMM` → 从 [backlog](.iteration/backlog.md) 按顺序取 5~8 个未完成功能 → `npx skills add Kotlin/kotlin-agent-skills` 加载技能（失败不阻断，history 记录）→ LLM（Anthropic Messages API）携带技能规则生成代码 → 应用 → 自检（禁改版本号/CI 触发/删测试/硬编码密钥）→ 本地 assembleDebug + assembleRelease（与 CI 一致，失败回传修复，最多 3 次）→ skills 审查 → 更新 backlog + 写 history → 提交推送 → 触发 CI 并 watch（失败修复循环最多 3 次）→ 仍失败则 `git reset --hard` 到回滚 tag、force push、开 issue 请求人工介入。
- 需要的 Secrets：`LLM_API_KEY`（必填；未配置时按设计直接失败并开 issue，不降级）；`LLM_BASE_URL`、`LLM_MODEL`（可选，默认 `https://api.anthropic.com` / `claude-sonnet-4-20250514`）。
- 版本号全程锁 0.1.0/1；每轮记录见 [.iteration/history/](.iteration/history/)；commit 遵循 `feat(iter): ...` + `Iteration-Round / Backlog-Items / Skills-Review` trailer。

## 安全说明

- API Key 使用 Android Keystore（AES/GCM 256）加密，Room 只存密文引用，绝不明文落盘；仓库不含任何真实密钥。
- openai-kotlin `LoggingConfig(logLevel = NONE)`，任何构建都不输出请求日志。
- 附件文本以 `<untrusted_file>` 强分隔符包裹 + 安全声明；注入扫描只标记不删除，发送前需用户确认。
- LLM 输出永远不会直接执行系统命令；工具调用层要求白名单、参数校验、用户确认与审计日志。
- 余额查询仅访问 `https://api.deepseek.com/user/balance`，Key 不落日志。

## License

MIT License © Ming-QWQ520（详见 [LICENSE](LICENSE)）。依赖及其许可：openai-kotlin（MIT）、Ktor（Apache-2.0）、PdfBox-Android（Apache-2.0）、JTokkit（MIT）、Coil（Apache-2.0）、Compose/Material3（Apache-2.0）。
