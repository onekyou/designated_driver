#!/usr/bin/env python3
import os
import re

def remove_unused_log_imports(file_path):
    """
    사용되지 않는 Log import만 제거
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        
        # Log import가 있는지 확인
        if 'import Log\n' in content or 'import android.util.Log\n' in content:
            # Log.를 사용하는 곳이 있는지 확인
            if 'Log.' not in content:
                # Log를 사용하지 않으면 import 제거
                content = re.sub(r'\nimport Log\n', '\n', content)
                content = re.sub(r'\nimport android\.util\.Log\n', '\n', content)
                content = re.sub(r'^import Log\n', '', content, flags=re.MULTILINE)
                content = re.sub(r'^import android\.util\.Log\n', '', content, flags=re.MULTILINE)
        
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        
        return False
        
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
        return False

def main():
    # call_manager_backup 폴더의 kt 파일들만 처리
    base_dir = "call_manager_backup/app/src/main/java"
    
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
                
                if remove_unused_log_imports(file_path):
                    changed_files += 1
                    print(f"Fixed unused Log import: {file_path}")
    
    print(f"\nUnused Import Cleanup Summary:")
    print(f"Total Kotlin files processed: {total_files}")
    print(f"Files modified: {changed_files}")

if __name__ == "__main__":
    main()