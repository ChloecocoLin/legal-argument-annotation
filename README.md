# 法律论证结构标注系统

面向中文裁判文书说理部分的法律论证结构标注平台。系统围绕“标注指南管理—要素标注—命题裁定—专家复核—论证图展示”组织业务流程，为法律论证语料建设、冲突裁定和质量复核提供统一的前后端工程。

> 当前版本：`0.1.0`　|　开发分支：功能分支协作　|　当前阶段：M4 专家复核闭环已完成

## 项目功能

| 模块 | 主要能力 | 当前状态 |
| --- | --- | --- |
| 标注指南管理 | 指南列表、版本详情、标签字典、版本创建/归档/审核/对比 | 已完成 |
| 文书题库 | 文书列表与详情、规范化原文与文本哈希校验 | 已完成（成员 A） |
| 任务与分配 | 创建任务、人员池、逐案分配、发布与启动 | 已完成（成员 A） |
| 原文与命题边界 | 工作台选区、拆分/合并、切割提交与裁定者审核 | 已完成（成员 A） |
| 共享要素类型 | 非命题、GM、SM、GF、SF 等类型的统一选择与展示 | 已完成 |
| 要素标注 | 文书片段选择、偏移量计算、要素类型标注、J09/G06 加载与 J10 保存 | 已完成 |
| 命题裁定 | 查看标注分歧、处理冲突、自定义裁定、升级专家复核 | 已完成 |
| 专家复核 | 待办列表、接受任务、提交结论与指南依据、确认完成、已确认问题文档 | 已完成 |
| 论证图展示 | 支持、反对、组合等关系的图形化展示 | 资源与基础依赖已接入 |

## 技术栈

### 前端

- Vue 3、TypeScript、Vite
- Vue Router、Pinia、Axios
- Element Plus
- AntV X6

### 后端

- Java 17 字节码（可用本机 JDK 25 编译运行）、Spring Boot 3.2.5
- MyBatis-Plus
- H2（本地开发）/ MySQL 8（联调与生产）
- Redis
- Knife4j / OpenAPI 3
- Maven

## 项目结构

```text
legal-argument-annotation/
├── frontend/                         # Vue 前端工程
│   ├── public/figures/               # 指南和论证关系示意图
│   └── src/
│       ├── api/                      # 前端接口封装
│       ├── features/                 # guide / adjudication / expert 页面
│       ├── router/                   # 路由配置
│       ├── shared/                   # 公共组件与视觉元数据
│       ├── stores/                   # Pinia 状态管理
│       ├── styles/                   # 全局样式与设计变量
│       └── types/                    # TypeScript 接口类型
├── backend/                          # Spring Boot 后端工程
│   └── src/main/
│       ├── java/com/argument/
│       │   ├── common/               # 响应信封、异常、鉴权测试桩、公共配置
│       │   ├── guide/                # 标注指南模块（成员 B）
│       │   ├── membera/              # 文书、任务、切割（成员 A）
│       │   ├── adjudication/         # 命题裁定模块（成员 B 骨架）
│       │   └── expert/               # 专家复核模块（成员 B 骨架）
│       └── resources/
│           ├── application.yml       # 公共配置
│           ├── application-local.yml # H2 本地环境
│           ├── application-prod.yml  # MySQL/Redis 联调环境
│           ├── schema.sql            # H2 初始化表结构
│           └── data.sql              # H2 演示数据
└── README.md
```

## 环境要求

| 工具 | 建议版本 |
| --- | --- |
| Node.js | 18 或更高版本 |
| npm | 9 或更高版本 |
| JDK | 17 或更高（本机可用 JDK 25 编译运行；字节码仍按 17） |
| Maven | 3.9 或更高版本 |
| MySQL / Redis | 仅 `prod` 环境需要 |

可先检查本机环境：

```bash
node -v
npm -v
java -version
mvn -v
```

## 本地运行

### 1. 获取代码

```bash
git clone https://github.com/ChloecocoLin/legal-argument-annotation.git
cd legal-argument-annotation
```

### 2. 启动后端

默认启用 `local` profile，使用 H2 内存数据库，不需要提前安装 MySQL 或 Redis。

```powershell
cd backend
mvn -DskipTests package
java -jar target/argument-annotation-backend-0.1.0.jar
```

后端默认运行在 `http://localhost:8080`：

- 健康检查：`http://localhost:8080/actuator/health`
- Knife4j 接口文档：`http://localhost:8080/doc.html`

> Windows 下如果项目路径包含中文，`mvn spring-boot:run` 的 fork 进程可能出现主类路径编码问题。建议使用上面的 `package` 和 `java -jar` 方式启动。

### 3. 启动前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到 `http://localhost:8080`。

本地鉴权使用联调清单中的固定 Bearer Token（不要做真实登录）。前端右上角可切换身份，默认 `dev-creator-li`。成员 A 页面：文书总库、创建任务、任务列表/分配、标注工作台原文与边界。预置任务 ID：`f102c523-b4ed-4323-81d3-0c564c4141a0`。

### 4. 构建检查

```bash
# 前端：类型检查与生产构建
cd frontend
npm run build

# 后端：编译
cd ../backend
mvn -DskipTests compile

# 后端：运行 M3 集成测试
mvn test
```

## 生产或联调环境

`prod` profile 使用 MySQL 与 Redis。使用前请修改 `backend/src/main/resources/application-prod.yml` 中的数据源和 Redis 配置，并避免把真实密码提交到仓库。

```bash
java -jar backend/target/argument-annotation-backend-0.1.0.jar --spring.profiles.active=prod
```

## 接口约定

- 前后端接口统一使用 `/api` 前缀。
- 成功响应：`{ "success": true, "data": ..., "meta": ..., "error": null }`。
- 失败响应：`{ "success": false, "error": { "code": "...", "message": "...", "details": ... } }`。
- 前端请求层会为 POST 请求补充 `Idempotency-Key`。
- 标签的存在性和分组以指南标签字典接口返回的 `category`、`baseType`、`sortOrder` 为准。

## 专家复核接口附录

专家接口可通过请求头 `X-User-Id` 指定当前用户；不传时 `local` 环境回退到演示专家（尾号 0010）。X03/X04/X05 是状态写操作，必须携带 `If-Match`，值为当前 `recordVersion`；版本不匹配返回 `CONFLICT`，缺头按参数校验失败处理。

| 编号 | 方法与路径 | 必需请求头 | 正常状态 | 关键错误码 |
| --- | --- | --- | --- | --- |
| J03 | GET `/api/v1/adjudication-sessions/{sessionId}/proposition-items` | — | 200 | `NOT_FOUND` |
| J12 | POST `/api/v1/adjudication-sessions/{sessionId}/expert-reviews` | `Idempotency-Key`（POST 自动带） | 200 | `EXPERT_NOT_ASSIGNED` |
| X01 | GET `/api/v1/expert-reviews` | `X-User-Id`（可选） | 200 | `FORBIDDEN` |
| X02 | GET `/api/v1/expert-reviews/{reviewId}` | `X-User-Id` | 200 | `FORBIDDEN` / `NOT_FOUND` |
| X03 | POST `/api/v1/expert-reviews/{reviewId}/transitions` | `If-Match` | 200 | `INVALID_REVIEW_STATE` / `CONFLICT` |
| X04 | POST `/api/v1/expert-issues/{issueId}/resolutions` | `If-Match` | 200 | `GUIDE_BASIS_REQUIRED` / `INVALID_REVIEW_STATE` / `CONFLICT` |
| X05 | POST `/api/v1/expert-reviews/{reviewId}/confirm` | `If-Match` | 200 | `OPEN_EXPERT_ISSUES` / `INVALID_REVIEW_STATE` / `CONFLICT` |

状态口径：复核单 `REQUESTED → IN_REVIEW → CONFIRMED`，问题 `OPEN → RESOLVED`；仍有未解决问题时 X05 返回 `OPEN_EXPERT_ISSUES`。

## 标签口径

正式 v1.2 使用 15 个标签：

- 非命题要素：`IS`、`AS`、`SS`、`JG`、`JD`、`Non-O`
- 一般规范判断：`GM-L`、`GM-I`、`GM-U`、`GM-M`、`GM-O`
- 个别规范判断：`SM`、`SM-C`
- 事实判断：`GF`、`SF`

合同及合同解释属于 `SM-C`，不启用早期原型中的 `GM-C`。

## 共享要素类型组件

`frontend/src/shared/ElementTypeSelector.vue` 是要素标注、命题裁定和专家复核页面共用的受控选择器。组件不维护业务字典，调用方应通过 G06 获取当前指南版本的 `LabelDefinition[]` 后传入。

```vue
<script setup lang="ts">
import { ref } from 'vue'
import ElementTypeSelector from '@/shared/ElementTypeSelector.vue'
import type { LabelDefinition } from '@/types/guide'

const labels = ref<LabelDefinition[]>([])
const selectedCode = ref<string | null>(null)
</script>

<template>
  <ElementTypeSelector
    v-model="selectedCode"
    :labels="labels"
    :loading="false"
    clearable
  />
</template>
```

主要输入：

- `labels`：G06 返回的标签定义数组。
- `modelValue`：当前选中的标签代码，可通过 `v-model` 双向绑定。
- `loading`、`error`：接口加载和失败状态。
- `disabled`：禁用全部选项。
- `clearable`：再次点击已选项时是否允许取消。

组件发出 `update:modelValue`、`change` 和 `retry` 事件。标签按照接口返回的 `category`、`baseType`、`sortOrder` 动态分组和排序，未知命题基础类型会进入“其他命题要素”，不会被静默丢弃。

## 要素标注工作台

`frontend/src/features/adjudication/ArbiterAnnotate.vue` 按原型实现三栏工作台，并复用共享要素类型选择器：

- 左侧选择裁判理由原文，按完整正文的 UTF-16 半开区间记录 `startOffset` 和 `endOffset`。
- 中间创建、定位、重新编号、修改类型和删除要素，并阻止要素范围重叠。
- 通过查询参数 `sessionId`、`itemKind`、`itemId` 调用 J09，再按上下文的指南版本调用 G06。
- 提交前检查标签、正文切片、标注范围与重叠情况，并按 J10 组装 `customElements` 和 `sourceElementIds`。
- 本地演示模式与真实接口模式明确分离，演示数据禁止提交到后端。

访问真实上下文的 URL 格式：

```text
/adjudication/annotate?sessionId=...&itemKind=PROPOSITION&itemId=...
```

后端 `CustomAnnotationController` 已完成 M3 的 J09/J10：

- J09 校验会话、裁定项、源版本、正文版本、标注范围和锁定指南版本的一致性，并返回候选要素与允许操作。
- 相同边界的多版本候选在前端合并来源链，保留一个可编辑命题；边界不同且交叠的候选不会被静默提交。
- J10 在服务端重新校验范围、重叠、标签、来源链和正文切片，生成自定义裁定快照及持久化要素。
- `Idempotency-Key` 支持同键同请求安全重试；同一个键对应不同请求时返回 `CONFLICT`。
- 当前 M3 仅开放 `PROPOSITION` 自定义裁定；关系自定义将在关系裁定阶段实现。

本地联调数据：

```text
sessionId = aba39933-394e-41e0-aa76-1cf8acc57ba7
itemKind = PROPOSITION
itemId = 058fa5ba-c4a8-457a-96f9-1efd15df8e20
```

可直接访问：

```text
/adjudication/annotate?sessionId=aba39933-394e-41e0-aa76-1cf8acc57ba7&itemKind=PROPOSITION&itemId=058fa5ba-c4a8-457a-96f9-1efd15df8e20
```

## 专家复核

成员 B 的 M4 闭环包含三个前端页面：

- `/adjudication/conflict`：命题冲突解决列表，按 `status`、`conflictType` 筛选命题项。
- `/expert`：专家复核工作台，左列表右详情；可接受任务、逐条提交结论与指南依据、全部解决后确认完成。
- `/expert/issues`：已确认疑难问题的只读文档，平铺展示问题、专家结论和指南依据。

演示数据（H2 每次重启重建）：

- 预置一张 `REQUESTED` 复核单（尾号 0030）和一个 `OPEN` 问题（尾号 0040）。
- 演示专家尾号 0010，已被指派到演示会话；页面顶部仅在开发环境显示“当前专家：演示专家（尾号 0010）”。

演示动线：打开 `/expert` → 选中预置复核单 → 点“接受任务”→ 填写专家结论和指南依据后“提交结论”→ 所有问题 `RESOLVED` 后点“确认完成”→ 打开 `/expert/issues` 查看只读结论。复核单状态变为 `CONFIRMED`；重启后端即恢复干净种子数据。

## 开发进度

- [x] M0：前后端工程骨架与接口契约层
- [x] M1：标注指南管理
- [x] 成员 A 功能切片：文书只读、任务分配、原文与边界切割
- [x] M2：共享要素类型组件
- [x] M2.5：要素标注工作台前端与接口编排
- [x] M3：自定义命题裁定（J09/J10 后端、数据结构、幂等与集成测试）
- [x] M4：专家复核闭环（J03/J12/X01–X05 后端、专家工作台与问题文档前端）
- [ ] M5：前后端联调与验收

### 成员 A 当前范围（本轮已接入）

READY 已实现：`D01` `D03` `D05`；`T01`–`T12` `T14`；`S01`–`S03`。页面发布按钮走 `T12` 的 `PUBLISH`，**没有第二条 T15 路由**。

适配/Mock（不当正式契约、不自创冻结表）：`D02/D04/D06/D08` 返回 `PENDING_MODEL`；`D07` 读预置概览；`T13` 仅核 X/N；`S04` 不写入，只读预置已确认关口。本地固定 Bearer Token，不做真实登录。

前端页面：文书总库/详情、创建任务、任务列表/分配/详情、标注工作台原文与边界。标签从 B 的 `G06` 加载，未分类边界不是 Element，不用 Non-O 冒充。

M1 当前保留事项：

- “设为启用”暂缺重新启用旧版本的后端接口。
- 附件上传接口未完成，新增版本目前只保存元数据。
- G11 版本对比接口已实现，对比弹窗尚未改为动态数据。

## Git 协作建议

开发前同步远程分支：

```bash
git switch main
git pull --rebase origin main
```

建议按功能创建分支，完成验证后再发 Pull Request 合并到 `main`。**不要直接 `git push origin main`**：默认推送会把新提交写进远程 `main`（快进时看起来像覆盖最新默认分支；`--force` 才会改写历史）。成员 A 请推独立分支，例如 `feature/member-a-document-task`。

```bash
git switch -c feature/your-feature
git add frontend backend README.md
git commit -m "feat: describe your change"
git push -u origin feature/your-feature
```

提交前至少执行前端构建和后端编译，且不要提交密码、IDE 配置、`node_modules` 或新的构建产物。

## 说明

本项目用于课程设计与团队协作开发。业务规则、标签定义及接口字段发生冲突时，以团队确认的最新指南和接口契约为准。
