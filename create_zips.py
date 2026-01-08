#!/usr/bin/env python3
import zipfile
from pathlib import Path
import time

def create_zip(source_dir, zip_name):
    """폴더를 zip 파일로 압축"""
    print(f"\n[{zip_name}] Creating zip file...")

    zip_path = source_dir.parent / zip_name
    file_count = 0

    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file_path in source_dir.rglob('*'):
            if file_path.is_file():
                arcname = file_path.relative_to(source_dir.parent)
                zipf.write(file_path, arcname)
                file_count += 1

    # 파일 크기 계산
    size_mb = zip_path.stat().st_size / (1024 * 1024)

    print(f"OK {zip_name} created: {file_count} files, {size_mb:.2f} MB")
    return zip_path

if __name__ == "__main__":
    source_base = Path("C:/app_dev/designated_driver/source")

    # 각 앱별 zip 생성
    apps = [
        ("call_detector", "01_call_detector.zip"),
        ("call_manager", "02_call_manager.zip"),
        ("customer_app", "03_customer_app.zip"),
        ("driver_app", "04_driver_app.zip"),
    ]

    print("="*60)
    print("Creating ZIP files for review...")
    print("="*60)

    created_zips = []
    for app_name, zip_name in apps:
        app_dir = source_base / app_name
        if app_dir.exists():
            zip_path = create_zip(app_dir, zip_name)
            created_zips.append(zip_path)

    # firestore.rules도 별도 zip으로
    print(f"\n[firestore.rules] Creating zip file...")
    rules_zip = source_base / "00_firestore_rules.zip"
    with zipfile.ZipFile(rules_zip, 'w', zipfile.ZIP_DEFLATED) as zipf:
        rules_file = source_base / "firestore.rules"
        if rules_file.exists():
            zipf.write(rules_file, "firestore.rules")

    size_mb = rules_zip.stat().st_size / (1024 * 1024)
    print(f"OK 00_firestore_rules.zip created: {size_mb:.2f} MB")
    created_zips.append(rules_zip)

    print(f"\n{'='*60}")
    print(f"DONE! Created {len(created_zips)} zip files:")
    for zip_path in created_zips:
        size_mb = zip_path.stat().st_size / (1024 * 1024)
        print(f"  - {zip_path.name} ({size_mb:.2f} MB)")
    print(f"{'='*60}")
    print(f"\nAll files are in: {source_base}")
