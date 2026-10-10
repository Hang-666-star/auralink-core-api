# Core API 团队协作与发布

本仓库管理 AuraLink 的 Spring Boot 后端。其他仓库为 [Web 前端](https://github.com/Hang-666-star/auralink-web)、[AI 服务](https://github.com/Hang-666-star/auralink-ai-service) 和 [部署配置](https://github.com/Hang-666-star/auralink-deploy)。每个仓库独立 clone，使用自己的 origin。

## 日常协作

从最新 main 创建 feat/功能名、fix/问题名或 codex/任务名分支，完成检查后提交 PR 到 main。main 用于集成团队成果。提交改动、验证结果、关联仓库 PR、API 兼容性和回退方法，满足现有 CI 和负责人审查规则后再合并。

```bash
git clone https://github.com/Hang-666-star/auralink-core-api.git
cd auralink-core-api
git switch main
git pull --ff-only origin main
git switch -c feat/example
mvn -B -ntp clean package
python3 scripts/verify-r25-artifact.py target/auralink-backend-0.0.1-SNAPSHOT.jar --output target/r25-artifact-verification.json
git push -u origin feat/example
```

先检查 git status，保留未提交工作。测试只使用隔离测试环境；真实数据库、图片、上传文件、环境文件和模型权重不进入 Git。CI 中的批次工具检查还使用 Python 3.12 和 openpyxl 3.1.5，具体命令见 .github/workflows/ci.yml。

## main 到 release

阶段验收后创建 base=release、compare=main 的 PR。来源必须是本仓库的 main，fork 中同名 main 不符合发布条件。必需检查名保留为 Build core API 和 Verify main-to-release promotion。

release PR 使用 Create a merge commit，保留两个长期分支的历史。若有冲突，通过专门协调 PR 解决并重新检查，不强推 main 或 release。

## 当前发布边界

当前 deploy.yml 的名称是 Stage Verified Core API for R25。release push 会构建 JAR、校验 R25 契约并上传 Actions artifact。**这一步不会连接生产服务器或替换正在运行的后端。** 构建成功不能记作生产部署成功。

现有 core_publication_gate.py 禁止独立 deploy 和 dry-run，激活须经过已绑定 stage、入口、部署清单和后端制品哈希的 R25 协调发布入口。旧 Actions 中成功的 SSH 部署记录不代表当前版本仍使用旧流程。

要实现 release 合并后自动发布，必须将确切 SHA 的已验证制品接到现有 R25 协调器，保留其部署锁、运行任务检查、服务身份检查、配对验证、上线检查和恢复流程。接通前不得仅恢复旧 deploy-core.sh deploy 命令或直接替换生产 JAR。

跨组件发布记录 Core SHA、兼容的 Web SHA、AI 服务版本、API/配置/数据库变化、候选验证和回退版本。协调配置由 auralink-deploy 维护；在该仓库与实际 R25 运行环境对齐前，不用其旧 Docker/SQLite 模板替换现有生产服务。

## 数据与密钥

代码发布与画作批次导入分别验收。现有 tools/catalog_batch/validate.py 只校验输入文件，不证明已经写入生产 catalog_* 表；批次回滚工具也必须确认与实际生产 schema 匹配后才能使用。

所有数据库和提供商凭证只通过私有环境文件或部署 Secrets 提供。贡献者不需要获得生产密钥。生产运行参数以已核验的 R25 配置为准，README 中旧 SQLite/Docker 示例不能作为当前生产操作指令。
