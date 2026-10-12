from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from openpyxl import Workbook

from tools.catalog_batch.validate import EXPECTED_HEADERS, validate_batch


class ValidateBatchTest(unittest.TestCase):
    def temporary_directory(self) -> tempfile.TemporaryDirectory[str]:
        parent = Path.cwd() / "target" / "catalog-batch-tests"
        parent.mkdir(parents=True, exist_ok=True)
        return tempfile.TemporaryDirectory(dir=parent)

    def make_batch(self, root: Path, rows: list[list[object]]) -> tuple[Path, Path]:
        workbook_path = root / "batch.xlsx"
        image_directory = root / "images"
        image_directory.mkdir()
        workbook = Workbook()
        sheet = workbook.active
        sheet.append(EXPECTED_HEADERS)
        for row in rows:
            sheet.append(row)
        workbook.save(workbook_path)
        workbook.close()
        return workbook_path, image_directory

    def row(self, code: str, image_name: str, title: str = "作品", artist: str = "作者") -> list[object]:
        values: dict[str, object] = {
            "入库编号": code,
            "源作品序号": code,
            "作者姓名": artist,
            "作品名称": title,
            "高清原图文件名": image_name,
        }
        return [values.get(header) for header in EXPECTED_HEADERS]

    def test_valid_batch(self) -> None:
        with self.temporary_directory() as directory:
            root = Path(directory)
            workbook, images = self.make_batch(root, [self.row("A-001", "a.jpg")])
            (images / "a.jpg").write_bytes(b"not-empty")
            report = validate_batch(workbook, images, expected_count=1)
            self.assertTrue(report["valid"])
            self.assertEqual(0, report["error_count"])
            self.assertEqual(1, report["row_count"])

    def test_duplicate_code_and_missing_image_fail(self) -> None:
        with self.temporary_directory() as directory:
            root = Path(directory)
            workbook, images = self.make_batch(
                root,
                [self.row("A-001", "a.jpg"), self.row("A-001", "missing.jpg")],
            )
            (images / "a.jpg").write_bytes(b"not-empty")
            report = validate_batch(workbook, images, expected_count=2)
            codes = {issue["code"] for issue in report["issues"]}
            self.assertFalse(report["valid"])
            self.assertIn("duplicate_record_code", codes)
            self.assertIn("missing_images", codes)
            self.assertIn("image_count_mismatch", codes)

    def test_orphan_and_duplicate_content_are_reported(self) -> None:
        with self.temporary_directory() as directory:
            root = Path(directory)
            workbook, images = self.make_batch(root, [self.row("A-001", "a.jpg")])
            (images / "a.jpg").write_bytes(b"same")
            (images / "orphan.jpg").write_bytes(b"same")
            report = validate_batch(workbook, images)
            codes = {issue["code"] for issue in report["issues"]}
            self.assertFalse(report["valid"])
            self.assertIn("orphan_images", codes)
            self.assertIn("duplicate_image_content", codes)


if __name__ == "__main__":
    unittest.main()
