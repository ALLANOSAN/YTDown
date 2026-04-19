import json
import os
import re
import shutil
import sys
import tarfile
import tempfile
import zipfile
import hashlib
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from helpers import (
    _download_url_bytes,
    _failure_payload,
    _is_retryable_network_error,
    _parse_iso_datetime,
)

_RUNTIME_ROOT_DIR = "runtime_packages"
_UPDATE_META_FILENAME = "yt_dlp_update_meta.json"
_YT_DLP_MODULE = None
_DEFAULT_CHECK_CACHE_HOURS = 6
_DEFAULT_NETWORK_ATTEMPTS = 3
_DEFAULT_NETWORK_BACKOFF_SECONDS = 0.6


def _runtime_root(app_files_dir):
    return os.path.join(app_files_dir, _RUNTIME_ROOT_DIR)


def _runtime_yt_dlp_dir(app_files_dir):
    return os.path.join(_runtime_root(app_files_dir), "yt_dlp")


def _update_meta_path(app_files_dir):
    return os.path.join(_runtime_root(app_files_dir), _UPDATE_META_FILENAME)


def _ensure_runtime_root(app_files_dir):
    os.makedirs(_runtime_root(app_files_dir), exist_ok=True)


def _read_update_meta(app_files_dir):
    path = _update_meta_path(app_files_dir)
    if not os.path.exists(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _write_update_meta(app_files_dir, data):
    _ensure_runtime_root(app_files_dir)
    path = _update_meta_path(app_files_dir)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)


def _activate_runtime_path(app_files_dir):
    if not app_files_dir:
        return

    runtime_root = _runtime_root(app_files_dir)
    runtime_pkg = _runtime_yt_dlp_dir(app_files_dir)
    if os.path.isdir(runtime_pkg) and runtime_root not in sys.path:
        sys.path.insert(0, runtime_root)


def _get_yt_dlp_module(app_files_dir=None):
    global _YT_DLP_MODULE

    if app_files_dir:
        _activate_runtime_path(app_files_dir)

    if _YT_DLP_MODULE is not None:
        return _YT_DLP_MODULE

    import yt_dlp

    _YT_DLP_MODULE = yt_dlp
    return _YT_DLP_MODULE


def _get_current_yt_dlp_version(app_files_dir=None):
    yt_dlp_module = _get_yt_dlp_module(app_files_dir)
    version = getattr(getattr(yt_dlp_module, "version", None), "__version__", None)
    if version:
        return str(version)
    return str(getattr(yt_dlp_module, "__version__", "unknown"))


def _version_tuple(version):
    numbers = re.findall(r"\d+", str(version))
    if not numbers:
        return (0,)
    return tuple(int(part) for part in numbers[:4])


def _is_newer(latest, current):
    return _version_tuple(latest) > _version_tuple(current)


def _fetch_latest_yt_dlp_info():
    payload_bytes = _download_url_bytes(
        "https://pypi.org/pypi/yt-dlp/json",
        timeout=8,
        attempts=3,
        backoff_seconds=0.7,
        label="pypi_metadata",
    )
    payload = json.loads(payload_bytes.decode("utf-8"))

    latest_version = payload.get("info", {}).get("version")
    if not latest_version:
        raise Exception("Não foi possível obter a versão mais recente do yt-dlp")

    release_files = payload.get("releases", {}).get(latest_version, [])
    wheel_url = None
    wheel_sha256 = None

    for file_info in release_files:
        filename = file_info.get("filename", "")
        if file_info.get("packagetype") == "bdist_wheel" and "py3-none-any" in filename:
            wheel_url = file_info.get("url")
            wheel_sha256 = file_info.get("digests", {}).get("sha256")
            break

    if not wheel_url:
        for file_info in release_files:
            if file_info.get("packagetype") == "sdist":
                wheel_url = file_info.get("url")
                wheel_sha256 = file_info.get("digests", {}).get("sha256")
                break

    if not wheel_url:
        raise Exception("Arquivo de atualização do yt-dlp não encontrado no PyPI")
    if not wheel_sha256:
        raise Exception("Hash SHA256 do pacote yt-dlp indisponível no PyPI")

    return {
        "latest_version": latest_version,
        "package_url": wheel_url,
        "package_sha256": wheel_sha256,
    }


def _extract_runtime_package_archive(
    package_url,
    temp_package_path,
    extract_dir,
    is_safe_path,
):
    if package_url.endswith(".whl") or package_url.endswith(".zip"):
        with zipfile.ZipFile(temp_package_path, "r") as zf:
            for member in zf.namelist():
                if not is_safe_path(extract_dir, member):
                    raise Exception(
                        f"Caminho inseguro detectado na extração (Zip Slip): {member}"
                    )
            zf.extractall(extract_dir)
        return

    if package_url.endswith(".tar.gz") or package_url.endswith(".tgz"):
        with tarfile.open(temp_package_path, "r:gz") as tf:
            for member in tf.getmembers():
                if not is_safe_path(extract_dir, member.name):
                    raise Exception(
                        "Caminho inseguro detectado na extração "
                        f"(Zip Slip): {member.name}"
                    )
            tf.extractall(extract_dir)
        return

    raise Exception("Formato de pacote não suportado para atualização runtime")


def _install_yt_dlp_package(app_files_dir, package_url, expected_sha256=None):
    _ensure_runtime_root(app_files_dir)
    runtime_pkg = _runtime_yt_dlp_dir(app_files_dir)

    fd, temp_package_path = tempfile.mkstemp(suffix=".pkg")
    os.close(fd)
    extract_dir = tempfile.mkdtemp(prefix="yt_dlp_extract_")

    def _is_safe_path(base, target):
        resolved = os.path.abspath(os.path.join(base, target))
        return resolved.startswith(os.path.abspath(base))

    try:
        package_bytes = _download_url_bytes(
            package_url,
            timeout=30,
            attempts=3,
            backoff_seconds=1.0,
            label="yt_dlp_package",
        )
        checksum = hashlib.sha256(package_bytes)

        with open(temp_package_path, "wb") as out:
            out.write(package_bytes)

        if expected_sha256:
            actual_sha256 = checksum.hexdigest()
            if actual_sha256.lower() != str(expected_sha256).lower():
                raise Exception(
                    "Falha na verificação de integridade do pacote yt-dlp "
                    f"(esperado={expected_sha256}, atual={actual_sha256})"
                )

        _extract_runtime_package_archive(
            package_url,
            temp_package_path,
            extract_dir,
            _is_safe_path,
        )

        source_pkg_dir = None
        for root, dirs, files in os.walk(extract_dir):
            if os.path.basename(root) == "yt_dlp" and "__init__.py" in files:
                source_pkg_dir = root
                break

        if not source_pkg_dir:
            raise Exception(
                "Pacote de atualização inválido: diretório yt_dlp não encontrado"
            )

        if os.path.isdir(runtime_pkg):
            shutil.rmtree(runtime_pkg)
        shutil.copytree(source_pkg_dir, runtime_pkg)

    finally:
        if os.path.exists(temp_package_path):
            os.remove(temp_package_path)
        if os.path.isdir(extract_dir):
            shutil.rmtree(extract_dir)


def check_yt_dlp_update(
    app_files_dir, force_remote=False, cache_hours=_DEFAULT_CHECK_CACHE_HOURS
):
    try:
        meta = _read_update_meta(app_files_dir)

        if (
            not force_remote
            and _parse_iso_datetime(meta.get("last_check_at")) is not None
        ):
            elapsed = datetime.now(timezone.utc) - _parse_iso_datetime(
                meta.get("last_check_at")
            ).astimezone(timezone.utc)
            if elapsed.total_seconds() < cache_hours * 3600:
                return json.dumps(
                    {
                        "success": True,
                        "current_version": meta.get("current_version", "unknown"),
                        "latest_version": meta.get("latest_version", "unknown"),
                        "update_available": bool(meta.get("update_available", False)),
                        "cached": True,
                    }
                )

        current_version = _get_current_yt_dlp_version(app_files_dir)
        latest_info = _fetch_latest_yt_dlp_info()
        latest_version = latest_info["latest_version"]
        update_available = _is_newer(latest_version, current_version)

        meta.update(
            {
                "last_check_at": datetime.now(timezone.utc).isoformat(),
                "current_version": current_version,
                "latest_version": latest_version,
                "package_url": latest_info["package_url"],
                "package_sha256": latest_info["package_sha256"],
                "update_available": update_available,
            }
        )
        _write_update_meta(app_files_dir, meta)

        return json.dumps(
            {
                "success": True,
                "current_version": current_version,
                "latest_version": latest_version,
                "update_available": update_available,
                "cached": False,
            }
        )
    except Exception as e:
        return json.dumps(
            _failure_payload(
                str(e),
                stage="check_yt_dlp_update",
                retryable=_is_retryable_network_error(e),
            )
        )


def update_yt_dlp_if_needed(app_files_dir, force=False):
    try:
        check_result = json.loads(
            check_yt_dlp_update(app_files_dir, force_remote=force)
        )
        if not check_result.get("success"):
            return json.dumps(check_result)

        current_version = check_result.get("current_version", "unknown")
        latest_version = check_result.get("latest_version", current_version)
        update_available = bool(check_result.get("update_available", False))

        if not force and not update_available:
            return json.dumps(
                {
                    "success": True,
                    "updated": False,
                    "current_version": current_version,
                    "latest_version": latest_version,
                    "update_available": False,
                    "message": "yt-dlp já está atualizado",
                }
            )

        meta = _read_update_meta(app_files_dir)
        package_url = meta.get("package_url")
        package_sha256 = meta.get("package_sha256")
        if not package_url or not package_sha256:
            latest_info = _fetch_latest_yt_dlp_info()
            package_url = latest_info["package_url"]
            package_sha256 = latest_info["package_sha256"]

        _install_yt_dlp_package(app_files_dir, package_url, package_sha256)

        global _YT_DLP_MODULE
        if "yt_dlp" in sys.modules:
            del sys.modules["yt_dlp"]
        _YT_DLP_MODULE = None

        installed_version = _get_current_yt_dlp_version(app_files_dir)

        meta.update(
            {
                "installed_runtime_version": installed_version,
                "updated_at": datetime.now(timezone.utc).isoformat(),
                "package_sha256": package_sha256,
                "update_available": _is_newer(latest_version, installed_version),
            }
        )
        _write_update_meta(app_files_dir, meta)

        return json.dumps(
            {
                "success": True,
                "updated": installed_version != current_version,
                "current_version": installed_version,
                "latest_version": latest_version,
                "update_available": _is_newer(latest_version, installed_version),
                "message": "yt-dlp atualizado com sucesso",
            }
        )
    except Exception as e:
        return json.dumps(
            _failure_payload(
                str(e),
                stage="update_yt_dlp_if_needed",
                retryable=_is_retryable_network_error(e),
            )
        )
