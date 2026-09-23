package com.adam.jarvis;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gemini REST bridge with structured function calling.
 * The model can request only the tools declared here; MainActivity remains the final permission gate.
 */
public final class GeminiBridge {
    public interface Callback {
        void onSuccess(String reply);
        void onToolCall(String name, JSONObject args, String callId, String originalUserText);
        void onError(String message);
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void ask(String apiKey, String model, String system, String user, Callback cb) {
        pool.execute(() -> {
            try {
                JSONObject response = request(apiKey, model, system, user, null);
                JSONObject part = firstPart(response);
                JSONObject fc = part.optJSONObject("functionCall");
                if (fc != null) {
                    String name = fc.optString("name", "");
                    JSONObject args = fc.optJSONObject("args");
                    if (args == null) args = new JSONObject();
                    String callId = fc.optString("id", "");
                    final JSONObject finalArgs = args;
                    main.post(() -> cb.onToolCall(name, finalArgs, callId, user));
                    return;
                }
                String text = part.optString("text", "").trim();
                main.post(() -> cb.onSuccess(text));
            } catch (Exception e) {
                main.post(() -> cb.onError(e.getMessage() == null ? "AI request failed." : e.getMessage()));
            }
        });
    }

    /** Sends the local tool result back to Gemini so it can produce a natural final answer. */
    public void sendToolResult(String apiKey, String model, String system, String user,
                               String toolName, JSONObject toolArgs, JSONObject toolResult, Callback cb) {
        pool.execute(() -> {
            try {
                JSONObject response = requestWithToolResult(apiKey, model, system, user, toolName, toolArgs, toolResult);
                JSONObject part = firstPart(response);
                JSONObject fc = part.optJSONObject("functionCall");
                if (fc != null) {
                    String name = fc.optString("name", "");
                    JSONObject args = fc.optJSONObject("args");
                    if (args == null) args = new JSONObject();
                    main.post(() -> cb.onToolCall(name, args, fc.optString("id", ""), user));
                    return;
                }
                main.post(() -> cb.onSuccess(part.optString("text", "").trim()));
            } catch (Exception e) {
                main.post(() -> cb.onError(e.getMessage() == null ? "AI tool result failed." : e.getMessage()));
            }
        });
    }

    private JSONObject request(String apiKey, String model, String system, String user, JSONArray contents) throws Exception {
        JSONArray tools = new JSONArray().put(new JSONObject()
            .put("functionDeclarations", buildTools()));

        JSONObject body = baseBody(system, tools);
        if (contents != null) body.put("contents", contents);
        else body.put("contents", new JSONArray().put(userContent(user)));
        return post(apiKey, model, body);
    }

    private JSONObject requestWithToolResult(String apiKey, String model, String system, String user,
                                             String toolName, JSONObject toolArgs, JSONObject result) throws Exception {
        JSONArray contents = new JSONArray();
        contents.put(userContent(user));
        JSONObject callPart = new JSONObject().put("functionCall",
            new JSONObject().put("name", toolName).put("args", toolArgs == null ? new JSONObject() : toolArgs));
        contents.put(new JSONObject().put("role", "model").put("parts", new JSONArray().put(callPart)));
        // Function response follows the model function call.
        JSONObject functionPart = new JSONObject().put("functionResponse",
            new JSONObject().put("name", toolName).put("response", result));
        contents.put(new JSONObject().put("role", "user").put("parts", new JSONArray().put(functionPart)));

        JSONObject body = baseBody(system, new JSONArray().put(new JSONObject().put("functionDeclarations", buildTools())));
        body.put("contents", contents);
        return post(apiKey, model, body);
    }

    private JSONObject baseBody(String system, JSONArray tools) throws Exception {
        JSONObject body = new JSONObject();
        body.put("system_instruction", new JSONObject().put("parts", new JSONArray()
            .put(new JSONObject().put("text", system))));
        body.put("tools", tools);
        body.put("toolConfig", new JSONObject().put("functionCallingConfig",
            new JSONObject().put("mode", "AUTO")));
        body.put("generationConfig", new JSONObject().put("temperature", 0.55).put("maxOutputTokens", 320));
        return body;
    }

    private JSONArray buildTools() throws Exception {
        JSONArray a = new JSONArray();
        a.put(fn("open_app", "Open an approved app. Allowed values: youtube, chrome, phone, messages, maps, gallery.", objProp("app", "string", new String[]{"youtube","chrome","phone","messages","maps","gallery"}), "app"));
        a.put(fn("open_settings", "Open the main Android Settings screen.", new JSONObject(), null));
        a.put(fn("open_wifi_settings", "Open Android Wi-Fi settings.", new JSONObject(), null));
        a.put(fn("open_bluetooth_settings", "Open Android Bluetooth settings.", new JSONObject(), null));
        a.put(fn("set_flashlight", "Turn the phone flashlight on or off.", objProp("on", "boolean", null), "on"));
        a.put(fn("change_volume", "Raise or lower media volume by one step.", objProp("direction", "string", new String[]{"up","down"}), "direction"));
        a.put(fn("media_toggle", "Toggle media play/pause.", new JSONObject(), null));
        a.put(fn("open_camera", "Open the camera app.", new JSONObject(), null));
        a.put(fn("open_calculator", "Open the calculator app.", new JSONObject(), null));
        a.put(fn("open_alarm", "Open the Android alarm interface.", new JSONObject(), null));
        a.put(fn("open_google", "Open Google in the browser.", new JSONObject(), null));
        a.put(fn("open_calendar", "Open the calendar app.", new JSONObject(), null));
        a.put(fn("open_contacts", "Open the contacts app.", new JSONObject(), null));
        a.put(fn("open_downloads", "Open the Downloads screen.", new JSONObject(), null));
        a.put(fn("open_display_settings", "Open Android display settings.", new JSONObject(), null));
        a.put(fn("open_sound_settings", "Open Android sound settings.", new JSONObject(), null));
        a.put(fn("open_airplane_settings", "Open Android airplane mode settings. Do not toggle airplane mode directly.", new JSONObject(), null));
        a.put(fn("search_web", "Open a Google web search for the requested query.", objProp("query", "string", null), "query"));
        return a;
    }

    private JSONObject fn(String name, String desc, JSONObject props, String required) throws Exception {
        JSONObject x = new JSONObject().put("name", name).put("description", desc)
            .put("parameters", new JSONObject().put("type", "object").put("properties", props));
        if (required != null) x.getJSONObject("parameters").put("required", new JSONArray().put(required));
        return x;
    }

    private JSONObject objProp(String name, String type, String[] enums) throws Exception {
        JSONObject p = new JSONObject().put(name, new JSONObject().put("type", type));
        if (enums != null) p.getJSONObject(name).put("enum", new JSONArray(enums));
        return p;
    }

    private JSONObject userContent(String user) throws Exception {
        return new JSONObject().put("role", "user").put("parts", new JSONArray()
            .put(new JSONObject().put("text", user)));
    }

    private JSONObject post(String apiKey, String model, JSONObject body) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("Gemini API key is not configured.");
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" +
            URLEncoder.encode(model, "UTF-8") + ":generateContent?key=" + URLEncoder.encode(apiKey.trim(), "UTF-8"));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setDoOutput(true);
        c.setUseCaches(false);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode();
        String response = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        if (response.length() > 1024 * 1024) throw new IOException("Gemini response was too large.");
        if (code < 200 || code >= 300) throw new IOException("Gemini request failed (" + code + "): " + response);
        return new JSONObject(response);
    }

    private static JSONObject firstPart(JSONObject json) throws Exception {
        return json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0);
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) b.append(line);
        return b.toString();
    }

    public void shutdown() { pool.shutdownNow(); }
}
