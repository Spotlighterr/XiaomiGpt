"""Module entrypoint for `python3 -m account_bridge`."""

import sys

from .server import main

if __name__ == "__main__":
    raise SystemExit(main())
