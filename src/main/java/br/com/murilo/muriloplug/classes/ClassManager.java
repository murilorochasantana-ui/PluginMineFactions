package br.com.murilo.muriloplug.classes;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Guarda a classe de cada jogador em {@code classes.yml}, regravando o arquivo a cada mudança. */
public final class ClassManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerClass> classes = new HashMap<>();

    public ClassManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "classes.yml");
    }

    /** Mana máxima individual (só de quem tem um valor próprio). */
    private final Map<UUID, Integer> manaMax = new HashMap<>();

    /** Mana máxima individual do jogador, ou null para usar a do config.yml. */
    public Integer getManaMax(UUID player) {
        return manaMax.get(player);
    }

    /** Define (ou, com null, remove) a mana máxima individual e salva. */
    public void setManaMax(UUID player, Integer value) {
        if (value == null) {
            manaMax.remove(player);
        } else {
            manaMax.put(player, value);
        }
        save();
    }

    /** Classe do jogador, ou null se ele não tiver. */
    public PlayerClass getClass(UUID player) {
        return classes.get(player);
    }

    public boolean hasClass(UUID player, PlayerClass playerClass) {
        return playerClass != null && classes.get(player) == playerClass;
    }

    /** Define a classe (null = sem classe). Salva e dispara {@link PlayerClassChangeEvent} se algo mudou. */
    public void setClass(UUID player, PlayerClass playerClass) {
        PlayerClass old = playerClass == null ? classes.remove(player) : classes.put(player, playerClass);
        if (old == playerClass) {
            return;
        }
        save();
        Bukkit.getPluginManager().callEvent(new PlayerClassChangeEvent(player, old, playerClass));
    }

    public void removeClass(UUID player) {
        setClass(player, null);
    }

    public void load() {
        classes.clear();
        manaMax.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection maxSection = yaml.getConfigurationSection("mana-maxima");
        if (maxSection != null) {
            for (String key : maxSection.getKeys(false)) {
                try {
                    manaMax.put(UUID.fromString(key), maxSection.getInt(key));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("classes.yml: mana máxima inválida ignorada: " + key);
                }
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                classes.put(UUID.fromString(key), PlayerClass.valueOf(section.getString(key, "")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("classes.yml: entrada inválida ignorada: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        classes.forEach((id, playerClass) -> yaml.set("players." + id, playerClass.name()));
        manaMax.forEach((id, value) -> yaml.set("mana-maxima." + id, value));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar classes.yml: " + e.getMessage());
        }
    }
}
