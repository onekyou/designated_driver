#!/usr/bin/env python3
import os
import re

def remove_android_logs(file_path):
    """Remove all android.util.Log statements"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        
        # Remove all android.util.Log statements
        patterns = [
            r'\s*android\.util\.Log\.[a-z]+\([^;]+?\);?\s*\n',  # Multi-line with semicolon
            r'\s*android\.util\.Log\.[a-z]+\([^}]+?\)\s*\n',    # Multi-line without semicolon
        ]
        
        for pattern in patterns:
            content = re.sub(pattern, '', content, flags=re.MULTILINE | re.DOTALL)
        
        # Clean up extra blank lines
        content = re.sub(r'\n\n\n+', '\n\n', content)
        
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        
        return False
        
    except Exception as e:
        print(f"Error: {e}")
        return False

def main():
    files = [
        "call_detector/app/src/main/java/com/example/calldetector/CallDetectorApplication.kt",
        "call_detector/app/src/main/java/com/example/calldetector/CrashReportService.kt"
    ]
    
    for file_path in files:
        if os.path.exists(file_path):
            if remove_android_logs(file_path):
                print(f"Cleaned android logs from: {file_path}")
            else:
                print(f"No android logs found in: {file_path}")
        else:
            print(f"File not found: {file_path}")

if __name__ == "__main__":
    main()