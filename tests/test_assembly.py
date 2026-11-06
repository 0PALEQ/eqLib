import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import assemble_universal as assembly


class CoreAssemblyTest(unittest.TestCase):
    def test_deleted_nested_class_is_not_shipped_after_recompilation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / 'shared/core/example/Example.java'
            source.parent.mkdir(parents=True)
            source.write_text('package example; public class Example { static class Removed {} }')
            with patch.object(assembly, 'ROOT', root), patch.object(assembly, 'BUILD', root / 'build'):
                first = assembly.compile_core()
                with zipfile.ZipFile(io.BytesIO(first)) as jar:
                    self.assertIn('example/Example$Removed.class', jar.namelist())
                source.write_text('package example; public class Example {}')
                second = assembly.compile_core()
                with zipfile.ZipFile(io.BytesIO(second)) as jar:
                    self.assertEqual(['example/Example.class'], jar.namelist())
                self.assertEqual(second, assembly.compile_core())


if __name__ == '__main__':
    unittest.main()
