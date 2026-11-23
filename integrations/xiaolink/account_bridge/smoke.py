"""Opt-in real account/image smoke. Use a synthetic image, never a private photo.

python3 -m account_bridge.smoke --image /path/to/synthetic.jpg --output /private/evidence.json
No persistent server, credential reads, token output, or phone/glasses commands.
"""
import argparse
import base64
import http.client
import json
from pathlib import Path
import secrets
import threading
import time

from .server import BridgeConfig, create_server


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--image', required=True)
    args.add_argument('--output', required=True)
    options = args.parse_args()
    token = secrets.token_urlsafe(32)
    server = create_server(BridgeConfig(bridge_token=token, host='127.0.0.1', port=0, timeout_seconds=120))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    port = server.server_address[1]
    def request(method, path, payload=None, authenticated=True):
        connection = http.client.HTTPConnection('127.0.0.1', port, timeout=130)
        body = json.dumps(payload).encode() if payload is not None else None
        headers = {'Content-Type': 'application/json'}
        if authenticated:
            headers['Authorization'] = 'Bearer ' + token
        try:
            connection.request(method, path, body=body, headers=headers)
            response = connection.getresponse()
            return response.status, json.loads(response.read())
        finally:
            connection.close()
    try:
        health, _ = request('GET', '/health', authenticated=False)
        denied, _ = request('GET', '/v1/status', authenticated=False)
        status, providers = request('GET', '/v1/status')
        began = time.monotonic()
        vision_status, vision = request('POST', '/v1/ask', {
            'provider': 'gemini',
            'question': 'Describe the top and bottom colors of the attached image and read its printed text. Be concise.',
            'image_base64': base64.b64encode(Path(options.image).read_bytes()).decode('ascii'),
        })
        evidence = {'health_http': health, 'unauthenticated_status_http': denied,
                    'authenticated_status_http': status, 'providers': providers,
                    'vision_http': vision_status, 'vision_response': vision,
                    'vision_seconds': round(time.monotonic() - began, 2),
                    'synthetic_image_only': True, 'glasses_camera_verified': False,
                    'server_closed_after_test': True}
        Path(options.output).write_text(json.dumps(evidence, indent=2, ensure_ascii=False), encoding='utf8')
        print(json.dumps(evidence, indent=2, ensure_ascii=False))
        if (health, denied, status, vision_status) != (200, 401, 200, 200):
            raise SystemExit(1)
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


if __name__ == '__main__':
    main()
