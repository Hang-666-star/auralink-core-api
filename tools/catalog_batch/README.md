# 国画数据批次防护工具

本目录为新增国画批次提供两道独立保护：导入前校验，以及导入后的整批原子回滚。它不会修改现有 9553 条线上数据；只有显式执行回滚并提供匹配的确认令牌时才会写数据库。

## 1. 导入前校验

```bash
python -m tools.catalog_batch.validate \
  --workbook /data/batch.xlsx \
  --image-dir /data/images \
  --expected-count 486 \
  --report /data/reports/batch-01.json
```

校验包括：表头、有效记录数、图片数、入库编号唯一性、图片引用唯一性、缺图、孤儿图片、空文件和重复图片内容。只有报告中的 `valid` 为 `true` 才允许导入，JSON 报告必须随数据 PR 留档。

## 2. 安装回滚审计表

首次使用前，由数据库管理员执行：

```bash
psql "$AURALINK_IMPORT_DATABASE_URL" \
  -v ON_ERROR_STOP=1 \
  -f tools/catalog_batch/schema/V1__add_batch_rollback_audit.sql
```

## 3. 预演回滚

```bash
python -m tools.catalog_batch.rollback \
  --batch-code batch_01 \
  --expected-artworks 486
```

默认只锁定并读取批次，随后回滚事务，不修改数据库。命令会输出精确作品数、图片数和 `confirmation_token`。

## 4. 执行回滚

人工复核预演结果后，原样带回确认令牌：

```bash
python -m tools.catalog_batch.rollback \
  --batch-code batch_01 \
  --expected-artworks 486 \
  --execute \
  --confirm 'ROLLBACK:batch_01:486:486' \
  --reason 'batch validation regression'
```

作品、图片、批次状态和审计记录在同一事务中更新；任一步失败都会整体撤销。该工具只用于独立新增批次。若未来允许覆盖既有批次，应先实现逐行前镜像恢复，不得直接使用整批删除语义。
