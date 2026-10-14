# 成员 D 页面与契约基线

基线提交 `9d5db9a`，分支 `feature/member-d`。业务解释遵循指南、需求、开发框架、原型的优先级。参考可运行 HTML 原型；历史截图仅用于辅助，不替代当前原型。

| 原型页面 | 保留的区域和操作 | D 接口 | 本批次范围 |
| --- | --- | --- | --- |
| task-detail.html | 案例与标注版本；收集进度；任务概要、配置和操作 | T13，V01/V02 | 任务详情、X/N、本人与裁定者版本入口；A 的任务与文书使用本地适配器 |
| annotate-submit.html | 左侧成果统计、类型分布、关系清单；右侧核对、说明、声明和确认 | A04/A05 | 真实事务提交；未勾选不可提交；确认对话框；成功后版本入口 |
| conflict-resolve.html | 命题→关系→最终确认阶段条、候选、来源与进度 | V03、J01/J02/J04/J05/J07/J08/J11 | 下一批；复用 B 页面，命题/关系组件分别由 B/C 提供 |
| arbiter-annotate.html | 自定义编辑、来源、保存返回冲突项 | D 决定引用；B 的 J09/J10 | 下一批；J10 接口由 B 唯一维护 |
| final-review.html | 要素、关系、全部独立图、来源、质量检查、退回/入库 | FV01–FV06、L02 | 下一批；FV06 和通用审计仍待模型 |
| teaching 六页 | 教师首页、发布、标准样例、学生独立作答、结果、逐项比对 | ED01–ED20 | 后续；全部待模型，复用 A/B/C 引擎与评分 |
| export.html / export-records.html | 范围、格式、包含项、预览、记录、下载 | EX01–EX05、F01–F03 | 后续；文件/导出/下载模型待确认 |
| auto-annotate.html | 新任务、处理阶段、任务列表、模型信息 | M01–M04 | 后续；只有获授权任务创建者可管理 |
| admin-home.html / 历史入口 | 聚合统计及来源/审计查询 | U08、L01–L03 | U08 后续；审计公共对象待确认 |

## 本批次必要的原型差异

1. 原型中的所有核对项固定成功，正式页读取真实草稿/预置依赖状态，提交时服务端再次校验。
2. 原型默认勾选声明，正式页要求用户主动勾选。
3. 任务创建者不展示他人版本内容；标注员只读本人，裁定者读负责案例。X/N 是汇总，不作为内容访问授权。
4. 版本详情新增完整要素和结构化关系读取、来源与撤回理由入口。表达式由参与项推导，数据库不存其为关系事实。
5. 原型的“返回工作台/继续修改”需要 B 的本人工作台路由；当前返回任务，并在本地场景撤回后恢复来源草稿。不会跳到 B 的裁定者重标页冒充本人编辑页。
6. 没有用 N/N 自动打开未实现的裁定写入口；关系和最终流程不在本批次。

## 冻结输入与传输修正

`contracts/openapi-ready.yaml` 是共享 READY 文件的原样副本，SHA-256：
`813539491A53C0151C44BD426985F74A1DF4D5AD35A7DF1955D20C9CAE13B390`。

`.gitattributes` 对该文件关闭文本换行转换，避免 Windows Git checkout 改变团队要求的 SHA-256。

使用 Node 24.16.0、npm 11.13.0 和 `@hey-api/openapi-ts` 0.99.0 的默认输出。生成器作为精确版本开发依赖，与项目 TypeScript 5.6.3 一起锁定，避免 npx 临时安装选到 TypeScript 7 的不兼容依赖。生成物只读。

```powershell
cd frontend
npx --yes @hey-api/openapi-ts@0.99.0 -i ../contracts/openapi-ready.yaml -o ./src/generated/api-client
```

用户已明确授权按修订版 Markdown 的完整资源规则实现，并提交 OpenAPI 待评审补丁：

- A04/A05 返回完整 AnnotationVersion，包括 id、recordVersion、时间与来源，不丢失后续操作所需身份。
- V01 items 返回完整 AnnotationVersion，分页 meta.total 只计当前身份可见记录，submittedCount 仍为案例聚合值。
- V02 返回 `{version,elements,relations,participants}`，读取完整不可变快照。

补丁位于 `contracts/review/member-d-transport.patch.json`，是 RFC 6902 JSON Patch；不修改共享文件，不宣称已获四人评审。前端以生成的既有实体类型组合传输视图，不手改生成物。待评审通过后需同时修正全量与 READY 两份 YAML 的 Schema/示例、更新哈希并重新生成。

`ready-interface-baseline.json` 保存 D 的全部 READY 接口登记；PENDING_MODEL 的完整清单仍以共享目录为准。S04/S05/J04/FV06/ED/EX/F/L 不因演示或内部事务记录而变为 READY。
