#!/usr/bin/env python3
"""Rewrite only sdk.dir in private local.properties for this host."""
from __future__ import annotations

import os
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not sdk or not Path(sdk).is_absolute() or not Path(sdk).is_dir():
    raise SystemExit("Set ANDROID_HOME to an existing absolute Android SDK path")
path = root / "local.properties"
lines = path.read_text(encoding="utf-8").splitlines(keepends=True) if path.exists() else []
other = [line for line in lines if not line.lstrip().startswith("sdk.dir=")]
escaped = sdk.replace("\\", "\\\\").replace(" ", "\\ ").replace(":", "\\:")
content = "".join(other)
if content and not content.endswith("\n"):
    content += "\n"
content += f"sdk.dir={escaped}\n"
tmp = path.with_name(".local.properties.preparing")
fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as out:
    out.write(content)
os.chmod(tmp, 0o600)
os.replace(tmp, path)
print("Android SDK configured in private local.properties")
