"""Build every supported loader/version using its own Gradle wrapper and matching JDK."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('task', choices=['build', 'check', 'publish', 'compileJava'])
parser.add_argument('--target', action='append', help='Build only this target (repeatable)')
parser.add_argument('--adapters-only', action='store_true', help='Build internal adapters without assembling the release.')
parser.add_argument('--workers',type=int,default=2)
parser.add_argument('--offline',action='store_true')
args = parser.parse_args()

from java_tools import jdk

report=ROOT/'build/matrix-results.json'
results=json.loads(report.read_text()) if report.exists() else {}
selected_results={}

def build(target):
    loader, mc=target.name.split('_',1)
    major=25 if mc.startswith('26.') or target.name=='neoforge_1.21.11' else 17 if loader=='forge' else 21
    java=jdk(major)
    env=dict(os.environ, JAVA_HOME=str(java))
    env['PATH']=str(Path(java)/'bin')+os.pathsep+env['PATH']
    task='build' if args.task=='publish' else args.task
    log=ROOT/'build/matrix-logs'/(target.name+'.log');log.parent.mkdir(parents=True,exist_ok=True)
    wrapper=target/('gradlew.bat' if os.name=='nt' else 'gradlew')
    print('Building',target.name,'with',java,flush=True)
    command=[str(wrapper), '-Dorg.gradle.java.home='+str(java), task, '-PeqlibBuildDir=build/publishing', '--console=plain', '--no-daemon']
    if args.offline:command.append('--offline')
    with log.open('w') as stream: result=subprocess.run(command,cwd=target,env=env,stdout=stream,stderr=subprocess.STDOUT)
    print('PASS' if result.returncode==0 else 'FAIL',target.name,flush=True)
    return target.name, {'exit_code':result.returncode,'log':str(log)}

targets=[target for target in sorted((ROOT/'versions').iterdir())
         if (target/'build.gradle').exists() and (not args.target or target.name in args.target)]
if not targets:raise SystemExit('No matching targets')
with ThreadPoolExecutor(max_workers=max(1,args.workers)) as pool:
    for future in as_completed([pool.submit(build,target) for target in targets]):
        name,result=future.result();results[name]=result;selected_results[name]=result
        report.parent.mkdir(parents=True,exist_ok=True)
        report.write_text(json.dumps(results,indent=2))
if any(v['exit_code'] for v in selected_results.values()): raise SystemExit(1)
if not args.target and not args.adapters_only and args.task in ['build','publish']:
    command=[sys.executable,str(ROOT/'tools/assemble_universal.py'),'--from-builds']
    if args.task=='publish':command.append('--publish')
    subprocess.run(command,check=True)
