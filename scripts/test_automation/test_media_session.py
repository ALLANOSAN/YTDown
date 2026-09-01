#!/usr/bin/env python3
"""
Test MediaSession - Verifies MediaSession status, notifications, and lock screen visibility.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import common


PACKAGE_NAME = common.resolve_package("com.example.ytdown") or "com.example.ytdown"
APP_DISPLAY_NAME = "YTDown"


def check_media_session() -> bool:
    """Check if MediaSession is active."""
    common.print_info("Checking MediaSession status...")

    success, output = common.run_adb(
        "adb shell dumpsys media_session",
        timeout=15
    )

    if success and output:
        if PACKAGE_NAME in output:
            common.print_success(f"MediaSession active for {APP_DISPLAY_NAME}")
            return True
        else:
            common.print_warning(f"No MediaSession found for {APP_DISPLAY_NAME}")
            return False
    else:
        common.print_warning("Could not retrieve MediaSession info")
        return False


def check_notifications() -> bool:
    """Check app notifications."""
    common.print_info(f"Checking notifications for {APP_DISPLAY_NAME}...")

    success, output = common.run_adb(
        f"adb shell dumpsys notification | grep -i {PACKAGE_NAME}",
        timeout=15
    )

    if success and output and PACKAGE_NAME.lower() in output.lower():
        common.print_success(f"Notifications found for {APP_DISPLAY_NAME}")
        return True
    else:
        common.print_warning(f"No notifications from {APP_DISPLAY_NAME}")
        return False


def check_notification_channels() -> bool:
    """Check notification channels."""
    common.print_info("Checking notification channels...")

    success, output = common.run_adb(
        f"adb shell dumpsys notification --dump {PACKAGE_NAME}",
        timeout=15
    )

    if success and output:
        common.print_success("Notification channels accessible")
        return True
    else:
        common.print_warning("Could not access notification channels")
        return False


def check_media_playback_state() -> bool:
    """Check media playback state in MediaSession."""
    common.print_info("Checking media playback state...")

    success, output = common.run_adb(
        "adb shell dumpsys media_session | grep -A 20 'Sessions'",
        timeout=15
    )

    if success and output:
        common.print_success("Media playback state retrieved")
        return True
    else:
        common.print_warning("Could not retrieve media playback state")
        return False


def check_lock_screen_media() -> bool:
    """Check lock screen media visibility."""
    common.print_info("Checking lock screen visibility...")

    success, output = common.run_adb(
        "adb shell settings get secure lock_screen_show_media",
        timeout=10
    )

    # Preferencia do usuario, nao saude do app: sai "null" quando nunca foi
    # tocada. Informativo — nao entra no exit code.
    if success and output:
        show_media = output.strip()
        common.print_info(f"Lock screen media visibility: {show_media}")
        return show_media in ["1", "true"]

    common.print_info("Preferencia de lock screen nao definida no aparelho")
    return False


def run_checks() -> bool:
    """Run all MediaSession tests."""
    common.print_header(f"{APP_DISPLAY_NAME} MediaSession Test")

    if not common.check_adb_connected():
        return False

    obrigatorios = {
        "media_session": check_media_session(),
        "notifications": check_notifications(),
        "notification_channels": check_notification_channels(),
        "media_playback": check_media_playback_state(),
    }
    # Depende de config do aparelho, nao do app.
    informativos = {"lock_screen": check_lock_screen_media()}

    return common.resumir_checks(obrigatorios, informativos)


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