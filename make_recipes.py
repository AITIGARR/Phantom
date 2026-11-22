import json
from pathlib import Path
root = Path('src/main/resources/data/phantomdecor/recipe')
root.mkdir(parents=True, exist_ok=True)
woods = 'oak spruce birch jungle acacia dark_oak mangrove cherry bamboo crimson warped pale_oak'.split()
colors = 'white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black'.split()
items = [('item_frame','item_frame','frame','Невидимая рамка','invisible_frame'),('glow_item_frame','glow_item_frame','frame','Невидимая светящаяся рамка','invisible_glow_frame')]
items += [(f'{w}_sign','pale_oak_sign','sign','Невидимая табличка','invisible_sign') for w in woods]
items += [(f'{c}_banner',f'{c}_banner','banner','Фантомный флаг','phantom_banner') for c in colors]
for ingredient, output, kind, name, model in items:
    components = {
        'minecraft:custom_data':{'phantomdecor':kind},
        'minecraft:item_name':{'text':name,'color':'aqua','italic':False}
    }
    if kind != 'banner':
        components['minecraft:item_model'] = f'phantomdecor:{model}'
    recipe = {
      'type':'minecraft:crafting_shapeless', 'category':'decorations',
      'ingredients':['minecraft:phantom_membrane',f'minecraft:{ingredient}'],
      'result':{'id':f'minecraft:{output}', 'count':1, 'components':components}
    }
    (root/f'{ingredient}.json').write_text(json.dumps(recipe,ensure_ascii=False,indent=2)+'\n')
