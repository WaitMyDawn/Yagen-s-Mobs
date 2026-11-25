"""Compile complete Bedrock rigs into a shared GeckoLib track bank and ordered clip recipes."""
import argparse
import json
from collections import defaultdict
from pathlib import Path

CHANNELS = ("rotation", "position", "scale")
BANK_CLIP = "track_bank"
SUFFIX = ".animation.json"


def compact(value):
    # Retain insertion order inside keyframes: the native adapter iterates the authored timeline.
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def intern(table, key):
    return table.setdefault(key, len(table))


def partition(layouts):
    """Split at branches so each uninterrupted sequence of shared bindings is stored once."""
    before, after = defaultdict(set), defaultdict(set)
    for layout in layouts:
        for i, binding in enumerate(layout):
            before[binding].add(layout[i - 1] if i else None)
            after[binding].add(layout[i + 1] if i + 1 < len(layout) else None)
    parts, recipes = {}, []
    for layout in layouts:
        recipe, start = [], 0
        for i, binding in enumerate(layout):
            if i + 1 == len(layout) or len(after[binding]) != 1 or len(before[layout[i + 1]]) != 1:
                recipe.append(intern(parts, layout[start:i + 1]))
                start = i + 1
        recipes.append(recipe)
    return list(parts), recipes


def verify_rig(path, index, animations):
    """Check the runtime recipe against the export, including bone order and authored timeline order."""
    original = json.loads(path.read_bytes())["animations"]
    actions = index["rigs"][path.name.removesuffix(SUFFIX)]
    if actions.keys() != original.keys():
        raise ValueError(f"Action set changed for {path}")
    tracks = animations[BANK_CLIP]["bones"]
    for name, slot in actions.items():
        source = original[name]
        clip = index["clips"][slot]
        header = animations["header_" + str(clip["header"])]
        bones = {}
        for part in clip["parts"]:
            for binding_id in index["parts"][part]:
                binding = index["bindings"][binding_id]
                bones[binding["bone"]] = {
                    ch: tracks[str(binding[ch])][ch] for ch in CHANNELS if binding[ch] >= 0
                }
        expected = source.get("bones", {})
        if ({k: v for k, v in header.items() if k != "bones"} !=
                {k: v for k, v in source.items() if k != "bones"} or list(bones) != list(expected)):
            raise ValueError(f"Clip metadata or bone order changed for {path}:{name}")
        for bone, channels in bones.items():
            if channels.keys() != expected[bone].keys() or any(
                    compact(values) != compact(expected[bone][ch]) for ch, values in channels.items()):
                raise ValueError(f"Track changed for {path}:{name}:{bone}")


def pack(source, assets):
    bank, tracks, bindings, clips, headers, rigs = {}, {}, {}, {}, {}, {}
    version = None
    for path in sorted(source.glob("*" + SUFFIX)):
        doc = json.loads(path.read_bytes())
        if version is None:
            version = doc["format_version"]
        if doc["format_version"] != version or doc.keys() - {"format_version", "animations"}:
            raise ValueError(f"Unsupported Bedrock document in {path}")
        rig = rigs[path.name.removesuffix(SUFFIX)] = {}
        for name, clip in doc["animations"].items():
            header = {k: v for k, v in clip.items() if k != "bones"}
            if "animation_length" not in header:
                raise ValueError(f"{path}:{name} requires an explicit clip length for independent baking")
            header_id = intern(headers, compact(header))
            layout = []
            for bone, channels in clip.get("bones", {}).items():
                if channels.keys() - set(CHANNELS):
                    raise ValueError(f"Unknown channel on {path}:{name}:{bone}")
                ids = []
                for channel in CHANNELS:
                    if channel not in channels:
                        ids.append(-1)
                        continue
                    value = channels[channel]
                    index = intern(tracks, (channel, compact(value)))
                    bank.setdefault(str(index), {channel: value})
                    ids.append(index)
                layout.append(intern(bindings, (bone, *ids)))
            rig[name] = intern(clips, (name, header_id, tuple(layout)))
    if not rigs:
        raise ValueError(f"No rig animations found in {source}")

    parts, recipes = partition([layout for _, _, layout in clips])
    clips_meta = [{"header": header, "parts": recipe} for (_, header, _), recipe in zip(clips, recipes)]
    index = {"bindings": [dict(zip(("bone", *CHANNELS), b)) for b in bindings],
             "parts": parts, "clips": clips_meta, "rigs": rigs}
    animations = {"header_" + str(i): dict(json.loads(data), bones={}) for data, i in headers.items()}
    animations[BANK_CLIP] = {"bones": bank}
    for rig in rigs:
        verify_rig(source / (rig + SUFFIX), index, animations)
    outputs = {
        assets / "animations/entity/kavat_library.animation.json": {"format_version": version, "animations": animations},
        assets / "kavat/animation_index.json": index,
    }
    for path, value in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(compact(value) + "\n", encoding="utf-8")
    return index


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Directory of full exported rig animations")
    parser.add_argument("assets", type=Path, help="Destination assets namespace directory")
    args = parser.parse_args()
    pack(args.source, args.assets)
