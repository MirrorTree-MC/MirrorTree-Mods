"""Prepare reviewable Mojang-named reference sources from matching 1.21.1 Tiny maps.
This does not claim API compatibility with 26.1.2; generated files need manual porting.
"""
from pathlib import Path
from collections import defaultdict
import re, argparse
p=argparse.ArgumentParser(); p.add_argument('yarn'); p.add_argument('mojang'); p.add_argument('source'); p.add_argument('output'); a=p.parse_args()
def read(path):
    classes={}; members={}; owner=''
    for line in Path(path).read_text().splitlines()[1:]:
        t=line.split('\t')
        if t[0]=='c':
            owner=t[1]; classes[owner]=t[-1]
        elif len(t)>5 and t[1] in ('m','f'):
            members[(owner,t[1],t[2],t[3])]=t[-1]
    return classes,members
yc,ym=read(a.yarn); mc,mm=read(a.mojang)
classes={v:mc[k] for k,v in yc.items() if k in mc}
classes={k:v.replace('net/minecraft/resources/ResourceLocation','net/minecraft/resources/Identifier') for k,v in classes.items()}
options=defaultdict(set)
for k,v in ym.items():
    if k in mm: options[v].add(mm[k])
member={k:next(iter(v)) for k,v in options.items() if len(v)==1 and k!=next(iter(v))}
# Names changed between Mojang's 1.21.1 and 26.1.2.
member.update({'getWorld':'level','getServerWorld':'serverLevel','isClient':'isClientSide','hasStatusEffect':'hasEffect','getStatusEffect':'getEffect','getStatusEffects':'getActiveEffects','addStatusEffect':'addEffect','removeStatusEffect':'removeEffect','clearStatusEffects':'removeAllEffects','getEffectType':'getEffect','getAmplifier':'getAmplifier','getVelocity':'getDeltaMovement','setVelocity':'setDeltaMovement','getPos':'position','getBlockPos':'blockPosition','getEyePos':'getEyePosition','getRegistryKey':'dimension','getMainHandStack':'getMainHandItem','getOffHandStack':'getOffhandItem'})
full={k.replace('/','.'):v.replace('/','.').replace('$','.') for k,v in classes.items()}
out=Path(a.output)
for f in Path(a.source).rglob('*.java'):
    text=f.read_text(); body='\n'.join(l for l in text.splitlines() if not l.startswith('import net.minecraft.'))
    imports=[]; simple={}
    for line in text.splitlines():
        if not line.startswith('import net.minecraft.'): continue
        name=line[len('import '):].rstrip(';')
        if name.endswith('.*'):
            prefix=name[:-2]
            for old,new in full.items():
                if old.rsplit('.',1)[0]==prefix and re.search(r'\b'+re.escape(old.rsplit('.',1)[-1])+r'\b',body):
                    imports.append('import '+new+';'); simple[old.rsplit('.',1)[-1]]=new.rsplit('.',1)[-1]
        else:
            old=name; new=full.get(old,old)
            imports.append('import '+new+';'); simple[old.rsplit('.',1)[-1]]=new.rsplit('.',1)[-1]
    # Static imports include class prefixes.
    for old,new in sorted(full.items(),key=lambda x:-len(x[0])):
        body=body.replace(old,new)
    names={**member,**simple}
    body=re.sub(r'(?m)^package .*?$|^import .*?$|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|\b[A-Za-z_$][\w$]*\b',lambda m:names.get(m[0],m[0]),body)
    lines=body.splitlines(); idx=next((i for i,l in enumerate(lines) if l.startswith('package ')),0)+1
    lines[idx:idx]=['']+sorted(set(imports))
    dest=out/f.relative_to(Path(a.source)); dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text('\n'.join(lines)+'\n')
print('Prepared',len(list(out.rglob('*.java'))),'reference files')
