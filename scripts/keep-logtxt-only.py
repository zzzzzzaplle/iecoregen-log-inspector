from __future__ import annotations

import argparse
import os
import stat
from pathlib import Path
import shutil
import sys


APPLY_CHANGES = True


def build_keep_dirs(root: Path) -> set[Path]:
    keep_dirs: set[Path] = set()
    for log_file in root.rglob("log.txt"):
        current = log_file.parent
        while True:
            keep_dirs.add(current)
            if current == root:
                break
            try:
                current = current.parent
            except RuntimeError:
                break
            if root not in current.parents and current != root:
                break
    return keep_dirs


def collect_targets(root: Path, keep_dirs: set[Path]) -> tuple[list[Path], list[Path]]:
    files_to_remove: list[Path] = []
    dirs_to_remove: list[Path] = []

    for path in root.rglob("*"):
        if path.is_file():
            if path.name != "log.txt":
                files_to_remove.append(path)
        elif path.is_dir():
            if path != root and path not in keep_dirs:
                dirs_to_remove.append(path)

    dirs_to_remove.sort(key=lambda item: len(item.parts), reverse=True)
    return files_to_remove, dirs_to_remove


def remove_readonly(func, path, exc_info) -> None:
    try:
        os.chmod(path, stat.S_IWRITE)
    except OSError:
        pass
    func(path)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Keep only log.txt files under a logs tree and remove everything else."
    )
    parser.add_argument(
        "root",
        nargs="?",
        default=Path(__file__).resolve().parent.parent / "logs",
        type=Path,
        help="Root folder to clean (default: ../logs)",
    )

    args = parser.parse_args()
    root = args.root.resolve()

    if not root.exists() or not root.is_dir():
        print(f"Root path is not a directory: {root}", file=sys.stderr)
        return 1

    keep_dirs = build_keep_dirs(root)
    log_files = sorted(root.rglob("log.txt"))
    files_to_remove, dirs_to_remove = collect_targets(root, keep_dirs)

    print(f"Root: {root}")
    print(f"log.txt files kept: {len(log_files)}")
    print(f"directories kept: {len(keep_dirs)}")
    print(f"files to remove: {len(files_to_remove)}")
    print(f"directories to remove: {len(dirs_to_remove)}")

    if not APPLY_CHANGES:
        print("Dry run only. Set APPLY_CHANGES = True in the script to delete the listed files and directories.")
        return 0

    for file_path in files_to_remove:
        try:
            file_path.unlink()
        except FileNotFoundError:
            pass

    for dir_path in dirs_to_remove:
        if dir_path.exists():
            shutil.rmtree(dir_path, onerror=remove_readonly)

    print("Cleanup complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())