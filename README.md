# 法律论证结构标注系统

面向中文裁判文书说理部分的法律论证结构标注平台。系统围绕“标注指南管理—要素标注—命题裁定—专家复核—论证图展示”组织业务流程，为法律论证语料建设、冲突裁定和质量复核提供统一的前后端工程。

> 当前版本：`0.1.0`　|　开发分支：`main`　|　当前阶段：M1 已完成，M2 待开发

## 项目功能

| 模块 | 主要能力 | 当前状态 |
| --- | --- | --- |
| 标注指南管理 | 指南列表、版本详情、标签字典、版本创建/归档/审核/对比 | 已完成 |
| 共享要素类型 | 非命题、GM、SM、GF、SF 等类型的统一选择与展示 | 待开发 |
| 要素标注 | 文书片段选择、要素类型标注、自定义标注 | 待开发 |
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

默认启用 `local` profile，使用持久化 H2 文件数据库和 Flyway，不需要提前安装 MySQL 或 Redis；启动不会重建已有数据。

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

## 开发进度

- [x] M0：前后端工程骨架与接口契约层
- [x] M1：标注指南管理
- [ ] M2：共享要素类型组件
- [ ] M2.5：要素标注
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

## 成员 D 第 1–4 步

已新增任务进度、版本列表/完整详情、提交确认与撤回页面，以及 T13、V01/V02、A04/A05 后端实现。使用数据库事务冻结完整快照，支持幂等、乐观锁、来源保留和盲标权限校验。

访问 `/tasks`，在开发模式选择“本地联调身份”，可用陈晓/刘一鸣各自提交，然后以赵明远查看负责案例的版本。预置统一切割和 A/B/C 依赖不计为对应模块已实现；正式版本保存到数据库。生产必须由 A/B/C 提供正式适配器和认证。

如果默认端口被原型或其他服务占用，可分别指定：

```powershell
# backend，已使用 JDK 17 打包后
java -jar target/argument-annotation-backend-0.1.0.jar --server.port=18080
# frontend，在另一个终端运行
$env:VITE_API_TARGET='http://127.0.0.1:18080'
npm run dev -- --host 127.0.0.1 --port 5174
```

后端检查：`mvn test`；前端检查：`npm run build`。D 的基线、接口评审补丁及适配边界见 [页面与契约基线](docs/member-d/01-page-and-contract-baseline.md)、[基础与接入](docs/member-d/02-foundation-and-integration.md)。共享 READY 契约文件未改动；V01/V02/A04/A05 按用户授权的修订版 Markdown 实现完整资源，OpenAPI 补丁单独待评审。

本地 H2 数据位于 `backend/.local-data`，不提交到 Git。正式公共审计、统一切割确认、裁定和入库、教学、导出不在本批次。旧指南表的整体规范迁移仍需团队统一。

本项目用于课程设计与团队协作开发。业务规则、标签定义及接口字段发生冲突时，以团队确认的最新指南和接口契约为准。
