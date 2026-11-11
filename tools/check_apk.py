#!/usr/bin/env python3
"""Проверка release-APK «Лупы» после `./gradlew assembleRelease`.

Падает (код выхода 1), если:
  * APK нет или он больше 10 МБ;
  * в итоговом манифесте APK среди uses-permission не ровно android.permission.CAMERA;
  * в releaseRuntimeClasspath есть запрещённые группы (Firebase, Play Services, реклама,
    OkHttp, Sentry).

Манифест читается через `aapt2 dump xmltree` из Android SDK (ANDROID_HOME, ANDROID_SDK_ROOT
или sdk.dir из local.properties). Зависимости — через `./gradlew :app:dependencies`.

    python3 tools/check_apk.py [--apk ПУТЬ] [--deps ФАЙЛ]

--deps — готовый вывод `:app:dependencies --configuration releaseRuntimeClasspath`
вместо запуска Gradle.
"""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
MAX_APK_BYTES = 10 * 1024 * 1024
ALLOWED_PERMISSIONS = ["android.permission.CAMERA"]
FORBIDDEN_GROUPS = (
    "com.google.firebase",
    "com.google.android.gms",
    "com.google.ads",
    "com.squareup.okhttp3",
    "io.sentry",
)
PERMISSION_ELEMENTS = {"uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"}
ANDROID_NAME_ATTR = re.compile(r':name\(0x01010003\)="([^"]*)"')
COORDINATE = re.compile(r"^([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+)")


def find_sdk() -> Path:
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(var)
        if value and Path(value).is_dir():
            return Path(value)
    local_properties = ROOT / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            key, sep, value = line.partition("=")
            if sep and key.strip() == "sdk.dir" and Path(value.strip()).is_dir():
                return Path(value.strip())
    raise SystemExit("Не найден Android SDK: задайте ANDROID_HOME или sdk.dir в local.properties.")


def find_aapt2(sdk: Path) -> Path:
    def version_key(path: Path):
        return [int(part) if part.isdigit() else -1 for part in re.split(r"[.\-]", path.parent.name)]

    candidates = sorted(sdk.glob("build-tools/*/aapt2"), key=version_key)
    if not candidates:
        raise SystemExit(f"В {sdk}/build-tools нет aapt2.")
    return candidates[-1]


def manifest_permissions(apk: Path, aapt2: Path) -> list:
    """Все uses-permission итогового манифеста APK, в порядке объявления."""
    result = subprocess.run(
        [str(aapt2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"aapt2 не смог прочитать манифест: {result.stderr.strip()}")
    permissions = []
    current_element = None
    for line in result.stdout.splitlines():
        text = line.strip()
        if text.startswith("E: "):
            current_element = text[3:].split()[0]
        elif text.startswith("A: ") and current_element in PERMISSION_ELEMENTS:
            match = ANDROID_NAME_ATTR.search(text)
            if match:
                permissions.append(match.group(1))
    return permissions


def dependency_groups(deps_file) -> set:
    """Группы всех модулей releaseRuntimeClasspath."""
    if deps_file:
        output = Path(deps_file).read_text(encoding="utf-8")
    else:
        gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        result = subprocess.run(
            [str(gradlew), "-q", "--no-daemon", ":app:dependencies",
             "--configuration", "releaseRuntimeClasspath"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise SystemExit("Gradle не вывел зависимости:\n" + result.stderr[-2000:])
        output = result.stdout
    groups = set()
    for line in output.splitlines():
        match = COORDINATE.match(line.lstrip(" |+\\-"))
        if match:
            groups.add(match.group(1))
    return groups


def is_forbidden(group: str) -> bool:
    return any(group == bad or group.startswith(bad + ".") for bad in FORBIDDEN_GROUPS)


def main() -> int:
    parser = argparse.ArgumentParser(description="Проверка release-APK «Лупы».")
    parser.add_argument("--apk", default=str(DEFAULT_APK), help="путь к APK")
    parser.add_argument("--deps", help="файл с выводом :app:dependencies вместо запуска Gradle")
    args = parser.parse_args()

    failures = []
    apk = Path(args.apk)

    if not apk.is_file():
        failures.append(f"APK не найден: {apk}")
    else:
        size = apk.stat().st_size
        if size > MAX_APK_BYTES:
            failures.append(f"APK весит {size / 1048576:.2f} МБ — больше 10 МБ")
        else:
            print(f"OK   размер APK {size / 1048576:.2f} МБ (≤ 10 МБ)")

        permissions = manifest_permissions(apk, find_aapt2(find_sdk()))
        if permissions != ALLOWED_PERMISSIONS:
            failures.append(
                "uses-permission в манифесте: "
                + (", ".join(permissions) or "нет ни одного")
                + " — должно быть ровно android.permission.CAMERA"
            )
        else:
            print("OK   uses-permission: только android.permission.CAMERA")

    groups = dependency_groups(args.deps)
    if not groups:
        failures.append("в выводе зависимостей не нашлось ни одного модуля — проверка не состоялась")
    else:
        forbidden = sorted(g for g in groups if is_forbidden(g))
        if forbidden:
            failures.append("запрещённые группы в releaseRuntimeClasspath: " + ", ".join(forbidden))
        else:
            print(f"OK   зависимости: {len(groups)} групп, запрещённых нет")

    for failure in failures:
        print(f"FAIL {failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
