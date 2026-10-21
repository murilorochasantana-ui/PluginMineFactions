package br.com.murilo.muriloplug.stats;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Kills, mortes e assistências de cada jogador. */
public final class StatsManager {

    private static final class Stats {
        int kills;
        int deaths;
        int assists;
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Stats> stats = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };

    public StatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    /** Chamado para cada jogador cujos números mudaram (para atualizar o sidebar). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public int getKills(UUID player) {
        Stats s = stats.get(player);
        return s == null ? 0 : s.kills;
    }

    public int getDeaths(UUID player) {
        Stats s = stats.get(player);
        return s == null ? 0 : s.deaths;
    }

    public int getAssists(UUID player) {
        Stats s = stats.get(player);
        return s == null ? 0 : s.assists;
    }

    /** (kills + assistências) / mortes; com 0 mortes, divide por 1. */
    public double getKda(UUID player) {
        return (getKills(player) + getAssists(player)) / (double) Math.max(getDeaths(player), 1);
    }

    /** KDA com 2 casas decimais. */
    public String formatKda(UUID player) {
        return String.format(Locale.ROOT, "%.2f", getKda(player));
    }

    /** Conta a kill, a morte da vítima e as assistências, e depois dispara o {@link PlayerKillEvent}. */
    public void recordKill(UUID killer, UUID victim, List<UUID> assistants) {
        entry(killer).kills++;
        entry(victim).deaths++;
        for (UUID assistant : assistants) {
            entry(assistant).assists++;
        }

        changeListener.accept(killer);
        changeListener.accept(victim);
        assistants.forEach(changeListener);

        Bukkit.getPluginManager().callEvent(new PlayerKillEvent(killer, victim, assistants));
    }

    public void load() {
        stats.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Stats s = entry(UUID.fromString(key));
                s.kills = section.getInt("kills");
                s.deaths = section.getInt("deaths");
                s.assists = section.getInt("assists");
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em stats.yml, ignorado: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Stats> entry : stats.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".kills", entry.getValue().kills);
            yaml.set(path + ".deaths", entry.getValue().deaths);
            yaml.set(path + ".assists", entry.getValue().assists);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar stats.yml: " + e.getMessage());
        }
    }

    private Stats entry(UUID player) {
        return stats.computeIfAbsent(player, k -> new Stats());
    }
}
