package br.com.murilo.muriloplug.home;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HomeManager {

    public static final int MAX_HOMES = 5;

    public enum SetResult { ADDED, UPDATED, LIMIT_REACHED }

    private final JavaPlugin plugin;
    private final File file;
    /** Homes de cada jogador, na ordem de criação. */
    private final Map<UUID, List<Home>> homes = new LinkedHashMap<>();

    public HomeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
    }

    public List<Home> getByOwner(UUID owner) {
        return List.copyOf(homes.getOrDefault(owner, List.of()));
    }

    /** Busca uma home do jogador pelo nome, sem diferenciar maiúsculas. */
    public Home getByName(UUID owner, String name) {
        for (Home home : homes.getOrDefault(owner, List.of())) {
            if (home.name().equalsIgnoreCase(name)) {
                return home;
            }
        }
        return null;
    }

    /** Cria a home, ou atualiza o local se já houver uma com o mesmo nome (mantendo a ordem e o nome original). */
    public SetResult set(UUID owner, Home home) {
        List<Home> list = homes.computeIfAbsent(owner, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(home.name())) {
                list.set(i, home.withName(list.get(i).name()));
                save();
                return SetResult.UPDATED;
            }
        }
        if (list.size() >= MAX_HOMES) {
            return SetResult.LIMIT_REACHED;
        }
        list.add(home);
        save();
        return SetResult.ADDED;
    }

    public boolean remove(UUID owner, String name) {
        List<Home> list = homes.get(owner);
        if (list == null || !list.removeIf(h -> h.name().equalsIgnoreCase(name))) {
            return false;
        }
        if (list.isEmpty()) {
            homes.remove(owner);
        }
        save();
        return true;
    }

    public void load() {
        homes.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em homes.yml, ignorado: " + key);
                continue;
            }
            List<Home> list = new ArrayList<>();
            for (Map<?, ?> map : players.getMapList(key)) {
                list.add(new Home(String.valueOf(map.get("name")), String.valueOf(map.get("world")),
                        number(map, "x").doubleValue(), number(map, "y").doubleValue(), number(map, "z").doubleValue(),
                        number(map, "yaw").floatValue(), number(map, "pitch").floatValue()));
            }
            if (!list.isEmpty()) {
                homes.put(owner, list);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<Home>> entry : homes.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Home home : entry.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", home.name());
                map.put("world", home.world());
                map.put("x", home.x());
                map.put("y", home.y());
                map.put("z", home.z());
                map.put("yaw", home.yaw());
                map.put("pitch", home.pitch());
                list.add(map);
            }
            yaml.set("players." + entry.getKey(), list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar homes.yml: " + e.getMessage());
        }
    }

    private static Number number(Map<?, ?> map, String key) {
        return (Number) map.get(key);
    }
}
