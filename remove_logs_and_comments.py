#!/usr/bin/env python3
import os
import re
import sys

def remove_logs_and_comments(file_path):
    """Remove debug logs and comments from Kotlin file"""
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # Remove single-line comments that are not essential (keep copyright, licenses)
    # Skip comments that might be annotations or important
    content = re.sub(r'^\s*//(?!.*@|.*copyright|.*license|.*TODO|.*FIXME).*$', '', content, flags=re.MULTILINE)
    
    # Remove inline comments at end of lines
    content = re.sub(r'\s+//(?!.*@).*$', '', content, flags=re.MULTILINE)
    
    # Remove multi-line comments (/* ... */) but keep JavaDoc/KDoc (/** ... */)
    content = re.sub(r'/\*(?!\*).*?\*/', '', content, flags=re.DOTALL)
    
    # Remove all Log.d, Log.v, Log.i, Log.w, Log.e statements
    # Handle multi-line log statements
    content = re.sub(r'Log\.[divew]\([^)]*\)[;\s]*', '', content)
    content = re.sub(r'Log\.[divew]\(\s*[^)]*,\s*[^)]*\)[;\s]*', '', content)
    
    # Handle multi-line Log statements with proper matching
    content = re.sub(r'Log\.[divew]\([^{};]*?\)[;\s]*', '', content, flags=re.DOTALL)
    
    # Remove println statements used for debugging
    content = re.sub(r'println\([^)]*\)[;\s]*', '', content)
    
    # Remove Timber logs if present
    content = re.sub(r'Timber\.[divew]\([^)]*\)[;\s]*', '', content)
    
    # Remove empty lines that result from removing logs
    content = re.sub(r'\n\s*\n\s*\n', '\n\n', content)
    
    # Clean up any trailing whitespace
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
                    if remove_logs_and_comments(file_path):
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