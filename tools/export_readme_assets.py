from pathlib import Path
import xml.etree.ElementTree as ET
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'images'
OUT.mkdir(parents=True, exist_ok=True)
ANDROID = '{http://schemas.android.com/apk/res/android}'

assets = {
    'logo_paw': (ROOT / 'app/src/main/res/drawable/ic_paw.xml', 256),
    'cat_sit': (ROOT / 'app/src/main/res/drawable/ic_cat_chibi_sit.xml', 200),
    'cat_peek': (ROOT / 'app/src/main/res/drawable/ic_cat_chibi_peek.xml', 160),
}

def color_value(raw: str) -> str:
    if len(raw) == 9 and raw.startswith('#FF'):
        return '#' + raw[3:]
    if raw.upper() == '#FFFFFFFF':
        return '#6B45B5'
    return raw

def serialize(node: ET.Element) -> str:
    if node.tag.endswith('path'):
        d = node.attrib[ANDROID + 'pathData']
        fill = color_value(node.attrib.get(ANDROID + 'fillColor', '#000000'))
        return f'<path d="{d}" fill="{fill}"/>'
    if node.tag.endswith('group'):
        px = node.attrib.get(ANDROID + 'pivotX')
        py = node.attrib.get(ANDROID + 'pivotY')
        sx = node.attrib.get(ANDROID + 'scaleX', '1')
        sy = node.attrib.get(ANDROID + 'scaleY', '1')
        tx = node.attrib.get(ANDROID + 'translateX', '0')
        ty = node.attrib.get(ANDROID + 'translateY', '0')
        transform = f'translate({tx} {ty})'
        if px is not None and py is not None:
            transform += f' translate({px} {py}) scale({sx} {sy}) translate(-{px} -{py})'
        else:
            transform += f' scale({sx} {sy})'
        return f'<g transform="{transform}">' + ''.join(serialize(child) for child in node) + '</g>'
    return ''.join(serialize(child) for child in node)

def android_vector_to_svg(path: Path, size: int) -> str:
    root = ET.parse(path).getroot()
    vw = root.attrib[ANDROID + 'viewportWidth']
    vh = root.attrib[ANDROID + 'viewportHeight']
    content = ''.join(serialize(node) for node in root)
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {vw} {vh}">' + content + '</svg>'

for name, (source, size) in assets.items():
    svg = android_vector_to_svg(source, size)
    out = OUT / f'{name}.png'
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(out), output_width=size, output_height=size)
    print(f'{out} {size}x{size}')
