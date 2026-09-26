#!/usr/bin/env python3
"""Export, verify, and stage a product's protected data without service actions."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import posixpath
from pathlib import Path
from pathlib import PurePosixPath
import shutil
import sqlite3
import subprocess
import tarfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "transfer.json"

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def source_paths() -> list[Path]:
    data = json.loads(CONFIG.read_text())
    paths = []
    for item in data["paths"]:
        if item.startswith("@project/"):
            candidate = ROOT / item.removeprefix("@project/")
            if not candidate.resolve().is_relative_to(ROOT.resolve()):
                raise SystemExit(f"project-relative path escapes root: {item}")
            paths.append(candidate)
        else:
            paths.append(Path(item))
    if any(not p.is_absolute() or str(p) == "/" for p in paths):
        raise SystemExit("transfer.json requires exact non-root absolute paths")
    return paths

def walk(paths: list[Path]):
    for source in paths:
        if not source.exists() and not source.is_symlink():
            raise SystemExit(f"missing source: {source}")
        yield source
        if source.is_dir() and not source.is_symlink():
            for base, dirs, files in os.walk(source, followlinks=False):
                here = Path(base)
                for name in sorted(dirs + files):
                    yield here / name
                dirs[:] = [d for d in dirs if not (here / d).is_symlink()]

def is_sqlite(path: Path) -> bool:
    if not path.is_file() or path.is_symlink():
        return False
    try:
        with path.open("rb") as stream:
            return stream.read(16) == b"SQLite format 3\0"
    except OSError:
        return False

def export() -> None:
    paths = source_paths()
    exports = ROOT / "exports"
    exports.mkdir(mode=0o700, exist_ok=True)
    os.chmod(exports, 0o700)
    output = exports / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output.mkdir(mode=0o700, exist_ok=False)
    os.chmod(output, 0o700)
    sqlite_dir = output / "sqlite"
    sqlite_dir.mkdir(mode=0o700)
    items = list(dict.fromkeys(walk(paths)))
    databases = [p for p in items if is_sqlite(p)]
    db_names = {str(p) for p in databases}
    db_hashes = {}
    for src in databases:
        dst = sqlite_dir / src.relative_to("/")
        dst.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(f"file:{src}?mode=ro", uri=True, timeout=30) as read_db:
            with sqlite3.connect(dst) as write_db:
                read_db.backup(write_db, pages=1000, sleep=0.1)
        os.chmod(dst, 0o600)
        db_hashes[str(src.relative_to("/"))] = digest(dst)
    archive = output / "state.tar.zst"
    with archive.open("wb") as compressed:
        compressor = subprocess.Popen(["zstd", "-T2", "-q", "-3", "-c"], stdin=subprocess.PIPE, stdout=compressed)
        assert compressor.stdin is not None
        with tarfile.open(fileobj=compressor.stdin, mode="w|") as tar:
            for path in items:
                if str(path) in db_names or any(str(path) == name + suffix for name in db_names for suffix in ("-wal", "-shm", "-journal")):
                    continue
                tar.add(path, arcname=str(path.relative_to("/")), recursive=False)
        compressor.stdin.close()
        code = compressor.wait()
        if code:
            raise SystemExit(f"zstd failed: {code}")
    os.chmod(archive, 0o600)
    external_links = [str(p) for p in items if p.is_symlink() and os.path.isabs(os.readlink(p)) and not any(Path(os.readlink(p)).is_relative_to(root) for root in paths)]
    manifest = {"external_symlinks": external_links, "format": 1, "project": ROOT.name, "source_paths": [str(p) for p in paths], "archive_sha256": digest(archive), "sqlite_sha256": db_hashes, "created_utc": datetime.now(timezone.utc).isoformat()}
    file = output / "manifest.json"
    file.write_text(json.dumps(manifest, indent=2) + "\n")
    os.chmod(file, 0o600)
    print(f"export={output} sqlite={len(databases)} items={len(items)}")

def verify(package: Path) -> dict:
    manifest = json.loads((package / "manifest.json").read_text())
    if manifest["project"] != ROOT.name:
        raise SystemExit("package belongs to another project")
    archive = package / "state.tar.zst"
    if digest(archive) != manifest["archive_sha256"]:
        raise SystemExit("archive digest mismatch")
    result = subprocess.run(["zstd", "-tq", str(archive)], check=False)
    if result.returncode:
        raise SystemExit("archive integrity failure")
    limited_checks = 0
    for rel, expected in manifest["sqlite_sha256"].items():
        safe_relative(rel)
        db = package / "sqlite" / rel
        if digest(db) != expected:
            raise SystemExit(f"SQLite digest mismatch: {rel}")
        with sqlite3.connect(f"file:{db}?mode=ro", uri=True) as conn:
            try:
                result = conn.execute("PRAGMA quick_check").fetchone()[0]
            except sqlite3.OperationalError as error:
                if "no such collation sequence" not in str(error):
                    raise
                limited_checks += 1
                continue
            if result != "ok":
                raise SystemExit(f"SQLite integrity failure: {rel}")
    count = len(manifest['sqlite_sha256'])
    print(f"verified={package} sqlite={count} quickcheck_limited={limited_checks}")
    return manifest

def safe_relative(value: str) -> PurePosixPath:
    path = PurePosixPath(value)
    if path.is_absolute() or not path.parts or ".." in path.parts or "." in path.parts:
        raise SystemExit(f"unsafe relative path in manifest: {value}")
    return path

def restore(package: Path, destination: Path) -> None:
    manifest = verify(package)
    if destination.is_symlink():
        raise SystemExit("restore destination must not be a symlink")
    if destination.resolve() == Path("/"):
        raise SystemExit("restore to / is intentionally disabled; stage into an empty directory")
    destination.mkdir(parents=True, exist_ok=True)
    os.chmod(destination, 0o700)
    if any(destination.iterdir()):
        raise SystemExit("restore destination must be empty")
    decoder = subprocess.Popen(["zstd", "-dcq", str(package / "state.tar.zst")], stdout=subprocess.PIPE)
    assert decoder.stdout is not None
    skipped = []
    roots = [Path(p) for p in manifest["source_paths"]]
    def portable_filter(member, dest):
        if member.issym() and member.linkname.startswith("/"):
            target = Path(member.linkname)
            if any(target.is_relative_to(root) for root in roots):
                member.linkname = posixpath.relpath(str(target.relative_to("/")), posixpath.dirname(member.name))
            else:
                skipped.append(member.name)
                return None
        try:
            return tarfile.data_filter(member, dest)
        except (tarfile.AbsoluteLinkError, tarfile.LinkOutsideDestinationError):
            skipped.append(member.name)
            return None
    with tarfile.open(fileobj=decoder.stdout, mode="r|") as tar:
        tar.extractall(path=destination, filter=portable_filter)
    if decoder.wait():
        raise SystemExit("archive extraction failed")
    for rel in manifest["sqlite_sha256"]:
        relative = safe_relative(rel)
        candidate = destination
        for part in relative.parts[:-1]:
            candidate = candidate / part
            if candidate.is_symlink():
                raise SystemExit(f"SQLite parent is a symlink: {rel}")
        target = destination / rel
        if target.is_symlink():
            raise SystemExit(f"SQLite target is a symlink: {rel}")
        if not target.resolve().is_relative_to(destination.resolve()):
            raise SystemExit(f"SQLite target escapes staging: {rel}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(package / "sqlite" / rel, target)
    print(f"restored_staging={destination} skipped_external_symlinks={len(skipped)}")

def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("check")
    sub.add_parser("export")
    ver = sub.add_parser("verify"); ver.add_argument("package", type=Path)
    rst = sub.add_parser("restore"); rst.add_argument("package", type=Path); rst.add_argument("destination", type=Path)
    args = parser.parse_args()
    if args.command == "check":
        paths = source_paths()
        missing = [str(p) for p in paths if not p.exists() and not p.is_symlink()]
        print(f"paths={len(paths)} missing={len(missing)}")
        for item in missing: print(item)
        if missing: raise SystemExit(1)
    elif args.command == "export": export()
    elif args.command == "verify": verify(args.package)
    else: restore(args.package, args.destination)

if __name__ == "__main__": main()
