"""Validate every registered lamp/item's reachable model and texture references."""
from pathlib import Path
import json,re,zipfile,sys
root=Path('src/main/resources'); code=Path('src/main/java/top/bearcabbage/lanterninstorm/content/StormContent.java').read_text()
lamps=re.findall(r'registerLamp\("([^"]+)"',code)
items=[x for x in lamps if x!='white_paper_lantern']+re.findall(r'registerItem\("([^"]+)"',code)
mc=zipfile.ZipFile('.gradle-home/caches/fabric-loom/26.1.2/minecraft-client.jar')
errors=[];visited=set()
def resource(id,kind,extension):
 ns,path=id.split(':',1) if ':' in id else ('minecraft',id)
 name='assets/'+ns+'/'+kind+'/'+path+extension
 if ns=='minecraft':
  try:return mc.read(name)
  except KeyError:return None
 p=root/name
 return p.read_bytes() if p.is_file() else None
def model(id,inherited=None,trail=()):
 if id in trail:errors.append('model cycle '+id);return
 raw=resource(id,'models','.json')
 if raw is None:errors.append('missing model '+id);return
 j=json.loads(raw); chain=[j]; parent=j.get('parent')
 while parent and parent not in trail:
  if parent.startswith('builtin/'):break
  raw=resource(parent,'models','.json')
  if raw is None:errors.append('missing parent '+parent+' from '+id);break
  q=json.loads(raw);chain.append(q);parent=q.get('parent')
 textures={}
 for q in reversed(chain):textures.update(q.get('textures',{}))
 for q in chain:
  for element in q.get('elements',[]):
   for face in element.get('faces',{}).values():
    texture=face.get('texture','');seen=set()
    if isinstance(texture,dict):texture=texture.get('sprite','')
    while texture.startswith('#'):
     if texture in seen:errors.append('texture cycle '+texture+' in '+id);break
     seen.add(texture);texture=textures.get(texture[1:],'')
     if isinstance(texture,dict):texture=texture.get('sprite','')
    if not texture:errors.append('unresolved texture '+str(face.get('texture'))+' in '+id)
    elif not texture.startswith('#') and resource(texture,'textures','.png') is None:errors.append('missing texture '+texture+' in '+id)
 visited.add(id)
for name in lamps:
 p=root/'assets/lantern-in-storm/blockstates'/(name+'.json')
 if not p.is_file():errors.append('missing blockstate '+name);continue
 j=json.loads(p.read_text())
 def find(o):
  if isinstance(o,dict):
   if 'model' in o:model(o['model'])
   for v in o.values():find(v)
  elif isinstance(o,list):
   for v in o:find(v)
 find(j)
for name in items:
 p=root/'assets/lantern-in-storm/items'/(name+'.json')
 if not p.is_file():errors.append('missing item definition '+name);continue
 j=json.loads(p.read_text());model(j['model']['model'])
for error in sorted(set(errors)):print(error)
print('Checked',len(lamps),'lamps,',len(items),'items and',len(visited),'reachable models;',len(set(errors)),'errors')
sys.exit(bool(errors))
