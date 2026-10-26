"""Locate installed JDKs without relying on a workstation-specific installation path."""
from pathlib import Path
import os


def jdk(major):
    candidates = [os.environ.get('JAVA%d_HOME' % major), os.environ.get('JAVA_HOME')]
    for base in [Path.home()/'.jdks', Path.home()/'.gradle/jdks', Path('C:/Program Files/Java'), Path('C:/Program Files/Eclipse Adoptium')]:
        if base.exists():
            candidates.extend(str(p) for p in sorted(base.iterdir()) if p.is_dir())
    for candidate in filter(None, candidates):
        release = Path(candidate)/'release'
        if release.exists():
            version = release.read_text()
            if ('JAVA_VERSION="%d.' % major) in version or (major == 8 and 'JAVA_VERSION="1.8.' in version):
                return Path(candidate)
    raise SystemExit('Set JAVA%d_HOME to JDK %d.' % (major, major))
