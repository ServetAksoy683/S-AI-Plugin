package com.servetaksoyy.sai;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SAICommand implements CommandExecutor {
    private final SAIPlugin plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public SAICommand(SAIPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        boolean owner = plugin.isOwner(s);
        if (a.length == 0) {
            msg(s, "Kullanım: /sai <soru>");
            return true;
        }

        if (owner) {
            switch (a[0].toLowerCase()) {
                case "hafiza" -> { msg(s, plugin.memory().asPromptText()); return true; }
                case "unut" -> {
                    if (a.length < 2) { msg(s, "Kullanım: /sai unut <anahtar>"); return true; }
                    msg(s, plugin.memory().remove(a[1]) ? "Silindi." : "Böyle bir kayıt yok.");
                    return true;
                }
                case "hafizasifirla" -> { plugin.memory().clear(); msg(s, "Hafıza sıfırlandı."); return true; }
                case "sifirla" -> { plugin.gemini().clearHistory(s.getName()); msg(s, "Sohbet geçmişi temizlendi."); return true; }
                case "yenile" -> { plugin.reloadConfig(); msg(s, "Config yenilendi."); return true; }
                default -> { }
            }
        } else {
            long now = System.currentTimeMillis();
            long cd = plugin.getConfig().getLong("cooldown-seconds", 5) * 1000L;
            Long last = cooldowns.get(s.getName());
            if (last != null && now - last < cd) {
                msg(s, "Biraz bekle.");
                return true;
            }
            cooldowns.put(s.getName(), now);
        }

        String q = String.join(" ", a);
        msg(s, "§7düşünüyor...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String r;
            try {
                r = plugin.gemini().ask(s, q);
            } catch (Exception e) {
                plugin.getLogger().warning("Hata: " + e);
                r = "Hata: " + e.getMessage();
            }
            final String out = r;
            Bukkit.getScheduler().runTask(plugin, () -> msg(s, out));
        });
        return true;
    }

    private void msg(CommandSender s, String text) {
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            s.sendMessage(Component.text("[S-AI] ", NamedTextColor.AQUA)
                    .append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(line)));
        }
    }
}
