"""Record successful build and runtime checks for the current universal artifact."""
from pathlib import Path
import argparse
import hashlib
import json
from verify_universal_bundle import ROOT, TARGETS, verify
from project_paths import runtime_jar


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def read_log(path):
    data = path.read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8', errors='replace')


def record(consumer):
    verify()
    artifact = runtime_jar()
    checksum = hashlib.sha256(artifact.read_bytes()).hexdigest()
    builds = json.loads((consumer/'build/eqlib-matrix/results.json').read_text())
    runtime = json.loads((consumer/'build/eqlib-runtime/results.json').read_text())
    adapters = json.loads((ROOT/'build/matrix-results.json').read_text())
    for target in sorted(TARGETS):
        require(builds[target]['exit_code'] == 0, 'Consumer build failed: '+target)
        require(runtime[target]['exit_code'] == 0 and runtime[target]['sha256'] == checksum, 'Missing current-artifact server check: '+target)
        internal = 'fabric_26.2-26.3' if target in ['fabric_26.2', 'fabric_26.3'] else target
        require(adapters[internal]['exit_code'] == 0, 'Internal adapter build failed: '+internal)
        log = Path(runtime[target]['log']).read_text(encoding='utf-8', errors='replace')
        marker = 'BACKPORT_SMOKE_PASSED' if target == 'forge_1.16.5' else 'UNIVERSAL_SERVER_PASSED' if target in ['fabric_26.2', 'fabric_26.3'] else 'PLACEMENT_UNLOAD_SHUTDOWN_PASSED'
        require(marker in log and 'EQLIB_ADAPTER '+target+' development' in log, 'Missing runtime assertions: '+target)
    clients = {}
    for target in ['fabric_26.2', 'fabric_26.3', 'forge_1.16.5']:
        folder = consumer/'versions'/target/'build'/('client-smoke-run' if target == 'forge_1.16.5' else 'universal-client-run')
        result = folder/('client-smoke-result.txt' if target == 'forge_1.16.5' else 'universal-client-result.txt')
        text = result.read_text()
        require('PASSED' in text and result.stat().st_mtime >= artifact.stat().st_mtime, 'Missing current client result: '+target)
        clients[target] = {'result': text.strip(), 'file': str(result)}
        if target.startswith('fabric_'):
            screenshots = [folder/('universal-preview-'+kind+'.png') for kind in ['valid', 'blocked']]
            require(all(p.exists() for p in screenshots), 'Missing preview screenshots: '+target)
            clients[target]['screenshots'] = list(map(str, screenshots))
    require('BOOTSTRAP_PASSED adapters=26' in read_log(ROOT/'build/bootstrap-test.log'), 'Missing Java 8 bootstrap validation')
    report = {'jar': str(artifact), 'sha256': checksum, 'bytes': artifact.stat().st_size,
              'consumer_builds': {t: builds[t] for t in sorted(TARGETS)},
              'development_server_runs': {t: runtime[t] for t in sorted(TARGETS)},
              'clients': clients, 'bootstrap': 'Java 8 extraction, isolated core linkage, corrupt-cache repair and unsupported-target checks passed',
              'production_adapters': 'Built with each target loader toolchain; production game launches are not part of this report.'}
    output = ROOT/'dist/universal-validation.json'
    output.write_text(json.dumps(report, indent=2)+'\n')
    print('Recorded 13 consumer builds, 13 checksum-matched development server runs, and 3 client checks:', output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--consumer', type=Path, default=ROOT.parents[2]/'Modern Decorations/mdm')
    args = parser.parse_args()
    record(args.consumer.resolve())
