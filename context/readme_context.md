此目录用于保存 iecoregen-log-inspector 项目的持续上下文。

本地(macbook)已经忽略对log.txt文件的改动,若想恢复跟踪日志文件可以运行
git update-index --no-skip-worktree -- $(git ls-files 'logs/**/log.txt')

## 审查单位
最小审查单位定义为一次 `sampleN.mwe2` 运行，使用以下复合标识：

```text
<case>/<model>/<run>#sampleN.mwe2
```
sample 边界：
- 启动标记：`[<model>] 正在启动 sampleN.mwe2...`
- 完成标记：`sampleN.mwe2 启动完成`

切分规则：
- 一个 `log.txt` 可能只包含一个 sample 片段。
- 一个 `log.txt` 也可能连续包含多个 sample 片段，例如 `logs/R22_IPOApplication/deepseek-v4-flash/ipo5/log.txt` 中包含 `sample3.mwe2`、`sample4.mwe2`、`sample5.mwe2`。
- 如果某个 sample 有启动标记但没有完成标记，应视为未闭合片段，并进入失败/异常候选集合。
- sample 的“有效流程起点”不能简单等同于启动标记或 sample 片段首行。当前规则以 `Annotating EOperations` 为阶段起点，但如果同一 sample 内先后出现多次因 `Cannot invoke "java.lang.Iterable.iterator()" because "iterable" is null` 触发的重启，则应以该 sample 内最后一次 `Cannot invoke...iterable...` 之后首次出现的 `Annotating EOperations` 作为有效起点。
- 这意味着一个 sample 片段内部可能存在多轮前置尝试，只有最后一轮成功衔接后续阶段的 `Annotating EOperations` 才代表真正进入本轮有效流程。

## 关键文件描述
- `logs/**/log.txt`：待分析的原始日志容器。路径结构固定为 `logs/<case>/<model>/<run>/log.txt`。单个日志中可能包含一个或多个 `sampleN.mwe2` 运行片段，以及 MWE2/Xtext/EcoreGenerator 启动、WARN/INFO/DEBUG 日志、时间戳、线程名、Java/Eclipse 插件加载路径等信息。
- `scripts/keep-logtxt-only.py`：清理 `logs/` 树，只保留各运行目录中的 `log.txt`，删除其他文件和无关目录。脚本默认会实际删除文件，执行前需要确认目标路径。
- `prompts.txt`、`prompts_cleaned.md`：项目相关提示词资料，后续可用于理解生成任务、归纳审查规则或构建基准。
- `context/日志分析.md`：日志规则分析主文档，当前关于 sample 有效起点、阶段边界、重试/重启现象、异常模式的结论应以此文件为准。
- `context/`：项目上下文和进展记录目录，所有后续工作需要同步更新这里。
- `architecture.md`：当前仓库结构、前后端分层、日志数据布局与主要目录职责。
- `project_progress.md`：日志审查系统的目标、已知数据范围、分析方向、进展记录和待办。
- `backend/`：Spring Boot + Maven 后端。负责扫描日志、切分 sample、抽取阶段信息与异常事件，并提供日志列表、分析结果、行号片段读取等 API。
- `backend/src/main/java/com/iecoregen/loginspector/service/LogAnalysisService.java`：当前日志解析核心实现。已实现 sample 切分、有效起点判定、异常收集、操作规格补全/校验阶段的 `LLM Response` 提取、代码补全/修复阶段的类级 `LLM Response` 提取。
- `frontend/`：Vue 3 + Vite 前端。本地网页当前已经支持日志选择、sample 切换、异常列表按行聚类展示、阶段折叠展开、类级/阶段级 `LLM Response` 片段查看。
- `frontend/src/App.vue`：当前页面主视图，集中承载日志选择栏、sample 列表、阶段详情区、行号预览等交互。

## 当前数据上下文
- 当前技术路线：`Spring Boot + Vue`
- 后端端口：`8080`
- 前端开发端口：`5173`
- `log.txt` 数量：49
- case：`AI-Games-Hex`、`OLRS`、`R22_IPOApplication`
- model：`deepseek-v4-flash`、`gemini-3.1-flash-lite`、`gpt-5.4-mini`、`minimax-m3`、`qwen3.6-flash`
- case 分布：`AI-Games-Hex` 4 份，`OLRS` 25 份，`R22_IPOApplication` 20 份
- 当前网页第一版已经落地的核心界面包括：顶部日志选择栏、左侧 sample 列表、右侧 sample 详情、异常列表、操作规格补全阶段、操作规格校验阶段、代码补全阶段、代码修复阶段、最终状态、日志片段预览
- 当前已落地的重点交互包括：异常列表按行聚类；操作规格补全阶段与操作规格校验阶段按 `LLM Response1/2/...` 展示回复片段；代码补全阶段与代码修复阶段按类顺序展开，并可查看对应类的 `LLM Response` 日志片段
