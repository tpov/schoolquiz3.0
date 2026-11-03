#!/usr/bin/env bash
# Prints "<serial> <uid> <provider> <email>" for every connected device that has the app signed in.
# The uid is decoded from the cached Firebase ID token; no login and no credentials are involved.
set -u
PKG="${PKG:-com.tpov.schoolquiz}"
for serial in $(adb devices | awk '/\tdevice$/{print $1}'); do
  store=$(adb -s "$serial" shell run-as "$PKG" ls /data/data/"$PKG"/shared_prefs 2>/dev/null \
          | tr -d '\r' | grep '^com.google.firebase.auth.api.Store' | head -1)
  if [ -z "$store" ]; then echo "$serial - - (app not installed or no auth store)"; continue; fi
  adb -s "$serial" shell run-as "$PKG" cat /data/data/"$PKG"/shared_prefs/"$store" 2>/dev/null \
    | SERIAL="$serial" python3 -c '
import sys, os, re, json, base64
raw = sys.stdin.read()
out = None
for tok in re.findall(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{20,}", raw):
    seg = tok.split(".")[1]
    seg += "=" * (-len(seg) % 4)
    try:
        c = json.loads(base64.urlsafe_b64decode(seg))
    except Exception:
        continue
    if "user_id" in c:
        out = c
        break
if out is None:
    print(os.environ["SERIAL"], "-", "-", "(no token)")
else:
    print(os.environ["SERIAL"], out.get("user_id", "-"),
          (out.get("firebase") or {}).get("sign_in_provider", "-"),
          out.get("email", "(anonymous)"))
'
done
