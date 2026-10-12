# Knowledge 的 Core 业务升级发布提案

状态：2026-10-12 的**待实现、待独立审查提案**。本文件没有修改 Deploy 控制器、安装控制器、启用发布开关或授权生产操作。两个业务 PR 可以继续评审；本提案不将现有发布器描述为已支持 Knowledge。

## 已核实的缺口

只读检查了 `Hang-666-star/auralink-deploy` 的 `main`，精确提交为 `c042058fc87eadc800e107014efa6b57a1de30fe`（GitHub 提交时间 2026-10-10）。没有连接生产服务器；仓库所写“待安装 / 开关关闭”不是本轮核实的现场状态。

当前没有可直接发布本候选的受支持业务升级路径：

1. [`r25/core_publish.py:41–83`](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/r25/core_publish.py#L41) 在独立核验仓库、release commit、run ID/attempt、JAR 和报告之后，仍强制完整 payload fingerprint 等于 V1 policy；不相等即 `executable_change_requires_new_reviewed_acceptance_contract`。Knowledge 增加 Java 类及 SQL 资源，必然不等价。
2. [`r25/core_release_guard.py`](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/r25/core_release_guard.py) 的 `POLICY_SCHEMA` 是 `R25_CORE_EQUIVALENT_PAYLOAD_POLICY_V1`；policy 绑定单一 payload 和单一 `application.yml` SHA。只有独立证明字节等价，才把数据能力检查委托给原 60c5 guard。
3. [`r25/core_runtime.py:209–251`](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/r25/core_runtime.py#L209) 对 `application.yml` 只做原样备份和安装，不能交付新的 Knowledge 目录与三哈希配置。仅放行新 JAR 仍不构成完整上线方案。
4. [`r25/core_acceptance.py`](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/r25/core_acceptance.py) 当前只做匿名 GET 与只读 PG 验收；认证详情明确为 `NOT_EXERCISED_NO_TEST_CREDENTIAL`。三个新接口都需要认证，现有检查不能证明新业务可用或公开字段聚合正确。
5. [`r25/install_core_controller.py:66–172`](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/r25/install_core_controller.py#L66) 是精确旧基线的一次性安装器，不是任意已安装 controller/policy 的升级器。不能通过手改既有摘要或重新执行安装来掩盖漂移。

Core 的当前 [`CONTRIBUTING.md`](../CONTRIBUTING.md) 和 [R25 制品契约](r25-coordinated-artifact-contract.md) 要求：业务升级先通过 Deploy 仓库 PR 更新兼容性与验收契约。Core `.github/workflows/deploy.yml` 仅在 `release` push / 手动 release 工作流构建候选，只有 `R25_DEPLOY_ENABLED` 精确为 `true` 才调用已安装控制器。Web release 有自动部署副作用；合并 main 和 CI 成功都不是部署成功。

## 最小受审改动范围

Owner：`Hang-666-star/auralink-deploy`，按其 `CONTRIBUTING.md` / `CODEOWNERS` 走独立功能分支 → PR 到 main；负责人和 c5ea/R25 协调审查。不要把这项变化混入 Core 或 Web 业务 PR 的部署脚本。

目标是**显式批准的无 schema 变更、只读 Knowledge 业务升级**，不建设任意 payload 自动放行机制。保留 V1 等价重打包语义、既有候选身份验证和所有原有拒绝条件。

| 归属文件 | 最小职责变化 |
| --- | --- |
| `r25/CORE_RELEASE_CONTRACT.md` | 增加独立 V2 业务契约、身份字段、认证验收、配置/资产状态与恢复规则；明确 V1 不变。 |
| `r25/core_release_guard.py` | 为 V1 与经受信安装的 V2 明确分派；V2 按精确候选 identity、完整 payload、受审兼容性及该事务允许的配置验证。不得让候选自报 capability 获得权限。 |
| `r25/core_publish.py` | 保留受信校验器及 commit/run/attempt 绑定；仅 V2 的受审授权记录可以接纳指定业务候选，记录所用契约、证据、配置和资产 manifest 摘要。V1 原比较仍严格执行。 |
| `r25/core_runtime.py` | 根据 release/transaction 绑定核验兼容性和配置；把受信配置变更纳入现有完整快照、固定文件集、CAS 安装及恢复；验收完成才更新 CURRENT。 |
| `r25/core_acceptance.py` | 增加候选版本对应的三个 Knowledge GET、认证与公开投影验收；只接受私有的既有验收会话输入，不登录、不签发 token、不打印 token、不提交任务。 |
| `r25/install_core_controller.py`（显式新模式或同目录独立受审升级器） | 增加旧 policy/controller → 新 policy/controller 的精确、可恢复安装事务；原一次性基线安装流程保持。实现前须只读刷新实际旧控制器身份；不允许自动吸收任意漂移。 |
| `r25/test_core_release_guard.py`, `test_core_runtime.py`, `test_core_acceptance.py`, `test_install_core_controller.py`, `test_publisher_transaction.py` | 增加下列隔离验收和故障注入；保留已有 Linux 文件系统与进程身份回归。 |
| `scripts/package-r25-release.py`, `r25/test_release_bundle.py` | 仅当新增受信模块时，显式更新 bundle 文件白名单和测试；不能用通配符打包候选代码、配置、fixture 或资产。 |

优先复用 `publisher_transaction.py` 已有无 schema 升级事务，不改为另一个生产 writer。现有 lifecycle/launcher 的 registry 接口和 c5ea 前端委托接口应能保留；只有具体证明接口不足时才另行审查 `control_overlay.py`，并保留其完整原摘要/单次精确匹配要求。Core 工作流和 `core-release-candidate.py` 已能携带精确候选，原则上无需扩大权限或改变触发器。

## V2 授权、配置与兼容性约束

受信 Deploy 契约/安装事务产生版本化的私有批准记录；候选上传包无权提供或更新该授权。记录至少绑定：

- 部署契约与实际执行校验器摘要；允许的仓库、release commit、Actions run/attempt、JAR SHA-256/字节数及独立解析的完整 payload fingerprint。
- Core API 能力 `knowledge-read-v1` 与 schema V001–V013 兼容性证据，配套 Web commit、当前真实运行 release 和经过验证的回退 release。
- 既有数据库/媒体保留能力证据及适用限制；完整 `application.yml` before/after 摘要、严格限定的 Knowledge 配置差异、外部资产 manifest 的摘要和固定根。
- Linux 控制器/发布事务故障测试、实际打包 JAR + 隔离 PG/认证 HTTP 回执的身份和位置；回执不能只写 `PASS`，也不能由候选自签后自动获得权限。

未来 release merge commit 与 Actions 产物尚未产生。当前业务基线是 Core `a6875b63fafd4c8658d79f6ec040d08b7cad04ad` 和 Web `a5e5c50b48c046eb5972c0d614fc932c8f3036b6`；本地 Core JAR `550d1bb9e71717226d5fc7645c94b6cb21234ecbfff44aa3fdae4f1f9c1a84d8` 只标识已验证本地产物。不能预填成未来 release 的 JAR 或把 feature commit 冒充 release commit。后续仅文档提交也须记录实际 PR head；正式授权必须绑定最终实际产物。

V2 不能借用“candidate == 60c5”等价证明。应由新受审兼容性适配器针对实际候选、实际当前数据检查 V001–V013、全部保留批次与媒体根及活动工作；可复用原只读 PostgreSQL 检查的语义，但不得把不同 payload 冒充 BASELINE 传给旧 guard 作为身份绕过。旧 guard 和历史 downgrade barrier 保持原样。未知或失效的兼容性证据拒绝升级和回退。

Knowledge 没有 schema/data migration。只允许新增 `auralink.knowledge.snapshot-root`、`graph-sha256`、`stats-sha256`、`china-sha256` 四项配置；其余配置原样保留，尤其认证、媒体路径、数据库、Guide/Critic 和 `configured-provider`。由受信协调程序生成和比较配置，不能执行候选提供的脚本或接受候选任意配置文件。记录 before/after 完整私有配置摘要，公开回执只写摘要和已脱敏差异。

现有 policy 对单一配置摘要的绑定必须升级为 release/事务阶段允许的精确 before/after 配置关系，否则安装 candidate 配置或恢复旧配置都会自相矛盾。必须覆盖 CURRENT 仍旧、ACTIVE 正在启动新版本及 RESTORE 三种状态，并拒绝任何第三种配置。不要用环境变量绕开 policy 的配置绑定。

资产在 Git 与应用发布包之外预置为完整、不可原地修改的版本目录；应用账号只有读/遍历权限。目录选择、三个 SHA 和权限证据参与批准记录，不能来自 HTTP 参数。数据安装与 publisher 的代码写入职责分开，publisher 仅验证已交付资产并在配置事务中选择固定批准目录。需要切换时使用完整新目录；graph/stats 不能逐个覆盖在线文件。详见 [快照契约](knowledge-snapshots.md) 及本轮资产交付说明。

## 必需的隔离验收标准

这些是**下一项 Deploy 改动的验收标准，尚未实现或执行**；已完成业务测试应作为输入证据复用，不无理由重跑。

| 场景 | 明确通过/拒绝标准 |
| --- | --- |
| V1 不回退 | 等价 JAR 仍可验证；单字节 class/resource/dependency/launcher 变化仍被 V1 拒绝；Knowledge 不能通过 V1。 |
| V2 身份 | 仅批准的仓库、commit、run/attempt、JAR bytes/hash/payload 组合能 stage；未批准候选、过期队列、报告替换、未知 policy、伪 capability、额外可执行条目均拒绝且不动 live 文件。 |
| 能力与保留数据 | 新旧代码分别在独立 PG16、V001–V013、legacy/huaniao 形状及保留媒体 fixture 上接受；保留对象缺失、数据资格不兼容、三类活动工作或查询失败均拒绝。与真实 huaniao 相关的 fixture 验证不能写成生产对账。 |
| 认证及隐私 | 无凭据的三个 GET 均 JSON 401；现有私有验收会话只用于 GET，过期/错误会话阻止完成验收。统计总体等于同一稳定数据窗口的 Gallery 总体；隐藏注释改变不影响聚合；字面量 0/未知按字段排除；不得暴露内部路径或私有注释。 |
| 资产正常 | 实际候选读取已验证外部三文件，图谱 711/609、三哈希与 manifest 一致、服务账号可读且不可写目录/文件，未配置/错误权限不得被记为完整 Knowledge 发布成功。 |
| 资产故障 | 在独立 staging 复制件验证缺失、截断、摘要错误、不一致 graph/stats、非法 map ring 和逃逸链接，得到安全 503；其余端点保持独立。恢复同一已批准完整文件后重试成功，无需模型/数据库写入。 |
| 配置及恢复 | 四项允许差异外的任何配置变化拒绝；before/after/POLICY/CURRENT/ACTIVE 阶段不匹配拒绝。故障注入涵盖每个 stop/install/start/accept/CURRENT CAS 及 controller/policy 升级边界；恢复完整文件/配置对，任何外来漂移拒绝并留下 RECOVERY_REQUIRED。 |
| 协调与进程 | 固定共享锁竞争阻止 Core；非终态 Core/AI 事务阻止新切换；验证 PID/start-ticks/cwd/port/所有权后才停 owned Java/watchdog；c5ea 保持 Next 唯一生命周期负责人，不重启 Web/AI/Tunnel。 |
| 正常验收与回退 | 旧 Web + 新 Core 正常；新 Web + 旧 Core 明确 unavailable/retry。保持 session-generation 和媒体 JSON 错误行为。仅 CURRENT 仍为该候选且旧版重新证明数据兼容时允许事务回退；停止后再次检查活动工作。 |
| 无副作用 | 全程无 Creation/Critic/上传/提供方请求；保留 `configured-provider`；无 DB 写入/恢复/导入；日志不含凭据；controller bundle 仍是固定白名单、源码字节加载并拒绝非受信缓存。 |

Deploy 必需检查仍为 `python -B -m unittest discover -s r25 -p 'test_*.py' -v`、Shell 语法、两个 Compose `config --quiet` 和既有 legacy 启动拒绝检查；只在隔离开发环境运行，不执行生产入口。新增权限/目录与 fsync/O_NOFOLLOW 测试必须在 Linux 跑，Windows skip 不算通过。实际打包 Core/PG/认证检查使用隔离账户和合成数据；生产验收会话的交付方式留给独立发布审批，不为本轮获取生产账号、JWT 或密钥。

## 候选专属发布与回退顺序

1. 先完成 Core/Web 业务 PR 审查和 Deploy V2 提案实现、CI 与负责人审查。main 合并不等于生产部署；本轮只创建业务 PR，不合并任何分支。
2. 在另行批准的准备窗口刷新实际运行 JAR/Web/AI/config/controller/policy 身份、磁盘、共享锁与恢复状态；不能把 10 月 10 日或 12 日报告当作当前身份。安装/升级受审控制器必须单独记录事务并验收 `recover-install` 或其 V2 对应入口。不要启用 `R25_DEPLOY_ENABLED` 以尝试绕过缺失契约。
3. 取得精确地图资产的再分发授权证据，交付 Git 外三文件、manifest 与只读权限。准备当前配置的四项限定差异及完整回退配置，绑定正式 release CI 产物。现有本地 owner-writable 预览目录不是生产只读权限证据。
4. 由 R25 在受审共享锁内先发布 Core 与配套配置，完成候选相关认证 GET、匿名 401、Gallery/媒体 JSON、公开字段与真实统计分母核验。允许发布验收暂挂，不能把缺认证/缺资产视为成功。不要提交模型任务来证明页面可用。
5. Core 与资产通过后，再安排 Web main→release；该分支动作有自动部署副作用，须有明确发布授权并由 c5ea/R25 协调。核实最终 Web SHA/BUILD_ID、API base 和两视图；记录组件 SHA、JAR SHA、Actions、资产 manifest、配置摘要与验收回执。
6. 回退优先通过 c5ea/R25 回退 Web，再用当前发布事务的受控 rollback 恢复**发布前刚验证的兼容 Core/配置**。不能默认采用历史 60c5 文件覆盖更新后的合法工作。若 Core 先回退，新 Web 仅 Knowledge 展示 unavailable/retry；无 schema 逆迁移，也不删除资产。
7. 保留全部现有数据、媒体、486 条已完成 huaniao 和缺图记录。不得恢复旧数据库、重导入或重发批次。回退重新检查数据能力、活动工作和进程身份；失败按 journal 显式 recover，不能声称自动回退成功。

生产数据核验、控制器现场安装、正式跨组件发布和回退均**未在本轮执行或验证**。已完成的 24 个 Core 测试、83 个 Web 测试、浏览器检查及 10,000 合成画作 / 266,000 注释 / 7,000 eligible / 50 名称验证仅证明所述隔离范围；1.585 秒为 JIT off、热缓存、单并发的本地 HTTP 中位数，不是生产性能承诺。

## 下一项可执行工作

由 Deploy 负责人从上述已核实 main（执行时先 fetch 并检查是否前进）建立独立功能分支，将本提案实现为候选限定 V2 契约与隔离测试，提交 Deploy PR 到 main，并关联 Core/Web PR。没有完成该独立改动和其验收前，保持业务候选为可评审、可本地预览，**不可通过现有 V1 发布器生产发布**。这不是授权本轮修改或安装生产控制器。

补充来源：同一 Deploy SHA 的 [团队规则](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/CONTRIBUTING.md)、[跨仓库协调](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/docs/collaboration.md)、[Core 发布契约](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/r25/CORE_RELEASE_CONTRACT.md)、[事务引擎](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/r25/publisher_transaction.py)、[bundle 白名单](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/scripts/package-r25-release.py) 与 [仅打包的 release 工作流](https://github.com/Hang-666-star/auralink-deploy/blob/c042058fc87eadc800e107014efa6b57a1de30fe/.github/workflows/release-bundle.yml)。
