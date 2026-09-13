@echo off
title SilentService Notification Client
echo Starting SilentService Notification Client...

:: Check if ADB port forwarding is needed for BlueStacks / USB
adb -s 127.0.0.1:5555 forward tcp:5556 tcp:5556 >nul 2>&1

:: Run the client
python notify_client.py 127.0.0.1 5556
pause
