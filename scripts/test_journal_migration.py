import re
import sqlite3
import sys
import unittest
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/ru/slem/taskwidget/LocalTaskWriteJournal.kt"


class JournalMigrationTest(unittest.TestCase):
    def test_resolved_history_is_capped_without_deleting_recovery(self):
        source = SOURCE.read_text(encoding="utf-8")
        create = re.search(r'db\.execSQL\("""(CREATE TABLE actions \(.*?)"""\)', source, re.S)
        prune = re.search(r'const val PRUNE_RESOLVED = """(.*?)"""', source, re.S)
        self.assertIsNotNone(prune, "bounded retention SQL is missing")
        db = sqlite3.connect(":memory:")
        db.execute(create.group(1))
        for number in range(105):
            db.execute("INSERT INTO actions VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                       (str(number), "vault", "doc", "Note.md", number, "a", "b", None, 1, "DONE", b"backup"))
        db.execute("INSERT INTO actions VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                   ("recovery", "vault", "doc", "Note.md", 0, "a", "b", None, 1,
                    "NEEDS_RECOVERY", b"keep"))
        db.execute(prune.group(1), ("vault",))
        self.assertEqual(100, db.execute("SELECT count(*) FROM actions WHERE status='DONE'").fetchone()[0])
        self.assertEqual(b"keep", db.execute("SELECT backup FROM actions WHERE id='recovery'").fetchone()[0])
        self.assertIsNone(db.execute("SELECT id FROM actions WHERE id='0'").fetchone())
    def test_clear_history_keeps_recovery_and_other_vault(self):
        source = SOURCE.read_text(encoding="utf-8")
        create = re.search(r'db\.execSQL\("""(CREATE TABLE actions \(.*?)"""\)', source, re.S)
        clear = re.search(r'const val CLEAR_RESOLVED = """(.*?)"""', source, re.S)
        self.assertIsNotNone(clear, "clear-history SQL is missing")
        db = sqlite3.connect(":memory:")
        db.execute(create.group(1))
        for id, vault, status in (("done", "vault", "DONE"),
                                  ("undone", "vault", "UNDONE"),
                                  ("recovery", "vault", "NEEDS_RECOVERY"),
                                  ("other", "other-vault", "DONE")):
            db.execute("INSERT INTO actions VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                       (id, vault, "doc", "Note.md", 1, "a", "b", None, 1, status, b"backup"))
        db.execute(clear.group(1), ("vault",))
        self.assertEqual({"recovery", "other"},
                         {row[0] for row in db.execute("SELECT id FROM actions")})
    def test_legacy_recovery_is_visible_without_allowing_legacy_undo(self):
        source = SOURCE.read_text(encoding="utf-8")
        create = re.search(r'db\.execSQL\("""(CREATE TABLE actions \(.*?)"""\)', source, re.S)
        scope = re.search(r'const val VISIBLE_ACTIONS = """(.*?)"""', source, re.S)
        self.assertIsNotNone(scope, "legacy recovery view is missing")
        db = sqlite3.connect(":memory:")
        db.execute(create.group(1))
        for id, vault, status in (("current", "vault", "DONE"),
                                  ("legacy-recovery", "", "NEEDS_RECOVERY"),
                                  ("legacy-done", "", "DONE"),
                                  ("other", "other-vault", "NEEDS_RECOVERY")):
            db.execute("INSERT INTO actions VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                       (id, vault, "doc", "Note.md", 1, "a", "b", None, 1, status, b"backup"))
        visible = {row[0] for row in db.execute("SELECT id FROM actions WHERE " + scope.group(1), ("vault",))}
        self.assertEqual({"current", "legacy-recovery"}, visible)
        self.assertIn('VISIBLE_ACTIONS', source)
    def test_v1_rows_survive_v2_migration_without_being_assigned_to_wrong_vault(self):
        source = SOURCE.read_text(encoding="utf-8")
        create = re.search(r'db\.execSQL\("""(CREATE TABLE actions \(.*?)"""\)', source, re.S)
        migration = re.search(r'const val MIGRATE_V1_TO_V2 = """(.*?)"""', source, re.S)
        self.assertIsNotNone(create, "v1 schema must be present in source")
        self.assertIsNotNone(migration, "migration SQL is missing")
        db = sqlite3.connect(":memory:")
        db.execute(create.group(1).replace("vault_key TEXT NOT NULL, ", ""))
        db.execute(
            "INSERT INTO actions VALUES (?,?,?,?,?,?,?,?,?,?)",
            ("id", "old-doc", "Old.md", 123, "before", "after", None, 1, "DONE", b"backup"),
        )
        db.execute(migration.group(1))
        row = db.execute("SELECT id, document_id, vault_key, backup FROM actions").fetchone()
        self.assertEqual(("id", "old-doc", "", b"backup"), row)
        self.assertIn("db.execSQL(MIGRATE_V1_TO_V2)", source)


if __name__ == "__main__":
    unittest.main()