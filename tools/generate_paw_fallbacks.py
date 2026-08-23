from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Render at 4x for clean antialiasing, then downsample to Android density size.
for density, size in DENSITIES.items():
    scale = 4
    canvas_size = size * scale
    image = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Brand background matching the adaptive launcher background.
    draw.rounded_rectangle(
        (0, 0, canvas_size - 1, canvas_size - 1),
        radius=int(canvas_size * 0.22),
        fill=(99, 59, 170, 255),
    )

    # White paw silhouette: four toe pads and one broad lower pad.
    cx = canvas_size / 2
    toe_y = canvas_size * 0.34
    toe_r = canvas_size * 0.105
    toe_centers = [
        (cx - canvas_size * 0.235, toe_y + canvas_size * 0.025),
        (cx - canvas_size * 0.085, toe_y - canvas_size * 0.10),
        (cx + canvas_size * 0.085, toe_y - canvas_size * 0.10),
        (cx + canvas_size * 0.235, toe_y + canvas_size * 0.025),
    ]
    for x, y in toe_centers:
        draw.ellipse((x - toe_r, y - toe_r, x + toe_r, y + toe_r), fill=(255, 255, 255, 255))

    pad_box = (
        cx - canvas_size * 0.285,
        canvas_size * 0.43,
        cx + canvas_size * 0.285,
        canvas_size * 0.78,
    )
    draw.ellipse(pad_box, fill=(255, 255, 255, 255))

    target = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
    target.mkdir(parents=True, exist_ok=True)
    result = image.resize((size, size), Image.Resampling.LANCZOS)
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        result.save(target / name, format="PNG", optimize=True)
        print(target / name, result.size, result.mode)
