# Assistant Core 基础阶段合同（本地候选）

本轮只提供账户所有的会话、历史结构、执行状态持久化原语和私有图片附件。没有聊天提交 HTTP 接口、模型调用、RAG、生产可选 mock provider、定时清理或新进程管理器。已有 Knowledge、Gallery、Critic、认证、媒体规则及 R25/c5ea 不变。数据库和图片仍由 Core 管理；后续 AI 只执行任务，不能成为第二个会话数据库。

## 关闭兼容和配置

`auralink.assistant.enabled` 只有显式 `true` 才创建 Assistant 配置、存储和附件 bean。缺省或 `false` 均关闭；关闭时不读取 Assistant 表、不创建附件目录、不要求内部令牌或提供商配置。未使用的 Assistant 附件/限额配置也不绑定。现有 PostgreSQL V001–V013 可以启动新 Core。现有认证仍适用于所有 `/api/v1/assistant/**` 路由：未登录拒绝；登录后 `/status` 返回 `enabled:false`，其余已实现接口返回 503 `ASSISTANT_DISABLED`。既有健康语义不受影响。

开启基础阶段需预先通过受审查的显式过程应用 `db/postgres/migrations/V014__assistant_records.sql`，启用既有 catalog authentication 的 PostgreSQL 数据源，并配置独立绝对附件根。没有启动 DDL、Flyway 新位置或自动迁移。生产 `flyway.enabled=false`、既有 SQL 及校验和保持原样。V014 依据本分支起点和已核实 V001–V013 预留；实施下一轮前仍应检查主干编号占用。测试 harness 的显式迁移仅针对其创建的隔离数据库，不是生产迁移工具。

| 配置（Core 独占） | 本轮值/含义 |
|---|---|
| `auralink.assistant.enabled` / `AURALINK_ASSISTANT_ENABLED` | 缺省关闭；显式 true 仅开启 foundation，`executionAvailable:false` |
| `auralink.assistant.attachment-root` / `AURALINK_ASSISTANT_ATTACHMENT_ROOT` | 无默认值；独立 0700 目录，不能与已配置批次/公共/私有媒体根互相包含；首次只接受空目录，专用标记确认命名空间 |
| `auralink.assistant.max-upload-bytes` | 缺省 10485760，允许 1–10485760；既有 multipart 上限保持不变 |
| `auralink.assistant.max-image-pixels` | 缺省 32000000，上界 40000000 |
| `auralink.assistant.request-replay-window` | 缺省 7d，仅结果引用可重放窗口，不是请求键重用期限 |
| `auralink.assistant.max-pending-per-session` | 缺省 8，上界 100；排队量和执行槽不同 |

这些限额/清理时间是隔离候选默认方案，没有激活生产保留策略。使用既有 `catalogAuthDataSource`，不新增数据库口令变量，不复用 `PORT`、`QWEN_MODEL` 或 demo `.env`。Spring 正常属性优先级适用，未增加隐藏 fallback。无 AI 内部令牌或 provider 设置。后续 AI 必须把 Assistant 配置局部隔离：缺失/格式错误只能使 Assistant 不可用，不能阻止既有 Critic 启动或改变其健康判断。

## 公共 API 和 DTO

所有 owner 来自 `CurrentUserService.requireCurrentUser().getId()`，不读取客户端 `userId`、`ownerId` 或 `clientId` 作为授权。对象 ID 是 UUID。另一账户、不存在和已删除对象统一 404。响应使用 `Cache-Control: no-store`；附件失败即使请求 `Accept:image/*` 也返回既有 JSON 错误结构。`storage_key`、磁盘路径、执行 fence、checkpoint 和数据库凭据不在公共 DTO。

| 方法/相对 `/api/v1/assistant` 路径 | 请求与返回 |
|---|---|
| GET `/status` | `{enabled,stage:"foundation",executionAvailable:false}`，不是模型能力声明 |
| POST `/sessions` | JSON `{requestKey,title}` → Session；key 8–128 字符 `[A-Za-z0-9_.:-]`，title 1–160 字符 |
| GET `/sessions?limit=30&offset=0` | 当前账户 ACTIVE 会话数组；limit 1–100，offset 0–100000 |
| GET `/sessions/{id}` | Session `{id,title,activeBranchId,version,summaryAutoEnabled,status,createdAt,updatedAt}` |
| PATCH `/sessions/{id}` | `{expectedVersion,title?,activeBranchId?,summaryAutoEnabled?}`；乐观版本不符 409；分支必须属于本会话 |
| DELETE `/sessions/{id}` | 204；同一 owner 重复删除保持 204；立即 tombstone，不等待文件清理 |
| GET `/sessions/{id}/branches` | 分支 ID、父分支、fork/head message、version 和时间 |
| GET `/sessions/{id}/messages?branchId=&includeHistory=false&limit=200` | 消息包含 parent/versionGroup/supersedes、role/body/state/runId、时间和 attachmentIds；缺省当前分支，显式 history 可读保留版本；limit 1–10000 |
| GET `/sessions/{id}/export?format=json&includeHistory=false` | 默认当前分支；显式 history 才含历史分支。JSON schemaVersion=1；CSV 消息列；有界导出超限拒绝，不无限占内存 |
| POST `/sessions/{id}/attachments` | multipart `file` + `requestKey`，JPEG/PNG；返回私有 Attachment DTO |
| GET `/attachments/{id}` | `{id,sessionId,filename,mimeType,byteSize,sha256,width,height,status,contentUrl,createdAt,updatedAt}` |
| GET `/attachments/{id}/content` | 再次检查所有权/生命周期/READY、长度和哈希；返回实际图片，nosniff，受认证的相对 URL |
| DELETE `/attachments/{id}` | 未被历史引用附件标记 DELETING；已被保留消息引用则 409，删除会话可整体撤销 |

会话更新的版本和幂等键都用 JSON/form 字段；不修改共享 CORS 允许头。没有新增公开原始文件路由。分支编辑、聊天接受、排队/claim/checkpoint/finish 是**内部持久化原语**，本轮只通过明确测试夹具使用，不提供能伪装真实模型生成的 API。创建摘要开关仅保存偏好，不触发模型。

JSON 导出不嵌入图片原文，包含受控附件 ID、哈希、元数据及读取地址；CSV 是消息内容导出，不宣称包含所有关联对象。CSV 对引号/换行做转义，对 `= + - @`（包括前置空白）、首位制表/回车/换行做公式防护。显示标签和原始文本不修改。后来 Web 仍需安全文本渲染。

## 持久化和四项修正

新增表为 `assistant_sessions/branches/messages/requests/runs/attachments/message_attachments/run_events/citations/interaction_selections/summaries/stage_classifications`。后五类是后续执行/交互所需的有界持久结构，本轮不伪造业务写入。所有 session/branch/message/run/attachment 关联使用同会话复合 FK；request owner/session 和 message version group 也有复合完整性约束。没有改 catalog 数据或引入 SQLite/MySQL。

1. **关闭兼容**：条件 bean 保证 Assistant 关闭时无依赖；schema 扩展是独立、显式、加法操作。关闭不能被某个非空 key 或文件悄悄打开。
2. **队列/执行**：会话行锁内控制待处理容量。唯一索引只限定 `status='EXECUTING'` 每会话一个，另限定一个 QUEUED SUMMARY；没有把 QUEUED 和 EXECUTING 合成全局唯一槽。相同上下文摘要复用，后续聊天使旧排队摘要失效；分支切换也使排队摘要失效；claim 优先 CHAT。正在执行的付费摘要不取消重发，新 CHAT 等下一槽。一次仅保留一个待完成 CHAT，避免未完成回复造成上下文顺序歧义。没有本轮 dispatcher/自动重试/真实计费。
3. **删除/迟到写**：会话 tombstone 事务先锁会话，清空正文/标题/运行 checkpoint，撤销请求结果、附件读取和未结束运行，递增 fence，删除事件/引用/摘要等内容。上传 commit、checkpoint、finish、读取/导出生成及清理同样检查/锁定生命周期。删除提交后新订阅、导出、worker 写入必须拒绝，不可自动新建 session。已在删除前线性化并交给网络的字节无法从客户端撤回；本轮没有 SSE 广播。后续 SSE 必须在重连和每次发送前校验 lifecycle/fence，墓碑后停止发布，不可只在订阅建立时校验。
4. **幂等生命周期**：键按 owner+operation 隔离，保存键哈希、规范 payload 哈希和结果引用。窗口内相同 payload 重放原结果；不同 payload 409；EXPIRED/DELETED 同 payload 410 `ASSISTANT_REQUEST_GONE`，绝不作为新请求调用 provider。过期只清 result 引用，不释放键；最小墓碑无正文/原始键，当前无自动清除。删除后不同 payload 同键仍是 409。后续保留策略必须覆盖备份恢复/重放风险，不能仅按 TTL 删除墓碑。

accepted user message、附件关联、QUEUED CHAT 和幂等记录同一事务提交后才允许未来执行。checkpoint 带 fence 和 expected sequence；不匹配拒绝。COMPLETED/FAILED/CANCELLED/INTERRUPTED 均显式保存，重启不能把不确定执行变回 QUEUED。显式 `interruptExecuting` 封锁旧 worker；不静默重发已可能付费请求。编辑创建新 branch/message version，保留旧分支，当前上下文沿 parent chain，只含选中分支；不能静默覆盖原文。

## 附件写入、恢复和隔离清理

仅复用 `ImageContentValidator`，用独立限额实例；不调用 Critic 上传 store、不写 `catalog_private_media_assets`，也不触碰既有 media root/ACL。JPEG/PNG 做签名、结尾、解码、像素上限、MIME/扩展名校验；WebP 不在本轮支持范围。文件名仅元数据，真实文件名使用服务生成 UUID。

有界读入 → PostgreSQL UPLOADING reservation → 0600 `.part`/fsync → 验证 → 原子 rename `.blob`/目录 fsync → READY。上传和删除以会话锁串行化。失败可能保留 UPLOADING/FAILED 和已知文件：不会假称数据库事务能回滚文件系统。重复 pending 键返回冲突，客户端查询状态，不重送付费任务。READY 读取每次校验真实 bytes/hash，降低新上传限额不使历史有效图片失效。

显式 `AssistantAttachmentService.cleanup(staleAge,limit)` 使用受控 Clock；不是公开接口、启动任务或生产 cron。只处理 DB 登记的 FAILED/DELETING，或超过指定期限的 UPLOADING；删除前重新加锁判断，校验 root inode/标记、NOFOLLOW/nlink、内容哈希和文件 preimage，拒绝漂移。不会递归扫描删除未知文件或更改权限。中断的 .part/.blob 可按同一记录恢复清理；删除会话立即撤权，物理擦除独立进行。

记录/附件配额及未引用 READY 附件到期策略仍为下一轮产品/运营政策；本轮仅显式删除与失败上传恢复，未开启生产回收。既有备份可能仍含被删除内容，其受控访问和保留边界必须在生产启用前确认。不承诺零数据丢失或准确计费。

## 验证和后续范围

本分支测试使用真实隔离 PostgreSQL 16、两个合成账户、非空既有 catalog/user/task fixture、单独 0700 附件命名空间，使用正常 `/api/auth/login`，无生产连接或提供商调用。具体命令、制品哈希、通过/跳过数量及重启证据见本轮 stage handoff，不把有条件跳过的历史 PG 测试记作通过。

下一 AI 轮接入模型能力/显式选择、幂等执行、取消与不确定状态恢复、RAG/平台画作分离引用、摘要和选择卡写入，补齐 SSE lifecycle 和 bounded dispatch 验收；客户端断线不代表自动取消付费执行。下一 Web 轮实现共享 AppShell 下跨设备会话/图片、分支、时间线、导出和浏览器语音，保留 session-generation 与媒体 JSON 错误处理。实际 provider/RAG 调用预算尚未授权。本轮不包含生产迁移/控制器升级/PR/发布。

## S3 附件刷新修复（2026-10-14）

- `GET /api/v1/assistant/sessions/{id}/attachments/draft?limit=50&cursor=...`：沿用 `items/nextCursor/hasMore`，limit 1–100。仅返回当前账号/活动会话内 `READY`、没有任何保留消息引用、且关联原 `UPLOAD_ATTACHMENT` 请求仍为 `ACTIVE`、未过期的附件。每项增加 `draftExpiresAt`（原请求的 expires_at）。不会把无有效请求的孤儿记录、失败/上传中、已移除/删除、已发送图片批量重新放回输入框。
- 草稿属于会话，而不是某个猜测的分支。原上传合同没有 branchId；刷新/选择分支只恢复会话草稿，不自动生成或改变历史。当前请求重放窗口（缺省7天）也是未发送附件可恢复窗口；过期不自动删除文件，已发送图片继续经持久消息引用读取，不受草稿窗口影响。没有迁移或新清理任务。
- `POST /requests/resolve` 的附件结果增加 `resultStatus`（UPLOADING/READY/FAILED 等实际状态）、`attachmentLinked`、`expiresAt`。这是读取原请求的状态，不重启写入或创建请求。未知键为 NOT_FOUND；已删除/过期仍410，同键不同payload仍409。他人/删除会话404。非附件操作新状态字段为null。
- 浏览器丢失响应应先查原请求；UPLOADING 只能等待/查询或明确移除，不自动换键。FAILED/已移除/过期可结束旧操作，用户明确重新选择时建立新的上传意图；同键不能重启。READY+linked从历史恢复，不再作为待发送草稿。撤销浏览器object URL、页面卸载/刷新不能发DELETE。
- 本次添加只读恢复接口，不放宽内容权限、路径/哈希检查或删除tombstone。失败的历史上传/文件保留供检查；没有对已有记录运行自动修复或清理。

## S4 显式存储策略和有界维护（本地候选，未批准生产值）

V016 `V016__assistant_storage_controls.sql` 在 V001–V015 之后加法应用，旧 SQL 字节不变。新增 `assistant_attachments.draft_expires_at` 和三个查询索引。历史草稿从原上传请求的 `expires_at` 回填；无原请求映射的附件不会被猜测为可恢复草稿。不存在自动 Flyway/启动迁移。Assistant 关闭仍不访问新增表或目录；既有应用可使用扩展 schema，需以执行证据确认具体旧 JAR。

生产没有默认账户配额或自动清理期限。下列属性只由 Core 消费，不复用 Guide/Critic 提供商变量；环境变量使用对应全大写下划线名。策略解析失败不会抛出启动异常；新上传返回 503 `ASSISTANT_STORAGE_POLICY_UNAVAILABLE`，既有读取、下载、导出、删除和其他模块继续可用。文本执行是否启用仍由既有 execution 合同决定，不因存储策略非空而自动启用。

| 属性前缀 `auralink.assistant.storage.` | 合同 |
|---|---|
| `quota-bytes` | 必填正整数，每账户总附件逻辑字节上限；未配置不允许新附件 |
| `draft-lifetime` | 必填 ISO-8601 正整数秒 duration；实际新草稿到期为此值与原请求重放窗口的较短者 |
| `minimum-free-bytes` | 必填非负整数；写入前保留的磁盘可用空间，不能保证并发其他程序不耗尽磁盘 |
| `cleanup-enabled` | 缺省 false；只能精确 true/false。true 才允许本地维护命令，不启动后台定时任务 |
| `stale-upload-age` | cleanup 开启时必填正整数秒 ISO duration；过期上传 reservation 可回收 |
| `failed-grace` / `deleted-grace` | cleanup 开启时必填正整数秒 ISO duration；故障/逻辑删除后的物理清理延迟 |

`GET /api/v1/assistant/storage` 返回仅当前账户的 `policyStatus`、`historyOnly`、`uploadsAvailable`、`quotaBytes`、`readyBytes`、`reservedBytes`、`logicalBytes`、`cleanupReservedBytes`、`chargedBytes`、`retainedFileBytes`、`pendingCleanupBytes`、`availableBytes`、`draftLifetimeSeconds`、`cleanupEnabled`。响应无目录或他人数据，no-store。logical=READY 原始文件字节+UPLOADING完整声明字节；本版本没有衍生图片，衍生字节为0。接收HTTP中的有界内存/容器multipart临时文件不等于已接受的私有附件 reservation；只在有界读入后、写入专用目录前原子预留。若以后生成衍生文件，必须先扩展显式计费，不能免费绕过此配额。

配额检查与 reservation 插入处于现有每 owner 的 PostgreSQL advisory transaction lock 内，跨会话并发也串行核算。相同请求重放查原结果，不再次占用空间；同键异 payload/过期/删除语义不变。UPLOADING全额计费，READY不增加第二份；FAILED/DELETING释放可用历史的逻辑计数，但其完整声明字节保留在cleanupReservedBytes；chargedBytes=logicalBytes+cleanupReservedBytes，配额准入按chargedBytes核算，只有物理清理确认后DELETED才释放该预留。这是保守上界，防止反复坏文件绕过账户限制；不宣称部分文件一定占满声明大小。删除后清理完成之前不能立即复用容量。`retainedFileBytes` 实际检查该账户DB登记的 `.blob` 与 `.part`，可高于逻辑值；`pendingCleanupBytes` 为FAILED/DELETING等不计逻辑额度但仍存在的文件字节。未登记/非法文件不被扫描删除，不谎报其物理空间已释放。响应是观察值，不是跨数据库/文件系统原子快照。

达到配额返回413 `ASSISTANT_QUOTA_EXCEEDED`，不禁止历史读、下载、导出、删除。磁盘预检不足或实际写入ENOSPC返回507 `ASSISTANT_DISK_FULL`；不会把失败/fsync不成功的文件标为READY。数据库不可用时 reservation 可保留待核查，不自动换键或再提交模型。reservation写入前无任何模型调用。

草稿到期来自持久服务器时间，不由浏览器卸载触发。过期且未关联任何保留消息的READY才可清理；所有历史分支引用均保护附件。发送和维护加同一 session 锁，发送再次检查草稿有效期/既有历史引用；维护执行重新检查状态/引用。会话删除立即撤权和fence，延迟写不能使附件READY或恢复正文。清理只处理登记UUID的专用目录文件，保留request/session/attachment tombstone；没有短期墓碑TTL。旧备份中可能含已删除内容，备份恢复必须维持恢复点之后的撤权账本或采用停写一致恢复，不能把旧备份直接覆盖现库作为应用回滚。

维护入口为 `com.auralink.assistant.AssistantStorageMaintenance`，不是公开HTTP接口或新supervisor。使用核验后解包的**同一Core JAR**的 classes/lib；私有0600 JSON 含 `jdbcUrl/jdbcUser/jdbcPassword/attachmentRoot` 和上述完整属性名。禁止把配置加入Git、打印凭据或复用生产env到测试。执行者必须使用当前R25共享锁/维护停写合同；普通cron未获授权。

```sh
java -cp '/absolute/verified-core/BOOT-INF/classes:/absolute/verified-core/BOOT-INF/lib/*' \
  com.auralink.assistant.AssistantStorageMaintenance /absolute/private/maintenance.json --dry-run 50
# 审阅输出和策略后；每次1..100项，重新检查状态，不使用过时清单强删：
java -cp '/absolute/verified-core/BOOT-INF/classes:/absolute/verified-core/BOOT-INF/lib/*' \
  com.auralink.assistant.AssistantStorageMaintenance /absolute/private/maintenance.json --apply 50
```

输出只有UUID、原状态、ELIGIBLE/REMOVED/STATE_CHANGED/REFUSED及计数。dry-run不改数据库/文件；apply不迁移、不启动应用、不调用模型、不能创建缺失命名空间。文件漂移/权限/软硬链接/哈希不符时REFUSED并保留记录，不能递归chmod/chown“修复”。重复执行安全，进程中断需检查实际记录，不能保证文件系统和数据库有跨介质原子提交。已有S1 `cleanup(staleAge,limit)` 保留为内部显式故障恢复原语，不作为运行期调度/策略入口；新运维只能使用策略入口。


`auralink.assistant.history-only=true` 是显式恢复模式（缺省 false）：即使 execution.enabled 误留 true，也不创建 dispatcher；不接受新附件写入，已有附件幂等读取、会话/历史、导出、删除仍可用。存储策略可独立允许受控清理。非法 history-only 值使 Assistant 新写入/执行 fail-closed，不抛出整个应用启动错误。生效需要通过现有生命周期重启 Core；不宣称热切换或零停机。生产恢复必须先停止/fence现有工作，不能用配置变化自动重派不确定请求。
