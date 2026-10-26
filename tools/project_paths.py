from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def runtime_jar():
    properties = ROOT / 'versions/fabric_26.2-26.3/gradle.properties'
    version = next(line.split('=', 1)[1].strip() for line in properties.read_text().splitlines()
                   if line.startswith('mod_version='))
    return ROOT / 'dist' / f'eqlib-{version}.jar'