package com.example.blinkscriptapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    TextView question;
    TextView txtHistory;
    TextView txtSuggestions;
    TextView txtStateIndicator;
    TextView txtEmergencyContact;
    LinearLayout suggestionsContainer;

    Button btn1, btn2, btn3, btn4, btnSend;
    EditText editMessage;
    TextToSpeech tts;
    Handler handler = new Handler();

    // Emergency contact settings (can be updated via settings)
    String emergencyContactName = "Caregiver";
    String emergencyContactNumber = "+91 98765 43210";
    String emergencyVoiceMessage = "Emergency! Emergency! I need help immediately. Please call my emergency contact now.";

    int screen = 0;
    int previousScreen = 0; // To return to chatbot from keyboard

    // Typing Keyboard variables
    StringBuilder typedText = new StringBuilder();
    String[] dictionary = {
            "hello", "help", "water", "food", "outside", "yes", "no", "hurt",
            "pain", "doctor", "nurse", "washroom", "medicine", "comedy",
            "movie", "music", "park", "walk", "cold", "warm", "normal",
            "tv", "book", "good", "bad", "tired", "sleep", "thanks", "please"
    };
    List<String> activeSuggestions = new ArrayList<>();
    boolean isEmergencyActive = false;
    ToneGenerator emergencyTone;

    BroadcastReceiver blinkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleBlinkOption(intent.getIntExtra("option", 0));
        }
    };

    // Called from BlinkWatcherService, ADB broadcast, or Flask polling
    void handleBlinkOption(int option) {
        if (option < 1 || option > 6) return;
        android.util.Log.d("BlinkScript", "Blink option selected: " + option);
        runOnUiThread(() -> {
            highlightBlinkSelection(option);
            Toast.makeText(MainActivity.this, "BLINK SELECTED: Option " + option, Toast.LENGTH_SHORT).show();
            switch (option) {
                case 1:
                    btn1.performClick();
                    break;
                case 2:
                    btn2.performClick();
                    break;
                case 3:
                    btn3.performClick();
                    break;
                case 4:
                    btn4.performClick();
                    break;
                case 5:
                    triggerSOS();
                    break;
                case 6:
                    enterKeyboardMode();
                    break;
            }
        });
    }

    // Flash the selected button so user sees blink worked
    void highlightBlinkSelection(int option) {
        Button selected = null;
        switch (option) {
            case 1: selected = btn1; break;
            case 2: selected = btn2; break;
            case 3: selected = btn3; break;
            case 4: selected = btn4; break;
            case 6: selected = btn3; break;
        }
        if (selected != null) {
            final Button highlightBtn = selected;
            int originalColor = option == 4 ? 0xFFDC2626 : 0xFF2563EB;
            highlightBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF10B981));
            handler.postDelayed(() ->
                    highlightBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(originalColor)),
                    600);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Views
        question = findViewById(R.id.question);
        txtHistory = findViewById(R.id.txtHistory);
        txtSuggestions = findViewById(R.id.txtSuggestions);
        txtStateIndicator = findViewById(R.id.txtStateIndicator);
        txtEmergencyContact = findViewById(R.id.txtEmergencyContact);
        suggestionsContainer = findViewById(R.id.suggestionsContainer);

        btn1 = findViewById(R.id.btn1);
        btn2 = findViewById(R.id.btn2);
        btn3 = findViewById(R.id.btn3);
        btn4 = findViewById(R.id.btn4);
        btnSend = findViewById(R.id.btnSend);
        editMessage = findViewById(R.id.editMessage);

        // Set Click Listeners for accessibility & manual mode
        btn1.setOnClickListener(v -> option1());
        btn2.setOnClickListener(v -> option2());
        btn3.setOnClickListener(v -> option3());
        btn4.setOnClickListener(v -> option4());
        btnSend.setOnClickListener(v -> sendCustomMessage());

        // Long press question to enter manual text typing (caregiver help)
        question.setOnLongClickListener(v -> {
            enterKeyboardMode();
            return true;
        });

        // Setup TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        showHome();

        // Register Broadcast Receiver
        IntentFilter filter = new IntentFilter("com.example.blinkscriptapp.BLINK_ACTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(blinkReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(blinkReceiver, filter);
        }

        // Start watcher service
        startService(new Intent(this, BlinkWatcherService.class));

        // Handle blink option if launched via ADB broadcast
        handleIncomingBlinkIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingBlinkIntent(intent);
    }

    private void handleIncomingBlinkIntent(Intent intent) {
        if (intent == null) return;
        if ("com.example.blinkscriptapp.BLINK_SELECT".equals(intent.getAction())) {
            handleBlinkOption(intent.getIntExtra("option", 0));
            intent.setAction(null);
        }
    }

    // Speak helper that updates current question on screen
    void speak(String msg) {
        question.setText(msg);
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    // Logging helper: Updates local history, sends HTTP logs, and generates response.txt
    void logEvent(String sender, String content) {
        // 1. Update history scroll screen
        String currentHistory = txtHistory.getText().toString();
        if (currentHistory.startsWith("Welcome to") || currentHistory.isEmpty()) {
            txtHistory.setText(sender + ": " + content);
        } else {
            txtHistory.setText(currentHistory + "\n" + sender + ": " + content);
        }

        // 2. Write to local file for Python ADB thread to pull
        writeResponseToFile(sender, content);

        // 3. Post to Flask API in the background
        postLogToServer(sender, content, false);
    }

    // Direct local logging of SOS with voice alert and emergency contact
    void triggerSOS() {
        isEmergencyActive = true;
        String contactName = emergencyContactName;
        String contactNumber = emergencyContactNumber;
        String voiceMessage = emergencyVoiceMessage;

        txtStateIndicator.setText("MODE: EMERGENCY");
        txtEmergencyContact.setVisibility(View.VISIBLE);
        txtEmergencyContact.setText("EMERGENCY CONTACT: " + contactName + " — " + contactNumber);

        question.setText("EMERGENCY ALERT SENT!");
        question.setTextColor(0xFFFCA5A5);

        btn1.setText("[1] Call " + contactName);
        btn2.setText("[2] Play Voice Message");
        btn3.setText("[3] Open Dialer");
        btn4.setText("[4] Cancel Emergency");

        playEmergencyAlarm();
        speakEmergency(voiceMessage, contactName, contactNumber);

        String sosLog = "SOS TRIGGERED — Contact: " + contactName + " (" + contactNumber + ")";
        logEvent("SYSTEM", sosLog);
        postLogToServer("SYSTEM", sosLog, true);
        Toast.makeText(this, "Emergency Alert! Contact: " + contactName + " - " + contactNumber, Toast.LENGTH_LONG).show();
    }

    void speakEmergency(String message, String contactName, String contactNumber) {
        if (tts == null) return;
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "sos_msg");
        String numberSpeech = contactNumber.replace("+", " plus ").replace("-", " ");
        tts.speak("Emergency contact is " + contactName + ". Phone number is " + numberSpeech,
                TextToSpeech.QUEUE_ADD, null, "sos_contact");
    }

    void playEmergencyAlarm() {
        try {
            if (emergencyTone != null) emergencyTone.release();
            emergencyTone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            emergencyTone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800);
        } catch (Exception ignored) {
        }

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 500, 200, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
            }
        }
    }

    void openEmergencyDialer() {
        String dialNumber = emergencyContactNumber.replace(" ", "").replace("-", "");
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialNumber));
        startActivity(dialIntent);
    }

    void handleEmergencyOption(int btn) {
        String contactName = emergencyContactName;
        String contactNumber = emergencyContactNumber;
        if (btn == 1) {
            speak("Calling " + contactName);
            openEmergencyDialer();
        } else if (btn == 2) {
            playEmergencyAlarm();
            speakEmergency(emergencyVoiceMessage, contactName, contactNumber);
            Toast.makeText(this, "Playing emergency voice message", Toast.LENGTH_SHORT).show();
        } else if (btn == 3) {
            speak("Opening dialer for " + contactName);
            openEmergencyDialer();
        } else {
            clearEmergencyMode();
            showHome();
        }
    }

    void clearEmergencyMode() {
        isEmergencyActive = false;
        txtEmergencyContact.setVisibility(View.GONE);
        question.setTextColor(0xFFFFFFFF);
        if (emergencyTone != null) {
            emergencyTone.release();
            emergencyTone = null;
        }
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.cancel();
    }

    // Write file for ADB transfer
    private void writeResponseToFile(String sender, String content) {
        try {
            java.io.File dir = getFilesDir();
            java.io.File file = new java.io.File(dir, "response.txt");
            java.io.FileWriter writer = new java.io.FileWriter(file, true);
            writer.write(sender + ":" + content + "\n");
            writer.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    // Post log asynchronously to Flask API
    private void postLogToServer(final String sender, final String content, final boolean isSos) {
        new Thread(() -> {
            try {
                // Try localhost from Android Emulator (10.0.2.2) and port 5000
                java.net.URL url = new java.net.URL(isSos ? "http://10.0.2.2:5000/api/sos" : "http://10.0.2.2:5000/api/message");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);

                String jsonInputString;
                if (isSos) {
                    jsonInputString = "{\"status\": \"PENDING\"}";
                } else {
                    jsonInputString = "{\"sender\": \"" + sender + "\", \"content\": \"" + content + "\"}";
                }

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                // Ignore network errors, ADB sync is our fallback
            }
        }).start();
    }

    // Helper to display word suggestions while typing
    void updateSuggestions() {
        String currentText = typedText.toString().trim();
        if (currentText.isEmpty()) {
            activeSuggestions.clear();
            suggestionsContainer.setVisibility(View.GONE);
            return;
        }

        // Get last word being typed
        String[] words = currentText.split("\\s+");
        String lastWord = words[words.length - 1].toLowerCase();

        activeSuggestions.clear();
        for (String w : dictionary) {
            if (w.startsWith(lastWord) && !w.equals(lastWord)) {
                activeSuggestions.add(w);
                if (activeSuggestions.size() >= 3) break;
            }
        }

        if (!activeSuggestions.isEmpty()) {
            suggestionsContainer.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < activeSuggestions.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(activeSuggestions.get(i)).append("   ");
            }
            txtSuggestions.setText(sb.toString().trim());
        } else {
            suggestionsContainer.setVisibility(View.GONE);
        }
    }

    // Apply selected suggestion to text buffer
    void applySuggestion(int index) {
        if (index < 0 || index >= activeSuggestions.size()) return;
        String suggestion = activeSuggestions.get(index);

        String currentText = typedText.toString();
        int lastSpace = currentText.lastIndexOf(" ");
        if (lastSpace == -1) {
            typedText.setLength(0);
            typedText.append(suggestion).append(" ");
        } else {
            typedText.setLength(lastSpace + 1);
            typedText.append(suggestion).append(" ");
        }
        editMessage.setText(typedText.toString());
        updateSuggestions();
    }

    // RESET TO HOME SCREEN
    void showHome() {
        clearEmergencyMode();
        screen = 0;
        txtStateIndicator.setText("MODE: HOME");
        question.setText("How can I help you today?");

        btn1.setVisibility(View.VISIBLE);
        btn2.setVisibility(View.VISIBLE);
        btn3.setVisibility(View.VISIBLE);
        btn4.setVisibility(View.VISIBLE);

        editMessage.setVisibility(View.GONE);
        btnSend.setVisibility(View.GONE);
        suggestionsContainer.setVisibility(View.GONE);

        btn1.setText("[1] CHATBOT");
        btn2.setText("[2] QUICK PHRASES");
        btn3.setText("[3] BLINK TYPING");
        btn4.setText("[4] EMERGENCY");
    }

    // ==========================================
    // OPTION ROUTERS
    // ==========================================

    void option1() {
        if (isEmergencyActive) {
            handleEmergencyOption(1);
            return;
        }
        if (screen == 0) {
            enterChatbotMode();
            return;
        }

        // Quick Phrases Branch (must be before chatbot — screens 200+ also match >= 100)
        if (screen >= 200) {
            handlePhrasesOption(1);
            return;
        }

        // Chatbot Branch
        if (screen >= 100) {
            handleChatbotOption(1);
            return;
        }

        // Keyboard Branch
        if (screen >= 50 && screen < 100) {
            handleKeyboardOption(1);
            return;
        }
    }

    void option2() {
        if (isEmergencyActive) {
            handleEmergencyOption(2);
            return;
        }
        if (screen == 0) {
            enterPhrasesMode();
            return;
        }

        if (screen >= 200) {
            handlePhrasesOption(2);
            return;
        }

        if (screen >= 100) {
            handleChatbotOption(2);
            return;
        }

        if (screen >= 50 && screen < 100) {
            handleKeyboardOption(2);
            return;
        }
    }

    void option3() {
        if (isEmergencyActive) {
            handleEmergencyOption(3);
            return;
        }
        if (screen == 0) {
            enterKeyboardMode();
            return;
        }

        if (screen >= 200) {
            handlePhrasesOption(3);
            return;
        }

        if (screen >= 100) {
            handleChatbotOption(3);
            return;
        }

        if (screen >= 50 && screen < 100) {
            handleKeyboardOption(3);
            return;
        }
    }

    void option4() {
        if (isEmergencyActive) {
            handleEmergencyOption(4);
            return;
        }
        if (screen == 0) {
            triggerSOS();
            return;
        }

        if (screen >= 200) {
            handlePhrasesOption(4);
            return;
        }

        if (screen >= 100) {
            handleChatbotOption(4);
            return;
        }

        if (screen >= 50 && screen < 100) {
            handleKeyboardOption(4);
            return;
        }
    }

    // ==========================================
    // CHATBOT FLOW IMPLEMENTATION
    // ==========================================

    void enterChatbotMode() {
        screen = 100;
        txtStateIndicator.setText("MODE: CHATBOT");
        logEvent("CHATBOT", "Hello! How are you feeling today?");
        speak("Hello! How are you feeling today?");

        btn1.setText("[1] Good");
        btn2.setText("[2] Not Good");
        btn3.setText("[3] Something Else");
        btn4.setText("[4] Exit Chatbot");
    }

    void handleChatbotOption(int btn) {
        // Screen 100: Intro question
        if (screen == 100) {
            if (btn == 1) {
                logEvent("USER", "Good");
                screen = 101;
                logEvent("CHATBOT", "Great! Would you like to do something?");
                speak("Great! Would you like to do something?");
                btn1.setText("[1] Go Outside");
                btn2.setText("[2] Watch Movie");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                logEvent("USER", "Not Good");
                screen = 104;
                logEvent("CHATBOT", "I'm sorry. Are you in pain?");
                speak("I'm sorry. Are you in pain?");
                btn1.setText("[1] Yes, I have pain");
                btn2.setText("[2] No, daily needs request");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                // Something else -> open keyboard
                previousScreen = 100;
                enterKeyboardMode();
            } else {
                showHome();
            }
            return;
        }

        // Screen 101: Feel Good subscreen
        if (screen == 101) {
            if (btn == 1) {
                logEvent("USER", "Go Outside");
                screen = 102;
                logEvent("CHATBOT", "Do you want to go to the park?");
                speak("Do you want to go to the park?");
                btn1.setText("[1] Yes, go to park");
                btn2.setText("[2] No, go for walk");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                logEvent("USER", "Watch Movie");
                screen = 103;
                logEvent("CHATBOT", "Would you like to watch a Comedy?");
                speak("Would you like to watch a Comedy?");
                btn1.setText("[1] Yes, comedy movie");
                btn2.setText("[2] No, action movie");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                previousScreen = 101;
                enterKeyboardMode();
            } else {
                enterChatbotMode();
            }
            return;
        }

        // Screen 102: Go outside details
        if (screen == 102) {
            if (btn == 1) {
                logEvent("USER", "Yes, go to park");
                speak("Let's go to the park!");
                logEvent("CHATBOT", "Request sent. I will ask caregiver to take you to the park.");
                showHome();
            } else if (btn == 2) {
                logEvent("USER", "No, go for walk");
                speak("Let's go for a walk!");
                logEvent("CHATBOT", "Request sent. Caregiver notified for a walk.");
                showHome();
            } else if (btn == 3) {
                previousScreen = 102;
                enterKeyboardMode();
            } else {
                screen = 101;
                btn1.setText("[1] Go Outside");
                btn2.setText("[2] Watch Movie");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
                speak("Great! Would you like to do something?");
            }
            return;
        }

        // Screen 103: Watch movie details
        if (screen == 103) {
            if (btn == 1) {
                logEvent("USER", "Yes, comedy movie");
                speak("Playing a comedy movie.");
                logEvent("CHATBOT", "Let's play a comedy movie for you.");
                showHome();
            } else if (btn == 2) {
                logEvent("USER", "No, action movie");
                speak("Playing an action movie.");
                logEvent("CHATBOT", "Let's play an action movie for you.");
                showHome();
            } else if (btn == 3) {
                previousScreen = 103;
                enterKeyboardMode();
            } else {
                screen = 101;
                btn1.setText("[1] Go Outside");
                btn2.setText("[2] Watch Movie");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
                speak("Great! Would you like to do something?");
            }
            return;
        }

        // Screen 104: Not feeling well subscreen
        if (screen == 104) {
            if (btn == 1) {
                logEvent("USER", "Yes, in pain");
                screen = 105;
                logEvent("CHATBOT", "Where is the pain?");
                speak("Where is the pain?");
                btn1.setText("[1] Head");
                btn2.setText("[2] Chest");
                btn3.setText("[3] Stomach");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                logEvent("USER", "No pain, daily needs");
                screen = 106;
                logEvent("CHATBOT", "What daily need do you request?");
                speak("What daily need do you request?");
                btn1.setText("[1] Water");
                btn2.setText("[2] Food");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                previousScreen = 104;
                enterKeyboardMode();
            } else {
                enterChatbotMode();
            }
            return;
        }

        // Screen 105: Pain Location
        if (screen == 105) {
            if (btn == 1) {
                logEvent("USER", "Head pain");
                speak("Headache reported.");
                logEvent("CHATBOT", "Caregiver has been alerted of your headache.");
                showHome();
            } else if (btn == 2) {
                logEvent("USER", "Chest pain");
                speak("Chest pain reported.");
                logEvent("CHATBOT", "ALERT: Caregiver and doctor notified for chest pain.");
                postLogToServer("SYSTEM", "CHEST PAIN ALERT", true);
                showHome();
            } else if (btn == 3) {
                logEvent("USER", "Stomach pain");
                speak("Stomach pain reported.");
                logEvent("CHATBOT", "Caregiver alerted of stomach pain.");
                showHome();
            } else {
                screen = 104;
                btn1.setText("[1] Yes, I have pain");
                btn2.setText("[2] No, daily needs request");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
                speak("I'm sorry. Are you in pain?");
            }
            return;
        }

        // Screen 106: Daily Needs inside chatbot
        if (screen == 106) {
            if (btn == 1) {
                logEvent("USER", "Water");
                speak("Water requested.");
                logEvent("CHATBOT", "I will request water for you immediately.");
                showHome();
            } else if (btn == 2) {
                logEvent("USER", "Food");
                speak("Food requested.");
                logEvent("CHATBOT", "I will notify caregiver to bring you food.");
                showHome();
            } else if (btn == 3) {
                previousScreen = 106;
                enterKeyboardMode();
            } else {
                screen = 104;
                btn1.setText("[1] Yes, I have pain");
                btn2.setText("[2] No, daily needs request");
                btn3.setText("[3] Something Else");
                btn4.setText("[4] Back");
                speak("I'm sorry. Are you in pain?");
            }
            return;
        }
    }

    // ==========================================
    // QUICK PHRASES IMPLEMENTATION
    // ==========================================

    void enterPhrasesMode() {
        screen = 200;
        txtStateIndicator.setText("MODE: QUICK PHRASES");
        question.setText("Select a category");
        speak("Select a category");

        btn1.setText("[1] Daily Needs");
        btn2.setText("[2] Health Needs");
        btn3.setText("[3] Entertainment");
        btn4.setText("[4] Back to Home");
    }

    void handlePhrasesOption(int btn) {
        if (screen == 200) {
            if (btn == 1) {
                screen = 201;
                question.setText("Daily Needs");
                speak("Daily Needs");
                btn1.setText("[1] Water");
                btn2.setText("[2] Food");
                btn3.setText("[3] Washroom");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                screen = 202;
                question.setText("Health Needs");
                speak("Health Needs");
                btn1.setText("[1] Medicine");
                btn2.setText("[2] Doctor");
                btn3.setText("[3] Nurse");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                screen = 203;
                question.setText("Entertainment");
                speak("Choose Entertainment");
                btn1.setText("[1] Music");
                btn2.setText("[2] Watch TV");
                btn3.setText("[3] Read Book");
                btn4.setText("[4] Back");
            } else {
                showHome();
            }
            return;
        }

        // Daily Needs submenu
        if (screen == 201) {
            if (btn == 1) {
                screen = 204;
                question.setText("Which water?");
                speak("Which water?");
                btn1.setText("[1] Cold");
                btn2.setText("[2] Warm");
                btn3.setText("[3] Normal");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                speak("Requesting Food.");
                logEvent("USER", "Requesting Food");
                showHome();
            } else if (btn == 3) {
                speak("Assistance for washroom requested.");
                logEvent("USER", "Requesting Washroom Assistance");
                showHome();
            } else {
                enterPhrasesMode();
            }
            return;
        }

        // Water submenu
        if (screen == 204) {
            if (btn == 1) {
                speak("Cold water requested.");
                logEvent("USER", "Cold Water Requested");
                showHome();
            } else if (btn == 2) {
                speak("Warm water requested.");
                logEvent("USER", "Warm Water Requested");
                showHome();
            } else if (btn == 3) {
                speak("Normal water requested.");
                logEvent("USER", "Normal Water Requested");
                showHome();
            } else {
                screen = 201;
                question.setText("Daily Needs");
                speak("Daily Needs");
                btn1.setText("[1] Water");
                btn2.setText("[2] Food");
                btn3.setText("[3] Washroom");
                btn4.setText("[4] Back");
            }
            return;
        }

        // Health submenu
        if (screen == 202) {
            if (btn == 1) {
                speak("Medicine requested.");
                logEvent("USER", "Medicine Requested");
                showHome();
            } else if (btn == 2) {
                speak("Notifying doctor.");
                logEvent("USER", "Calling Doctor");
                showHome();
            } else if (btn == 3) {
                speak("Notifying Nurse.");
                logEvent("USER", "Calling Nurse");
                showHome();
            } else {
                enterPhrasesMode();
            }
            return;
        }

        // Entertainment submenu
        if (screen == 203) {
            if (btn == 1) {
                speak("Playing music.");
                logEvent("USER", "Play Music");
                showHome();
            } else if (btn == 2) {
                speak("Turning on TV.");
                logEvent("USER", "Watch TV");
                showHome();
            } else if (btn == 3) {
                speak("Read book request.");
                logEvent("USER", "Read Book");
                showHome();
            } else {
                enterPhrasesMode();
            }
            return;
        }
    }

    // ==========================================
    // BLINK KEYBOARD IMPLEMENTATION (TYPING)
    // ==========================================

    void enterKeyboardMode() {
        screen = 50;
        txtStateIndicator.setText("MODE: KEYBOARD (ROOT)");
        question.setText("Type your message below:");
        
        btn1.setVisibility(View.VISIBLE);
        btn2.setVisibility(View.VISIBLE);
        btn3.setVisibility(View.VISIBLE);
        btn4.setVisibility(View.VISIBLE);
        editMessage.setVisibility(View.VISIBLE);
        btnSend.setVisibility(View.VISIBLE);

        editMessage.setText(typedText.toString());
        updateSuggestions();
        showRootKeyboardButtons();
    }

    void showRootKeyboardButtons() {
        btn1.setText("[1] A - I");
        btn2.setText("[2] J - R");
        btn3.setText("[3] S - Z");
        
        if (!activeSuggestions.isEmpty()) {
            btn4.setText("[4] Suggestions / Actions");
        } else {
            btn4.setText("[4] Space / Clear / Speak");
        }
    }

    void handleKeyboardOption(int btn) {
        // Screen 50: Root Menu
        if (screen == 50) {
            if (btn == 1) {
                screen = 51; // A - I
                btn1.setText("[1] A B C");
                btn2.setText("[2] D E F");
                btn3.setText("[3] G H I");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                screen = 52; // J - R
                btn1.setText("[1] J K L");
                btn2.setText("[2] M N O");
                btn3.setText("[3] P Q R");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                screen = 53; // S - Z
                btn1.setText("[1] S T U");
                btn2.setText("[2] V W X");
                btn3.setText("[3] Y Z");
                btn4.setText("[4] Back");
            } else {
                if (!activeSuggestions.isEmpty()) {
                    // Go to Suggestion list screen
                    screen = 63;
                    btn1.setText("[1] " + activeSuggestions.get(0));
                    btn2.setText("[2] " + (activeSuggestions.size() > 1 ? activeSuggestions.get(1) : "-"));
                    btn3.setText("[3] " + (activeSuggestions.size() > 2 ? activeSuggestions.get(2) : "-"));
                    btn4.setText("[4] Other Actions");
                } else {
                    // Go directly to Actions
                    showActionsKeyboard();
                }
            }
            return;
        }

        // Screen 51: A - I Subgroups
        if (screen == 51) {
            if (btn == 1) {
                screen = 54; // A B C
                btn1.setText("[1] A");
                btn2.setText("[2] B");
                btn3.setText("[3] C");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                screen = 55; // D E F
                btn1.setText("[1] D");
                btn2.setText("[2] E");
                btn3.setText("[3] F");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                screen = 56; // G H I
                btn1.setText("[1] G");
                btn2.setText("[2] H");
                btn3.setText("[3] I");
                btn4.setText("[4] Back");
            } else {
                enterKeyboardMode();
            }
            return;
        }

        // Screen 52: J - R Subgroups
        if (screen == 52) {
            if (btn == 1) {
                screen = 57; // J K L
                btn1.setText("[1] J");
                btn2.setText("[2] K");
                btn3.setText("[3] L");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                screen = 58; // M N O
                btn1.setText("[1] M");
                btn2.setText("[2] N");
                btn3.setText("[3] O");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                screen = 59; // P Q R
                btn1.setText("[1] P");
                btn2.setText("[2] Q");
                btn3.setText("[3] R");
                btn4.setText("[4] Back");
            } else {
                enterKeyboardMode();
            }
            return;
        }

        // Screen 53: S - Z Subgroups
        if (screen == 53) {
            if (btn == 1) {
                screen = 60; // S T U
                btn1.setText("[1] S");
                btn2.setText("[2] T");
                btn3.setText("[3] U");
                btn4.setText("[4] Back");
            } else if (btn == 2) {
                screen = 61; // V W X
                btn1.setText("[1] V");
                btn2.setText("[2] W");
                btn3.setText("[3] X");
                btn4.setText("[4] Back");
            } else if (btn == 3) {
                screen = 62; // Y Z
                btn1.setText("[1] Y");
                btn2.setText("[2] Z");
                btn3.setText("[3] Clear All");
                btn4.setText("[4] Back");
            } else {
                enterKeyboardMode();
            }
            return;
        }

        // Specific Letter Appends
        if (screen == 54) { // A B C
            if (btn == 1) appendChar('A');
            else if (btn == 2) appendChar('B');
            else if (btn == 3) appendChar('C');
            enterKeyboardMode();
            return;
        }
        if (screen == 55) { // D E F
            if (btn == 1) appendChar('D');
            else if (btn == 2) appendChar('E');
            else if (btn == 3) appendChar('F');
            enterKeyboardMode();
            return;
        }
        if (screen == 56) { // G H I
            if (btn == 1) appendChar('G');
            else if (btn == 2) appendChar('H');
            else if (btn == 3) appendChar('I');
            enterKeyboardMode();
            return;
        }
        if (screen == 57) { // J K L
            if (btn == 1) appendChar('J');
            else if (btn == 2) appendChar('K');
            else if (btn == 3) appendChar('L');
            enterKeyboardMode();
            return;
        }
        if (screen == 58) { // M N O
            if (btn == 1) appendChar('M');
            else if (btn == 2) appendChar('N');
            else if (btn == 3) appendChar('O');
            enterKeyboardMode();
            return;
        }
        if (screen == 59) { // P Q R
            if (btn == 1) appendChar('P');
            else if (btn == 2) appendChar('Q');
            else if (btn == 3) appendChar('R');
            enterKeyboardMode();
            return;
        }
        if (screen == 60) { // S T U
            if (btn == 1) appendChar('S');
            else if (btn == 2) appendChar('T');
            else if (btn == 3) appendChar('U');
            enterKeyboardMode();
            return;
        }
        if (screen == 61) { // V W X
            if (btn == 1) appendChar('V');
            else if (btn == 2) appendChar('W');
            else if (btn == 3) appendChar('X');
            enterKeyboardMode();
            return;
        }
        if (screen == 62) { // Y Z + Clear
            if (btn == 1) appendChar('Y');
            else if (btn == 2) appendChar('Z');
            else if (btn == 3) {
                typedText.setLength(0);
                editMessage.setText("");
                updateSuggestions();
            }
            enterKeyboardMode();
            return;
        }

        // Screen 63: Suggestions selection
        if (screen == 63) {
            if (btn == 1) {
                applySuggestion(0);
                enterKeyboardMode();
            } else if (btn == 2 && activeSuggestions.size() > 1) {
                applySuggestion(1);
                enterKeyboardMode();
            } else if (btn == 3 && activeSuggestions.size() > 2) {
                applySuggestion(2);
                enterKeyboardMode();
            } else {
                showActionsKeyboard();
            }
            return;
        }

        // Screen 64: Actions Keyboard (Space / Backspace / Send / Exit)
        if (screen == 64) {
            if (btn == 1) {
                typedText.append(" ");
                editMessage.setText(typedText.toString());
                updateSuggestions();
                enterKeyboardMode();
            } else if (btn == 2) {
                if (typedText.length() > 0) {
                    typedText.deleteCharAt(typedText.length() - 1);
                    editMessage.setText(typedText.toString());
                    updateSuggestions();
                }
                enterKeyboardMode();
            } else if (btn == 3) {
                sendCustomMessage(); // Speak, log, and exit
            } else {
                // Back to keyboard root
                enterKeyboardMode();
            }
            return;
        }
    }

    void showActionsKeyboard() {
        screen = 64;
        btn1.setText("[1] Space");
        btn2.setText("[2] Backspace");
        btn3.setText("[3] Speak & Send");
        btn4.setText("[4] Exit Keyboard");
    }

    void appendChar(char c) {
        typedText.append(String.valueOf(c).toLowerCase());
        editMessage.setText(typedText.toString());
        updateSuggestions();
    }

    // Handles sending the custom message typed inside Keyboard Mode
    void sendCustomMessage() {
        String msg = editMessage.getText().toString().trim();
        if (!msg.isEmpty()) {
            speak(msg);
            logEvent("USER", msg);
            typedText.setLength(0);
            editMessage.setText("");
            activeSuggestions.clear();
            suggestionsContainer.setVisibility(View.GONE);
            
            // If we came from a Chatbot subquestion, return to chatbot
            if (previousScreen >= 100) {
                screen = previousScreen;
                previousScreen = 0;
                
                // Chatbot replies to user typed response
                String reply = "Got it! Notifying caregiver about: " + msg;
                logEvent("CHATBOT", reply);
                
                // Short delay before chatbot speaks the reply
                handler.postDelayed(() -> speak(reply), 1000);
                
                // Return to appropriate chatbot button options
                setupChatbotButtonsAfterType();
            } else {
                showHome();
            }
        } else {
            showHome();
        }
    }

    void setupChatbotButtonsAfterType() {
        txtStateIndicator.setText("MODE: CHATBOT");
        btn1.setText("[1] Good");
        btn2.setText("[2] Not Good");
        btn3.setText("[3] Something Else");
        btn4.setText("[4] Exit Chatbot");
        
        editMessage.setVisibility(View.GONE);
        btnSend.setVisibility(View.GONE);
        suggestionsContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (emergencyTone != null) {
            emergencyTone.release();
            emergencyTone = null;
        }
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(blinkReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }
}