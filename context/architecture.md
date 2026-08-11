# 仓库结构

当前仓库用于构建一个面向 iEcoregen pipeline 输出的日志仓库审查工具。日志数据集中存放在 `logs/` 下，每个实验运行目录保留一个 `log.txt`，但审查工具的最小分析单位不是文件，而是一次 `sample.mwe2` 运行。

```text
.
├── Readme_cn.md
├── Readme_en.md
├── prompts.txt
├── prompts_cleaned.md
├── context/
│   ├── architecture.md
│   ├── project_progress.md
│   └── readme_context.md
├── logs/
│   ├── AI-Games-Hex/
│   ├── OLRS/
│   └── R22_IPOApplication/
└── scripts/
    └── keep-logtxt-only.py
```

## 日志数据布局

`logs/` 使用三层业务目录：

```text
logs/<case>/<model>/<run>/log.txt
```

`log.txt` 是一个运行日志容器，内部可能包含一个或多个 sample 运行片段。工具应先按 sample 边界切分，再对每个 sample 运行做审查。

已观察到的维度：
- case：`AI-Games-Hex`、`OLRS`、`R22_IPOApplication`
- model：`deepseek-v4-flash`、`gemini-3.1-flash-lite`、`gpt-5.4-mini`、`minimax-m3`、`qwen3.6-flash`
- run：如 `olrs1`、`ipo3`、`hexgame5`

当前 `log.txt` 文件数量：50。

## 审查单位

最小审查单位定义为一次 `sampleN.mwe2` 运行，建议使用以下复合标识：

```text
<case>/<model>/<run>#sampleN.mwe2
```

已观察到的 sample 边界：
- 启动标记：`[<model>] 正在启动 sampleN.mwe2...`
- 完成标记：`sampleN.mwe2 启动完成`

切分规则：
- 一个 `log.txt` 可能只包含一个 sample 片段。
- 一个 `log.txt` 也可能连续包含多个 sample 片段，例如 `logs/R22_IPOApplication/deepseek-v4-flash/ipo5/log.txt` 中包含 `sample3.mwe2`、`sample4.mwe2`、`sample5.mwe2`。
- 如果某个 sample 有启动标记但没有完成标记，应视为未闭合片段，并进入失败/异常候选集合。
- 如果片段内出现 `ERROR`，不能立即判定失败；样例中存在 `ERROR` 后又输出 `There is no more compilation error` 和 `Workflow - Done.` 的情况，需要结合阶段和完成标记判断。

## 关键文件职责

- `logs/**/log.txt`：待分析的原始日志容器。样例中包含一个或多个 `sampleN.mwe2` 运行片段，以及 MWE2/Xtext/EcoreGenerator 启动、WARN/INFO/DEBUG 日志、时间戳、线程名、Java/Eclipse 插件加载路径等信息。
- `scripts/keep-logtxt-only.py`：清理 `logs/` 树，只保留各运行目录中的 `log.txt`，删除其他文件和无关目录。脚本默认会实际删除文件，执行前需要确认目标路径。
- `prompts.txt`、`prompts_cleaned.md`：项目相关提示词资料，后续可用于理解生成任务、归纳审查规则或构建基准。
- `context/`：项目上下文和进展记录目录，所有后续工作需要同步更新这里。
