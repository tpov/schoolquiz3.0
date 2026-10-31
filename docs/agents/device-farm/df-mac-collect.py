#!/usr/bin/env python3
"""Mac-side Device Farm run collector (run during the VPS egress incident).

Usage: df-mac-collect.py <run-arn> <out-dir>   env: AWS_KEY_ID, AWS_SECRET
All Device Farm API calls (list-jobs/suites/tests/artifacts) and artifact
downloads go Mac -> AWS directly: URLs obtained via VPS responses arrive
corrupted (SigV4 SignatureDoesNotMatch everywhere); Mac-fetched URLs work.
Files are written straight into <out-dir> (point it at the VPS-mounted repo).
"""
import sys, os, json, hashlib, hmac, datetime, urllib.request, urllib.error, re

KEY = os.environ["AWS_KEY_ID"]; SECRET = os.environ["AWS_SECRET"]
REGION = "us-west-2"; SERVICE = "devicefarm"

def sign(key, msg): return hmac.new(key, msg.encode(), hashlib.sha256).digest()

def api(action, payload):
    t = datetime.datetime.now(datetime.timezone.utc)
    amzdate = t.strftime("%Y%m%dT%H%M%SZ"); datestamp = t.strftime("%Y%m%d")
    body = json.dumps(payload).encode()
    headers = {"content-type": "application/x-amz-json-1.1",
               "x-amz-target": f"DeviceFarm_20150623.{action}",
               "x-amz-date": amzdate,
               "host": f"{SERVICE}.{REGION}.amazonaws.com"}
    ph = hashlib.sha256(body).hexdigest()
    sn = sorted(headers); ch = "".join(f"{k}:{headers[k]}\n" for k in sn)
    cr = "\n".join(["POST", "/", "", ch, ";".join(sn), ph])
    scope = f"{datestamp}/{REGION}/{SERVICE}/aws4_request"
    sts = "\n".join(["AWS4-HMAC-SHA256", amzdate, scope, hashlib.sha256(cr.encode()).hexdigest()])
    k = sign(("AWS4" + SECRET).encode(), datestamp)
    for part in (REGION, SERVICE, "aws4_request"): k = sign(k, part)
    headers["Authorization"] = (f"AWS4-HMAC-SHA256 Credential={KEY}/{scope}, "
        f"SignedHeaders={';'.join(sn)}, Signature={hmac.new(k, sts.encode(), hashlib.sha256).hexdigest()}")
    req = urllib.request.Request(f"https://{SERVICE}.{REGION}.amazonaws.com/", data=body, method="POST", headers=headers)
    try:
        return json.load(urllib.request.urlopen(req, timeout=60))
    except urllib.error.HTTPError as e:
        print(f"API {action} -> HTTP {e.code}: {e.read()[:200]!r}", file=sys.stderr)
        return {}

def fetch(url, path):
    try:
        with urllib.request.urlopen(urllib.request.Request(url), timeout=300) as r, open(path, "wb") as f:
            while True:
                chunk = r.read(1 << 16)
                if not chunk: break
                f.write(chunk)
        print("saved", path)
    except urllib.error.HTTPError as e:
        print(f"DL FAIL {e.code} {path}: {e.read()[:120]!r}", file=sys.stderr)

def main(run_arn, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    run = api("GetRun", {"arn": run_arn}).get("run", {})
    summary = {k: run.get(k) for k in ("status", "result", "started", "stopped", "deviceMinutes")}
    with open(os.path.join(out_dir, "run-summary.json"), "w") as f:
        json.dump(summary, f, indent=2)
    print("run:", json.dumps(summary))
    urls = []
    jobs = api("ListJobs", {"arn": run_arn}).get("jobs", [])
    for job in jobs:
        for suite in api("ListSuites", {"arn": job["arn"]}).get("suites", []):
            for test in api("ListTests", {"arn": suite["arn"]}).get("tests", []):
                for atype in ("LOG", "FILE", "SCREENSHOT"):
                    arts = api("ListArtifacts", {"arn": test["arn"], "type": atype}).get("artifacts", [])
                    for a in arts:
                        urls.append((a.get("name", "artifact"), a.get("type", "FILE"), a["url"]))
    with open(os.path.join(out_dir, "artifacts.json"), "w") as f:
        json.dump([{"name": n, "type": t, "url": u[:80] + "..."} for n, t, u in urls], f, indent=2)
    seen = set()
    for name, typ, url in urls:
        if url in seen: continue
        seen.add(url)
        safe = re.sub(r"[/ ]", "_", name)
        fetch(url, os.path.join(out_dir, f"{typ.lower()}-{safe}"))

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
