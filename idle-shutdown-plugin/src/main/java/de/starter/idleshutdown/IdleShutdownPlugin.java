package de.starter.idleshutdown;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public final class IdleShutdownPlugin extends JavaPlugin implements Listener {

    private int idleMinutes = 5;
    private String starterUrl = "http://130.61.151.106:9009";
    private String shutdownSecret = "gridmc_secret_shutdown_token";
    private BukkitTask countdownTask = null;
    private int secondsRemaining = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.idleMinutes = Math.max(1, getConfig().getInt("idle-minutes", 5));
        this.starterUrl = getConfig().getString("starter-url", "http://130.61.151.106:9009").replaceAll("/$", "");
        this.shutdownSecret = getConfig().getString("shutdown-secret", "gridmc_secret_shutdown_token");

        // Event-Listener registrieren
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("=========================================");
        getLogger().info("IdleShutdown Modul aktiv!");
        getLogger().info("Auto-Shutdown nach " + this.idleMinutes + " Min. Leerlauf.");
        getLogger().info("Signalweg: Pelican Panel Stop via " + this.starterUrl);
        getLogger().info("=========================================");

        // Beim Serverstart nach 15 Sekunden prüfen, ob Spieler da sind
        getServer().getScheduler().runTaskLater(this, this::checkAndStartTimer, 300L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (countdownTask != null) {
            getLogger().info("Spieler '" + event.getPlayer().getName() + "' beigetreten. Auto-Shutdown abgebrochen!");
            cancelCountdown();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Spieler nach 1 Sekunde prüfen (wenn Quit-Vorgang abgeschlossen ist)
        getServer().getScheduler().runTaskLater(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                checkAndStartTimer();
            }
        }, 20L);
    }

    private synchronized void checkAndStartTimer() {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            cancelCountdown();
            return;
        }

        if (countdownTask == null) {
            this.secondsRemaining = this.idleMinutes * 60;
            getLogger().info("Keine Spieler online. Starte Countdown (" + this.idleMinutes + " Min.) bis zum Pelican-Stop...");

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
                    getLogger().info("Server ist leer: Automatischer Pelican-Stop in " + minLeft + " Minute(n)...");
                }

                // Countdown abgelaufen: Sauberen Stop-Befehl an Pelican über das Web-Backend senden
                if (secondsRemaining <= 0) {
                    getLogger().info("Server war " + idleMinutes + " Minuten leer. Sende Stop-Signal an Pelican Panel...");
                    cancelCountdown();
                    sendPelicanStopSignalAsync();
                }
            }, 20L, 20L);
        }
    }

    private void sendPelicanStopSignalAsync() {
        // Asynchron ausführen, damit der Minecraft-Tick-Thread niemals blockiert
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = URI.create(this.starterUrl + "/api/shutdown").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-Shutdown-Secret", this.shutdownSecret);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    getLogger().info("Pelican Stop-Signal erfolgreich gesendet! Server fährt jetzt regulär herunter.");
                } else {
                    getLogger().warning("Pelican Starter-Dienst meldete HTTP " + responseCode + ". Fallback: Rufe /stop auf.");
                    // Fallback nur falls Webserver nicht erreichbar ist
                    Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop"));
                }
            } catch (Exception e) {
                getLogger().warning("Konnte Stop-Signal nicht an " + this.starterUrl + " senden (" + e.getMessage() + "). Fallback auf /stop...");
                Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop"));
            }
        });
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
