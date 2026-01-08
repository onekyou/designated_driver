#!/usr/bin/env python3
import os
import re

def fix_broken_logs(file_path):
    """Fix broken android.util. fragments left by log removal"""
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # Remove standalone android.util. fragments
    content = re.sub(r'android\.util\.\s*', '', content)
    content = re.sub(r'android\.util\."[^"]*"[^}]*\}[^}]*\}[^"]*"[^"]*"\}\)', '', content)
    
    # Clean up multiple empty lines
    content = re.sub(r'\n\s*\n\s*\n+', '\n\n', content)
    
    # Remove any trailing whitespace
    content = re.sub(r'[ \t]+$', '', content, flags=re.MULTILINE)
    
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def process_directory(root_dir):
    """Process all Kotlin files in directory"""
    
    modified_count = 0
    total_count = 0
    
    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Skip test directories
        if 'test' in dirpath or 'androidTest' in dirpath:
            continue
            
        for filename in filenames:
            if filename.endswith('.kt'):
                file_path = os.path.join(dirpath, filename)
                total_count += 1
                
                try:
                    if fix_broken_logs(file_path):
                        modified_count += 1
                        print(f"Fixed: {file_path}")
                except Exception as e:
                    print(f"Error processing {file_path}: {e}")
    
    return modified_count, total_count

def main():
    apps = [
        r"C:\app_dev\designated_driver\call_detector\app\src\main\java",
        r"C:\app_dev\designated_driver\call_manager\app\src\main\java", 
        r"C:\app_dev\designated_driver\driver_app\app\src\main\java"
    ]
    
    for app_path in apps:
        if os.path.exists(app_path):
            print(f"\nFixing: {app_path}")
            modified, total = process_directory(app_path)
            print(f"Fixed {modified} out of {total} files")
        else:
            print(f"Path not found: {app_path}")

if __name__ == "__main__":
    main()