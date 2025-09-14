#!/usr/bin/env python3
import os
import re
import sys

def remove_logs_from_file(file_path):
    """
    안전하게 로그 문만 제거하고 기능은 유지
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        changes_made = False
        
        # 1. android.util.Log 문 제거 (가장 안전한 패턴)
        log_patterns = [
            r'\s*android\.util\.Log\.[dviwe]\([^}]+?\)\s*$',  # 단일 라인
            r'\s*android\.util\.Log\.[dviwe]\([^}]+?\)\s*;?\s*$',  # 세미콜론 있는 경우
        ]
        
        for pattern in log_patterns:
            new_content = re.sub(pattern, '', content, flags=re.MULTILINE)
            if new_content != content:
                changes_made = True
                content = new_content
        
        # 2. println 문 제거
        content = re.sub(r'\s*println\([^)]+\)\s*$', '', content, flags=re.MULTILINE)
        
        # 3. 빈 줄 정리 (3개 이상 연속 빈 줄을 2개로)
        content = re.sub(r'\n\n\n+', '\n\n', content)
        
        if content != original_content:
            changes_made = True
        
        if changes_made:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        
        return False
        
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
        return False

def main():
    base_dirs = [
        "call_detector/app/src/main/java",
        "call_manager/app/src/main/java", 
        "pickup_app/app/src/main/java"
    ]
    
    total_files = 0
    changed_files = 0
    
    for base_dir in base_dirs:
        if not os.path.exists(base_dir):
            print(f"Directory not found: {base_dir}")
            continue
            
        for root, dirs, files in os.walk(base_dir):
            for file in files:
                if file.endswith('.kt'):
                    file_path = os.path.join(root, file)
                    total_files += 1
                    
                    if remove_logs_from_file(file_path):
                        changed_files += 1
                        print(f"Cleaned: {file_path}")
    
    print(f"\nSummary:")
    print(f"Total Kotlin files processed: {total_files}")
    print(f"Files modified: {changed_files}")

if __name__ == "__main__":
    main()