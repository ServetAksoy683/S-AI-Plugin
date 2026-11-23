package com.servetaksoyy.sai;

import com.google.gson.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** SADECE sahibe açık araçlar. */
public class Tools {
    private final SAIPlugin plugin;
    private static final Set<String> TEXT_EXT = Set.of("yml", "yaml", "json", "txt", "properties", "conf", "toml", "cfg", "ini", "md", "log", "xml", "csv", "sk", "js", "lua");

    public Tools(SAIPlugin plugin) { this.plugin = plugin; }

    // ---------- Gemini araç tanımları ----------
    public static JsonArray declarations() {
        JsonArray f = new JsonArray();
        f.add(fn("run_command", "Sunucuda konsol yetkisiyle komut çalıştırır (başında / olmadan). Çıktısını döndürür.",
                new String[][]{{"command", "STRING", "Çalıştırılacak komut"}}, "command"));
        f.add(fn("list_files", "Sunucu klasöründeki bir dizinin içeriğini listeler. Boş path = sunucu kökü.",
                new String[][]{{"path", "STRING", "Sunucu köküne göre dizin yolu, örn. plugins/CrazyCrates"}}));
        f.add(fn("read_file", "Metin dosyasını okur.",
                new String[][]{{"path", "STRING", "Dosya yolu"}, {"start_line", "INTEGER", "Başlangıç satırı (1'den)"}}, "path"));
        f.add(fn("search_files", "Bir dizin altındaki metin dosyalarında (ve dosya adlarında) büyük/küçük harf duyarsız arama yapar.",
                new String[][]{{"query", "STRING", "Aranacak kelime"}, {"path", "STRING", "Aranacak dizin, varsayılan plugins"}}, "query"));
        f.add(fn("write_file", "Dosyaya içerik yazar (eskisinin .bak yedeğini alır). Tüm dosya içeriğini verin.",
                new String[][]{{"path", "STRING", "Dosya yolu"}, {"content", "STRING", "Yeni tam içerik"}}, "path", "content"));
        f.add(fn("get_plugin_info", "Bir pluginin sürümünü, komutlarını ve izinlerini döndürür.",
                new String[][]{{"name", "STRING", "Plugin adı"}}, "name"));
        f.add(fn("get_players", "Online oyuncuların detaylı listesini verir.", new String[0][]));
        f.add(fn("remember", "Kalıcı hafızaya bilgi kaydeder (örn. plugin item/key ID'leri ve komut formatları).",
                new String[][]{{"key", "STRING", "Kısa anahtar, örn. crate.sovalyeler.key"}, {"value", "STRING", "Değer/açıklama"}}, "key", "value"));
        f.add(fn("forget", "Hafızadan bir kaydı siler.", new String[][]{{"key", "STRING", "Anahtar"}}, "key"));
        JsonObject tool = new JsonObject();
        tool.add("functionDeclarations", f);
        JsonArray arr = new JsonArray();
        arr.add(tool);
        return arr;
    }

    private static JsonObject fn(String n, String d, String[][] ps, String... req) {
        JsonObject f = new JsonObject();
        f.addProperty("name", n);
        f.addProperty("description", d);
        if (ps.length > 0) {
            JsonObject params = new JsonObject();
            params.addProperty("type", "OBJECT");
            JsonObject props = new JsonObject();
            for (String[] p : ps) {
                JsonObject o = new JsonObject();
                o.addProperty("type", p[1]);
                o.addProperty("description", p[2]);
                props.add(p[0], o);
            }
            params.add("properties", props);
            JsonArray r = new JsonArray();
            for (String s : req) r.add(s);
            params.add("required", r);
            f.add("parameters", params);
        }
        return f;
    }

    // ---------- Çalıştırma ----------
    public JsonObject execute(String name, JsonObject a) {
        JsonObject res = new JsonObject();
        try {
            switch (name) {
                case "run_command" -> res.addProperty("output", runCommand(s(a, "command")));
                case "list_files" -> res.addProperty("output", listFiles(s(a, "path")));
                case "read_file" -> res.addProperty("output", readFile(s(a, "path"), i(a, "start_line", 1)));
                case "search_files" -> res.addProperty("output", search(s(a, "query"), s(a, "path")));
                case "write_file" -> res.addProperty("output", writeFile(s(a, "path"), s(a, "content")));
                case "get_plugin_info" -> res.addProperty("output", pluginInfo(s(a, "name")));
                case "get_players" -> res.addProperty("output", players());
                case "remember" -> { plugin.memory().put(s(a, "key"), s(a, "value")); res.addProperty("output", "kaydedildi"); }
                case "forget" -> res.addProperty("output", plugin.memory().remove(s(a, "key")) ? "silindi" : "kayıt yok");
                default -> res.addProperty("error", "Bilinmeyen araç: " + name);
            }
        } catch (Exception e) {
            res = new JsonObject();
            res.addProperty("error", String.valueOf(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        return res;
    }

    private static String s(JsonObject a, String k) {
        return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsString() : "";
    }

    private static int i(JsonObject a, String k, int def) {
        try { return a.has(k) ? a.get(k).getAsInt() : def; } catch (Exception e) { return def; }
    }

    private <T> T sync(Callable<T> c) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(plugin, c).get(30, TimeUnit.SECONDS);
    }

    private String runCommand(String cmd) throws Exception {
        cmd = cmd.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) return "komut boş";
        String first = cmd.split("\\s+")[0].toLowerCase();
        if (first.equals("sai") || first.equals("ai") || first.endsWith(":sai")) return "S-AI kendi kendini çağıramaz.";
        final String fcmd = cmd;
        return sync(() -> {
            StringBuilder sb = new StringBuilder();
            CommandSender cs = Bukkit.createCommandSender(c ->
                    sb.append(PlainTextComponentSerializer.plainText().serialize(c)).append("\n"));
            boolean ok = Bukkit.dispatchCommand(cs, fcmd);
            String out = sb.toString().trim();
            if (out.length() > 3000) out = out.substring(0, 3000) + "...";
            return "başarılı=" + ok + (out.isEmpty() ? " (çıktı yok)" : "\n" + out);
        });
    }

    private Path root() { return Path.of("").toAbsolutePath().normalize(); }

    private Path resolve(String p) {
        Path root = root();
        Path r = root.resolve(p == null || p.isBlank() ? "." : p).normalize();
        if (!r.startsWith(root)) throw new IllegalArgumentException("Sunucu klasörü dışına çıkılamaz.");
        return r;
    }

    private String listFiles(String p) throws IOException {
        Path d = resolve(p);
        if (!Files.isDirectory(d)) return "dizin değil / yok: " + p;
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> st = Files.list(d)) {
            st.sorted().limit(200).forEach(x -> {
                try {
                    if (Files.isDirectory(x)) sb.append("d ").append(x.getFileName()).append("/\n");
                    else sb.append("f ").append(x.getFileName()).append(" (").append(Files.size(x)).append("b)\n");
                } catch (IOException ignored) { }
            });
        }
        return sb.toString();
    }

    private String readFile(String p, int start) throws IOException {
        Path f = resolve(p);
        if (!Files.isRegularFile(f)) return "dosya yok: " + p;
        if (Files.size(f) > 5_000_000) return "dosya çok büyük (>5MB)";
        List<String> lines;
        try { lines = Files.readAllLines(f, StandardCharsets.UTF_8); }
        catch (java.nio.charset.MalformedInputException e) { return "metin dosyası değil (binary)"; }
        int max = plugin.getConfig().getInt("max-read-chars", 12000);
        StringBuilder sb = new StringBuilder();
        int from = Math.max(1, start);
        for (int n = from; n <= lines.size(); n++) {
            String line = lines.get(n - 1);
            if (sb.length() + line.length() + 1 > max) {
                sb.append("\n[KESİLDİ] devam için start_line=").append(n).append(" kullan. Toplam satır: ").append(lines.size());
                break;
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private String search(String q, String p) throws IOException {
        if (q.isBlank()) return "sorgu boş";
        Path base = resolve(p.isBlank() ? "plugins" : p);
        String ql = q.toLowerCase();
        List<String> hits = new ArrayList<>();
        Path root = root();
        try (Stream<Path> st = Files.walk(base, 8)) {
            for (Path f : (Iterable<Path>) st.filter(Files::isRegularFile)::iterator) {
                if (hits.size() >= 40) break;
                String rel = root.relativize(f).toString();
                if (f.getFileName().toString().toLowerCase().contains(ql)) hits.add("[dosya adı] " + rel);
                String fn = f.getFileName().toString();
                int dot = fn.lastIndexOf('.');
                String ext = dot < 0 ? "" : fn.substring(dot + 1).toLowerCase();
                if (!TEXT_EXT.contains(ext)) continue;
                try {
                    if (Files.size(f) > 2_000_000) continue;
                    List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                    for (int n = 0; n < lines.size() && hits.size() < 40; n++) {
                        if (lines.get(n).toLowerCase().contains(ql)) {
                            String t = lines.get(n).trim();
                            if (t.length() > 200) t = t.substring(0, 200);
                            hits.add(rel + ":" + (n + 1) + ": " + t);
                        }
                    }
                } catch (Exception ignored) { }
            }
        }
        return hits.isEmpty() ? "sonuç yok" : String.join("\n", hits);
    }

    private String writeFile(String p, String content) throws IOException {
        Path f = resolve(p);
        if (f.equals(root())) return "geçersiz yol";
        if (f.getParent() != null) Files.createDirectories(f.getParent());
        if (Files.exists(f)) Files.copy(f, f.resolveSibling(f.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return "yazıldı (" + content.length() + " karakter), yedek: .bak";
    }

    private String pluginInfo(String name) {
        Plugin target = null;
        for (Plugin p : Bukkit.getPluginManager().getPlugins())
            if (p.getName().equalsIgnoreCase(name)) { target = p; break; }
        if (target == null) return "plugin bulunamadı";
        var d = target.getDescription();
        StringBuilder sb = new StringBuilder();
        sb.append(d.getName()).append(" v").append(d.getVersion()).append(" (klasör: plugins/").append(target.getDataFolder().getName()).append(")\n");
        sb.append("Komutlar:\n");
        d.getCommands().forEach((k, v) -> sb.append(" /").append(k)
                .append(v.get("usage") != null ? " -> " + v.get("usage") : "")
                .append(v.get("description") != null ? " | " + v.get("description") : "").append("\n"));
        sb.append("İzinler: ");
        d.getPermissions().forEach(pm -> sb.append(pm.getName()).append(", "));
        return sb.toString();
    }

    private String players() throws Exception {
        return sync(() -> {
            StringBuilder sb = new StringBuilder();
            for (Player p : Bukkit.getOnlinePlayers()) {
                var l = p.getLocation();
                sb.append(p.getName()).append(" | ").append(p.getWorld().getName())
                        .append(" ").append(l.getBlockX()).append(",").append(l.getBlockY()).append(",").append(l.getBlockZ())
                        .append(" | ").append(p.getGameMode()).append(p.isOp() ? " | op" : "").append("\n");
            }
            return sb.length() == 0 ? "kimse yok" : sb.toString();
        });
    }
}
