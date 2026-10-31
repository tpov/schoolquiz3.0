#!/usr/bin/env python3
"""Mac-side Device Farm spec uploader (bypasses VPS network entirely).

Usage: df-mac-put.py <name> <spec-file>   env: AWS_KEY_ID, AWS_SECRET
Creates the upload via the Device Farm JSON API signed with SigV4 (Mac -> AWS),
then PUTs the spec to the returned presigned URL (Mac -> S3).
Prints: UPLOAD_ARN=... and PUT_RESULT=...
"""
import sys, os, json, hashlib, hmac, datetime, urllib.request

KEY = os.environ["AWS_KEY_ID"]; SECRET = os.environ["AWS_SECRET"]
REGION = "us-west-2"; SERVICE = "devicefarm"
PROJ = "arn:aws:devicefarm:us-west-2:453713320584:project:69133ff9-bd68-4307-82e3-02855263c8f8"

def sign(key, msg): return hmac.new(key, msg.encode(), hashlib.sha256).digest()
def sigv4_headers(method, canon_uri, query, body, extra_headers):
    t = datetime.datetime.now(datetime.timezone.utc)
    amzdate = t.strftime("%Y%m%dT%H%M%SZ"); datestamp = t.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(body).hexdigest()
    headers = dict(extra_headers); headers["x-amz-date"] = amzdate
    signed_names = sorted(k.lower() for k in headers)
    canon_headers = "".join(f"{k}:{headers[next(h for h in headers if h.lower()==k)].strip()}\n" for k in signed_names)
    cr = "\n".join([method, canon_uri, query, canon_headers, ";".join(signed_names), payload_hash])
    scope = f"{datestamp}/{REGION}/{SERVICE}/aws4_request"
    sts = "\n".join(["AWS4-HMAC-SHA256", amzdate, scope, hashlib.sha256(cr.encode()).hexdigest()])
    k = sign(("AWS4" + SECRET).encode(), datestamp)
    for part in (REGION, SERVICE, "aws4_request"): k = sign(k, part)
    headers["Authorization"] = (f"AWS4-HMAC-SHA256 Credential={KEY}/{scope}, "
        f"SignedHeaders={';'.join(signed_names)}, Signature={hmac.new(k, sts.encode(), hashlib.sha256).hexdigest()}")
    return headers

name, path = sys.argv[1], sys.argv[2]
body = json.dumps({"projectArn": PROJ, "name": name, "type": "APPIUM_NODE_TEST_SPEC"}).encode()
h = sigv4_headers("POST", "/", "", body, {
    "content-type": "application/x-amz-json-1.1",
    "x-amz-target": "DeviceFarm_20150623.CreateUpload",
    "host": f"{SERVICE}.{REGION}.amazonaws.com"})
req = urllib.request.Request(f"https://{SERVICE}.{REGION}.amazonaws.com/", data=body, method="POST", headers=h)
resp = json.load(urllib.request.urlopen(req, timeout=30))
arn = resp["upload"]["arn"]; url = resp["upload"]["url"]
print("UPLOAD_ARN=" + arn)

data = open(path, "rb").read()
r2 = urllib.request.Request(url, data=data, method="PUT")
try:
    print("PUT_RESULT=" + str(urllib.request.urlopen(r2, timeout=120).status))
except urllib.error.HTTPError as e:
    print(f"PUT_RESULT=HTTP {e.code}: {e.read()[:200]!r}")
