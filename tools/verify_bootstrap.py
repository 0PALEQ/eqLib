"""Verify adapter extraction and neutral-core linkage using a Java 8 runtime."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]


from java_tools import jdk
from project_paths import runtime_jar


def verify():
    output = ROOT/'build/bootstrap-test/classes'
    output.mkdir(parents=True, exist_ok=True)
    artifact = runtime_jar()
    subprocess.run([str(jdk(25)/'bin/javac.exe'), '--release', '8', '-proc:none', '-d', str(output),
                    '-classpath', str(artifact), str(ROOT/'tests/bootstrap/BootstrapCacheTest.java')], check=True)
    run = ROOT/'build/bootstrap-test/run'
    run.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(jdk(8)/'bin/java.exe'), '-classpath', os.pathsep.join(map(str, [output, artifact])),
                    'BootstrapCacheTest'], cwd=run, check=True)


if __name__ == '__main__':
    verify()
