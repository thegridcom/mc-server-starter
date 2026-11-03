package de.starter.idleshutdown;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class IdleShutdownPlugin extends JavaPlugin implements Listener {

    private int idleMinutes = 5;
    private BukkitTask countdownTask = null;
    private int secondsRemaining = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.idleMinutes = Math.max(1, getConfig().getInt("idle-minutes", 5));

        // Event-Listener registrieren
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("=========================================");
        getLogger().info("IdleShutdown Modul aktiv!");
        getLogger().info("Server stoppt nach " + this.idleMinutes + " Minuten bei 0 Spielern.");
        getLogger().info("=========================================");

        // Beim Serverstart nach 10 Sekunden prüfen, ob Spieler da sind
        getServer().getScheduler().runTaskLater(this, this::checkAndStartTimer, 200L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (countdownTask != null) {
            getLogger().info("Spieler '" + event.getPlayer().getName() + "' ist beigetreten. Auto-Shutdown abgebrochen!");
            cancelCountdown();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // getServer().getScheduler().runTaskLater nutzen, da der Spieler zum Zeitpunkt des Events noch in getOnlinePlayers() ist
        getServer().getScheduler().runTaskLater(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                checkAndStartTimer();
            }
        }, 20L); // 1 Sekunde Verzögerung
    }

    private synchronized void checkAndStartTimer() {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            cancelCountdown();
            return;
        }

        if (countdownTask == null) {
            this.secondsRemaining = this.idleMinutes * 60;
            getLogger().info("Keine Spieler online. Starte Countdown (" + this.idleMinutes + " Min.) bis zum '/stop'...");

            // Jede Sekunde herunterzählen (20 Ticks)
            countdownTask = getServer().getScheduler().runTaskTimer(this, () -> {
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    getLogger().info("Spieler beigetreten. Auto-Shutdown abgebrochen!");
                    cancelCountdown();
                    return;
                }

                secondsRemaining--;

                // Logge jede volle Minute
                if (secondsRemaining > 0 && secondsRemaining % 60 == 0) {
                    int minLeft = secondsRemaining / 60;
                    getLogger().info("Server ist leer: Automatischer Shutdown in " + minLeft + " Minute(n)...");
                }

                // Countdown abgelaufen: /stop Befehl ausführen
                if (secondsRemaining <= 0) {
                    getLogger().info("Server war " + idleMinutes + " Minuten ohne Spieler. Führe Befehl 'stop' aus...");
                    cancelCountdown();
                    // Befehl 'stop' über die Konsole ausführen
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
                }
            }, 20L, 20L);
        }
    }

    private synchronized void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        secondsRemaining = 0;
    }

    @Override
    public void onDisable() {
        cancelCountdown();
        getLogger().info("IdleShutdown Modul deaktiviert.");
    }
}
