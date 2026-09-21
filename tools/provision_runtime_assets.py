#!/usr/bin/env python3
"""Restore separately distributed runtime assets from a recipient-supplied APK."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    manifest = json.loads((root / "runtime-assets.json").read_text())
    destination = root / "android/YiwooTtsCandidate/app/src/main/assets"
    verified = []
    with zipfile.ZipFile(args.apk) as archive:
        for asset in manifest["assets"]:
            relative = Path(asset["path"])
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("Unsafe manifest path")
            info = archive.getinfo("assets/" + relative.as_posix())
            if info.file_size != asset["bytes"]:
                raise ValueError("Size mismatch: " + str(relative))
            digest = hashlib.sha256()
            with archive.open(info) as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(block)
            if digest.hexdigest() != asset["sha256"]:
                raise ValueError("Hash mismatch: " + str(relative))
            verified.append((relative, info))
        if not args.verify_only:
            for relative, info in verified:
                target = destination / relative
                if not target.resolve().is_relative_to(destination.resolve()):
                    raise ValueError("Destination escapes assets directory")
                target.parent.mkdir(parents=True, exist_ok=True)
                temporary = target.with_suffix(target.suffix + ".tmp")
                with archive.open(info) as source, temporary.open("wb") as out:
                    for block in iter(lambda: source.read(1024 * 1024), b""):
                        out.write(block)
                temporary.replace(target)
    print(f"Verified {len(verified)} runtime assets; copied={not args.verify_only}")


if __name__ == "__main__":
    main()
