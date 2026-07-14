@echo off
echo ============================================
echo  BlinkScript - START EVERYTHING
echo ============================================
echo.
echo STEP 1: Make sure Android Emulator is running with BlinkScriptApp open
echo STEP 2: This script starts Flask backend + Blink detection
echo.
echo BLINK CONTROLS:
echo   1 blink  = Select Option 1 (e.g. CHATBOT / YES)
echo   2 blinks = Select Option 2 (e.g. NO)
echo   3 blinks = Select Option 3 (e.g. Something Else / TYPE)
echo   4 blinks = Select Option 4 (e.g. Back / Emergency)
echo   5 blinks = Open Typing Mode
echo   6+ blinks = SOS Emergency
echo.
echo Wait 1.5 seconds after your last blink before it registers!
echo.
pause

cd /d C:\AndroidProject\BlinkScriptApp

echo Starting Flask backend on port 5000...
start "BlinkScript Backend" cmd /k python backend.py

timeout /t 3 /nobreak >nul

echo Starting Blink Detection (camera)...
start "Blink Detection" cmd /k python blink_detect.py

echo.
echo Both services started! Blink at your laptop camera to control the emulator.
pause
