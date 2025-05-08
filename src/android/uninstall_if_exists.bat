@echo off
rem 1. Kiểm tra package có cài chưa
adb shell pm list packages | find "com.example.doorbell_mobile" >nul

if %ERRORLEVEL% EQU 0 (
    rem 2. Xóa data & cache
    echo [*] Package found, clearing data ^& cache...
    adb shell pm clear com.example.doorbell_mobile
    echo [*] Data ^& cache cleared.

    rem 3. Gỡ cài đặt app
    echo [*] Uninstalling app...
    adb shell pm uninstall com.example.doorbell_mobile
    echo [*] Uninstall completed.
) else (
    echo [!] Package com.example.doorbell_mobile not installed. Skipping.
)
