"""Compile loader bootstrap bridges and package the selected Minecraft adapters."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import os
import subprocess
import zipfile
from java_tools import jdk

ROOT=Path(__file__).resolve().parents[1]
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
BUILD=ROOT/'build/universal'
JAVA=jdk(25)

def artifact(group,name,version):
    return next(p for p in (CACHE/group/name/version).glob('*/*.jar') if not any(s in p.name for s in ['sources','javadoc']))

def properties(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if '=' in line and not line.startswith('#'))

def compile_bootstrap():
    output=BUILD/'bootstrap';output.mkdir(parents=True,exist_ok=True)
    # Only compile against the bootstrap contracts and their signature dependencies.
    # A broad cache class path can silently select incompatible APIs or javac plugins.
    others=[]
    for group,name in [('com.google.code.gson','gson'),('org.ow2.asm','asm'),('org.ow2.asm','asm-tree'),('org.apache.logging.log4j','log4j-api'),('org.apache.maven','maven-artifact'),('org.jetbrains','annotations'),('com.google.guava','guava')]:
        candidates=[p for p in (CACHE/group/name).glob('*/*/*.jar') if not any(s in p.name for s in ['sources','javadoc'])]
        if not candidates:raise RuntimeError('Missing bootstrap compile dependency: '+group+':'+name+'; build the adapter matrix first.')
        others.append(sorted(candidates,key=lambda p:p.as_posix())[-1])
    legacy=next(p for p in (CACHE/'net.minecraftforge/forge/1.16.5-36.2.42').glob('*/*.jar') if p.name.endswith('-launcher.jar'))
    groups=[
        ('common',8,[]),
        ('fabric',8,[artifact('net.fabricmc','fabric-loader','0.16.14'),artifact('org.spongepowered','mixin','0.8.5')]),
        ('forge-legacy',8,[artifact('net.minecraftforge','forgespi','3.2.0'),legacy]),
        ('forge-modern',17,[artifact('net.minecraftforge','forgespi','6.0.0'),artifact('net.minecraftforge','fmlloader','1.19.2-43.5.2'),artifact('cpw.mods','securejarhandler','2.1.4')]),
        ('neoforge',17,[artifact('net.neoforged.fancymodloader','loader','4.0.44')]),
    ]
    for group,release,first in groups:
        source=list((ROOT/'bootstrap'/group).rglob('*.java'))
        args=['--release',str(release),'-proc:none','-encoding','UTF-8','-d',str(output),'-classpath',os.pathsep.join(str(p) for p in [output,*first,*others]),*map(str,source)]
        argfile=BUILD/(group+'-javac.args')
        argfile.write_text('\n'.join('"'+arg.replace('\\','/')+'"' for arg in args))
        subprocess.run([str(JAVA/'bin/javac.exe'),'@'+str(argfile)],check=True)
    return output

def zip_bytes(entries):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
    return stream.getvalue()

def without_core(data):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        return zip_bytes({name:jar.read(name) for name in jar.namelist() if not name.startswith('com/cookiecraftmods/eqlib/core/') and not name.endswith('/')})

def compile_core():
    output=BUILD/'core';output.mkdir(parents=True,exist_ok=True)
    gson=next(p for p in (CACHE/'com.google.code.gson/gson').glob('*/*/*.jar') if not any(s in p.name for s in ['sources','javadoc']))
    args=['--release','8','-proc:none','-encoding','UTF-8','-classpath',str(gson),'-d',str(output),*map(str,(ROOT/'shared/core').rglob('*.java'))]
    argfile=BUILD/'core-javac.args';argfile.write_text('\n'.join('"'+arg.replace('\\','/')+'"' for arg in args))
    subprocess.run([str(JAVA/'bin/javac.exe'),'@'+str(argfile)],check=True)
    return zip_bytes({p.relative_to(output).as_posix():p.read_bytes() for p in output.rglob('*.class')})

def payloads(target):
    props=properties(target/'gradle.properties')
    label=props.get('library_target',props['minecraft_version'])
    filename='eqlib-'+props['mod_version']+'-'+props['loader_name']+'-'+label+'.jar'
    candidates=[target/'build/publishing/libs'/filename,target/'build/libs'/filename]
    if target.name=='forge_1.16.5':candidates += [target/'build/libs'/('eqlib-'+props['mod_version']+'.jar')]
    production=max((p for p in candidates if p.exists()),key=lambda p:p.stat().st_mtime)
    with zipfile.ZipFile(production) as archive: manifest=archive.read('META-INF/MANIFEST.MF')
    entries={}
    build_dir=production.parent.parent
    for base in [build_dir/'classes/java/main',build_dir/'resources/main']:
        entries.update({p.relative_to(base).as_posix():p.read_bytes() for p in base.rglob('*') if p.is_file()})
    if 'com/cookiecraftmods/eqlib/api/FurnitureApi.class' not in entries:
        raise RuntimeError('Missing development classes for '+target.name+'; rebuild this adapter before packaging.')
    entries['META-INF/MANIFEST.MF']=manifest
    development=zip_bytes(entries)
    return production.read_bytes(),development

def assemble():
    classes=compile_bootstrap()
    core=compile_core()
    version=properties(ROOT/'versions/fabric_26.2-26.3/gradle.properties')['mod_version']
    entries={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    entries['META-INF/MANIFEST.MF']=('Manifest-Version: 1.0\r\nAutomatic-Module-Name: com.cookiecraftmods.eqlib.bootstrap\r\nFMLModType: LIBRARY\r\nImplementation-Version: '+version+'\r\n\r\n').encode()
    entries['META-INF/eqlib/core.jar']=core
    entries['LICENSE.txt']=(ROOT/'LICENSE.txt').read_bytes()
    index=[];games=set();targets=[]
    for target in sorted((ROOT/'versions').iterdir()):
        if not (target/'build.gradle').exists():continue
        prod,dev=payloads(target)
        names=[target.name] if target.name!='fabric_26.2-26.3' else ['fabric_26.2','fabric_26.3']
        for mode,data in [('prod',prod),('dev',dev)]:
            resource='META-INF/eqlib/adapters/'+target.name+'-'+mode+'.jar';entries[resource]=without_core(data)
            for name in names:index.append(name+'.'+mode+'='+resource)
        for name in names:
            if name.startswith('fabric_'):games.add(name.split('_',1)[1])
            targets.append(name)
        with zipfile.ZipFile(io.BytesIO(dev)) as jar:
            api_entries={name:jar.read(name) for name in jar.namelist() if not name.endswith('/')}
        with zipfile.ZipFile(io.BytesIO(core)) as jar:
            api_entries.update({name:jar.read(name) for name in jar.namelist()})
        development_api=zip_bytes(api_entries)
        for name in names:
            api=ROOT/'build/api'/('eqlib-'+name+'.jar')
            api.parent.mkdir(parents=True,exist_ok=True)
            api.write_bytes(development_api)
    entries['META-INF/eqlib/adapters.properties']=('\n'.join(index)+'\n').encode()
    entries['fabric.mod.json']=json.dumps({'schemaVersion':1,'id':'eqlib','version':version,'name':"opaleq's Library",'environment':'*','mixins':['eqlib.mixins.json'],'depends':{'minecraft':sorted(games),'java':'>=8','fabricloader':'>=0.16.14'}},indent=2).encode()
    entries['eqlib.mixins.json']=json.dumps({'required':True,'minVersion':'0.8','package':'com.cookiecraftmods.eqlib.mixin','compatibilityLevel':'JAVA_8','plugin':'com.cookiecraftmods.eqlib.bootstrap.fabric.MixinSelector','mixins':[],'client':[],'injectors':{'defaultRequire':1}},indent=2).encode()
    services={
        'net.minecraftforge.forgespi.locating.IModLocator':'com.cookiecraftmods.eqlib.bootstrap.forge.LegacyLocator',
        'net.minecraftforge.forgespi.locating.IDependencyLocator':'com.cookiecraftmods.eqlib.bootstrap.forge.DependencyLocator',
        'net.neoforged.neoforgespi.locating.IDependencyLocator':'com.cookiecraftmods.eqlib.bootstrap.neoforge.DependencyLocator',
    }
    for interface,implementation in services.items():entries['META-INF/services/'+interface]=(implementation+'\n').encode()
    output=ROOT/'dist/eqlib-universal.jar';output.parent.mkdir(exist_ok=True)
    data=zip_bytes(entries)
    temporary=output.with_suffix('.jar.tmp')
    try:
        if not output.exists() or output.read_bytes()!=data:
            temporary.write_bytes(data)
            temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    print('Assembled',output,len(output.read_bytes()),'bytes;',len(targets),'loader/game targets')
    return output

def publish(output):
    version=properties(ROOT/'versions/fabric_26.2-26.3/gradle.properties')['mod_version']
    repository=ROOT/'maven/com/cookiecraftmods/eqlib'/version
    repository.mkdir(parents=True,exist_ok=True)
    (repository/('eqlib-'+version+'.jar')).write_bytes(output.read_bytes())
    for api in (ROOT/'build/api').glob('eqlib-*.jar'):
        target=api.stem.removeprefix('eqlib-')
        if target=='fabric_26.2-26.3':continue
        (repository/('eqlib-'+version+'-dev-'+target+'.jar')).write_bytes(api.read_bytes())
    pom=f"""<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.cookiecraftmods</groupId><artifactId>eqlib</artifactId><version>{version}</version><name>opaleq's Library</name><description>Universal runtime with Minecraft-specific development API classifiers.</description></project>"""
    (repository/('eqlib-'+version+'.pom')).write_text(pom)
    for file in repository.iterdir():
        if file.suffix in ['.jar','.pom']:
            for algorithm in ['sha256','sha1']:
                file.with_name(file.name+'.'+algorithm).write_text(hashlib.new(algorithm,file.read_bytes()).hexdigest())
    print('Published universal runtime and development APIs to',repository)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--from-builds',action='store_true',help='Use existing adapter outputs instead of rebuilding the matrix.')
    parser.add_argument('--publish',action='store_true',help='Publish the runtime and development API classifiers to the local Maven repository.')
    args=parser.parse_args()
    if not args.from_builds:
        subprocess.run([os.sys.executable,str(ROOT/'tools/build_matrix.py'),'build','--adapters-only'],check=True)
    output=assemble()
    if args.publish:publish(output)
