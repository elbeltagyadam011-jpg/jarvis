package com.adam.jarvis;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.hardware.camera2.CameraManager;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import android.text.InputType;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextView status, assistant, transcript, log;
    private Button talk;
    private CheckBox allowApps, allowSettings, allowVolume, allowTorch, allowMedia, allowUtilities, allowCamera, allowAI, wakeMode;
    private boolean sessionListening = false;
    private boolean waitingForWakeWord = false;
    private EditText apiKey, modelName;
    private GeminiBridge gemini;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SharedPreferences prefs;
    private JarvisMemory memory;
    private boolean listening = false;
    private CameraManager cameraManager;
    private String cameraId;
    private static final int MIC = 41;
    private static final int CAM = 42;
    private static final int MAX_AI_TOOL_CALLS = 3;
    private int aiToolCalls = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        // Protect the API-key/settings screen from screenshots and screen recording.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        prefs = getSharedPreferences("jarvis", MODE_PRIVATE);
        memory = new JarvisMemory(this);
        bind();
        loadSwitches();
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tts = new TextToSpeech(this, this);
        cameraManager = (CameraManager)getSystemService(CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics c =
                    cameraManager.getCameraCharacteristics(id);
                Integer facing = c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                Boolean flash = c.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash && facing != null &&
                    facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id; break;
                }
            }
        } catch(Exception ignored) {}

        talk.setOnClickListener(v -> {
            if (listening || sessionListening) { stopListening(); return; }
            if (wakeMode.isChecked()) { sessionListening=true; startRecognition(true); } else listen();
        });
        setQuick();
        bindSwitchListeners();
        String last = memory.getLastCommand();
        if (last.isEmpty()) say("Welcome back, Sir.");
        else say("Welcome back, Sir. Last command received: " + last);
    }

    private void bind() {
        status=findViewById(R.id.status); assistant=findViewById(R.id.assistant);
        transcript=findViewById(R.id.transcript); log=findViewById(R.id.log);
        talk=findViewById(R.id.talk);
        allowApps=findViewById(R.id.allowApps); allowSettings=findViewById(R.id.allowSettings);
        allowVolume=findViewById(R.id.allowVolume); allowTorch=findViewById(R.id.allowTorch);
        allowMedia=findViewById(R.id.allowMedia); allowUtilities=findViewById(R.id.allowUtilities);
        allowCamera=findViewById(R.id.allowCamera);
        allowAI=findViewById(R.id.allowAI);
        wakeMode=findViewById(R.id.wakeMode);
        apiKey=findViewById(R.id.apiKey);
        modelName=findViewById(R.id.modelName);
        gemini=new GeminiBridge();
    }

    private void bindSwitchListeners() {
        CompoundButton.OnCheckedChangeListener l=(b,c)->prefs.edit().putBoolean(b.getId()+"",c).apply();
        allowApps.setOnCheckedChangeListener(l); allowSettings.setOnCheckedChangeListener(l);
        allowVolume.setOnCheckedChangeListener(l); allowTorch.setOnCheckedChangeListener(l);
        allowMedia.setOnCheckedChangeListener(l); allowUtilities.setOnCheckedChangeListener(l);
        allowCamera.setOnCheckedChangeListener(l); allowAI.setOnCheckedChangeListener(l); wakeMode.setOnCheckedChangeListener((b,c)->{prefs.edit().putBoolean("wake_mode",c).apply();});
    }

    private void loadSwitches() {
        // Least-privilege defaults: every device-control capability starts disabled.
        // Existing user choices are preserved once they have been explicitly saved.
        allowApps.setChecked(prefs.getBoolean(R.id.allowApps+"",false));
        allowSettings.setChecked(prefs.getBoolean(R.id.allowSettings+"",false));
        allowVolume.setChecked(prefs.getBoolean(R.id.allowVolume+"",false));
        allowTorch.setChecked(prefs.getBoolean(R.id.allowTorch+"",false));
        allowMedia.setChecked(prefs.getBoolean(R.id.allowMedia+"",false));
        allowUtilities.setChecked(prefs.getBoolean(R.id.allowUtilities+"",false));
        allowCamera.setChecked(prefs.getBoolean(R.id.allowCamera+"",false));
        allowAI.setChecked(prefs.getBoolean(R.id.allowAI+"",false));
        wakeMode.setChecked(prefs.getBoolean("wake_mode", false));
        apiKey.setText(SecureStore.get(this));
        modelName.setText(prefs.getString("gemini_model","gemini-3.8-flash"));
    }

    private boolean saveAISettings() {
        String key = apiKey.getText().toString().trim();
        if (key.length() > 0 && (key.length() < 10 || key.length() > 256 || key.matches(".*\\s+.*"))) {
            respond("The Gemini API key format looks invalid, Sir.");
            return false;
        }
        SecureStore.put(this, key);
        prefs.edit().putString("gemini_model", modelName.getText().toString().trim()).apply();
        return true;
    }

    private void setQuick() {
        findViewById(R.id.qYouTube).setOnClickListener(v->execute("افتح يوتيوب"));
        findViewById(R.id.qChrome).setOnClickListener(v->execute("افتح كروم"));
        findViewById(R.id.qSettings).setOnClickListener(v->execute("افتح الاعدادات"));
        findViewById(R.id.qTorch).setOnClickListener(v->execute("شغل الكشاف"));
        findViewById(R.id.qVolumeUp).setOnClickListener(v->execute("ارفع الصوت"));
        findViewById(R.id.qVolumeDown).setOnClickListener(v->execute("وطي الصوت"));
        findViewById(R.id.qBattery).setOnClickListener(v->execute("البطارية"));
        findViewById(R.id.qTime).setOnClickListener(v->execute("الساعة"));
        findViewById(R.id.qCamera).setOnClickListener(v->execute("افتح الكاميرا"));
        findViewById(R.id.qPlay).setOnClickListener(v->execute("شغل او وقف"));
        findViewById(R.id.qCalendar).setOnClickListener(v->execute("افتح التقويم"));
        findViewById(R.id.qContacts).setOnClickListener(v->execute("افتح جهات الاتصال"));
        findViewById(R.id.qDisplay).setOnClickListener(v->execute("اعدادات الشاشة"));
        findViewById(R.id.qStatus).setOnClickListener(v->execute("حالة الجهاز"));
    }

    private void stopListening() {
        sessionListening=false; waitingForWakeWord=false;
        if(recognizer!=null){ try{recognizer.cancel();}catch(Exception ignored){} recognizer.destroy(); recognizer=null; }
        listening=false; status.setText("SYSTEM ONLINE"); talk.setText("●  TALK TO JARVIS");
        addLog("JARVIS: Voice session stopped.");
    }

    private void listen() {
        sessionListening = false;
        waitingForWakeWord = false;
        startRecognition(false);
    }

    private void listenForSession() {
        if (!wakeMode.isChecked()) return;
        waitingForWakeWord = !sessionListening;
        startRecognition(waitingForWakeWord);
    }

    private void startRecognition(boolean wakeOnly) {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            respond("Speech recognition is not available on this device, Sir.");
            return;
        }

        if (recognizer!=null) recognizer.destroy();
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p){
                listening=true;
                status.setText(wakeOnly ? "SAY JARVIS…" : "LISTENING…");
                talk.setText(wakeOnly ? "◉  WAITING FOR JARVIS" : "●  LISTENING");
            }
            public void onBeginningOfSpeech(){status.setText("HEARING YOUR COMMAND");}
            public void onEndOfSpeech(){status.setText("PROCESSING");}
            public void onError(int e){
                listening=false;
                status.setText("SYSTEM ONLINE");
                talk.setText("●  TALK TO JARVIS");
                if (wakeMode.isChecked() && (wakeOnly || sessionListening)) {
                    new Handler().postDelayed(() -> startRecognition(wakeOnly), 500);
                } else {
                    respond("I didn't catch that, Sir.");
                }
            }
            public void onResults(Bundle r){
                listening=false;
                status.setText("SYSTEM ONLINE");
                talk.setText("●  TALK TO JARVIS");
                ArrayList<String> a=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(a==null || a.isEmpty()){
                    if (wakeMode.isChecked() && (wakeOnly || sessionListening))
                        new Handler().postDelayed(() -> startRecognition(wakeOnly), 400);
                    return;
                }

                String heard=a.get(0);
                transcript.setText("You: "+heard);
                String low=heard.toLowerCase(Locale.ROOT);

                if (wakeOnly) {
                    if (low.contains("jarvis") || low.contains("جارفيس")) {
                        waitingForWakeWord=false;
                        sessionListening=true;
                        respond("Yes, Sir?");
                        new Handler().postDelayed(() -> startRecognition(false), 500);
                    } else if (wakeMode.isChecked()) {
                        new Handler().postDelayed(() -> startRecognition(true), 400);
                    }
                    return;
                }

                execute(heard);
                if (sessionListening && wakeMode.isChecked())
                    new Handler().postDelayed(() -> startRecognition(false), 700);
            }
            public void onPartialResults(Bundle r){}
            public void onEvent(int t,Bundle b){}
            public void onRmsChanged(float r){}
            public void onBufferReceived(byte[] b){}
        });

        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-EG");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ar-EG");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
        recognizer.startListening(i);
    }

    private void execute(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        memory.setLastCommand(raw);

        java.util.List<String> commands = JarvisParser.splitCommands(raw);
        if (commands.size() > 3) {
            respond("For safety, I will not execute more than three commands at once, Sir.");
            return;
        }
        if (commands.size() > 1) {
            StringBuilder batch = new StringBuilder("JARVIS will execute these commands one by one:\n");
            for (String command : commands) batch.append("• ").append(command.trim()).append("\n");
            confirmLocalAction(batch.toString().trim(), () -> {
                for (String command : commands) executeSingle(command, true);
            });
        } else {
            executeSingle(raw, false);
        }
    }

    private void executeSingle(String raw) { executeSingle(raw, false); }

    private void executeSingle(String raw, boolean confirmed) {
        JarvisIntent intent = JarvisParser.parse(raw);
        addLog("You: " + raw);
        if (!confirmed && requiresConfirmation(intent.type)) {
            confirmLocalAction(raw, () -> executeSingle(raw, true));
            return;
        }

        switch (intent.type) {
            case GREETING:
                respond("Welcome back, Sir. How can I assist you?");
                break;
            case IDENTITY:
                respond("I am JARVIS, your controlled personal assistant. You decide what I am allowed to control.");
                break;
            case OPEN_YOUTUBE:
                if (!allowApps.isChecked()) { deny(); break; }
                openPackage("com.google.android.youtube","YouTube");
                break;
            case OPEN_CHROME:
                if (!allowApps.isChecked()) { deny(); break; }
                openPackage("com.android.chrome","Chrome");
                break;
            case OPEN_PHONE:
                if (!allowApps.isChecked()) { deny(); break; }
                openIntent("android.intent.action.DIAL",null,"Phone");
                break;
            case OPEN_MESSAGES:
                if (!allowApps.isChecked()) { deny(); break; }
                openIntent("android.intent.action.MAIN","android.intent.category.APP_MESSAGING","Messages");
                break;
            case OPEN_MAPS:
                if (!allowApps.isChecked()) { deny(); break; }
                openIntent("android.intent.action.VIEW","geo:0,0","Maps");
                break;
            case OPEN_GALLERY:
                if (!allowApps.isChecked()) { deny(); break; }
                openIntent("android.intent.action.VIEW","content://media/internal/images/media","Gallery");
                break;
            case OPEN_SETTINGS:
                if (!allowSettings.isChecked()) { deny(); break; }
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                respond("Opening settings, Sir.");
                break;
            case OPEN_WIFI:
                if (!allowSettings.isChecked()) { deny(); break; }
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                respond("Opening Wi-Fi settings, Sir.");
                break;
            case OPEN_BLUETOOTH:
                if (!allowSettings.isChecked()) { deny(); break; }
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                respond("Opening Bluetooth settings, Sir.");
                break;
            case TORCH_ON:
                if (!allowTorch.isChecked()) { deny(); break; }
                setTorch(true);
                break;
            case TORCH_OFF:
                if (!allowTorch.isChecked()) { deny(); break; }
                setTorch(false);
                break;
            case VOLUME_UP:
                volume(true);
                break;
            case VOLUME_DOWN:
                volume(false);
                break;
            case MEDIA_TOGGLE:
                if (!allowMedia.isChecked()) { deny(); break; }
                AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                respond("Media command sent, Sir.");
                break;
            case BATTERY:
                if (!allowUtilities.isChecked()) { deny(); break; }
                IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent b=registerReceiver(null,f);
                int level=b.getIntExtra("level",-1), scale=b.getIntExtra("scale",-1);
                int percent=(scale > 0) ? (int)(100f*level/scale) : -1;
                respond("Battery level is " + percent + " percent, Sir.");
                break;
            case TIME:
                if (!allowUtilities.isChecked()) { deny(); break; }
                String time=new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date());
                respond("The time is " + time + ", Sir.");
                break;
            case DATE:
                if (!allowUtilities.isChecked()) { deny(); break; }
                String date=new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date());
                respond("Today is " + date + ", Sir.");
                break;
            case CAMERA:
                if (!allowCamera.isChecked()) { deny(); break; }
                try {
                    startActivity(new Intent("android.media.action.IMAGE_CAPTURE"));
                    respond("Opening the camera, Sir.");
                } catch(Exception e) {
                    respond("The camera app is unavailable, Sir.");
                }
                break;
            case CALCULATOR:
                if (!allowUtilities.isChecked()) { deny(); break; }
                try {
                    startActivity(new Intent("android.intent.action.MAIN")
                        .addCategory("android.intent.category.APP_CALCULATOR"));
                    respond("Opening calculator, Sir.");
                } catch(Exception e) {
                    respond("I could not find a calculator app, Sir.");
                }
                break;
            case ALARM:
                if (!allowUtilities.isChecked()) { deny(); break; }
                try {
                    startActivity(new Intent(android.provider.AlarmClock.ACTION_SET_ALARM));
                    respond("Opening the alarm control, Sir.");
                } catch(Exception e) {
                    respond("I cannot open the alarm control on this device, Sir.");
                }
                break;
            case GOOGLE:
                if (!allowApps.isChecked()) { deny(); break; }
                startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.google.com")));
                respond("Opening Google, Sir.");
                break;
            case CALENDAR:
                if (!allowApps.isChecked()) { deny(); break; }
                openIntent("android.intent.action.MAIN","android.intent.category.APP_CALENDAR","Calendar");
                break;
            case CONTACTS:
                if (!allowApps.isChecked()) { deny(); break; }
                openIntent("android.intent.action.VIEW","content://contacts/people","Contacts");
                break;
            case DOWNLOADS:
                if (!allowApps.isChecked()) { deny(); break; }
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://downloads/my_downloads"))); respond("Opening Downloads, Sir."); }
                catch(Exception e) { respond("Downloads is not available, Sir."); }
                break;
            case DISPLAY_SETTINGS:
                if (!allowSettings.isChecked()) { deny(); break; }
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); respond("Opening display settings, Sir.");
                break;
            case SOUND_SETTINGS:
                if (!allowSettings.isChecked()) { deny(); break; }
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); respond("Opening sound settings, Sir.");
                break;
            case AIRPLANE_SETTINGS:
                if (!allowSettings.isChecked()) { deny(); break; }
                try { startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); respond("Opening airplane mode settings, Sir."); }
                catch(Exception e) { respond("Airplane mode settings are unavailable, Sir."); }
                break;
            case SEARCH_WEB:
                if (!allowApps.isChecked()) { deny(); break; }
                String q = raw.replaceAll("(?i)(ابحث عن|دور على|ابحث في جوجل|search for|search google)", "").trim();
                if (q.isEmpty()) { respond("What would you like me to search for, Sir?"); break; }
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(q))));
                respond("Searching for " + q + ", Sir.");
                break;
            case DEVICE_STATUS:
                if (!allowUtilities.isChecked()) { deny(); break; }
                IntentFilter sf=new IntentFilter(Intent.ACTION_BATTERY_CHANGED); Intent sb=registerReceiver(null,sf);
                int sl=sb.getIntExtra("level",-1), ss=sb.getIntExtra("scale",-1);
                int sp=ss>0?(int)(100f*sl/ss):-1;
                android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                boolean net=cm!=null && cm.getActiveNetwork()!=null;
                respond("System status: battery " + sp + " percent, network " + (net?"connected":"offline") + ", Sir.");
                break;
            default:
                if (allowAI.isChecked() && !apiKey.getText().toString().trim().isEmpty()) {
                    askAI(raw);
                } else {
                    respond("I can hold a conversation when AI mode is enabled. Your phone controls remain protected, Sir.");
                }
                break;
        }
    }

    private void askAI(String userText) {
        if (!saveAISettings()) return;
        aiToolCalls = 0;
        status.setText("JARVIS AI THINKING…");
        String system =
            "You are JARVIS, a concise, polite personal assistant. " +
            "Speak naturally in Egyptian Arabic when the user speaks Arabic and in English when the user speaks English. " +
            "You may use only the declared Android tools when the user explicitly asks for a phone/browser action. " +
            "Every tool call requires a visible confirmation in the Android app; never assume permission. " +
            "Never invent a successful device action. The Android app is the final permission gate. " +
            "Do not request or perform messaging, calls, deletion, purchases, password changes, shell commands, accessibility actions, or hidden/background actions. " +
            "Do not request battery, time, date, network, contacts, files, images, microphone recordings, or other private device data through tools. " +
            "For ordinary conversation, answer directly. Keep spoken answers reasonably short.";
        String model = modelName.getText().toString().trim().isEmpty() ? "gemini-3.8-flash" : modelName.getText().toString().trim();
        if (!model.matches("[A-Za-z0-9._-]{1,100}")) {
            respond("The Gemini model name is invalid, Sir.");
            return;
        }
        gemini.ask(apiKey.getText().toString().trim(), model, system, userText, new GeminiBridge.Callback() {
            @Override public void onSuccess(String reply) {
                status.setText("SYSTEM ONLINE");
                respond(reply.isEmpty() ? "I have no response, Sir." : reply);
            }
            @Override public void onToolCall(String name, org.json.JSONObject args, String callId, String originalUserText) {
                executeAITool(name, args, originalUserText, system, model);
            }
            @Override public void onError(String message) {
                status.setText("SYSTEM ONLINE");
                respond("AI mode is unavailable right now, Sir. Your phone controls are still working locally.");
                addLog("AI error: " + message);
            }
        });
    }

    private void executeAITool(String name, org.json.JSONObject args, String originalUserText, String system, String model) {
        if (aiToolCalls >= MAX_AI_TOOL_CALLS) {
            addLog("AI tool blocked: call limit reached");
            respond("For safety, I stopped the AI action chain after three device operations, Sir.");
            return;
        }
        if (requiresAIConfirmation(name)) {
            String label = humanToolName(name);
            confirmLocalAction("AI requested: " + label, () -> executeAIToolConfirmed(name, args, originalUserText, system, model));
            return;
        }
        executeAIToolConfirmed(name, args, originalUserText, system, model);
    }

    private void executeAIToolConfirmed(String name, org.json.JSONObject args, String originalUserText, String system, String model) {
        aiToolCalls++;
        addLog("AI tool approved: " + name);
        org.json.JSONObject result = new org.json.JSONObject();
        boolean allowed = true;
        try {
            switch (name) {
                case "open_app":
                    if (!allowApps.isChecked()) { allowed=false; result.put("ok",false).put("reason","app control disabled by user"); break; }
                    String app=args.optString("app","");
                    if (app.equals("youtube")) openPackage("com.google.android.youtube","YouTube");
                    else if (app.equals("chrome")) openPackage("com.android.chrome","Chrome");
                    else if (app.equals("phone")) openIntent("android.intent.action.DIAL",null,"Phone");
                    else if (app.equals("messages")) openIntent("android.intent.action.MAIN","android.intent.category.APP_MESSAGING","Messages");
                    else if (app.equals("maps")) openIntent("android.intent.action.VIEW","geo:0,0","Maps");
                    else if (app.equals("gallery")) openIntent("android.intent.action.VIEW","content://media/internal/images/media","Gallery");
                    else { allowed=false; result.put("ok",false).put("reason","unsupported app"); }
                    break;
                case "open_settings":
                    if (!allowSettings.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Settings.ACTION_SETTINGS)); respond("Opening settings, Sir."); break;
                case "open_wifi_settings":
                    if (!allowSettings.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); respond("Opening Wi-Fi settings, Sir."); break;
                case "open_bluetooth_settings":
                    if (!allowSettings.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); respond("Opening Bluetooth settings, Sir."); break;
                case "set_flashlight":
                    if (!allowTorch.isChecked()) { allowed=false; break; }
                    boolean on=args.optBoolean("on",false); setTorch(on); result.put("state",on?"on":"off"); break;
                case "change_volume":
                    if (!allowVolume.isChecked()) { allowed=false; break; }
                    boolean up=args.optString("direction","up").equals("up"); volume(up); result.put("direction",up?"up":"down"); break;
                case "media_toggle":
                    if (!allowMedia.isChecked()) { allowed=false; break; }
                    AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
                    am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                    am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                    result.put("ok",true); break;
                case "get_battery":
                    if (!allowUtilities.isChecked()) { allowed=false; break; }
                    IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED); Intent b=registerReceiver(null,f);
                    int level=b.getIntExtra("level",-1), scale=b.getIntExtra("scale",-1);
                    result.put("battery_percent", scale>0 ? (int)(100f*level/scale) : -1); break;
                case "get_time":
                    if (!allowUtilities.isChecked()) { allowed=false; break; }
                    result.put("time",new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date())); break;
                case "get_date":
                    if (!allowUtilities.isChecked()) { allowed=false; break; }
                    result.put("date",new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date())); break;
                case "open_camera":
                    if (!allowCamera.isChecked()) { allowed=false; break; }
                    startActivity(new Intent("android.media.action.IMAGE_CAPTURE")); result.put("ok",true); break;
                case "open_calculator":
                    if (!allowUtilities.isChecked()) { allowed=false; break; }
                    startActivity(new Intent("android.intent.action.MAIN").addCategory("android.intent.category.APP_CALCULATOR")); result.put("ok",true); break;
                case "open_alarm":
                    if (!allowUtilities.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(android.provider.AlarmClock.ACTION_SET_ALARM)); result.put("ok",true); break;
                case "open_google":
                    if (!allowApps.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.google.com"))); result.put("ok",true); break;
                case "open_calendar":
                    if (!allowApps.isChecked()) { allowed=false; break; }
                    openIntent("android.intent.action.MAIN","android.intent.category.APP_CALENDAR","Calendar"); result.put("ok",true); break;
                case "open_contacts":
                    if (!allowApps.isChecked()) { allowed=false; break; }
                    openIntent("android.intent.action.VIEW","content://contacts/people","Contacts"); result.put("ok",true); break;
                case "open_downloads":
                    if (!allowApps.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("content://downloads/my_downloads"))); result.put("ok",true); break;
                case "open_display_settings":
                    if (!allowSettings.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); result.put("ok",true); break;
                case "open_sound_settings":
                    if (!allowSettings.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); result.put("ok",true); break;
                case "open_airplane_settings":
                    if (!allowSettings.isChecked()) { allowed=false; break; }
                    startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); result.put("ok",true); break;
                case "search_web":
                    if (!allowApps.isChecked()) { allowed=false; break; }
                    String query=args.optString("query","").trim();
                    if(query.isEmpty()){allowed=false; result.put("ok",false).put("reason","empty search query"); break;}
                    startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.google.com/search?q="+android.net.Uri.encode(query)))); result.put("ok",true).put("query",query); break;
                case "device_status":
                    if (!allowUtilities.isChecked()) { allowed=false; break; }
                    IntentFilter sf2=new IntentFilter(Intent.ACTION_BATTERY_CHANGED); Intent sb2=registerReceiver(null,sf2);
                    int sl2=sb2.getIntExtra("level",-1), ss2=sb2.getIntExtra("scale",-1);
                    int sp2=ss2>0?(int)(100f*sl2/ss2):-1;
                    android.net.ConnectivityManager cm2=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                    result.put("battery_percent",sp2).put("network_connected",cm2!=null && cm2.getActiveNetwork()!=null); break;
                default: allowed=false; result.put("ok",false).put("reason","unknown tool");
            }
        } catch(Exception e) { result.put("ok",false).put("error",e.getMessage()==null?"action failed":e.getMessage()); }
        if (!allowed) { result.put("ok",false).put("reason","that control is disabled in Control Center"); deny(); }
        else result.put("ok",true);

        gemini.sendToolResult(apiKey.getText().toString().trim(), model, system, originalUserText, name, args, result,
            new GeminiBridge.Callback() {
                @Override public void onSuccess(String reply) { status.setText("SYSTEM ONLINE"); respond(reply.isEmpty()?"Done, Sir.":reply); }
                @Override public void onToolCall(String n, org.json.JSONObject a, String id, String text) { executeAITool(n,a,text,system,model); }
                @Override public void onError(String message) { status.setText("SYSTEM ONLINE"); addLog("AI result error: "+message); respond("The local action was handled, Sir."); }
            });
    }

    private boolean requiresConfirmation(JarvisIntent.Type t) {
        switch (t) {
            case OPEN_YOUTUBE:
            case OPEN_CHROME:
            case OPEN_PHONE:
            case OPEN_MESSAGES:
            case OPEN_MAPS:
            case OPEN_GALLERY:
            case OPEN_SETTINGS:
            case OPEN_WIFI:
            case OPEN_BLUETOOTH:
            case TORCH_ON:
            case TORCH_OFF:
            case VOLUME_UP:
            case VOLUME_DOWN:
            case MEDIA_TOGGLE:
            case CAMERA:
            case CALCULATOR:
            case ALARM:
            case GOOGLE:
            case CALENDAR:
            case CONTACTS:
            case DOWNLOADS:
            case DISPLAY_SETTINGS:
            case SOUND_SETTINGS:
            case AIRPLANE_SETTINGS:
            case SEARCH_WEB:
                return true;
            default:
                return false;
        }
    }

    private boolean requiresAIConfirmation(String name) {
        // Every Gemini tool is a device/browser side effect. Require an explicit
        // one-time approval for every tool call; no hidden or automatic actions.
        return true;
    }

    private String humanToolName(String name) {
        if ("open_app".equals(name)) return "open an approved app";
        if ("open_camera".equals(name)) return "open the camera";
        if ("set_flashlight".equals(name)) return "change the flashlight state";
        if ("change_volume".equals(name)) return "change media volume";
        if ("media_toggle".equals(name)) return "control media playback";
        if (name.startsWith("open_")) return "open a phone screen";
        if ("search_web".equals(name)) return "open a web search";
        return name;
    }

    private void confirmLocalAction(String action, final Runnable onConfirm) {
        new AlertDialog.Builder(this)
            .setTitle("JARVIS safety confirmation")
            .setMessage(action + "\n\nDo you want to allow this action now?")
            .setNegativeButton("Cancel", (d, w) -> {
                addLog("Safety confirmation: cancelled");
                respond("Action cancelled, Sir.");
            })
            .setPositiveButton("Allow once", (d, w) -> {
                addLog("Safety confirmation: approved once");
                onConfirm.run();
            })
            .setCancelable(false)
            .show();
    }

    private void openIntent(String action, String dataOrCategory, String name) {
        try {
            Intent i=new Intent(action);
            if (dataOrCategory != null && dataOrCategory.startsWith("content:")) i.setData(android.net.Uri.parse(dataOrCategory));
            else if (dataOrCategory != null && dataOrCategory.startsWith("geo:")) i.setData(android.net.Uri.parse(dataOrCategory));
            else if (dataOrCategory != null) i.addCategory(dataOrCategory);
            startActivity(i); respond("Opening "+name+", Sir.");
        } catch(Exception e) { respond(name+" is not available, Sir."); }
    }

    private void openPackage(String pkg,String name){
        if(!allowApps.isChecked()){deny();return;}
        Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
        if(i!=null){startActivity(i); respond("Opening "+name+", Sir.");}
        else respond(name+" is not installed, Sir.");
    }

    private void volume(boolean up){
        if(!allowVolume.isChecked()){deny();return;}
        AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,up?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);
        respond(up?"Volume increased, Sir.":"Volume decreased, Sir.");
    }

    private void setTorch(boolean on){
        if(cameraId==null){respond("No flashlight is available, Sir.");return;}
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},CAM); return;
        }
        try { cameraManager.setTorchMode(cameraId,on); respond(on?"Flashlight on, Sir.":"Flashlight off, Sir."); }
        catch(Exception e){respond("I could not control the flashlight, Sir.");}
    }

    private boolean contains(String s,String... xs){for(String x:xs)if(s.contains(x.toLowerCase(Locale.ROOT)))return true;return false;}
    private void deny(){respond("That control is disabled in your Control Center, Sir.");}
    private void respond(String text){
        assistant.setText(text); addLog("JARVIS: "+text); say(text);
    }
    private void addLog(String x){
        String old=log.getText().toString();
        String[] lines=old.split("\n");
        StringBuilder b=new StringBuilder();
        int start=Math.max(0,lines.length-7);
        for(int i=start;i<lines.length;i++) if(lines[i].trim().length()>0) b.append(lines[i]).append("\n");
        b.append(x);
        log.setText(b.toString());
    }
    private void say(String text){
        if(tts==null) return;
        boolean arabic=false;
        for(int i=0;i<text.length();i++){ char ch=text.charAt(i); if(ch>='\u0600' && ch<='\u06FF'){ arabic=true; break; } }
        try { tts.setLanguage(arabic ? new Locale("ar","EG") : Locale.US); } catch(Exception ignored) {}
        tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis");
    }
    @Override public void onInit(int result){
        if(result==TextToSpeech.SUCCESS){
            tts.setLanguage(Locale.US);
            tts.setSpeechRate(0.90f);
            tts.setPitch(0.82f);
            say("Welcome back, Sir.");
        }
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){
        super.onRequestPermissionsResult(req,p,g);
        if(req==MIC && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) listen();
        if(req==CAM && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) setTorch(true);
    }
    @Override protected void onDestroy(){
        if(recognizer!=null)recognizer.destroy();
        if(tts!=null){tts.stop();tts.shutdown();}
        if(gemini!=null) gemini.shutdown();
        super.onDestroy();
    }
}
