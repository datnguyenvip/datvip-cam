"""Read-only App Store Connect build status. Credentials stay in the runner environment."""
import argparse
import base64
import json
import os
from pathlib import Path
import time
import urllib.parse
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils


def encoded(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=")


def get(path, params):
    now = int(time.time())
    header = encoded(json.dumps({"alg": "ES256", "kid": os.environ["ASC_KEY_ID"], "typ": "JWT"}).encode())
    payload = encoded(json.dumps({"iss": os.environ["ASC_ISSUER_ID"], "iat": now - 5, "exp": now + 120, "aud": "appstoreconnect-v1"}).encode())
    message = header + b"." + payload
    key = serialization.load_pem_private_key(os.environ["ASC_PRIVATE_KEY"].encode(), password=None)
    r, s = utils.decode_dss_signature(key.sign(message, ec.ECDSA(hashes.SHA256())))
    token = (message + b"." + encoded(r.to_bytes(32, "big") + s.to_bytes(32, "big"))).decode()
    request = urllib.request.Request("https://api.appstoreconnect.apple.com/v1/" + path + "?" + urllib.parse.urlencode(params), headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def status():
    apps = get("apps", {"filter[bundleId]": "com.datvip.cam"})["data"]
    if len(apps) != 1:
        raise RuntimeError("Expected exactly one Camera HT app in App Store Connect")
    app = apps[0]
    data = get("builds", {"filter[app]": app["id"], "filter[version]": "172", "include": "preReleaseVersion", "limit": "100"})
    versions = {item["id"]: item["attributes"]["version"] for item in data.get("included", []) if item["type"] == "preReleaseVersions"}
    builds = [item for item in data["data"] if versions.get(item["relationships"]["preReleaseVersion"]["data"]["id"]) == "1.2.0"]
    if len(builds) > 1:
        raise RuntimeError("Ambiguous Camera HT 1.2.0 (172) build")
    build = builds[0] if builds else None
    return {"app_id": app["id"], "app_name": app["attributes"]["name"], "bundle_id": "com.datvip.cam", "version": "1.2.0", "build_number": "172", "build": {"id": build["id"], **build["attributes"]} if build else None}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--wait", action="store_true")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    deadline = time.monotonic() + 30 * 60
    while True:
        result = status()
        Path(args.output).write_text(json.dumps(result, indent=2), encoding="utf-8")
        state = result["build"]["processingState"] if result["build"] else "NOT_VISIBLE"
        print("Camera HT 1.2.0 (172): " + state, flush=True)
        if state in ("FAILED", "INVALID"):
            raise SystemExit("Apple rejected build processing; see status artifact")
        if not args.wait or state == "VALID":
            break
        if time.monotonic() >= deadline:
            raise SystemExit("Apple processing is still pending; see status artifact")
        time.sleep(30)
