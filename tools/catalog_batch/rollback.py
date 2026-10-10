#!/usr/bin/env python3
"""Preview or atomically remove one imported painting batch from PostgreSQL."""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import asdict, dataclass
from typing import Any


@dataclass(frozen=True)
class RollbackPlan:
    batch_id: int
    batch_code: str
    status: str
    artwork_count: int
    image_count: int
    running_imports: int

    @property
    def confirmation_token(self) -> str:
        return f"ROLLBACK:{self.batch_code}:{self.artwork_count}:{self.image_count}"


def build_plan(cursor: Any, batch_code: str) -> RollbackPlan:
    cursor.execute(
        """
        SELECT batch_id, batch_code, status
        FROM import_batches
        WHERE batch_code = %s
        FOR UPDATE
        """,
        (batch_code,),
    )
    batch = cursor.fetchone()
    if not batch:
        raise RuntimeError(f"批次不存在: {batch_code}")
    batch_id, actual_code, status = batch
    cursor.execute("SELECT COUNT(*) FROM artworks WHERE batch_id = %s", (batch_id,))
    artwork_count = int(cursor.fetchone()[0])
    cursor.execute("SELECT COUNT(*) FROM artwork_images WHERE batch_id = %s", (batch_id,))
    image_count = int(cursor.fetchone()[0])
    cursor.execute("SELECT COUNT(*) FROM import_runs WHERE batch_id = %s AND status = 'running'", (batch_id,))
    running_imports = int(cursor.fetchone()[0])
    return RollbackPlan(int(batch_id), str(actual_code), str(status), artwork_count, image_count, running_imports)


def execute_rollback(cursor: Any, plan: RollbackPlan, operator: str, reason: str) -> None:
    if plan.running_imports:
        raise RuntimeError("批次仍有运行中的导入，拒绝回滚")
    if plan.status == "rolled_back":
        raise RuntimeError("批次已经回滚")
    cursor.execute("SET LOCAL lock_timeout = '5s'")
    cursor.execute("SET LOCAL statement_timeout = '5min'")
    cursor.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (f"catalog-batch:{plan.batch_code}",))
    cursor.execute("DELETE FROM artwork_images WHERE batch_id = %s", (plan.batch_id,))
    if cursor.rowcount != plan.image_count:
        raise RuntimeError("图片删除计数发生变化，事务已中止")
    cursor.execute("DELETE FROM artworks WHERE batch_id = %s", (plan.batch_id,))
    if cursor.rowcount != plan.artwork_count:
        raise RuntimeError("作品删除计数发生变化，事务已中止")
    cursor.execute(
        """
        INSERT INTO batch_rollbacks (
            batch_id, batch_code, artwork_count, image_count, operator_name, reason
        ) VALUES (%s, %s, %s, %s, %s, %s)
        """,
        (plan.batch_id, plan.batch_code, plan.artwork_count, plan.image_count, operator, reason),
    )
    cursor.execute(
        """
        INSERT INTO import_runs (
            batch_id, started_at, finished_at, status, rows_read, rows_inserted,
            images_scanned, images_matched, warning_count, error_count
        ) VALUES (%s, NOW(), NOW(), 'rolled_back', %s, 0, %s, 0, 0, 0)
        """,
        (plan.batch_id, plan.artwork_count, plan.image_count),
    )
    cursor.execute(
        """
        UPDATE import_batches
        SET imported_artwork_count = 0,
            status = 'rolled_back',
            rolled_back_at = NOW(),
            rolled_back_by = %s,
            rollback_reason = %s,
            updated_at = NOW()
        WHERE batch_id = %s
        """,
        (operator, reason, plan.batch_id),
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="预演或执行整批国画数据回滚")
    parser.add_argument("--batch-code", required=True)
    parser.add_argument("--dsn", default=os.environ.get("AURALINK_IMPORT_DATABASE_URL"))
    parser.add_argument("--expected-artworks", type=int, required=True)
    parser.add_argument("--operator", default=os.environ.get("USERNAME") or os.environ.get("USER") or "unknown")
    parser.add_argument("--reason", default="operator requested batch rollback")
    parser.add_argument("--execute", action="store_true", help="实际执行；默认仅生成回滚计划")
    parser.add_argument("--confirm", help="执行时必须等于预演输出的 confirmation_token")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if not args.dsn:
        print("缺少 --dsn 或 AURALINK_IMPORT_DATABASE_URL", file=sys.stderr)
        return 2
    try:
        import psycopg2
    except ImportError:
        print("缺少 psycopg2，请先安装 requirements-import.txt", file=sys.stderr)
        return 2

    connection = psycopg2.connect(args.dsn)
    connection.autocommit = False
    try:
        with connection.cursor() as cursor:
            plan = build_plan(cursor, args.batch_code)
            print(json.dumps({**asdict(plan), "confirmation_token": plan.confirmation_token}, ensure_ascii=False, indent=2))
            if plan.artwork_count != args.expected_artworks:
                raise RuntimeError(
                    f"作品数保护检查失败: expected={args.expected_artworks}, actual={plan.artwork_count}"
                )
            if not args.execute:
                connection.rollback()
                print("仅预演，数据库未修改。")
                return 0
            if args.confirm != plan.confirmation_token:
                raise RuntimeError("确认令牌不匹配，拒绝执行")
            execute_rollback(cursor, plan, args.operator, args.reason)
        connection.commit()
        print(f"批次 {plan.batch_code} 已原子回滚。")
        return 0
    except Exception as exc:
        connection.rollback()
        print(f"批次回滚失败，事务已撤销: {exc}", file=sys.stderr)
        return 1
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
