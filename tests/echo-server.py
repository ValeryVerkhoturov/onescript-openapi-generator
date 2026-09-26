"""Echo server for the end-to-end test.

Answers every request with a JSON description of what it received, so the
generated client's URL building, query encoding, headers and body
serialization can be asserted from OneScript without a network dependency.
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs, unquote


class Handler(BaseHTTPRequestHandler):
    def _reply(self):
        parsed = urlparse(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length).decode("utf-8") if length else ""

        if parsed.path == "/fail":
            status = 404
        else:
            status = 200

        payload = json.dumps({
            "method": self.command,
            "path": parsed.path,
            "pathDecoded": unquote(parsed.path),
            "rawQuery": parsed.query,
            "query": parse_qs(parsed.query),
            "headers": {k.lower(): v for k, v in self.headers.items()},
            "body": body,
        }, ensure_ascii=False).encode("utf-8")

        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = _reply

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8731
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
