#!/usr/bin/env python3
"""
Main test runner - Executes all YTDown app automation tests.
"""

import sys
import subprocess
import time
from pathlib import Path


SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

import common


PACKAGE_NAME = common.resolve_package("com.example.ytdown") or "com.example.ytdown"
APP_DISPLAY_NAME = "YTDown"


def print_banner():
    """Print test suite banner."""
    banner = f"""
{common.Colors.BOLD}{common.Colors.BLUE}
╔══════════════════════════════════════════════════════════╗
║                                                          ║
║     YTDown Android App Test Suite                       ║
║     Automated Testing via ADB                           ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
{common.Colors.RESET}
    """
    print(banner)


def garantir_app_aberto() -> None:
    """Abre o app antes das checagens.

    check_app_state.py falha se o app nao estiver rodando, e era o primeiro da fila:
    a suite reprovava por ordem de execucao, nao por defeito do app.
    """
    if common.check_app_running(PACKAGE_NAME):
        return
    common.print_info(f"Abrindo {APP_DISPLAY_NAME} antes das checagens...")
    common.start_app_activity(PACKAGE_NAME, "com.example.ytdown.MainActivity")
    time.sleep(6)


def run_test_script(script_name: str) -> bool:
    """Run a single test script and return success status."""
    script_path = SCRIPT_DIR / script_name

    print(f"\n{common.Colors.BLUE}─── Running: {script_name} ───{common.Colors.RESET}\n")

    try:
        result = subprocess.run(
            [sys.executable, str(script_path)],
            cwd=str(SCRIPT_DIR)
        )
        return result.returncode == 0
    except Exception as e:
        common.print_error(f"Failed to run {script_name}: {e}")
        return False


def main():
    """Run all test suites."""
    print_banner()
    garantir_app_aberto()

    if not common.check_adb_connected():
        common.print_error("No Android device connected. Exiting.")
        sys.exit(1)

    device_props = common.get_device_properties()
    print(f"\n{common.Colors.BLUE}Device: {device_props.get('model', 'Unknown')}")
    print(f"Android: {device_props.get('android_version', 'Unknown')} (SDK {device_props.get('sdk_version', 'Unknown')})")
    print(f"Testing: {APP_DISPLAY_NAME} ({PACKAGE_NAME}){common.Colors.RESET}\n")

    tests = [
        ("check_app_state.py", "App State Check"),
        ("test_media_session.py", "MediaSession Test"),
        ("test_download_flow.py", "Download Flow Test")
    ]

    results = {}

    for script, description in tests:
        common.print_header(description)
        success = run_test_script(script)
        results[description] = success

        if success:
            common.print_success(f"{description} - PASSED")
        else:
            common.print_error(f"{description} - FAILED")

    print("\n" + "=" * 60)
    print(f"{common.Colors.BOLD}FINAL RESULTS{common.Colors.RESET}")
    print("=" * 60)

    passed_count = sum(1 for v in results.values() if v)
    total_count = len(results)

    for test_name, passed in results.items():
        status = f"{common.Colors.GREEN}✓ PASS{common.Colors.RESET}" if passed \
                 else f"{common.Colors.RED}✗ FAIL{common.Colors.RESET}"
        print(f"  {test_name}: {status}")

    print(f"\nTotal: {passed_count}/{total_count} tests passed")

    if passed_count == total_count:
        print(f"\n{common.Colors.GREEN}{common.Colors.BOLD}All tests passed! ✓{common.Colors.RESET}")
        sys.exit(0)
    else:
        print(f"\n{common.Colors.RED}{common.Colors.BOLD}Some tests failed! ✗{common.Colors.RESET}")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nTests interrupted by user")
        sys.exit(1)
    except Exception as e:
        common.print_error(f"Unexpected error: {e}")
        sys.exit(1)