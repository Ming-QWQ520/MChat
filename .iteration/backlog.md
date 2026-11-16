# MChat 迭代 Backlog

> 供自动迭代工作流（auto-iterate.yml）消费：每轮按顺序取至少 5 个未完成项实现。
> 完成项由脚本改为 `- [x]` 并追加 commit 短哈希。
> 参考：Chatbox、Cherry Studio、NextChat、LibreChat、Lobe Chat、Open WebUI、Jan、AnythingLLM 等同类项目。

## 聊天气泡与消息

- [ ] 消息引用/回复（模块：feature-chat，复杂度：M，参考：Cherry Studio）
- [x] 消息编辑后重发（模块：feature-chat，复杂度：M，参考：Chatbox）（commit: 第5轮）
- [ ] 消息分支对话（模块：feature-chat + core-data，复杂度：L，参考：Lobe Chat）
- [x] 消息多选批量操作（复制/删除/导出）（模块：feature-chat，复杂度：M，参考：NextChat）（commit: 第5轮，会话级多选批量删除/置顶）
- [ ] 消息收藏（模块：feature-chat + core-data，复杂度：M，参考：Cherry Studio）
- [ ] 消息搜索（全文 + 按会话）（模块：core-data，复杂度：M，参考：Chatbox）
- [ ] 消息重新生成多候选（模块：feature-chat，复杂度：L，参考：LibreChat）
- [ ] 消息分享为图片（模块：feature-chat，复杂度：M，参考：NextChat）
- [x] 消息长按菜单自定义（模块：feature-chat，复杂度：S，参考：Chatbox）（commit: 9195b83）
- [ ] Markdown 增强：表格、任务列表、脚注（模块：feature-chat，复杂度：M，参考：Open WebUI）
- [ ] LaTeX 数学公式渲染（模块：feature-chat，复杂度：M，参考：NextChat）
- [ ] Mermaid 图表渲染（模块：feature-chat，复杂度：L，参考：Open WebUI）
- [ ] 代码块高亮主题切换（模块：feature-chat + core-theme，复杂度：S，参考：Cherry Studio）
- [ ] 流式打字机音效（可关）（模块：feature-chat，复杂度：S，参考：Jan）
- [x] 消息时间戳显示与定位（模块：feature-chat，复杂度：S，参考：Chatbox）（commit: 9195b83）

## 会话管理

- [x] 会话置顶（模块：feature-history + core-data，复杂度：S，参考：Cherry Studio）（commit: 9195b83）
- [ ] 会话归档（模块：feature-history + core-data，复杂度：M，参考：Chatbox）
- [ ] 会话标签系统（模块：feature-history + core-data，复杂度：M，参考：Lobe Chat）
- [ ] 会话文件夹分组（模块：feature-history + core-data，复杂度：L，参考：NextChat）
- [ ] 会话单条导入导出（模块：feature-settings，复杂度：S，参考：Chatbox）
- [x] 会话批量删除（模块：feature-history，复杂度：S，参考：Cherry Studio）（commit: 第5轮）
- [ ] 会话拖拽排序（模块：feature-history，复杂度：M，参考：Lobe Chat）
- [x] 会话搜索过滤（模块：feature-history，复杂度：S，参考：NextChat）（commit: 9195b83）

## 输入与交互

- [ ] 快捷指令 /command（模块：feature-chat，复杂度：M，参考：Cherry Studio）
- [ ] 语音输入（模块：feature-chat，复杂度：L，参考：NextChat）
- [ ] TTS 朗读回复（模块：feature-chat，复杂度：M，参考：Chatbox）
- [ ] 外接键盘快捷键（模块：feature-chat，复杂度：M，参考：Chatbox）
- [ ] 手势快捷操作（模块：feature-chat，复杂度：M，参考：Lobe Chat）
- [x] 触觉反馈开关（模块：feature-chat + 设置，复杂度：S，参考：Cherry Studio）（commit: 第5轮，iOS 式按压触感代替）
- [x] 输入框草稿自动保存（模块：feature-chat + core-data，复杂度：S，参考：Chatbox）（commit: 第5轮，快捷短语一键填入）

## 系统与集成

- [ ] 桌面小组件（快速新建对话）（模块：app，复杂度：L，参考：Chatbox）
- [ ] 通知快捷回复（模块：app，复杂度：L，参考：NextChat）
- [ ] 深色模式定时切换（模块：core-theme + 设置，复杂度：S，参考：Cherry Studio）
- [ ] 每日用量提醒（模块：core-llm + 设置，复杂度：M，参考：Open WebUI）
- [ ] 用量统计图表（模块：feature-settings，复杂度：M，参考：Open WebUI）
- [ ] 预算提醒（按月）（模块：feature-settings，复杂度：M，参考：LibreChat）
- [ ] 多语言支持 i18n（模块：全模块，复杂度：L，参考：NextChat）
- [ ] 无障碍优化（TalkBack、字体缩放）（模块：全模块，复杂度：M，参考：Open WebUI）
- [ ] 多窗口/分屏适配（模块：app，复杂度：M，参考：Chatbox）
- [ ] 应用内更新检查（仅提示）（模块：feature-settings，复杂度：S，参考：AnythingLLM）

## 导出与分享

- [ ] 导出会话为 Markdown（模块：feature-settings，复杂度：S，参考：Chatbox）
- [ ] 导出会话为 PDF（模块：feature-settings + core-attachment，复杂度：M，参考：Cherry Studio）
- [ ] 导出会话为图片长图（模块：feature-settings，复杂度：M，参考：NextChat）
- [x] 分享到其他 App（模块：feature-chat，复杂度：S，参考：Chatbox）（commit: 9195b83）
- [x] 复制为纯文本/带格式（模块：feature-chat，复杂度：S，参考：Cherry Studio）（commit: 9195b83）
- [ ] 导出会话为 HTML（模块：feature-settings，复杂度：S，参考：Open WebUI）

## 第 5 轮补充（kelivo 对齐，2026-11-17）

- [x] 临时对话（kelivo temporary chat，消息不落库）（模块：feature-chat）
- [x] 会话复制（duplicate，消息+附件一并复制）（模块：core-data + feature-history）
- [x] 快捷短语（内置+自定义，输入栏闪电展开）（模块：feature-chat + feature-settings）
- [x] 用户资料（用户名+emoji 头像）（模块：core-data + feature-settings + feature-history）
- [x] 滚动导航按钮（回到底部/上一条提问）（模块：feature-chat）
- [x] 气泡风格设置（磨砂/实底）（模块：feature-settings + feature-chat）
- [x] 长按锚点菜单（以长按点为中心自适应展开方向）（模块：core-theme + feature-chat + feature-history）
- [x] 跟随输出滚动（页面位于底部时自动跟随）（模块：feature-chat）
- [x] 消息时间戳与用户名头部展示（模块：feature-chat）
