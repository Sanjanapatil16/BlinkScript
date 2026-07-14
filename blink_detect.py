from scipy.spatial import distance as dist
from imutils.video import VideoStream
import numpy as np
import imutils
import time
import dlib
import cv2
import os
import pyttsx3
import threading
import requests

# Voice setup
engine = pyttsx3.init()
def speak(message):
    engine.say(message)
    engine.runAndWait()

# Eye Aspect Ratio
def eye_aspect_ratio(eye):
    A = dist.euclidean(eye[1], eye[5])
    B = dist.euclidean(eye[2], eye[4])
    C = dist.euclidean(eye[0], eye[3])
    return (A + B) / (2.0 * C)

# Full path to ADB
ADB = r"C:\Users\Sanjana\AppData\Local\Android\Sdk\platform-tools\adb.exe"
FLASK_URL = "http://127.0.0.1:5000"
BLINK_PAUSE_SECONDS = 1.5  # Stop blinking for this long to confirm selection
EMERGENCY_CONTACT_NAME = "Caregiver"
EMERGENCY_CONTACT_NUMBER = "+91 98765 43210"

# Map raw blink count to emulator option number (same logic as Android app)
def map_blink_to_option(count):
    if 1 <= count <= 4:
        return count
    if count == 5:
        return 6  # Open typing mode
    return 5  # 6+ blinks = emergency

# PRIMARY: Send blink directly to emulator via ADB broadcast (most reliable)
def send_blink_via_adb(option):
    cmd = (
        f'"{ADB}" shell am broadcast '
        f'-a com.example.blinkscriptapp.BLINK_ACTION '
        f'--ei option {option} '
        f'-p com.example.blinkscriptapp'
    )
    result = os.system(cmd)
    if result == 0:
        print(f"[ADB BROADCAST] Sent option {option} to emulator - button should click!")
    else:
        print(f"[ADB BROADCAST] FAILED (code {result}). Is emulator running? Run: adb devices")

# Send message to Android app via ADB file (fallback channel)
def send_to_app(count):
    with open("blink.txt", "w", encoding="ascii") as f:
        f.write(str(count))
    print(f"[ADB FILE] Sending blink count '{count}' to emulator...")
    os.system(f'"{ADB}" push blink.txt /data/local/tmp/blink.txt >nul 2>&1')
    os.system(
        f'"{ADB}" shell "run-as com.example.blinkscriptapp '
        f'cp /data/local/tmp/blink.txt /data/data/com.example.blinkscriptapp/files/blink.txt" >nul 2>&1'
    )
    os.system(f'"{ADB}" shell rm /data/local/tmp/blink.txt >nul 2>&1')

# Log message to Flask server helper
def log_to_server(sender, content):
    try:
        requests.post(f"{FLASK_URL}/api/message", json={"sender": sender, "content": content}, timeout=1)
    except Exception:
        pass  # Server not running or unreachable

# Log SOS alert to Flask server helper
def log_sos_to_server():
    try:
        requests.post(f"{FLASK_URL}/api/sos", json={"status": "PENDING"}, timeout=1)
    except Exception:
        pass

# Send raw blink count directly to Flask server
def send_blink_to_server(count):
    try:
        resp = requests.post(f"{FLASK_URL}/api/blink", json={"count": count}, timeout=1)
        if resp.status_code == 200:
            print(f"[FLASK] Sent blink count {count} to server")
        else:
            print(f"[FLASK] Server returned {resp.status_code}")
    except Exception as e:
        print(f"[FLASK] Not reachable ({e}). ADB broadcast is still used.")

# Background thread to check for Android response file (response.txt)
def android_response_listener():
    print("[INFO] Android response listener thread started...")
    local_filename = "response.txt"
    
    while True:
        # Check if response.txt exists on Android by copying it to tmp using run-as
        copy_cmd = f'"{ADB}" shell "run-as com.example.blinkscriptapp cp /data/data/com.example.blinkscriptapp/files/response.txt /data/local/tmp/response.txt"'
        if os.system(f"{copy_cmd} >nul 2>&1") == 0:
            # File exists! Pull it from tmp
            pull_cmd = f'"{ADB}" pull /data/local/tmp/response.txt {local_filename} >nul 2>&1'
            if os.system(pull_cmd) == 0:
                if os.path.exists(local_filename):
                    with open(local_filename, "r") as f:
                        lines = f.readlines()
                    
                    # Parse sender and content
                    # Format in file: SENDER:CONTENT (e.g. USER:Water or CHATBOT:How can I help you today?)
                    for line in lines:
                        line = line.strip()
                        if ":" in line:
                            parts = line.split(":", 1)
                            sender = parts[0].strip()
                            content = parts[1].strip()
                            if content:
                                print(f"[PULLED RESPONSE] {sender}: {content}")
                                log_to_server(sender, content)
                    
                    # Clean up local file
                    try:
                        os.remove(local_filename)
                    except OSError:
                        pass
                    
                    # Delete the files from Android so we don't process again
                    del_cmd = f'"{ADB}" shell "run-as com.example.blinkscriptapp rm /data/data/com.example.blinkscriptapp/files/response.txt" >nul 2>&1'
                    os.system(del_cmd)
                    del_tmp_cmd = f'"{ADB}" shell rm /data/local/tmp/response.txt >nul 2>&1'
                    os.system(del_tmp_cmd)
                    
        time.sleep(1.5) # Poll every 1.5 seconds

# Start response listener thread
listener_thread = threading.Thread(target=android_response_listener, daemon=True)
listener_thread.start()

# Settings
EYE_AR_THRESH = 0.25
EYE_AR_CONSEC_FRAMES = 3
COUNTER = 0
BLINK_COUNT = 0
LAST_ACTIVITY_TIME = time.time()
FINAL_MESSAGE = ""
STATUS_TEXT = "Blink to select an option"

print("Starting camera...")
vs = VideoStream(src=0).start()
time.sleep(1.0)
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("shape_predictor_68_face_landmarks.dat")

(lStart, lEnd) = (42, 48)
(rStart, rEnd) = (36, 42)

def deliver_blink_to_emulator(count):
    option = map_blink_to_option(count)
    print(f"=== BLINK CONFIRMED: {count} blink(s) -> Option {option} ===")

    send_blink_via_adb(option)
    send_blink_to_server(count)
    send_to_app(count)

    if option == 5:
        speak("Emergency! Emergency! I need help immediately.")
        speak(f"Please call {EMERGENCY_CONTACT_NAME} at {EMERGENCY_CONTACT_NUMBER}")
        log_sos_to_server()
    else:
        speak(f"Option {option}")

while True:
    frame = vs.read()
    if frame is None:
        continue
    frame = imutils.resize(frame, width=700)
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    rects = detector(gray, 0)
    
    for rect in rects:
        shape = predictor(gray, rect)
        shape = np.array([[p.x, p.y] for p in shape.parts()])
        leftEye = shape[lStart:lEnd]
        rightEye = shape[rStart:rEnd]
        leftEAR = eye_aspect_ratio(leftEye)
        rightEAR = eye_aspect_ratio(rightEye)
        ear = (leftEAR + rightEAR) / 2.0
        
        if ear < EYE_AR_THRESH:
            COUNTER += 1
        else:
            if COUNTER >= EYE_AR_CONSEC_FRAMES:
                BLINK_COUNT += 1
                LAST_ACTIVITY_TIME = time.time()
                STATUS_TEXT = f"Blink {BLINK_COUNT} detected - stop blinking!"
                print("Blink:", BLINK_COUNT)
            COUNTER = 0

    if BLINK_COUNT > 0:
        pause_remaining = BLINK_PAUSE_SECONDS - (time.time() - LAST_ACTIVITY_TIME)
        if pause_remaining <= 0:
            FINAL_MESSAGE = f"Option {map_blink_to_option(BLINK_COUNT)}"
            STATUS_TEXT = f"SENT: {FINAL_MESSAGE}"
            deliver_blink_to_emulator(BLINK_COUNT)
            BLINK_COUNT = 0
        else:
            STATUS_TEXT = f"Stop blinking! Confirming in {pause_remaining:.1f}s..."
        
    cv2.rectangle(frame, (0, 0), (700, 180), (0, 0, 0), -1)
    cv2.putText(frame,
                "BLINK COMMUNICATION SYSTEM",
                (20, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (255, 255, 255),
                2)
    cv2.putText(frame,
                f"Blink Count: {BLINK_COUNT}",
                (20, 80),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.9,
                (0, 255, 255),
                2)
    cv2.putText(frame,
                f"Status: {STATUS_TEXT}",
                (20, 130),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.7,
                (0, 255, 0),
                2)
    cv2.putText(frame,
                "1=Opt1  2=Opt2  3=Opt3  4=Opt4  5=Type  6+=SOS",
                (20, 165),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                (200, 200, 200),
                1)
    cv2.imshow("Blink Communication System", frame)
    if cv2.waitKey(1) & 0xFF == ord("q"):
        break

cv2.destroyAllWindows()
vs.stop()