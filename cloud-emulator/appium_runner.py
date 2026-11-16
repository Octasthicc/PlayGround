#!/usr/bin/env python3
"""Small, foreground-only Appium helper for a privately tunneled Android emulator.

This intentionally offers inspection and one-at-a-time taps, not unattended gameplay.
"""

from __future__ import annotations

import argparse
import sys
import urllib.error
import urllib.request
from pathlib import Path


DEFAULT_SERVER = "http://127.0.0.1:4723/wd/hub"
DEFAULT_PACKAGE = "com.google.android.apps.nexuslauncher"
DEFAULT_ACTIVITY = ".NexusLauncherActivity"


def server_status(url: str) -> int:
    status_url = url.rstrip("/") + "/status"
    try:
        with urllib.request.urlopen(status_url, timeout=5) as response:
            print(response.read().decode("utf-8", errors="replace"))
        return 0
    except (OSError, urllib.error.URLError) as exc:
        print(f"Cannot reach Appium at {status_url}: {exc}", file=sys.stderr)
        return 1


def create_session(server: str, package: str, activity: str):
    try:
        from appium import webdriver
        from appium.options.android import UiAutomator2Options
    except ImportError as exc:
        raise RuntimeError(
            "Install Appium-Python-Client: python3 -m pip install Appium-Python-Client"
        ) from exc

    caps = {
        "platformName": "Android",
        "appium:automationName": "UiAutomator2",
        "appium:deviceName": "emulator-5554",
        "appium:udid": "emulator-5554",
        "appium:appPackage": package,
        "appium:appActivity": activity,
        "appium:noReset": True,
        "appium:newCommandTimeout": 60,
    }
    options = UiAutomator2Options().load_capabilities(caps)
    return webdriver.Remote(server, options=options)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", default=DEFAULT_SERVER, help="Private Appium URL")
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help="App package to open")
    parser.add_argument("--activity", default=DEFAULT_ACTIVITY, help="App activity to open")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status", help="Check the Appium server without opening an app")
    commands.add_parser("inspect", help="Print the current app's accessible UI hierarchy")
    screenshot = commands.add_parser("screenshot", help="Save a screenshot of the current app")
    screenshot.add_argument("output", type=Path)
    tap = commands.add_parser("tap", help="Tap one screen coordinate (explicit action)")
    tap.add_argument("x", type=int)
    tap.add_argument("y", type=int)
    tap.add_argument("--confirm-tap", action="store_true", help="Required acknowledgement")
    args = parser.parse_args()

    if args.command == "status":
        return server_status(args.server)
    if args.command == "tap" and not args.confirm_tap:
        parser.error("tap requires --confirm-tap; review a screenshot before acting")

    driver = None
    try:
        driver = create_session(args.server, args.package, args.activity)
        if args.command == "inspect":
            print(driver.page_source)
        elif args.command == "screenshot":
            args.output.parent.mkdir(parents=True, exist_ok=True)
            if not driver.save_screenshot(str(args.output)):
                raise RuntimeError("Appium did not save the screenshot")
            print(f"Screenshot saved: {args.output}")
        elif args.command == "tap":
            size = driver.get_window_size()
            if args.x < 0 or args.y < 0 or args.x >= size["width"] or args.y >= size["height"]:
                raise ValueError(f"Coordinates must be within {size['width']}x{size['height']}")
            driver.execute_script("mobile: clickGesture", {"x": args.x, "y": args.y})
            print(f"Tapped ({args.x}, {args.y}) on {args.package}")
        return 0
    except Exception as exc:
        print(f"Appium operation failed: {exc}", file=sys.stderr)
        return 1
    finally:
        if driver is not None:
            driver.quit()


if __name__ == "__main__":
    raise SystemExit(main())
