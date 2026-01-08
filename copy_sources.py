#!/usr/bin/env python3
import os
import shutil
from pathlib import Path

def copy_app_sources(app_name, app_path, dest_base):
    """앱 소스를 복사하는 함수"""
    print(f"\n{'='*60}")
    print(f"[{app_name}] Copying started...")
    print(f"{'='*60}")

    source_dir = Path(app_path)
    dest_dir = Path(dest_base) / app_name

    # 복사할 파일 카운터
    kt_java_count = 0
    gradle_count = 0
    values_count = 0

    # 1. Kotlin/Java 소스 파일 복사 (폴더 구조 유지)
    java_src = source_dir / "app" / "src" / "main" / "java"
    if java_src.exists():
        for file_path in java_src.rglob("*"):
            if file_path.suffix in ['.kt', '.java']:
                rel_path = file_path.relative_to(source_dir)
                dest_file = dest_dir / rel_path
                dest_file.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(file_path, dest_file)
                kt_java_count += 1

    # 2. build.gradle 파일들 복사
    for gradle_file in ['build.gradle', 'build.gradle.kts', 'settings.gradle', 'gradle.properties']:
        # 루트 레벨
        src_file = source_dir / gradle_file
        if src_file.exists():
            dest_file = dest_dir / gradle_file
            dest_file.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src_file, dest_file)
            gradle_count += 1

        # app 레벨
        src_file = source_dir / "app" / gradle_file
        if src_file.exists():
            dest_file = dest_dir / "app" / gradle_file
            dest_file.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src_file, dest_file)
            gradle_count += 1

    # 3. values 리소스만 복사
    res_dir = source_dir / "app" / "src" / "main" / "res"
    if res_dir.exists():
        for values_dir in res_dir.glob("values*"):
            if values_dir.is_dir():
                for xml_file in values_dir.glob("*.xml"):
                    rel_path = xml_file.relative_to(source_dir)
                    dest_file = dest_dir / rel_path
                    dest_file.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(xml_file, dest_file)
                    values_count += 1

    print(f"OK {app_name} copy completed:")
    print(f"   - Kotlin/Java: {kt_java_count} files")
    print(f"   - Gradle: {gradle_count} files")
    print(f"   - Values XML: {values_count} files")

    return kt_java_count + gradle_count + values_count

if __name__ == "__main__":
    base_dir = Path("C:/app_dev/designated_driver")
    dest_base = base_dir / "source"

    # 각 앱 복사
    apps = [
        ("call_detector", base_dir / "call_detector"),
        ("call_manager", base_dir / "call_manager"),
        ("customer_app", base_dir / "customer_app" / "app"),
        ("driver_app", base_dir / "driver_app"),
    ]

    total_files = 0
    for app_name, app_path in apps:
        count = copy_app_sources(app_name, app_path, dest_base)
        total_files += count

    print(f"\n{'='*60}")
    print(f"DONE! Total files copied: {total_files}")
    print(f"{'='*60}")
