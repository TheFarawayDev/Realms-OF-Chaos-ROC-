package com.thefarawaydev.realmsofchaos.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The mod's words, on the server. Almost everything a player reads is sent as a key and put into words by their
 * own game - but a card has to be measured and wrapped before it is sent, and a server does not always load a
 * mod's language file. So the mod reads its own, once, and turns a component into plain text itself.
 */
public final class Words {
    private static Map<String, String> words;

    private Words() {}

    private static Map<String, String> words() {
        if (words == null) {
            Map<String, String> loaded = new HashMap<>();
            try (var in = RealmsOfChaos.class.getClassLoader().getResourceAsStream("assets/" + RealmsOfChaos.MOD_ID + "/lang/en_us.json")) {
                if (in != null) {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : json.entrySet()) loaded.put(e.getKey(), e.getValue().getAsString());
                }
            } catch (Exception e) {
                // without the file there are only keys to show; nothing else depends on it
            }
            words = loaded;
        }
        return words;
    }

    /** The pattern for a key: the mod's own, else whatever the server knows, else the key. */
    public static String pattern(String key) {
        String own = words().get(key);
        return own != null ? own : Language.getInstance().getOrDefault(key);
    }

    /** A pattern with its %s (or %1$s) filled in, and %% made a single sign. Anything else is left as it is written. */
    static String fill(String pattern, String[] args) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != '%' || i + 1 >= pattern.length()) {
                out.append(c);
                continue;
            }
            char n = pattern.charAt(i + 1);
            if (n == '%') {
                out.append('%');
                i++;
            } else if (n == 's') {
                out.append(next < args.length ? args[next] : "");
                next++;
                i++;
            } else if (Character.isDigit(n) && i + 3 < pattern.length() && pattern.charAt(i + 2) == '$' && pattern.charAt(i + 3) == 's') {
                int at = n - '1';
                out.append(at >= 0 && at < args.length ? args[at] : "");
                i += 3;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** A component as plain words. */
    public static String of(Component component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents t) {
            Object[] raw = t.getArgs();
            String[] args = new String[raw.length];
            for (int i = 0; i < raw.length; i++) args[i] = raw[i] instanceof Component c ? of(c) : String.valueOf(raw[i]);
            String pattern = pattern(t.getKey());
            out.append(fill(pattern.equals(t.getKey()) && t.getFallback() != null ? t.getFallback() : pattern, args));
        } else {
            // (a literal, or something only a client can fill in: whatever the server makes of it)
            out.append(component.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents p ? p.text() : "");
        }
        for (Component sibling : component.getSiblings()) out.append(of(sibling));
        return out.toString();
    }

    public static String of(String key, Object... args) {
        return of(Component.translatable(key, args));
    }
}
