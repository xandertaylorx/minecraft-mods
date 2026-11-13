"""Generates the tiny .vsh/.fsh entry files Iris looks for.
All real code lives in pack/shaders/program/*.glsl; each entry file just sets a few #defines and includes it.
Run: python3 tools/gen_wrappers.py
"""
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'pack', 'shaders')

# program name -> (source file, extra defines)
PROGRAMS = {
    'shadow':               ('shadow.glsl', []),
    'gbuffers_basic':       ('unlit.glsl',  ['NO_TEXTURE']),
    'gbuffers_lightning':   ('unlit.glsl',  ['NO_TEXTURE', 'LIGHTNING']),
    'gbuffers_textured':    ('unlit.glsl',  []),
    'gbuffers_weather':     ('unlit.glsl',  ['WEATHER']),
    'gbuffers_clouds':      ('unlit.glsl',  ['CLOUDS']),
    'gbuffers_skytextured': ('unlit.glsl',  ['SKYTEXTURED']),
    'gbuffers_skybasic':    ('sky.glsl',    []),
    'gbuffers_textured_lit':('lit.glsl',    []),
    'gbuffers_terrain':     ('lit.glsl',    ['TERRAIN']),
    'gbuffers_water':       ('lit.glsl',    ['TERRAIN', 'WATER_PASS']),
    'composite':            ('water.glsl',  []),
    'composite1':           ('shafts.glsl', []),
    'composite2':           ('bloom.glsl',  []),
    'final':                ('final.glsl',  []),
}
DIMENSIONS = {'': [], 'world-1': ['NETHER'], 'world1': ['END']}

for folder, dim_defines in DIMENSIONS.items():
    out_dir = os.path.join(ROOT, folder) if folder else ROOT
    os.makedirs(out_dir, exist_ok=True)
    for name, (src, defines) in PROGRAMS.items():
        for ext, stage in (('vsh', 'VERTEX'), ('fsh', 'FRAGMENT')):
            lines = ['#version 330 compatibility', f'#define {stage}']
            lines += [f'#define {d}' for d in dim_defines + defines]
            if stage == 'FRAGMENT' and src == 'lit.glsl':
                lines.append('/* RENDERTARGETS: 0,1,2 */' if 'WATER_PASS' in defines else '/* RENDERTARGETS: 0 */')
            lines.append(f'#include "/program/{src}"')
            with open(os.path.join(out_dir, f'{name}.{ext}'), 'w') as f:
                f.write('\n'.join(lines) + '\n')
print('wrote', len(PROGRAMS) * 2 * len(DIMENSIONS), 'files')
