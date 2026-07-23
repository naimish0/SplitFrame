#!/usr/bin/env python3

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "docs" / "play-store-assets"
OUTPUT = ASSETS / "screenshots" / "captioned" / "08-resize-image-captioned.png"
FONT = "/System/Library/Fonts/SFNS.ttf"
ROUNDED_FONT = "/System/Library/Fonts/SFNSRounded.ttf"
BOLD_FONT = "/System/Library/Fonts/HelveticaNeue.ttc"

INK = "#092f30"
SECONDARY = "#426263"
ACCENT = "#00897f"
OUTLINE = "#c5d8d6"
SURFACE = "#f8fbfa"
CONTAINER = "#eaf4f2"
BLUSH = "#fff0f2"
MINT = "#e9f7f5"
BLUE = "#e8f2ff"


def font(size: int, rounded: bool = False, bold: bool = False) -> ImageFont.FreeTypeFont:
    if bold:
        return ImageFont.truetype(BOLD_FONT, size, index=1)
    return ImageFont.truetype(ROUNDED_FONT if rounded else FONT, size)


def text(
    draw: ImageDraw.ImageDraw,
    position: tuple[int, int],
    value: str,
    size: int,
    fill: str = INK,
    *,
    rounded: bool = False,
    bold: bool = False,
    anchor: str | None = None,
) -> None:
    draw.text(position, value, font=font(size, rounded, bold), fill=fill, anchor=anchor)


def rounded_box(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    *,
    radius: int,
    fill: str,
    outline: str | None = None,
    width: int = 1,
) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def chip(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    label: str,
    *,
    selected: bool = False,
) -> None:
    rounded_box(
        draw,
        box,
        radius=24,
        fill=MINT if selected else "#ffffff",
        outline=ACCENT if selected else OUTLINE,
        width=3 if selected else 2,
    )
    text(
        draw,
        ((box[0] + box[2]) // 2, (box[1] + box[3]) // 2),
        label,
        23,
        ACCENT if selected else SECONDARY,
        rounded=True,
        anchor="mm",
    )


def photo_icon(draw: ImageDraw.ImageDraw, center: tuple[int, int]) -> None:
    x, y = center
    draw.rounded_rectangle(
        (x - 20, y - 18, x + 20, y + 18),
        radius=4,
        outline=SECONDARY,
        width=4,
    )
    draw.polygon(
        [(x - 15, y + 12), (x - 4, y), (x + 4, y + 7), (x + 11, y - 2), (x + 16, y + 12)],
        fill=SECONDARY,
    )
    draw.ellipse((x + 7, y - 12, x + 13, y - 6), fill=SECONDARY)


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_width, target_height = size
    ratio = max(target_width / image.width, target_height / image.height)
    resized = image.resize(
        (round(image.width * ratio), round(image.height * ratio)),
        Image.Resampling.LANCZOS,
    )
    left = (resized.width - target_width) // 2
    top = (resized.height - target_height) // 2
    return resized.crop((left, top, left + target_width, top + target_height))


def main() -> None:
    image = Image.new("RGB", (1080, 1920), "#ffffff")
    draw = ImageDraw.Draw(image)

    # Marketing header, consistent with the existing captioned source screens.
    draw.rectangle((0, 230, 720, 400), fill=BLUSH)
    rounded_box(draw, (720, 0, 1080, 350), radius=80, fill=MINT)
    draw.rectangle((970, 1540, 1080, 1810), fill=BLUE)
    icon = Image.open(ASSETS / "app-icon-512.png").convert("RGB").resize(
        (96, 96),
        Image.Resampling.LANCZOS,
    )
    image.paste(icon, (72, 72))
    text(draw, (190, 98), "SplitFrame", 29, ACCENT, bold=True)
    text(draw, (72, 184), "Resize Images Precisely", 54, INK, bold=True)
    text(
        draw,
        (72, 263),
        "Choose dimensions, Fit or Fill, format, quality, and target size",
        30,
        SECONDARY,
    )

    # Phone surface and subtle depth.
    rounded_box(draw, (122, 352, 982, 1904), radius=28, fill="#d7dfdf")
    rounded_box(
        draw,
        (110, 340, 970, 1892),
        radius=28,
        fill=SURFACE,
        outline="#aebfbe",
        width=3,
    )

    # Status and top app bar.
    text(draw, (180, 382), "9:35", 28, INK)
    for index, bar_height in enumerate((10, 16, 22, 28)):
        x = 818 + index * 10
        draw.rounded_rectangle((x, 398 - bar_height, x + 6, 398), radius=2, fill=INK)
    draw.rounded_rectangle((870, 374, 918, 398), radius=6, outline=INK, width=3)
    draw.rounded_rectangle((920, 381, 925, 391), radius=2, fill=INK)
    draw.rounded_rectangle((875, 379, 907, 393), radius=4, fill="#08b946")
    draw.line((182, 454, 160, 476, 182, 498), fill=INK, width=6, joint="curve")
    text(draw, (234, 445), "Resize image", 42, INK, bold=True)
    text(draw, (234, 492), "Resize and export one photo.", 25, SECONDARY)

    # Selection actions.
    rounded_box(
        draw,
        (154, 540, 926, 612),
        radius=36,
        fill="#ffffff",
        outline=OUTLINE,
        width=2,
    )
    photo_icon(draw, (438, 576))
    text(draw, (478, 576), "Select photo", 27, SECONDARY, rounded=True, anchor="lm")
    rounded_box(
        draw,
        (154, 628, 926, 700),
        radius=36,
        fill="#ffffff",
        outline=OUTLINE,
        width=2,
    )
    photo_icon(draw, (398, 664))
    text(draw, (438, 664), "Select batch photos", 27, SECONDARY, rounded=True, anchor="lm")

    # Selected-image preview using the release feature artwork.
    rounded_box(draw, (154, 724, 926, 1138), radius=22, fill=CONTAINER)
    artwork = Image.open(ASSETS / "feature-art-background.png").convert("RGB")
    preview = cover(artwork, (700, 366))
    mask = Image.new("L", preview.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, preview.width - 1, preview.height - 1),
        radius=18,
        fill=255,
    )
    image.paste(preview, (190, 748), mask)
    rounded_box(
        draw,
        (190, 748, 890, 1114),
        radius=18,
        fill=None,
        outline="#ffffff",
        width=3,
    )

    # Visible production resize controls.
    text(draw, (154, 1192), "Export preset", 29, INK, bold=True)
    text(draw, (154, 1234), "Social and common sizes", 23, SECONDARY)
    chip(draw, (154, 1270, 425, 1326), "Instagram Square", selected=True)
    chip(draw, (441, 1270, 596, 1326), "1080p")
    chip(draw, (612, 1270, 733, 1326), "2x")
    chip(draw, (749, 1270, 926, 1326), "Custom")

    text(draw, (154, 1387), "Content placement", 27, INK, bold=True)
    chip(draw, (154, 1424, 482, 1482), "Fit — whole image", selected=True)
    chip(draw, (498, 1424, 824, 1482), "Fill — crop edges")

    text(draw, (154, 1542), "Output format", 27, INK, bold=True)
    chip(draw, (154, 1579, 330, 1637), "JPEG", selected=True)
    chip(draw, (346, 1579, 512, 1637), "PNG")
    chip(draw, (528, 1579, 716, 1637), "WebP")

    text(draw, (154, 1700), "Encoding quality 94", 27, INK, bold=True)
    draw.line((154, 1752, 850, 1752), fill=OUTLINE, width=8)
    draw.line((154, 1752, 724, 1752), fill=ACCENT, width=8)
    draw.ellipse((706, 1734, 742, 1770), fill=ACCENT)
    rounded_box(draw, (154, 1810, 926, 1870), radius=30, fill=ACCENT)
    text(draw, (540, 1840), "Resize and save", 27, "#ffffff", bold=True, anchor="mm")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, format="PNG", optimize=True)
    print(f"Generated {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
