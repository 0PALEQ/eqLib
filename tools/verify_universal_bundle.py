"""Check the universal distribution's adapter coverage and packaging invariants."""
from pathlib import Path
import hashlib
import io
import json
import struct
import zipfile
from project_paths import runtime_jar

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    'fabric_1.20.1', 'fabric_1.21.1', 'fabric_1.21.11',
    'fabric_26.1.2', 'fabric_26.2', 'fabric_26.3',
    'forge_1.16.5', 'forge_1.19.2', 'forge_1.20.1',
    'neoforge_1.21.1', 'neoforge_1.21.11', 'neoforge_26.1.2', 'neoforge_26.2',
}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def class_version(data):
    require(data[:4] == b'\xca\xfe\xba\xbe', 'Invalid class file')
    return struct.unpack('>H', data[6:8])[0]


def verify():
    artifact = runtime_jar()
    with zipfile.ZipFile(artifact) as outer:
        index = dict(line.split('=', 1) for line in outer.read('META-INF/eqlib/adapters.properties').decode().splitlines() if line)
        require(set(index) == {t + '.' + m for t in TARGETS for m in ['prod', 'dev']}, 'Unexpected adapter matrix')
        fabric = json.loads(outer.read('fabric.mod.json'))
        require(set(fabric['depends']['minecraft']) == {t.split('_', 1)[1] for t in TARGETS if t.startswith('fabric_')}, 'Wrong Fabric support range')
        require(fabric['id'] == 'eqlib', 'Wrong mod ID')
        with zipfile.ZipFile(io.BytesIO(outer.read('META-INF/eqlib/core.jar'))) as core:
            require(core.namelist(), 'Empty neutral core')
            for name in core.namelist():
                require(name.startswith('com/cookiecraftmods/eqlib/core/') and name.endswith('.class'), 'Unexpected neutral-core entry: ' + name)
                require(class_version(core.read(name)) == 52, 'Neutral core must run on Java 8')
                require(b'net/minecraft' not in core.read(name), 'Minecraft reference in neutral core')
        for target in sorted(TARGETS):
            for mode in ['prod', 'dev']:
                with zipfile.ZipFile(io.BytesIO(outer.read(index[target + '.' + mode]))) as adapter:
                    names = set(adapter.namelist())
                    require('com/cookiecraftmods/eqlib/api/FurnitureApi.class' in names, 'Missing public API: ' + target)
                    require(not any(n.startswith('com/cookiecraftmods/eqlib/core/') for n in names), 'Duplicated core: ' + target)
                    config = json.loads(adapter.read('eqlib.mixins.json'))
                    for mixin in config['mixins'] + config['client']:
                        require(config['package'].replace('.', '/') + '/' + mixin + '.class' in names, 'Missing mixin: ' + target + ':' + mixin)
                    for name in names:
                        if name.endswith('.class'):
                            require(b'com/cookiecraftmods/mdm/' not in adapter.read(name), 'Consumer dependency: ' + target + ':' + name)
            api = ROOT / 'build/api' / ('eqlib-' + target + '.jar')
            with zipfile.ZipFile(api) as development:
                require('com/cookiecraftmods/eqlib/core/clock/ClockTime.class' in development.namelist(), 'Missing core in development API: ' + target)
        require(index['fabric_26.2.prod'] == index['fabric_26.3.prod'], 'Compatible Fabric pair must share one payload')
        for name in outer.namelist():
            if name.endswith('.class'):
                release = class_version(outer.read(name))
                require(release == (61 if '/forge/DependencyLocator' in name or '/neoforge/' in name else 52), 'Unexpected bootstrap bytecode: ' + name)
    report = {'jar': str(artifact), 'bytes': artifact.stat().st_size,
              'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
              'targets': sorted(TARGETS), 'packaging': 'passed',
              'note': 'Packaging verification is separate from runtime validation.'}
    (ROOT / 'dist/universal-packaging.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Verified universal packaging:', len(TARGETS), 'targets;', report['bytes'], 'bytes;', report['sha256'])


if __name__ == '__main__':
    verify()
