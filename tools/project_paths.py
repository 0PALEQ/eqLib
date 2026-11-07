from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def mod_metadata():
    return properties(ROOT / 'gradle.properties')


def runtime_jar():
    metadata = mod_metadata()
    return ROOT / 'dist' / f"{metadata['archives_base_name']}-{metadata['mod_version']}.jar"
