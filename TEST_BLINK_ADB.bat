@echo off
echo ============================================
echo  BlinkScript - TEST ADB BLINK (no camera)
echo ============================================
echo.
echo Make sure emulator is running with BlinkScriptApp open!
echo.
set ADB=C:\Users\Sanjana\AppData\Local\Android\Sdk\platform-tools\adb.exe

echo Checking emulator connection...
"%ADB%" devices
echo.

echo Sending Option 1 (should click CHATBOT button)...
"%ADB%" shell am broadcast -a com.example.blinkscriptapp.BLINK_ACTION --ei option 1 -p com.example.blinkscriptapp
timeout /t 3 /nobreak >nul

echo Sending Option 2 (should click next button)...
"%ADB%" shell am broadcast -a com.example.blinkscriptapp.BLINK_ACTION --ei option 2 -p com.example.blinkscriptapp

echo.
echo Done! Check emulator - buttons should have been clicked automatically.
pause
