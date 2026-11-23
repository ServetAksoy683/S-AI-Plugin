package com.servetaksoyy.sai;

import com.google.gson.*;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Kalıcı hafıza: plugins/S-AI/memory.json */
public class Memory {
    private final SAIPlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, String> data = new LinkedHashMap<>();

    public Memory(SAIPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "memory.json");
        load();
    }

    private synchronized void load() {
        if (!file.exists()) return;
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (var e : o.entrySet()) data.put(e.getKey(), e.getValue().getAsString());
        } catch (Exception ex) {
            plugin.getLogger().warning("Hafıza okunamadı: " + ex.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Files.writeString(file.toPath(), gson.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            plugin.getLogger().warning("Hafıza yazılamadı: " + ex.getMessage());
        }
    }

    public synchronized void put(String k, String v) { data.put(k, v); save(); }
    public synchronized boolean remove(String k) { boolean r = data.remove(k) != null; if (r) save(); return r; }
    public synchronized void clear() { data.clear(); save(); }
    public synchronized Map<String, String> all() { return new LinkedHashMap<>(data); }

    public synchronized String asPromptText() {
        if (data.isEmpty()) return "(boş)";
        StringBuilder sb = new StringBuilder();
        data.forEach((k, v) -> sb.append("- ").append(k).append(" = ").append(v).append("\n"));
        return sb.toString();
    }
}
