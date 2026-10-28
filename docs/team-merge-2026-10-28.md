# 团队分支集成记录（2026-10-28）

集成目标为 `integration/team-merge`，本次不合入 `main`。合并前备份分支为 `backup/team-merge-before-c-d`，指向 `32c0435`。

## 分支与历史

| 来源 | 本次固定提交 | 处理 |
| --- | --- | --- |
| A `feature/member-a-document-task` | `4b8cb14` | 原 PR #2 已合入 |
| B `feature/member-b-expert-review` | `a82a9fa` | 原 PR #4 已合入，包含 B 其余三个阶段分支 |
| C `feature/member-c-relation-graph` | `ab6f03d` | 第一次 merge commit 合入，处理 7 处冲突 |
| D `feature/member-d` | `4173ebe` | 第二次 merge commit 合入，处理 46 处冲突 |

保留真实双亲合并历史，不使用 squash、强制推送或 `-s ours`。本地 fetch 已改为跟踪全部远端分支，避免默认只同步 main/D 而漏读其他成员进展。

## 统一实现与冲突选择

D 的 12–21 批交接记录表明，其已从上述 A/B/C 提交移植并适配必要能力。因此共享文件以 D 的统一数据、身份和事务版本为基础，再保留 C 尚独立提供的指南关系定义接口、纯关系算法、图示组件、评分器及 B 的指南类型预览。

| 能力 | 集成后的权威实现 |
| --- | --- |
| 登录、账号与权限 | C 协议/安全链 + D 的 TokenStore、UnifiedVersionIdentity、共享账号；A AuthContext 读取相同主体 |
| 文书、任务、分配与切割 | D 已持久化适配的 A 模块，保留同一案例锁、事务与幂等日志 |
| 普通草稿、要素与关系 | DraftController/StructureController，复用 B 类型组件及 C 纯规则、装配器 |
| 裁定与专家复核 | D 会话、ReviewWorkflowService 及其 B Controller 适配，唯一状态与来源集合 |
| 完整图、最终审查与导出 | C 图元/规则 + D 冻结快照、审查、试行入库与文件归档 |
| 教学 | C 纯评分器 + D teachingtrial 的课程/作业/独立作答流程 |
| 指南关系定义 | C G07/G15/G16 + D Flyway 数据库；补充 V13/V902 增量迁移及 PATCH 权限 |

没有把 C/B 原独立 SQL 表再建成另一套业务事实，也没有重新启用 `schema.sql`/`data.sql` 的删表初始化。旧 SQL 文件仅保留源码参考，`spring.sql.init.mode=never`。

旧独立适配器仍保留源码，但移除 Spring 自动注册：C 的 RelationController、ArgumentGraphController、RelationItemController、TeachingController、MockPropositionController，以及对应依赖旧 SQL/Mock 的服务；B 旧的 ReviewQueryService、CustomAnnotationServiceImpl、专家服务和默认身份 Provider；A 的 WorkflowIdentityDirectory。它们已由上表的共享实现取代，不能手动重新加上组件注解，否则会出现重复路由、第二份事实或 Mock 身份。A 的旧 DevAuthFilter 已删除，其固定 Token ThreadLocal 协议与统一安全链不兼容。

纯算法继续保留并运行测试，包括 RelationValidator、GraphAssembler、RelationCanonicalizer、TeachingScorer 和 RelationTemplateCodec。保留 D 对嵌套结构键、无向关系等价性的修正。不要用旧演示 SQL 服务覆盖这些适配。

## 页面与公共层

任务内本人工作台为 `/tasks/:taskId/cases/:caseId/annotate`，集中提供要素、关系、图和布局；裁定为 `/adjudication/conflict`，最终图审查为 `/final-review`，教学为 `/teaching`。

旧独立演示地址通过重定向进入共享工作流：

| 旧地址 | 当前入口 |
| --- | --- |
| `/annotation/workbench` | `/tasks`，选择真实任务及案例 |
| `/annotation/graph-audit` | `/final-review` |
| `/adjudication/relation` | `/adjudication/conflict`，保留查询参数 |
| `/teaching/exercises` | `/teaching` |

旧独立 Vue 页面及客户端保留源码参考，默认路由不加载它们；不将 mock_draft 或独立 SQL 题库冒充共享数据。图审查能力位于共享工作台和最终审查中。

请求层同时保留 C 的统一 401 回调与 D 的下载错误解析、DELETE 幂等键；保留统一用户 store 与导航。错误码合并去重，异常处理同时保留 C 的拒绝访问日志和 D 的缺失头校验。

## 验证

使用 Java 17；默认 PATH 中 Java 8 不适用于本项目。后端测试使用 `test` 隔离内存库，未启动或修改现有 `backend/.local-data` 业务库。

- 前端：`npm run build`，包括 vue-tsc 类型检查及 Vite 生产构建。
- 后端：先清理旧 target 编译产物，运行全部测试；最终 `mvn package` 再次运行测试并打包。
- B 原无鉴权、固定 SQL 演示会话的 Controller 测试改为统一鉴权边界检查；真实自定义保存、幂等、源版本不变及专家回流由 OrdinaryDraftIntegrationTest 等集成测试验证，没有禁用这些测试。
- 新增 TeamMergeIntegrationTest，验证 C 指南关系字段能在 D 的迁移库读取、普通标注员不能经 C PATCH 路径修改关系定义、旧 Mock HTTP 入口未发布。
- C 的关系/差异/鉴权测试与 D 的跨模块、持久化、导出、教学等回归一并执行。

最终结果：`mvn package` 共 229 项测试，0 失败、0 错误、0 跳过，并成功生成可运行 JAR；前端类型检查与生产构建通过，仍有现有大包体积提示。

对打包 JAR 使用独立 test 内存库、端口 18961 做启动冒烟：health=UP，真实账号登录 200，指南关系定义 5 项，任务列表读取 200，匿名任务读取 401，已停用 Mock 地址 404。验证后进程已关闭。为正确返回该 404，公共异常处理补充 NoResourceFoundException 映射，避免缺失地址落入通用 500。

## 尚待团队联合验收

本次完成代码合并、自动化回归与构建检查，不替代多账号逐页人工验收。D 的 local/test 试行能力、待冻结契约、生产依赖及教学总分权重等边界保持不变。共享 READY YAML/生成 SDK不因本次合并被改写。下一步按团队流程完成联合验收后，再创建 `integration/team-merge` → `main` PR。
