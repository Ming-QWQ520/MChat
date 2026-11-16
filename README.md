# MChat

MChat 是一个仅面向 Android 的原生 LLM 聊天客户端：Jetpack Compose + Material3 打造，基于 **openai-kotlin（Ktor + OkHttp 引擎）** 实现流式传输，UI 全面对齐 [kelivo](https://github.com/Chevey339/kelivo)（设置 / 对话 / 侧边栏），液态玻璃参考 [Fluxhub](https://github.com/henryz78/Fluxhub)（[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) backdrop 库：vibrancy + 模糊 + 折射透镜），默认内置 DeepSeek，支持用户自行接入任意 OpenAI 兼容服务，本地优先、零遥测。

- **版本**：0.1.0（versionCode 1，全程锁定，CI 强制校验）
- **包名**：`com.ming.mchat`
- **协议**：MIT

## 截图（占位）

| 聊天页（kelivo 风格） | 侧边栏抽屉 | 液态玻璃（默认关闭，可开启） |
| :---: | :---: | :---: |
| ![chat](docs/placeholder-chat.png) | ![drawer](docs/placeholder-drawer.png) | ![glass](docs/placeholder-glass.png) |

## 功能列表

- **kelivo 风格 UI（第 5 轮全面对齐）**：默认调色板（主色 #4D5C92 / 浅色 #F7F7F7 / 深色 #121213）+ 动态取色；SurfaceLadder 语义表面（page / card / surfaceFill / 发丝描边 hairline）；聊天气泡磨砂 / 实底两档（磨砂 = 半透明 + 0.5dp 发丝描边，半径 16）；用户消息右对齐（最大 75% 屏宽）+ 用户名 / 时间戳头部；助手消息左对齐带思考折叠卡。
- **长按锚点菜单（自适应方向）**：消息与会话的长按菜单以长按点为水平中心展开；长按点偏上（含中间区域）向下展开、偏下向上展开，放不下自动切换方向并贴边；150ms EaseOutCubic 淡入 + 以按压侧为原点的轻微缩放；菜单容器自带 animateContentSize（子区域展开 / 收起有过渡）。
- **动画体系（Kotlin 原生）**：MotionCurves 标准三次贝塞尔缓动（EaseOutCubic / EaseInCubic / EaseInOutCubic / EaseOutQuart / EaseOutExpo）+ 弹簧（按压 / 回弹）；五档标准时长（110/150/200/220/300ms）；iOS 式无涟漪按压（collectIsPressedAsState + 轻缩放）；全部尊重"减少动画"与系统无障碍。
- **展开 / 收起动画（animateContentSize）**：思考过程折叠卡、快捷短语行、多选计数栏、上下文环浮层等展开收起全部使用 animateContentSize + EaseOutCubic。
- **跟随输出滚动**：流式输出期间仅当页面停留在底部时自动跟随滚动；上滑阅读历史不打断；离开底部时浮现玻璃圆钮（回到底部 / 上一条提问，220ms 缩放 + 淡入，kelivo ScrollNavigationButtons）。
- **流式聊天（openai-kotlin）**：`OpenAI.chatCompletions` Flow 逐事件映射（SSE 解析完全由库完成）；reasoning_content 原生字段（ChatDelta）；取消即断流（CancellationException 不吞）；usage 从末 chunk 捕获；思考模式 reasoning_effort 四档（none/low/high/max，Effort 值类直传）；思考开启时不发送 temperature。
- **多 Provider**：内置 DeepSeek（deepseek-flash 视觉+思考 / deepseek-v4-pro 思考）+ 自定义供应商（Base URL / API Key / 从 API 拉取模型，OpenAI 兼容 Bearer 与 Anthropic x-api-key 双认证）；按 providerId 缓存 OpenAI 客户端，配置变更失效重建。
- **侧边栏（kelivo）**：75% 屏宽抽屉；用户头像（emoji）+ 用户名；当前模型卡片（点击切换）；新建对话 / 临时对话开关；搜索过滤；置顶 + 今天/昨天/7天内/30天内/更早 分组；长按锚点菜单（多选 / 重命名 / 置顶 / 复制会话 / 删除）；多选模式（计数栏 + 全选 + 批量置顶 + 批量删除，AnimatedVisibility 淡入切换）。
- **临时对话（kelivo temporary chat）**：消息仅存内存不落库，退出即焚并恢复之前的会话；顶栏一键开关。
- **消息编辑后重发（kelivo / Chatbox）**：编辑用户消息 → 删除其后全部回复 → 基于新内容重新提问（二次确认对话框说明影响）。
- **快捷短语（kelivo quick phrase）**：输入栏闪电按钮展开短语行（animateContentSize），点击填入输入框；内置 6 条 + 设置页自定义管理。
- **用户资料（kelivo user profile）**：用户名 + emoji 头像，展示在抽屉头部 / 欢迎页 / 消息时间戳。
- **思考折叠卡**：「正在思考… / 已深度思考」+ 折叠摘要 2 行 + 点击展开（animateContentSize）；思维链落库持久化，历史消息可回看；设置可关默认折叠。
- **Token 统计双轨**：服务端 usage 优先，本地估算（JTokkit + 字符兜底）补偿；消息气泡展示 输入/输出/总数/模型/时延，流式中显示 `≈`。
- **附件**：Photo Picker 多选图片（最多 9 张）、txt/md/csv/json、PDF（PdfBox-Android）；图片压缩（长边 1568 / JPEG 85 / 4MB / 剥离 EXIF）；附件文本 `<untrusted_file>` 包裹 + 安全声明。
- **提示词系统**：内置 4 预设 + 自定义 CRUD + 导入导出；Mustache 子集模板；注入扫描只标记不删除。
- **会话标题状态机**：DEFAULT → AUTO_TRUNCATE → AUTO_LLM；MANUAL 不可被自动覆盖。
- **设置页（kelivo iOS 风格）**：分组卡片 + iOS 导航行（36dp 图标槽 / 15sp 标签 / chevron / 0.6dp 缩进分隔线）+ 二级页面内推（slide + fade）；分组：通用（主题与外观 / 用户资料 / 聊天显示）· 模型与聊天 · 效率（提示词 / 快捷短语）· 数据 · 关于；全部 DataStore 持久化即时生效。
- **错误码映射**：HTTP 400~5xx → 中文可读提示（服务端详情透传，不吞 400/413）；网络异常分类。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 2.0 · Jetpack Compose · Material 3（BOM 2024.10.01） |
| 构建 | Gradle 8.10.2 Kotlin DSL · Version Catalog · AGP 8.7.3 |
| 架构 | 多模块 · MVVM · Repository |
| 依赖注入 | Hilt 2.52（KSP） |
| 异步 | Coroutines + Flow |
| LLM 客户端 | **openai-kotlin 4.2.0（openai-client）+ Ktor 3.0.3（OkHttp 引擎）** |
| 序列化 | Kotlinx Serialization（openai-kotlin 同栈） |
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
core-llm         openai-kotlin 客户端工厂（按 Provider 缓存）、流式映射、错误映射、模型列表拉取、Token 估算、DeepSeek 余额（Ktor 直连）
core-prompt      提示词预设、模板引擎、注入解析、注入扫描
core-attachment  图片压缩、文本/PDF 提取、附件仓库
core-theme       kelivo 风格主题（SurfaceLadder 语义表面）、动效体系（MotionCurves）、iOS 分组组件、液态玻璃（backdrop）
core-backdrop    AndroidLiquidGlass（Kyant0，Apache-2.0）源码内嵌，适配 Kotlin 2.0.21 / Compose 1.7.4
feature-chat     聊天页、消息列表、输入区、附件条、上下文环
feature-history  左侧历史抽屉、会话列表、模型选择
feature-settings 完整设置页（九大分组）+ Provider 编辑
feature-title    会话标题生成器（状态机）
```

## 外观说明

- **默认**：克制的 MD3——Material3 组件、色调表面、圆角，无多余装饰；颜色跟随系统（Android 12+ 动态取色，以下回退默认 M3）；四档主题模式：跟随系统 / 浅色 / 深色 / 动态取色（仅 12+）。
- **液态玻璃（默认关闭，参考 Fluxhub）**：设置 → 外观开启后，根布局经 `layerBackdrop` 录制为采样源，TopAppBar / 底部输入栏 / 设置分组卡片通过 `drawBackdrop` 采样背后内容并施加 **vibrancy（饱和度提升）+ 高斯模糊 + 折射透镜（lens）**——iOS 26 风格的真实内容穿透玻璃；Android 12 以下自动跳过 RenderEffect（仅保留表面色调 + 发丝描边，不崩溃），Android 13 以下跳过折射；强度三档（低/中/高）分别调模糊半径 / 折射高度 / 色调浓度；列表项与长列表滚动区域不做实时模糊；文本统一 onSurface 保证对比度；"减少动画"开关同时尊重系统"移除动画"无障碍设置。Popup 独立窗口中的锚点菜单使用半透明 + 描边 + 软阴影的降级玻璃。
- 两套视觉共用同一份导航与功能，仅悬浮层绘制不同。

## Provider 配置

| Provider | Base URL | 模型 |
| --- | --- | --- |
| DeepSeek（内置） | `https://api.deepseek.com` | deepseek-flash（默认，视觉+思考）· deepseek-v4-pro（思考） |
| 自定义（用户添加） | 任意 OpenAI 兼容端点 | 从 API 拉取或手动添加，可编辑 / 删除 |

首次启动自动 seed（只插不覆盖用户 Key）。在 设置 → 模型与 Provider 中：填入 API Key（Keystore 加密存储）、测试连接、切换默认模型、调整 Base URL；**添加供应商**接入任意 OpenAI 兼容服务（填 Base URL / API Key 后可一键拉取模型列表）；模型支持添加 / 编辑 / 删除；DeepSeek 供应商可一键恢复内置模型。旧版本内置的 ChatGPT / GLM / Grok 升级时自动移除（已配置 Key 的保留为自定义供应商）。

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
- openai-kotlin 日志级别 LogLevel.None（不输出任何请求日志，Authorization 自动 sanitize），任何构建都不打印敏感信息。
- 附件文本以 `<untrusted_file>` 强分隔符包裹 + 安全声明；注入扫描只标记不删除，发送前需用户确认。
- LLM 输出永远不会直接执行系统命令；工具调用层要求白名单、参数校验、用户确认与审计日志。
- 余额查询仅访问 `https://api.deepseek.com/user/balance`，Key 不落日志。

## License

MIT License © Ming-QWQ520（详见 [LICENSE](LICENSE)）。依赖及其许可：openai-kotlin（MIT）、Ktor / OkHttp 引擎（Apache-2.0）、AndroidLiquidGlass backdrop（Apache-2.0，源码内嵌于 core-backdrop）、PdfBox-Android（Apache-2.0）、JTokkit（MIT）、Coil（Apache-2.0）、Compose/Material3（Apache-2.0）。UI 参考：kelivo（GPL-3.0，仅交互与视觉参考，未复制代码）与 Fluxhub（GPL-3.0，仅液态玻璃用法参考）。
