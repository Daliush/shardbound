# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=11"]
# ///
"""
Publishes the card art: turns each card's master painting into the image the game serves, and writes it to the art
store, the folder the game server reads (engine/README.md).

A master is assets/art/cards/<faction>/<slug>-v<n>.png, 1024x1536 and about 2.5 MB; the highest version of a card
wins. Its published image is assets/art/web/<faction>.<slug>.webp, named by the card's id: 768x1152, about 130 KB.
An image whose card has no master any more is removed, so the store holds exactly the published cards.

Usage, from anywhere in the repository: uv run scripts/publish-card-art.py
"""

import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MASTERS = ROOT / "assets" / "art" / "cards"
STORE = ROOT / "assets" / "art" / "web"
CARDS = ROOT / "content" / "cards"

WIDTH = 768
QUALITY = 82
MASTER_NAME = re.compile(r"^(?P<slug>[a-z0-9-]+)-v(?P<version>\d+)\.png$")


def latest_masters() -> dict[str, Path]:
    """Each card id with the path of its latest master."""
    found: dict[str, tuple[int, Path]] = {}
    for path in sorted(MASTERS.glob("*/*.png")):
        match = MASTER_NAME.match(path.name)
        if not match:
            continue
        card_id = path.parent.name + "." + match["slug"]
        version = int(match["version"])
        if card_id not in found or version > found[card_id][0]:
            found[card_id] = (version, path)
    return {card_id: path for card_id, (_, path) in found.items()}


def publish(master: Path, target: Path) -> None:
    with Image.open(master) as image:
        image = image.convert("RGB")
        height = round(image.height * WIDTH / image.width)
        image.resize((WIDTH, height), Image.Resampling.LANCZOS).save(target, "WEBP", quality=QUALITY, method=6)


def main() -> int:
    masters = latest_masters()
    STORE.mkdir(parents=True, exist_ok=True)
    for card_id, master in sorted(masters.items()):
        publish(master, STORE / (card_id + ".webp"))
        print(f"{card_id}: {master.relative_to(ROOT).as_posix()}")
    for stale in STORE.glob("*.webp"):
        if stale.stem not in masters:
            stale.unlink()
            print(f"{stale.stem}: removed, no master")
    cards = {path.parent.name + "." + path.stem for path in CARDS.glob("*/*.json")}
    for card_id in sorted(cards - masters.keys()):
        print(f"{card_id}: no master, the client draws a placeholder", file=sys.stderr)
    print(f"{len(masters)} images in {STORE.relative_to(ROOT).as_posix()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
