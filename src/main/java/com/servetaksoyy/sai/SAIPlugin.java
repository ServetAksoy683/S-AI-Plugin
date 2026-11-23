package com.servetaksoyy.sai;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SAIPlugin extends JavaPlugin {
    private Memory memory;
    private GeminiClient gemini;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        memory = new Memory(this);
        gemini = new GeminiClient(this);
        getCommand("sai").setExecutor(new SAICommand(this));
        getLogger().info("S-AI aktif.");
    }

    public Memory memory() { return memory; }
    public GeminiClient gemini() { return gemini; }

    public boolean isOwner(CommandSender s) {
        if (s instanceof ConsoleCommandSender) return true;
        if (!(s instanceof Player p)) return false;
        String uuid = getConfig().getString("owner-uuid", "").trim();
        if (!uuid.isEmpty()) return p.getUniqueId().toString().equalsIgnoreCase(uuid);
        return p.getName().equalsIgnoreCase(getConfig().getString("owner-name", "servetaksoyy"));
    }
}
