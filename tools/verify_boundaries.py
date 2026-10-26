from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
targets=sorted((root/'versions').iterdir())
for target in targets:
    roots=[target/'src/main/java',root/'shared/core']
    manifest=root/'shared/source-groups.json'
    if manifest.exists():
        for group in json.loads(manifest.read_text())['groups']:
            if target.name in group['targets']: roots.append(root/group['java'])
    sources=[p for source_root in roots for p in source_root.rglob('*.java')]
    assert sources, target
    relative=[p.relative_to(source_root).as_posix() for source_root in roots for p in source_root.rglob('*.java')]
    assert len(relative)==len(set(relative)), ('Duplicate source classes',target)
    for source in sources:
        assert 'com.cookiecraftmods.mdm' not in source.read_text(), source
        assert 'MdmMod' not in source.read_text(), source
    assert (target/'src/main/resources/pack.mcmeta').exists(), target
    mixins=json.loads((target/'src/main/resources/eqlib.mixins.json').read_text())
    for name in mixins['mixins']+mixins['client']:
        assert any((source_root/'com/cookiecraftmods/eqlib/mixin'/(name+'.java')).exists() for source_root in roots), (target,name)
print('Verified independent API and mixin sources for',len(targets),'targets')
