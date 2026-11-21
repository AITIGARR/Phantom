#!/usr/bin/env python3
"""Rebuild vanilla-compatible shapeless recipe JSON files for Phantom Decor."""
import json
from pathlib import Path

out = Path(__file__).parent / 'src/main/resources/data/phantomdecor/recipe'
out.mkdir(parents=True, exist_ok=True)
woods = ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove',
         'cherry', 'bamboo', 'crimson', 'warped', 'pale_oak')
dyes = ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
        'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black')

def recipe(item, kind):
    data = {
        'type': 'minecraft:crafting_shapeless',
        'category': 'misc',
        'ingredients': ['minecraft:phantom_membrane', f'minecraft:{item}'],
        'result': {
            'id': f'minecraft:{item}',
            'count': 1,
            'components': {
                'minecraft:custom_data': {'phantomdecor': kind},
                'minecraft:item_name': {'text': {'frame': 'Невидимая рамка',
                                                 'sign': 'Невидимая табличка',
                                                 'banner': 'Голографический флаг'}[kind],
                                        'color': 'aqua', 'italic': False}
            }
        }
    }
    (out / f'{item}.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

for wood in woods:
    recipe(f'{wood}_sign', 'sign')
for dye in dyes:
    recipe(f'{dye}_banner', 'banner')
for frame in ('item_frame', 'glow_item_frame'):
    recipe(frame, 'frame')
print('Recipes generated:', len(list(out.glob('*.json'))))
