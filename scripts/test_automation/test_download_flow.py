#!/usr/bin/env python3
"""
Test Download Flow - Simulates download test by opening app and checking logs.
Note: Does NOT perform actual downloads, only checks app state and logs.
"""

import sys
from pathlib import Path
import time

sys.path.insert(0, str(Path(__file__).parent))

import common


PACKAGE_NAME = common.resolve_package("com.example.ytdown") or "com.example.ytdown"
APP_DISPLAY_NAME = "YTDown"
MAIN_ACTIVITY = "MainActivity"


def open_app() -> bool:
    """Open the app via deep link or activity."""
    common.print_info(f"Opening {APP_DISPLAY_NAME}...")

    success, output = common.run_adb(
        f"adb shell am start -n {PACKAGE_NAME}/com.example.ytdown.{MAIN_ACTIVITY}",
        timeout=15
    )

    if success:
        common.print_success(f"{APP_DISPLAY_NAME} opened successfully")
        time.sleep(2)
        return True
    else:
        common.print_error(f"Failed to open {APP_DISPLAY_NAME}")
        return False


def check_app_ui_visible() -> bool:
    """O app e a janela em foco?"""
    common.print_info("Checking if app UI is visible...")

    focado = common.get_focused_package()
    if focado == PACKAGE_NAME:
        common.print_success("App UI is visible")
        return True

    common.print_error(f"Janela em foco: {focado or 'nenhuma'} (esperado {PACKAGE_NAME})")
    return False


def check_app_activities() -> bool:
    """Check app activity stack."""
    common.print_info("Checking app activity stack...")

    success, output = common.run_adb(
        f"adb shell dumpsys activity activities | grep {PACKAGE_NAME}",
        timeout=15
    )

    if success and output:
        common.print_success("Activity stack retrieved")
        return True
    else:
        common.print_warning("Could not retrieve activity stack")
        return False


def get_recent_logs() -> bool:
    """Get recent app logs."""
    common.print_info(f"Retrieving recent logs for {APP_DISPLAY_NAME}...")

    output = common.get_app_logcat(PACKAGE_NAME, lines=50)

    if output:
        common.print_success(f"Retrieved {len(output.splitlines())} log lines")
        print(f"\n--- Recent {APP_DISPLAY_NAME} logs ---")
        print(output[:1000] + "..." if len(output) > 1000 else output)
        print("--- End of logs ---\n")
        return True

    # App parado ou so ocioso nao emite log. Nao e defeito.
    common.print_info("Sem log recente (app ocioso)")
    return False


def check_service_status() -> bool:
    """Check app services status."""
    common.print_info("Checking app services...")

    success, output = common.run_adb(
        f"adb shell dumpsys activity service {PACKAGE_NAME}",
        timeout=15
    )

    if success and output:
        common.print_success("Services check completed")
        return True
    else:
        common.print_warning("Could not check services")
        return False


def check_download_manager() -> bool:
    """Check if download-related components are accessible."""
    common.print_info("Checking download manager status...")

    success, output = common.run_adb(
        "adb shell dumpsys activity service DownloadService",
        timeout=10
    )

    if success and output:
        common.print_success("DownloadService found")
        return True
    else:
        common.print_warning("DownloadService not accessible or not started")
        return False


def simulate_url_input_check() -> bool:
    """A arvore de UI na tela pertence ao app?"""
    common.print_info("Checking UI input accessibility...")

    xml = common.dump_ui_hierarchy()
    if xml and PACKAGE_NAME in xml:
        common.print_success("UI elements accessible")
        return True

    common.print_error("Nao foi possivel ler a UI do app")
    return False


def run_checks() -> bool:
    """Run all download flow tests."""
    common.print_header(f"{APP_DISPLAY_NAME} Download Flow Test")

    if not common.check_adb_connected():
        return False

    common.print_info("Clearing previous logs...")
    common.clear_logcat()

    obrigatorios = {
        "open_app": open_app(),
        "ui_visible": check_app_ui_visible(),
        "activities": check_app_activities(),
        "services": check_service_status(),
        "ui_input": simulate_url_input_check(),
    }
    # Informativos: dependem de estado do aparelho, nao da saude do app.
    informativos = {
        "download_manager": check_download_manager(),
        "logs": get_recent_logs(),
    }

    resultado = common.resumir_checks(obrigatorios, informativos)
    print(f"\n{common.Colors.BLUE}Note: This test only verifies app state, NOT actual downloads{common.Colors.RESET}")
    return resultado


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