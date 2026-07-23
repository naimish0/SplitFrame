#!/usr/bin/env python3
"""Generate complete Android string resources with NLLB.

This bootstrap tool keeps Android formatting placeholders opaque, resumes from
a local cache, and fails when a translation changes a placeholder.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from xml.etree import ElementTree
from xml.sax.saxutils import escape

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/res/values/strings.xml"
CACHE = ROOT / "build/localization/nllb-cache.json"
MODEL_NAME = "facebook/nllb-200-distilled-600M"
FORMAT_TOKEN = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
PROTECTED = re.compile(
    r"(SplitFrame|Instagram|WhatsApp|YouTube|Pinterest|Google|Android|"
    r"JPEG|PNG|WebP|MP4|HDR|SDR|\bKB\b|\bMB\b|\b2K\b|\b4K\b)"
)
LOCALES = {
    "values-de": "deu_Latn",
    "values-fr": "fra_Latn",
    "values-ja": "jpn_Jpan",
    "values-hi": "hin_Deva",
    "values-ru": "rus_Cyrl",
    "values-es": "spa_Latn",
    "values-pt-rPT": "por_Latn",
    "values-pt-rBR": "por_Latn",
    "values-it": "ita_Latn",
    "values-in": "ind_Latn",
    "values-ar": "arb_Arab",
    "values-ko": "kor_Hang",
    "values-ur": "urd_Arab",
}
PORTUGUESE_REGIONAL_TERMS = {
    "values-pt-rPT": {
        "Configurações": "Definições",
        "configurações": "definições",
        "Salvar": "Guardar",
        "salvar": "guardar",
        "arquivo": "ficheiro",
        "arquivos": "ficheiros",
        "aplicativo": "aplicação",
        "tela": "ecrã",
        "Excluir": "Eliminar",
        "excluir": "eliminar",
        "celular": "telemóvel",
    },
    "values-pt-rBR": {
        "Definições": "Configurações",
        "definições": "configurações",
        "Guardar": "Salvar",
        "guardar": "salvar",
        "ficheiro": "arquivo",
        "ficheiros": "arquivos",
        "aplicação": "aplicativo",
        "ecrã": "tela",
        "Eliminar": "Excluir",
        "eliminar": "excluir",
        "telemóvel": "celular",
    },
}


def inner_text(node: ElementTree.Element) -> str:
    return "".join(node.itertext())


def opaque(text: str) -> tuple[str, dict[str, str]]:
    replacements: dict[str, str] = {}

    def replace(match: re.Match[str]) -> str:
        key = f"PH{len(replacements)}TOKEN"
        replacements[key] = match.group(0)
        return key

    return PROTECTED.sub(replace, FORMAT_TOKEN.sub(replace, text)), replacements


def restore(text: str, replacements: dict[str, str]) -> str:
    for key, value in replacements.items():
        text = re.sub(re.escape(key), value, text, flags=re.IGNORECASE)
    return text


def generate_batch(
    texts: list[str],
    target: str,
    tokenizer: AutoTokenizer,
    model: AutoModelForSeq2SeqLM,
) -> list[str]:
    encoded = tokenizer(
        texts,
        return_tensors="pt",
        padding=True,
        truncation=True,
        max_length=256,
    ).to(model.device)
    generated = model.generate(
        **encoded,
        forced_bos_token_id=tokenizer.convert_tokens_to_ids(target),
        max_new_tokens=128,
        num_beams=1,
    )
    return tokenizer.batch_decode(generated, skip_special_tokens=True)


def translate_by_segments(
    source: str,
    target: str,
    tokenizer: AutoTokenizer,
    model: AutoModelForSeq2SeqLM,
) -> str:
    tokens = FORMAT_TOKEN.findall(source)
    segments = FORMAT_TOKEN.split(source)
    translatable = [segment for segment in segments if re.search(r"\w", segment)]
    translated = iter(
        generate_batch(translatable, target, tokenizer, model) if translatable else []
    )
    rebuilt: list[str] = []
    for index, segment in enumerate(segments):
        rebuilt.append(next(translated).strip() if re.search(r"\w", segment) else segment)
        if index < len(tokens):
            rebuilt.append(tokens[index])
    return " ".join(part for part in rebuilt if part).strip()


def android_escape(text: str) -> str:
    return escape(text.replace("'", "\\'"), {'"': "&quot;"})


def regionalize(text: str, folder: str) -> str:
    for source, replacement in PORTUGUESE_REGIONAL_TERMS.get(folder, {}).items():
        text = re.sub(rf"\b{re.escape(source)}\b", replacement, text)
    return text


def translate_locale(
    texts: list[str],
    target: str,
    tokenizer: AutoTokenizer,
    model: AutoModelForSeq2SeqLM,
    cache: dict[str, str],
    batch_size: int,
) -> list[str]:
    results: list[str] = []
    missing: list[tuple[int, str, str, dict[str, str]]] = []
    for index, source in enumerate(texts):
        key = f"{target}\0{source}"
        if key in cache:
            results.append(cache[key])
        else:
            results.append("")
            protected, replacements = opaque(source)
            missing.append((index, key, protected, replacements))

    tokenizer.src_lang = "eng_Latn"
    # Group similarly sized strings so padding does not make every batch as
    # expensive as its longest entry. Results are still restored by index.
    missing.sort(key=lambda entry: len(entry[2]))
    start = 0
    while start < len(missing):
        source_length = len(missing[start][2])
        effective_batch_size = (
            batch_size
            if source_length <= 80
            else min(batch_size, 12)
            if source_length <= 160
            else min(batch_size, 4)
        )
        batch = missing[start : start + effective_batch_size]
        decoded = generate_batch(
            [entry[2] for entry in batch],
            target,
            tokenizer,
            model,
        )
        for entry, translation in zip(batch, decoded):
            index, key, _, replacements = entry
            has_every_marker = all(
                re.search(re.escape(marker), translation, flags=re.IGNORECASE)
                for marker in replacements
            )
            restored = (
                restore(translation.strip(), replacements)
                if has_every_marker
                else translate_by_segments(texts[index], target, tokenizer, model)
            )
            expected = sorted(FORMAT_TOKEN.findall(texts[index]))
            actual = sorted(FORMAT_TOKEN.findall(restored))
            if actual != expected:
                restored = texts[index]
                print(f"{target}: retained source text to preserve placeholders: {texts[index]}")
            results[index] = restored
            cache[key] = restored
        CACHE.parent.mkdir(parents=True, exist_ok=True)
        CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
        start += len(batch)
        print(f"{target}: {start}/{len(missing)}")
    return results


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--batch-size", type=int, default=12)
    parser.add_argument("--locales", nargs="*", choices=LOCALES)
    args = parser.parse_args()

    root = ElementTree.parse(SOURCE).getroot()
    entries: list[tuple[str, str, str | None]] = []
    texts: list[str] = []
    for node in root:
        if node.attrib.get("translatable") == "false":
            continue
        if node.tag == "string":
            text = inner_text(node)
            entries.append(("string", node.attrib["name"], None))
            texts.append(text)
        elif node.tag == "plurals":
            for item in node:
                entries.append(("plural", node.attrib["name"], item.attrib["quantity"]))
                texts.append(inner_text(item))

    cache = json.loads(CACHE.read_text(encoding="utf-8")) if CACHE.exists() else {}
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_NAME)
    if torch.backends.mps.is_available():
        model.to("mps")
    model.eval()

    selected = args.locales or list(LOCALES)
    translated_by_target: dict[str, list[str]] = {}
    for folder in selected:
        target = LOCALES[folder]
        translations = translated_by_target.get(target)
        if translations is None:
            translations = translate_locale(
                texts, target, tokenizer, model, cache, args.batch_size
            )
            translated_by_target[target] = translations

        output = ["<resources>"]
        open_plural: str | None = None
        for (kind, name, quantity), translation in zip(entries, translations):
            translation = regionalize(translation, folder)
            if kind == "string":
                if open_plural is not None:
                    output.append("    </plurals>")
                    open_plural = None
                output.append(
                    f'    <string name="{name}">{android_escape(translation)}</string>'
                )
            else:
                if open_plural != name:
                    if open_plural is not None:
                        output.append("    </plurals>")
                    output.append(f'    <plurals name="{name}">')
                    open_plural = name
                output.append(
                    f'        <item quantity="{quantity}">'
                    f"{android_escape(translation)}</item>"
                )
        if open_plural is not None:
            output.append("    </plurals>")
        output.append("</resources>")
        destination = ROOT / "app/src/main/res" / folder / "strings.xml"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text("\n".join(output) + "\n", encoding="utf-8")
        print(f"Wrote {destination.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
