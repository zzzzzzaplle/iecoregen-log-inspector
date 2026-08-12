# 仓库结构

```text
.
├── Readme_cn.md
├── Readme_en.md
├── prompts.txt
├── prompts_cleaned.md
├── backend/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   └── src/
│       ├── main/java/com/iecoregen/loginspector/
│       │   ├── LogInspectorBackendApplication.java
│       │   ├── controller/LogController.java
│       │   ├── service/LogAnalysisService.java
│       │   └── model/
│       └── main/resources/application.properties
├── frontend/
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── App.vue
│       ├── api.js
│       ├── main.js
│       └── style.css
├── context/
│   ├── architecture.md
│   ├── project_progress.md
│   ├── readme_context.md
│   └── 日志分析.md
├── logs/
│   ├── AI-Games-Hex/
│   ├── OLRS/
│   └── R22_IPOApplication/
├── scripts/
│   └── keep-logtxt-only.py
└── web/
```

- 当前仓库是一个可运行的日志审查系统：`backend/` 负责日志解析与 API，`frontend/` 负责本地网页展示。
- `backend/` 使用 Spring Boot + Maven，默认端口为 `8080`，核心职责是扫描 `logs/`、切分 `sampleN.mwe2`、识别阶段边界、抽取异常事件、整理各阶段的 `LLM Response` 片段，并提供 `/api/logs`、`/api/logs/{id}/analysis`、`/api/logs/{id}/lines` 接口。
- `frontend/` 使用 Vue 3 + Vite，默认开发地址为 `http://127.0.0.1:5173/`，通过 `/api` 代理访问后端；当前页面结构已经包含日志选择栏、sample 列表、异常列表、阶段展开区以及日志片段预览。
- `context/日志分析.md` 是日志规则层面的主文档，记录 sample 切分、有效起点判定、阶段边界、异常模式等分析结论；`architecture.md` 和 `readme_context.md` 更偏向仓库结构与工程上下文。
- `logs/` 采用 `logs/<case>/<model>/<run>/log.txt` 的固定路径结构，当前仓库内共有 `49` 个 `log.txt`。
- 上面的树有意省略了构建产物与依赖目录，例如 `backend/target/`、`frontend/node_modules/`、`frontend/dist/`，避免把运行时噪声混进架构视图。
