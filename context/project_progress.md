# 项目进展

## 目标

构建日志仓库审查工具，分析 `logs/**/log.txt`。

## 关键规定

- 最小审查单位：一次 `sampleN.mwe2` 运行，不是一个 `log.txt` 文件。
- 日志路径：`logs/<case>/<model>/<run>/log.txt`
- sample ID：`<case>/<model>/<run>#sampleN.mwe2`
- 一个 `log.txt` 可能包含一个或多个 sample。
- `ERROR` 行不等于最终失败，需结合完成标记、`Workflow - Done.`、编译错误修复结果判断。

## 当前数据上下文

- `log.txt` 数量：50
- case：`AI-Games-Hex`、`OLRS`、`R22_IPOApplication`
- model：`deepseek-v4-flash`、`gemini-3.1-flash-lite`、`gpt-5.4-mini`、`minimax-m3`、`qwen3.6-flash`

