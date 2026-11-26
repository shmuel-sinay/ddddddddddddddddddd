@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Hebron Install - MDM setup
chcp 65001 >nul

rem ============================================================
rem  Hebron Install - one-click MDM setup.
rem  Works from ANY folder, with or without adb installed:
rem  if adb is missing it is downloaded automatically.
rem
rem    setup-mdm.bat           give all permissions (device owner + accessibility)
rem    setup-mdm.bat release   remove the MDM completely
rem
rem  Needs: USB debugging on, phone connected, no accounts on the
rem  phone while the device-owner step runs, app already installed.
rem ============================================================

set "PKG=com.hebron.preloadviewer"
set "ADMIN=%PKG%/.AdminReceiver"
set "SVC=%PKG%/.BlockAppsService"
set "ADB="
set "ADBDIR=%LOCALAPPDATA%\hebron-adb"

rem ---------- 1. find adb ----------
where adb >nul 2>nul
if not errorlevel 1 set "ADB=adb"
if not defined ADB if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
if not defined ADB if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if not defined ADB if exist "%ADBDIR%\platform-tools\adb.exe" set "ADB=%ADBDIR%\platform-tools\adb.exe"
if defined ADB goto have_adb

echo adb was not found - downloading Google platform-tools...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile '%TEMP%\hebron-pt.zip'; Expand-Archive -Force '%TEMP%\hebron-pt.zip' '%ADBDIR%'"
if exist "%ADBDIR%\platform-tools\adb.exe" set "ADB=%ADBDIR%\platform-tools\adb.exe"
if not defined ADB goto no_adb

:have_adb
echo Using adb: %ADB%
"%ADB%" start-server >nul 2>nul

rem ---------- 2. wait for the phone ----------
echo.
echo Waiting for the phone. USB debugging must be on, and approve the prompt on the phone if it appears...
"%ADB%" wait-for-device

if /i "%~1"=="release" goto do_release

rem ---------- 3. app installed? ----------
"%ADB%" shell pm path %PKG% | find "package:" >nul
if errorlevel 1 goto no_app

rem ---------- 4. device owner (MDM) ----------
echo.
echo [1/3] Making the app a device owner...
"%ADB%" shell dpm set-device-owner %ADMIN% > "%TEMP%\hebron-dpm.txt" 2>&1
type "%TEMP%\hebron-dpm.txt"
find /i "Success" "%TEMP%\hebron-dpm.txt" >nul
if not errorlevel 1 goto owner_ok
find /i "already" "%TEMP%\hebron-dpm.txt" >nul
if not errorlevel 1 goto owner_ok
echo.
echo *** Device owner step FAILED. See the message above.
echo     "already some accounts"  -^> remove Google / manufacturer accounts from the phone temporarily, then run again.
echo     other message           -^> send it to your developer.
goto accessibility
:owner_ok
echo Device owner OK.

rem ---------- 5. accessibility service ----------
:accessibility
echo.
echo [2/3] Enabling the accessibility service...
set "CUR="
for /f "usebackq delims=" %%A in (`"%ADB%" shell settings get secure enabled_accessibility_services`) do set "CUR=%%A"
set "CUR=!CUR: =!"
if "!CUR!"=="" set "CUR=null"
echo !CUR! | find "BlockAppsService" >nul
if not errorlevel 1 goto acc_set
if /i "!CUR!"=="null" (set "NEWSVC=%SVC%") else (set "NEWSVC=!CUR!:%SVC%")
"%ADB%" shell settings put secure enabled_accessibility_services "!NEWSVC!"
:acc_set
"%ADB%" shell settings put secure accessibility_enabled 1

rem ---------- 6. apply policy + open app ----------
echo.
echo [3/3] Applying the policy...
"%ADB%" shell am broadcast -n %PKG%/.PolicyCommandReceiver -a %PKG%.APPLY >nul 2>nul
"%ADB%" shell am start -n %PKG%/.MainActivity >nul 2>nul

echo.
echo Done. Open the hidden screen on the phone to check the status lines:
echo   long-press the title, then long-press the title of the activity launcher.
goto end

:do_release
echo.
echo Removing the MDM...
"%ADB%" shell am broadcast -n %PKG%/.PolicyCommandReceiver -a %PKG%.RELEASE
echo Done.
goto end

:no_adb
echo.
echo *** Could not find or download adb. Check the internet connection, or put adb.exe next to this file.
goto end

:no_app
echo.
echo *** The app %PKG% is not installed on the phone. Install the APK first, then run this again.

:end
echo.
pause
endlocal
