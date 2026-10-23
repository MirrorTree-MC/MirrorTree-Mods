"""Convert the frozen local 1.21.1 resources to 26.1.2 formats.
Run from the project root. Generated resources are included in the source distribution.
"""
from pathlib import Path
import json,re,shutil
root=Path('src/main/resources'); old=Path('../local-reference/Lantern-in-Storm/src/main/resources')
def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def ingredient(value):
 if isinstance(value,dict):
  if set(value)=={'item'}:return value['item']
  if set(value)=={'tag'}:return '#'+value['tag']
  return {k:ingredient(v) for k,v in value.items()}
 if isinstance(value,list):return [ingredient(x) for x in value]
 return value
# Restore resource originals before converting, for repeatable execution.
for p in (old/'data/lantern-in-storm/recipe').glob('*.json'):
 j=ingredient(json.loads(p.read_text())); deps=sorted(set(re.findall(r'"(biomesoplenty|mythicmetals):',json.dumps(j))))
 if deps:j['fabric:load_conditions']=[{'condition':'fabric:all_mods_loaded','values':deps}]
 write(root/'data/lantern-in-storm/recipe'/p.name,j)
models=root/'assets/lantern-in-storm/models'
for p in (models/'item').glob('*.json'):
 write(root/'assets/lantern-in-storm/items'/p.name,{'model':{'type':'minecraft:model','model':'lantern-in-storm:item/'+p.stem}})
# Complete the two missing metal blockstate files from the same family template.
base=json.loads((old/'assets/lantern-in-storm/blockstates/midas_gold_lantern.json').read_text())
for name in ('morkite','starrite'):
 write(root/'assets/lantern-in-storm/blockstates'/(name+'_lantern.json'),json.loads(json.dumps(base).replace('midas_gold',name)))
# Optional material packs override a vanilla-texture fallback when the material mod is present.
for p in (old/'assets/lantern-in-storm/models/block').glob('*.json'):
 j=json.loads(p.read_text()); deps={v.split(':')[0] for v in j.get('textures',{}).values() if ':' in v and v.split(':')[0] in ('biomesoplenty','mythicmetals')}
 if not deps:continue
 dep=next(iter(deps));pack='bop_materials' if dep=='biomesoplenty' else 'mythic_materials'
 write(root/'resourcepacks'/pack/'assets/lantern-in-storm/models/block'/p.name,j)
 for key,v in j.get('textures',{}).items():
  if v.startswith(dep+':'):
   if dep=='biomesoplenty': material='spruce_planks' if any(x in v for x in ('fir','pine','redwood','dead')) else 'oak_planks'
   else:material='gold_block' if 'midas' in v else 'diamond_block' if 'starrite' in v else 'redstone_block' if 'prometheum' in v else 'iron_block'
   j['textures'][key]='minecraft:block/'+material
 write(models/'block'/p.name,j)
for pack in ('bop_materials','mythic_materials'):
 write(root/'resourcepacks'/pack/'pack.mcmeta',{'pack':{'description':'Lantern material integration','min_format':84,'max_format':84}})
# Keep the original core-drop behavior for the lamps that used dropsLike(CUBIC_GLASS_LANTERN).
source=Path('../local-reference/Lantern-in-Storm/src/main/java/top/bearcabbage/lanterninstorm/lantern/SpiritLanternBlocks.java').read_text()
loot=json.loads((old/'data/lantern-in-storm/loot_table/blocks/cubic_glass_lantern.json').read_text());loot['pools'][0]['rolls']['n']=1
for line in source.splitlines():
 m=re.search(r'Identifier.of\(MOD_ID,"([^"]+)"\)',line)
 if m and ('dropsLike(CUBIC_GLASS_LANTERN)' in line or m[1]=='cubic_glass_lantern'):write(root/'data/lantern-in-storm/loot_table/blocks'/(m[1]+'.json'),loot)
print('Prepared 26.1.2 item models, recipes, material packs, blockstates, and loot tables')

# Ingredient alternatives containing tags require a union item tag in the modern codec.
recipe=root/'data/lantern-in-storm/recipe/talisman.json'
j=json.loads(recipe.read_text()); alternatives=j['key']['a']
write(root/'data/lantern-in-storm/tags/item/talisman_plants.json',{'values':alternatives})
j['key']['a']='#lantern-in-storm:talisman_plants';write(recipe,j)
tag=root/'data/lantern-in-storm/tags/block/spirit_lantern.json'
tag.write_text(tag.read_text().replace('promethium_lantern','prometheum_lantern'))
for p in (models/'block').glob('*.json'):
 j=json.loads(p.read_text()); textures=j.get('textures',{})
 if textures and 'particle' not in textures:
  textures['particle']='#'+next(iter(textures));write(p,j)
