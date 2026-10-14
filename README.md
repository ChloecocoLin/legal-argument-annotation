# 法律论证结构标注系统

面向中文裁判文书说理部分的法律论证结构标注平台。系统围绕“标注指南管理—要素标注—命题裁定—专家复核—论证图展示”组织业务流程，为法律论证语料建设、冲突裁定和质量复核提供统一的前后端工程。

> 当前版本：`0.1.0`　|　开发分支：功能分支协作　|　当前阶段：M2.5 前端已完成，M3 后端待开发

## 项目功能

| 模块 | 主要能力 | 当前状态 |
| --- | --- | --- |
| 标注指南管理 | 指南列表、版本详情、标签字典、版本创建/归档/审核/对比 | 已完成 |
| 共享要素类型 | 非命题、GM、SM、GF、SF 等类型的统一选择与展示 | 已完成 |
| 要素标注 | 文书片段选择、偏移量计算、要素类型标注、J09/G06 加载与 J10 保存编排 | 前端已完成，后端待 M3 |
| 命题裁定 | 查看标注分歧、处理冲突、升级专家复核 | 页面与接口骨架 |
| 专家复核 | 专家问题列表、复核详情、最终裁定 | 页面与接口骨架 |
| 论证图展示 | 支持、反对、组合等关系的图形化展示 | 资源与基础依赖已接入 |

## 技术栈

### 前端

- Vue 3、TypeScript、Vite
- Vue Router、Pinia、Axios
- Element Plus
- AntV X6

### 后端

- Java 17、Spring Boot 3.2.5
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
│       │   ├── common/               # 响应信封、异常、公共配置
│       │   ├── guide/                # 标注指南模块
│       │   ├── adjudication/         # 命题裁定模块
│       │   └── expert/               # 专家复核模块
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
| JDK | 17 |
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

### 4. 构建检查

```bash
# 前端：类型检查与生产构建
cd frontend
npm run build

# 后端：编译
cd ../backend
mvn -DskipTests compile
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

当前 `CustomAnnotationController` 的 J09/J10 仍是 M3 占位实现并返回空数据。前端只在 J10 返回有效 `annotationId` 时显示保存成功，不会把空响应当作成功。

## 开发进度

- [x] M0：前后端工程骨架与接口契约层
- [x] M1：标注指南管理
- [x] M2：共享要素类型组件
- [x] M2.5：要素标注工作台前端与接口编排
- [ ] M3：命题裁定
- [ ] M4：专家复核
- [ ] M5：前后端联调与验收

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

建议按功能创建分支，完成验证后再合并到 `main`：

```bash
git switch -c feature/your-feature
git add frontend backend README.md
git commit -m "feat: describe your change"
git push -u origin feature/your-feature
```

提交前至少执行前端构建和后端编译，且不要提交密码、IDE 配置、`node_modules` 或新的构建产物。

## 说明

本项目用于课程设计与团队协作开发。业务规则、标签定义及接口字段发生冲突时，以团队确认的最新指南和接口契约为准。
