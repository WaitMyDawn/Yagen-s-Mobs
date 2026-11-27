"""Serve the Collector pool editor against this checkout using Python's standard library."""
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import secrets
from tempfile import NamedTemporaryFile
from time import monotonic
import webbrowser

HTML = Path(__file__).with_name("collector-pools.html")
POOL_DIR = HTML.resolve().parents[1] / "platforms/neoforge-1.21.1/src/main/resources/data/yagens_mobs/tags/item/collector"
POOL_FILES = {"requests.json", "rewards.json"}


def digest(data):
    return '"' + sha256(data).hexdigest() + '"'


class Editor(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def route(self):
        if self.headers.get("Host") != self.server.host or not self.path.startswith(self.server.prefix):
            return None
        return self.path[len(self.server.prefix):]

    def reply(self, data, kind, etag=None):
        self.send_response(200)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Referrer-Policy", "no-referrer")
        if etag:
            self.send_header("ETag", etag)
        self.end_headers()
        self.wfile.write(data)
        self.server.active = monotonic()

    def do_GET(self):
        name = self.route()
        if name == "collector-pools.html":
            data = HTML.read_bytes().replace(b'<html lang="zh-CN">', b'<html lang="zh-CN" data-project-editor>', 1)
            self.reply(data, "text/html; charset=utf-8")
        elif name in POOL_FILES:
            data = (POOL_DIR / name).read_bytes()
            self.reply(data, "application/json", digest(data))
        elif name == "session":
            self.reply(b"", "text/plain")
        else:
            self.send_error(404)

    def do_PUT(self):
        name = self.route()
        if name not in POOL_FILES or self.headers.get("Origin") != "http://" + self.server.host:
            self.send_error(403)
            return
        try:
            size = int(self.headers["Content-Length"])
            if not 0 < size <= 8 * 1024 * 1024:
                raise ValueError()
            data = self.rfile.read(size)
            doc = json.loads(data)
            if not isinstance(doc, dict) or not isinstance(doc.get("values"), list):
                raise ValueError()
        except (TypeError, ValueError):
            self.send_error(400)
            return
        path = POOL_DIR / name
        tmp = None
        try:
            with NamedTemporaryFile(dir=POOL_DIR, delete=False) as out:
                tmp = Path(out.name)
                out.write(data)
            if self.headers.get("If-Match") != digest(path.read_bytes()):
                self.send_error(412)
                return
            os.replace(tmp, path)
            self.reply(b"", "text/plain")
        except OSError:
            self.send_error(500)
        finally:
            if tmp is not None:
                tmp.unlink(missing_ok=True)


def main():
    for name in POOL_FILES:
        if not (POOL_DIR / name).is_file():
            raise SystemExit(f"Missing project file: {POOL_DIR / name}")
    with HTTPServer(("127.0.0.1", 0), Editor) as server:
        server.host = f"127.0.0.1:{server.server_port}"
        server.prefix = "/" + secrets.token_urlsafe(24) + "/"
        server.timeout = 2
        server.active = monotonic()
        webbrowser.open(f"http://{server.host}{server.prefix}collector-pools.html")
        print(f"Collector editor: {POOL_DIR}\nCtrl+C to close.")
        try:
            while monotonic() - server.active < 300:
                server.handle_request()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()

