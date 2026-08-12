# 项目进展

## 技术大纲
backend: Spring Boot
- 扫描 logs/**/log.txt
- 按 sampleN.mwe2 切片
- 根据 日志分析.md 的规则解析阶段、异常、补全类、修复类
- 提供 REST API 给前端

frontend: Vue 3 + Vite
- 日志选择栏
- sample 列表
- 阶段行号展示
- 异常提示
- 代码补全/修复类列表
- 后续可加原始日志片段、搜索、筛选、统计面板