#!/usr/bin/env python3
"""
Common utilities for ADB test automation scripts.
Provides functions to execute ADB commands, check device connection, and capture logs.
"""

import re
import subprocess
import sys
from typing import Optional, Tuple


class Colors:
    """ANSI color codes for terminal output."""
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'


def print_success(message: str) -> None:
    """Print message in green."""
    print(f"{Colors.GREEN}✓ {message}{Colors.RESET}")


def print_error(message: str) -> None:
    """Print message in red."""
    print(f"{Colors.RED}✗ {message}{Colors.RESET}")


def print_warning(message: str) -> None:
    """Print message in yellow."""
    print(f"{Colors.YELLOW}⚠ {message}{Colors.RESET}")


def print_info(message: str) -> None:
    """Print message in blue."""
    print(f"{Colors.BLUE}ℹ {message}{Colors.RESET}")


def print_header(message: str) -> None:
    """Print header message in bold."""
    print(f"\n{Colors.BOLD}{'='*50}")
    print(f"{message}")
    print(f"{'='*50}{Colors.RESET}\n")


def run_adb(command: str, timeout: int = 30) -> Tuple[bool, str]:
    """
    Execute an ADB command and return success status and output.

    Args:
        command: ADB command to execute (without 'adb' prefix if using shell commands)
        timeout: Command timeout in seconds

    Returns:
        Tuple of (success: bool, output: str)
    """
    try:
        result = subprocess.run(
            command,
            shell=True,
            capture_output=True,
            text=True,
            timeout=timeout
        )
        success = result.returncode == 0
        output = result.stdout if success else result.stderr
        return success, output
    except subprocess.TimeoutExpired:
        return False, "Command timed out"
    except FileNotFoundError:
        return False, "ADB command not found. Is Android SDK installed?"
    except Exception as e:
        return False, f"Error executing command: {str(e)}"


def check_adb_connected() -> bool:
    """
    Check if an Android device is connected via ADB.

    Returns:
        True if device is connected, False otherwise
    """
    success, output = run_adb("adb get-state")

    if not success:
        print_error("ADB not responding. Is the Android SDK installed?")
        return False

    if "device" in output.lower() or "device" in output:
        device_info = run_adb("adb shell getprop ro.product.model")
        if device_info[0]:
            print_info(f"Device connected: {device_info[1].strip()}")
        return True
    elif "no device" in output.lower():
        print_error("No Android device connected")
        return False
    else:
        print_warning(f"Device state unclear: {output}")
        return False


def get_logcat(filter_tag: str, lines: int = 50) -> str:
    """
    Capture logcat output filtered by tag.

    Args:
        filter_tag: Tag to filter logcat (e.g., app package name)
        lines: Number of lines to retrieve

    Returns:
        Logcat output as string
    """
    success, output = run_adb(
        f"adb logcat -d -t {lines} | grep -i {filter_tag}",
        timeout=10
    )
    return output if success else ""


_UI_DUMP_PATH = "/sdcard/ui_dump.xml"

# mCurrentFocus=Window{da273a0 u0 <pacote>/<activity>}. Sem a barra nao ha
# componente — a tela de bloqueio aparece como "NotificationShade" sozinho.
_FOCO = re.compile(r"mCurrentFocus=Window\{[^}]*?(\S+)/\S+\}")


def get_focused_package() -> Optional[str]:
    """Pacote da janela em foco, ou None se nenhuma janela de app estiver focada.

    Usa `dumpsys window`, nao `dumpsys window windows`: no Android 16 o
    subcomando `windows` parou de imprimir mCurrentFocus, e quem dependia dele
    passou a reprovar app que estava em foco.
    """
    success, output = run_adb("adb shell dumpsys window | grep mCurrentFocus", timeout=10)
    if not success:
        return None
    encontrado = _FOCO.search(output)
    return encontrado.group(1) if encontrado else None


def get_app_logcat(package_name: str, lines: int = 50) -> str:
    """Logcat do processo do app, filtrado por PID.

    Filtrar por nome nao funciona: o logcat trunca o nome do processo em 15
    caracteres, entao "com.example.ytdown.native" sai como "e.ytdown.native" e
    o grep pelo pacote completo nunca casa.
    """
    success, saida = run_adb(f"adb shell pidof {package_name}", timeout=10)
    if not success:
        return ""
    pids = saida.split()
    if not pids:
        return ""
    success, logs = run_adb(f"adb logcat -d --pid={pids[0]} -t {lines}", timeout=10)
    return logs if success else ""


def dump_ui_hierarchy() -> Optional[str]:
    """XML da arvore de UI na tela, ou None se o dump falhar.

    Le o arquivo no aparelho em vez de usar `adb pull`: o pull imprime
    "1 file pulled...", e quem procurava conteudo nessa saida estava afirmando
    sobre a mensagem do pull, nao sobre a UI.
    """
    success, _ = run_adb(f"adb shell uiautomator dump {_UI_DUMP_PATH}", timeout=20)
    if not success:
        return None
    success, xml = run_adb(f"adb shell cat {_UI_DUMP_PATH}", timeout=15)
    return xml if success else None


def resumir_checks(obrigatorios: dict, informativos: dict = None) -> bool:
    """Imprime o resumo e decide o exit code so pelos checks obrigatorios.

    Existe porque a suite tratava qualquer check falsy como reprovacao. Sinais
    que dependem de preferencia do aparelho ou de timing (lock screen, ter log
    recente) reprovavam app saudavel; agora eles aparecem como INFO e nao
    entram na conta.
    """
    informativos = informativos or {}

    print("\n" + "=" * 40)
    print("SUMMARY:")
    print("=" * 40)

    for nome, passou in obrigatorios.items():
        status = f"{Colors.GREEN}PASS{Colors.RESET}" if passou \
                 else f"{Colors.RED}FAIL{Colors.RESET}"
        print(f"  {nome.replace('_', ' ').capitalize()}: {status}")

    for nome, passou in informativos.items():
        marca = "sim" if passou else "nao"
        print(f"  {nome.replace('_', ' ').capitalize()}: "
              f"{Colors.BLUE}INFO{Colors.RESET} ({marca})")

    # Suite sem check obrigatorio nao provou nada: verde aqui seria vacuo.
    if not obrigatorios:
        print_error("Nenhum check obrigatorio executado")
        return False
    return all(obrigatorios.values())


def check_package_installed(package_name: str) -> bool:
    """
    Check if a package is installed on the device.

    Args:
        package_name: Package name to check (e.g., com.example.ytdown)

    Returns:
        True if installed, False otherwise
    """
    return package_name in _installed_packages()


def _installed_packages() -> list:
    """Lista os pacotes instalados, sem o prefixo 'package:'."""
    success, output = run_adb("adb shell pm list packages")
    if not success:
        return []
    return [linha.strip().removeprefix("package:")
            for linha in output.splitlines() if linha.strip()]


def resolve_package(base_package: str) -> Optional[str]:
    """Descobre qual variante do app esta instalada.

    O build debug usa applicationIdSuffix ".native", entao o pacote no aparelho
    e "<base>.native". Preferimos o debug por ser o que se testa no dia a dia.

    Returns:
        Nome do pacote instalado, ou None se nenhuma variante estiver presente.
    """
    instalados = _installed_packages()
    for candidato in (f"{base_package}.native", base_package):
        if candidato in instalados:
            return candidato
    return None


def check_app_running(package_name: str) -> bool:
    """
    Check if an app is currently running.

    Args:
        package_name: Package name to check

    Returns:
        True if running, False otherwise
    """
    success, output = run_adb(f"adb shell ps -A | grep {package_name}")
    return success and package_name in output


def start_app_activity(package_name: str, activity: str = "MainActivity") -> bool:
    """
    Start an app via ADB activity manager.

    Args:
        package_name: Package name
        activity: Activity name (default: MainActivity)

    Returns:
        True if successful, False otherwise
    """
    # Nome com ponto e absoluto; sem ponto, o am resolve relativo ao pacote.
    # Necessario porque o build debug tem applicationIdSuffix ".native": o pacote
    # vira com.example.ytdown.native, mas a Activity segue em com.example.ytdown.
    componente = activity if "." in activity else f".{activity}"
    success, output = run_adb(
        f"adb shell am start -n {package_name}/{componente}",
        timeout=15
    )
    # O am start sai com codigo 0 mesmo quando a Activity nao existe.
    return success and "Error" not in output


def get_package_info(package_name: str) -> Optional[dict]:
    """
    Get package information including version.

    Args:
        package_name: Package name

    Returns:
        Dictionary with package info or None if failed
    """
    success, output = run_adb(
        f"adb shell dumpsys package {package_name}",
        timeout=10
    )

    if not success:
        return None

    info = {}
    for line in output.split('\n'):
        if 'versionName' in line:
            info['versionName'] = line.split('=')[-1].strip()
        if 'versionCode' in line:
            info['versionCode'] = line.split('=')[-1].strip()

    return info if info else None


def clear_logcat() -> bool:
    """Clear logcat buffer."""
    success, _ = run_adb("adb logcat -c", timeout=5)
    return success


def get_device_properties() -> dict:
    """Get device properties."""
    properties = {}

    props = [
        ("model", "ro.product.model"),
        ("manufacturer", "ro.product.manufacturer"),
        ("android_version", "ro.build.version.release"),
        ("sdk_version", "ro.build.version.sdk")
    ]

    for key, prop in props:
        success, output = run_adb(f"adb shell getprop {prop}")
        if success:
            properties[key] = output.strip()

    return properties