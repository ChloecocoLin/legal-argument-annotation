# 法律论证标注系统 · 正式工程

中文裁判文书说理部分法律论证结构标注系统。本仓库为正式开发工程。

## 技术栈

- 前端：Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus + Axios；论证图使用 AntV X6。
- 后端：Spring Boot 3.2.5 + Java 17 + MyBatis-Plus + MySQL 8 + Redis；接口文档 Knife4j。

## 目录结构

```
标注系统正式工程/
├── frontend/                 前端工程
│   └── src/
│       ├── api/              接口函数：guide / adjudication / expert / request
│       ├── types/            TS 类型：common / guide / element / adjudication / expert
│       ├── shared/           共享组件（类型选择组件等）
│       └── features/         页面：guide / adjudication / expert
└── backend/                  后端工程
    └── src/main/java/com/argument/
        ├── common/           统一信封、异常、配置
        ├── guide/            指南模块 G
        ├── adjudication/     裁定模块 J
        └── expert/           专家模块 X
```

## 启动前端

```bash
cd frontend
npm install
npm run dev        # 访问 http://localhost:5173
npm run build      # 类型检查 + 生产构建
```

开发服务器已配置代理：`/api` 转发到 `http://localhost:8080`。

## 启动后端

本机 JDK 17 路径：`C:\Users\CHENLIN\.jdks\ms-17.0.20.1`。

默认使用 `local` profile，数据源为 H2 内存库，**无需安装 MySQL**：

```powersShell
cd backend
$env:JAVA_HOME="C:\Users\CHENLIN\.jdks\ms-17.0.20.1"
mvn -DskipTests package
java -jar target/argument-annotation-backend-0.1.0.jar
```

启动后验证：

- 健康检查：http://localhost:8080/actuator/health 返回 `{"status":"UP"}`
- 接口文档：http://localhost:8080/doc.html

连接真实 MySQL 与 Redis，使用 `prod` profile（参数放在 jar 后面）：

```
java -jar target/argument-annotation-backend-0.1.0.jar --spring.profiles.active=prod
```

> 注意：项目路径含中文。`mvn spring-boot:run` 的 fork 进程可能因路径编码找不到主类。请使用上面的 `package` + `java -jar` 方式启动。

## 接口口径

- 以 `接口与数据格式约定/接口契约/openapi-ready.yaml` 为最终依据。
- 统一信封：成功 `{ success:true, data, meta, error:null }`；失败 `{ success:false, error:{ code, message, details } }`。
- POST 请求由前端自动补充 `Idempotency-Key`。

## 当前进度

- M0 工程骨架与契约层：已完成（前端构建通过，后端可打包启动，健康检查 UP）。
- M1 标注指南管理：已完成。
  - 后端：G01–G06、G08–G11、G13/G14/G17 全部实现；H2 建表与演示数据；curl 验证通过。
  - 前端：指南管理页、指南版本详情页，严格还原可运行原型；`npm run build` 通过。
  - 已注册 MyBatis-Plus 分页与乐观锁拦截器；H2 初始化使用 UTF-8。
- 待办：M2 共享类型组件 → M3 命题裁定 → M4 专家复核 → M5 联调。

## M1 已知差异（需团队确认）

- GM 子类型：原型 data.js 为 6 个（含 GM-C）；指南 v1.2 正文为 5 个（合同解释放在 SM-C）。前端按原型渲染 6 个。
- 「设为启用」：契约无重新启用旧版本接口，按钮保留，点击只提示接口未就绪。
- 文件附件上传接口未就绪，上传功能目前只保存版本元数据（DRAFT）。
- G11 版本对比接口已实现，前端对比弹窗当前为原型静态两栏，暂未动态调用。
