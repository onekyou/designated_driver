#!/usr/bin/env python3
import os
import re

def remove_logs_from_file(file_path):
    """
    콜매니저에서 안전하게 로그만 제거 (기능 코드는 절대 건드리지 않음)
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        
        # 1. android.util.Log 문만 제거 (매우 구체적인 패턴)
        patterns = [
            r'\s*android\.util\.Log\.[dviwe]\([^}]+?\)\s*\n',  # android.util.Log만
            r'\s*println\([^}]+?\)\s*\n',  # println만
        ]
        
        for pattern in patterns:
            content = re.sub(pattern, '', content, flags=re.MULTILINE | re.DOTALL)
        
        # 2. 주석 제거 (// 로 시작하는 단일 라인 주석만)
        content = re.sub(r'^\s*//.*$', '', content, flags=re.MULTILINE)
        
        # 3. 빈 줄 정리 (3개 이상 연속 빈 줄을 2개로)
        content = re.sub(r'\n\n\n+', '\n\n', content)
        
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        
        return False
        
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
        return False

def main():
    # 콜매니저 앱만 처리
    base_dir = "call_manager/app/src/main/java"
    
    if not os.path.exists(base_dir):
        print(f"Directory not found: {base_dir}")
        return
        
    total_files = 0
    changed_files = 0
    
    for root, dirs, files in os.walk(base_dir):
        for file in files:
            if file.endswith('.kt'):
                file_path = os.path.join(root, file)
                total_files += 1
                
                if remove_logs_from_file(file_path):
                    changed_files += 1
                    print(f"Cleaned logs from: {file_path}")
    
    print(f"\nCall Manager Cleanup Summary:")
    print(f"Total Kotlin files processed: {total_files}")
    print(f"Files modified: {changed_files}")

if __name__ == "__main__":
    main()