package de.starter.idleshutdown;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class IdleShutdownPlugin extends JavaPlugin {

    private long idleTicks;
    private int idleMinutes;
    private BukkitTask shutdownTask = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.idleMinutes = getConfig().getInt("idle-minutes", 5);
        this.idleTicks = 20L * 60L * this.idleMinutes;

        // Überprüfung alle 20 Sekunden (400 Ticks), erster Check nach 5 Sekunden (100 Ticks)
        getServer().getScheduler().runTaskTimer(this, this::evaluatePlayerCount, 100L, 400L);
        getLogger().info("IdleShutdown Modul aktiv: " + this.idleMinutes + "-Minuten-Limit.");
    }

    private void evaluatePlayerCount() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        if (onlinePlayers == 0) {
            if (shutdownTask == null) {
                getLogger().info("0 Spieler online. Starte " + this.idleMinutes + "-Minuten-Timer bis zum Shutdown...");
                shutdownTask = Bukkit.getScheduler().runTaskLater(this, () -> {
                    getLogger().info("Server war " + this.idleMinutes + " Minuten leer. Fahre sicher herunter...");
                    Bukkit.shutdown();
                }, this.idleTicks);
            }
        } else {
            if (shutdownTask != null) {
                getLogger().info("Spieler beigetreten. Shutdown-Timer abgebrochen.");
                shutdownTask.cancel();
                shutdownTask = null;
            }
        }
    }

    @Override
    public void onDisable() {
        if (shutdownTask != null) {
            shutdownTask.cancel();
            shutdownTask = null;
        }
    }
}
