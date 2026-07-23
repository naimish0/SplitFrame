#!/usr/bin/env python3

from __future__ import annotations

import os
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "docs" / "play-store-assets" / "localized"
EXPECTED = {
    "phone": (2160, 3840),
    "tablet-7-inch": (2160, 3840),
    "tablet-10-inch": (2160, 3840),
}
FEATURE_SIZE = (1024, 500)
SHEET_SIZE = (3200, 2400)


def optimize(path: Path, target_size: tuple[int, int]) -> None:
    with Image.open(path) as source:
        image = source.convert("RGB")
        if image.size != target_size:
            source_ratio = image.width / image.height
            target_ratio = target_size[0] / target_size[1]
            if source_ratio > target_ratio:
                cropped_width = round(image.height * target_ratio)
                left = (image.width - cropped_width) // 2
                image = image.crop((left, 0, left + cropped_width, image.height))
            elif source_ratio < target_ratio:
                cropped_height = round(image.width / target_ratio)
                top = (image.height - cropped_height) // 2
                image = image.crop((0, top, image.width, top + cropped_height))
            image = image.resize(target_size, Image.Resampling.LANCZOS)
        temporary = path.with_suffix(".optimized.png")
        image.save(temporary, format="PNG", compress_level=7)
    os.replace(temporary, path)


def validate_locale(locale: str, sheets_only: bool = False, features_only: bool = False) -> None:
    locale_root = ASSETS / locale
    if not sheets_only:
        feature = locale_root / "feature-graphic-1024x500.png"
        optimize(feature, FEATURE_SIZE)
    if features_only:
        print(f"Optimized and validated {locale}")
        return

    for folder, expected_size in EXPECTED.items():
        device_root = locale_root / folder
        screenshots = sorted(device_root.glob("[0-9][0-9]-*.png"))
        assert len(screenshots) == 7, (device_root, len(screenshots))
        if not sheets_only:
            for screenshot in screenshots:
                optimize(screenshot, expected_size)
        sheet = device_root / "content-sheet-all-features.png"
        optimize(sheet, SHEET_SIZE)

    print(f"Optimized and validated {locale}")


if __name__ == "__main__":
    arguments = sys.argv[1:]
    sheets_only = "--sheets-only" in arguments
    features_only = "--features-only" in arguments
    locales = [argument for argument in arguments if not argument.startswith("--")]
    locales = locales or sorted(path.name for path in ASSETS.iterdir() if path.is_dir())
    for locale_name in locales:
        validate_locale(locale_name, sheets_only=sheets_only, features_only=features_only)
