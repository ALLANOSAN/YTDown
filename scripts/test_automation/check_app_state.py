#!/usr/bin/env python3
"""
Check app state - Verifies installation, running status, and version of YTDown app.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import common


PACKAGE_NAME = common.resolve_package("com.example.ytdown") or "com.example.ytdown"
APP_DISPLAY_NAME = "YTDown"


def check_app_installed() -> bool:
    """Check if YTDown app is installed."""
    common.print_info(f"Checking if {APP_DISPLAY_NAME} is installed...")

    success, output = common.run_adb(
        f"adb shell pm list packages | grep {PACKAGE_NAME}"
    )

    if success and PACKAGE_NAME in output:
        common.print_success(f"{APP_DISPLAY_NAME} is installed")
        return True
    else:
        common.print_error(f"{APP_DISPLAY_NAME} is NOT installed")
        return False


def check_app_running() -> bool:
    """Check if YTDown app is currently running."""
    common.print_info(f"Checking if {APP_DISPLAY_NAME} is running...")

    success, output = common.run_adb(
        f"adb shell ps -A | grep {PACKAGE_NAME}"
    )

    if success and PACKAGE_NAME in output:
        common.print_success(f"{APP_DISPLAY_NAME} is running")
        return True
    else:
        common.print_warning(f"{APP_DISPLAY_NAME} is NOT running")
        return False


def check_app_version() -> bool:
    """Check YTDown app version."""
    common.print_info(f"Checking {APP_DISPLAY_NAME} version...")

    success, output = common.run_adb(
        f"adb shell dumpsys package {PACKAGE_NAME} | grep versionName"
    )

    if success and output:
        version_line = output.strip()
        version = version_line.split('=')[-1].strip() if '=' in version_line else "Unknown"
        common.print_success(f"Version: {version}")
        return True
    else:
        common.print_warning("Could not retrieve version info")
        return False


def check_app_permissions() -> bool:
    """Check app permissions."""
    common.print_info(f"Checking {APP_DISPLAY_NAME} permissions...")

    success, output = common.run_adb(
        f"adb shell dumpsys package {PACKAGE_NAME} | grep -A 50 'granted=true'"
    )

    if success and output:
        common.print_success("Permissions check completed")
        return True
    else:
        common.print_warning("Could not retrieve permissions")
        return False


def run_checks() -> bool:
    """Run all app state checks."""
    common.print_header(f"{APP_DISPLAY_NAME} State Check")

    if not common.check_adb_connected():
        return False

    results = {
        "installed": check_app_installed(),
        "running": check_app_running(),
        "version": check_app_version(),
        "permissions": check_app_permissions()
    }

    print("\n" + "=" * 40)
    print("SUMMARY:")
    print("=" * 40)

    all_passed = True
    for check, passed in results.items():
        status = f"{common.Colors.GREEN}PASS{common.Colors.RESET}" if passed \
                 else f"{common.Colors.RED}FAIL{common.Colors.RESET}"
        print(f"  {check.capitalize()}: {status}")
        if not passed:
            all_passed = False

    return all_passed


if __name__ == "__main__":
    try:
        success = run_checks()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
        sys.exit(1)
    except Exception as e:
        common.print_error(f"Unexpected error: {e}")
        sys.exit(1)