#!/usr/bin/env python3
"""Keep the provisional public URL synchronized after a tunnel restart."""
import os
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parent
release = root / "releases" / "20260908"
process = subprocess.Popen(
    [str(root / "cloudflared"), "tunnel", "--no-autoupdate", "--protocol", "http2",
     "--url", "http://127.0.0.1:8088"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1,
)
configured = False
try:
    for line in process.stdout:
        print(line, end="", flush=True)
        match = re.search(r"https://[a-z0-9-]+\.trycloudflare\.com\b", line)
        if match and not configured:
            url = match.group(0)
            env_file = release / ".env"
            previous = env_file.read_text()
            updated, count = re.subn(r"(?m)^PUBLIC_BASE_URL=.*$", "PUBLIC_BASE_URL=" + url, previous)
            if count != 1:
                raise RuntimeError("Expected exactly one PUBLIC_BASE_URL in .env")
            temporary = release / ".env.next"
            temporary.write_text(updated)
            temporary.chmod(0o600)
            os.replace(temporary, env_file)
            subprocess.run(["docker", "compose", "up", "-d", "--no-deps", "api"],
                           cwd=release, check=True)
            (root / "public-url.txt").write_text(url + "\n")
            configured = True
    sys.exit(process.wait() or 1)
finally:
    if process.poll() is None:
        process.terminate()
        process.wait(timeout=15)
