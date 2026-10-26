"""Check direct Minecraft member references in a universal JAR against each game's class files.

This checks descriptors and inherited members; runtime mixin/render tests are still required.
"""
import argparse
from functools import lru_cache
from pathlib import Path
import struct
import zipfile

class ClassFile:
    def __init__(self, data):
        self.data=data
        self.offset=8
        self.pool=[None]*self.u2()
        index=1
        while index<len(self.pool):
            tag=self.u1()
            if tag==1:
                length=self.u2()
                value=self.take(length).decode('utf-8',errors='replace')
            elif tag in (7,8,16,19,20): value=self.u2()
            elif tag in (9,10,11,12,17,18): value=(self.u2(),self.u2())
            elif tag in (3,4): value=self.take(4)
            elif tag in (5,6): value=self.take(8)
            elif tag==15: value=(self.u1(),self.u2())
            else: raise ValueError('Unsupported constant-pool tag: '+str(tag))
            self.pool[index]=(tag,value)
            index+=2 if tag in (5,6) else 1
        self.u2()
        self.name=self.class_name(self.u2())
        parent=self.u2()
        self.parents=([self.class_name(parent)] if parent else [])
        self.parents.extend(self.class_name(self.u2()) for _ in range(self.u2()))
        self.fields=self.members()
        self.methods=self.members()

    def take(self,length):
        result=self.data[self.offset:self.offset+length]
        self.offset+=length
        return result

    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack('>H',self.take(2))[0]
    def u4(self): return struct.unpack('>I',self.take(4))[0]
    def text(self,index): return self.pool[index][1]
    def class_name(self,index): return self.text(self.pool[index][1])

    def members(self):
        result=set()
        for _ in range(self.u2()):
            self.u2()
            result.add((self.text(self.u2()),self.text(self.u2())))
            for _ in range(self.u2()):
                self.u2()
                self.take(self.u4())
        return result

    def references(self):
        for entry in self.pool:
            if entry is None or entry[0] not in (9,10,11): continue
            owner,name_type=entry[1]
            name,descriptor=self.pool[name_type][1]
            yield entry[0],self.class_name(owner),self.text(name),self.text(descriptor)


def verify(library,game):
    with zipfile.ZipFile(game) as minecraft, zipfile.ZipFile(library) as archive:
        @lru_cache(None)
        def load(name):
            try: return ClassFile(minecraft.read(name+'.class'))
            except KeyError: return None

        @lru_cache(None)
        def resolves(tag,owner,name,descriptor):
            current=load(owner)
            if current is None:
                return (owner=='java/lang/Enum' and (name,descriptor) in {('ordinal','()I'),('name','()Ljava/lang/String;')}) or (owner=='java/lang/Object' and (name,descriptor) in {
                    ('getClass','()Ljava/lang/Class;'),('equals','(Ljava/lang/Object;)Z'),
                    ('hashCode','()I'),('toString','()Ljava/lang/String;')})
            if (name,descriptor) in (current.fields if tag==9 else current.methods): return True
            if name=='<init>': return False
            return any(resolves(tag,parent,name,descriptor) for parent in current.parents)

        references=set()
        for path in archive.namelist():
            if path.endswith('.class'):
                references.update(r for r in ClassFile(archive.read(path)).references()
                                  if r[1].startswith(('net/minecraft/','com/mojang/blaze3d/')))
        missing=sorted(r for r in references if not resolves(*r))
        for tag,owner,name,descriptor in missing: print('MISSING',owner,name,descriptor)
        print(game.name,':',len(references),'direct game member references;',len(missing),'unresolved')
        return not missing

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('library',type=Path)
    parser.add_argument('game',type=Path,nargs='+')
    args=parser.parse_args()
    results=[verify(args.library,game) for game in args.game]
    raise SystemExit(not all(results))
