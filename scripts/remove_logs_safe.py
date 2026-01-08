#!/usr/bin/env python3
import os
import re

def remove_logs_only(file_path):
    """Remove only Log.d, Log.v, Log.i, Log.w, Log.e statements safely"""
    
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    modified = False
    new_lines = []
    
    for line in lines:
        original_line = line
        
        # Remove Log.d, Log.v, Log.i, Log.w, Log.e statements
        # Match pattern: Log.[divew](...) including multiline
        if re.search(r'^\s*Log\.[divew]\s*\(', line):
            # Skip this line entirely if it starts with Log
            modified = True
            continue
        elif 'Log.d(' in line or 'Log.v(' in line or 'Log.i(' in line or 'Log.w(' in line or 'Log.e(' in line:
            # Remove inline Log statements
            line = re.sub(r'Log\.[divew]\([^)]*\)[;\s]*', '', line)
            if line.strip() == '':
                modified = True
                continue
        
        # Remove println statements
        if 'println(' in line:
            line = re.sub(r'println\([^)]*\)[;\s]*', '', line)
            if line.strip() == '':
                modified = True
                continue
        
        # Remove single-line comments (but keep TODOs and important annotations)
        if '//' in line and not any(keyword in line for keyword in ['TODO', 'FIXME', '@', 'http://', 'https://']):
            # Remove inline comments at end of line
            line = re.sub(r'\s*//[^"\n]*$', '', line)
            # If the entire line was a comment, skip it
            if line.strip() == '' and original_line.strip().startswith('//'):
                modified = True
                continue
        
        new_lines.append(line)
    
    if modified:
        # Clean up multiple empty lines
        content = ''.join(new_lines)
        content = re.sub(r'\n\s*\n\s*\n+', '\n\n', content)
        
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
                    if remove_logs_only(file_path):
                        modified_count += 1
                        print(f"Modified: {file_path}")
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
            print(f"\nProcessing: {app_path}")
            modified, total = process_directory(app_path)
            print(f"Modified {modified} out of {total} files")
        else:
            print(f"Path not found: {app_path}")

if __name__ == "__main__":
    main()