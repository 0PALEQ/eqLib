from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import assemble_universal as assembly
import project_paths


class MetadataTest(unittest.TestCase):
    def test_root_metadata_controls_runtime_and_publication(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'gradle.properties').write_text(
                "mod_version=2.3.4\narchives_base_name=custom-library\n"
                "maven_group=example.mods\nmod_name=Library & Tools\n"
                "mod_description=Shared <utilities>\n", encoding='utf-8')
            with patch.object(project_paths, 'ROOT', root), patch.object(assembly, 'ROOT', root):
                runtime = project_paths.runtime_jar()
                self.assertEqual(root / 'dist/custom-library-2.3.4.jar', runtime)
                runtime.parent.mkdir()
                runtime.write_bytes(b'runtime')
                api = root / 'build/api/eqlib-fabric_1.20.1.jar'
                api.parent.mkdir(parents=True)
                api.write_bytes(b'api')
                assembly.publish(runtime)
                repository = root / 'maven/example/mods/custom-library/2.3.4'
                self.assertEqual(b'runtime', (repository / runtime.name).read_bytes())
                self.assertEqual(b'api', (repository / 'custom-library-2.3.4-dev-fabric_1.20.1.jar').read_bytes())
                pom = ET.parse(repository / 'custom-library-2.3.4.pom')
                namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
                for field, expected in [('groupId', 'example.mods'), ('artifactId', 'custom-library'),
                                        ('version', '2.3.4'), ('name', 'Library & Tools'),
                                        ('description', 'Shared <utilities>')]:
                    self.assertEqual(expected, pom.findtext('m:' + field, namespaces=namespace))


if __name__ == '__main__':
    unittest.main()
