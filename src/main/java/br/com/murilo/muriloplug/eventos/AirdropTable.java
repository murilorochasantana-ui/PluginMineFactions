package br.com.murilo.muriloplug.eventos;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tabela de itens do Airdrop (airdrop.yml): cada item (ItemStack em Base64, com a quantidade) tem uma chance de 1% a
 * 100%. Também guarda onde está o baú ativo, para tirá-lo se o servidor cair com ele no mundo.
 */
public final class AirdropTable {

    /** Um item da tabela. O ItemStack é sempre copiado. */
    public static final class Entry {
        private final ItemStack item;
        private int chance;

        Entry(ItemStack item, int chance) {
            this.item = item.clone();
            this.chance = clampChance(chance);
        }

        public ItemStack item() {
            return item.clone();
        }

        public int chance() {
            return chance;
        }
    }

    public static final int MAX_ENTRIES = 135;

    private final JavaPlugin plugin;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();
    private String activeChest;

    public AirdropTable(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "airdrop.yml");
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public Entry get(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    /** Adiciona e devolve o índice (ou -1 se a tabela estiver cheia). */
    public int add(ItemStack item, int chance) {
        if (entries.size() >= MAX_ENTRIES) {
            return -1;
        }
        entries.add(new Entry(item, chance));
        save();
        return entries.size() - 1;
    }

    public void addChance(int index, int delta) {
        Entry entry = get(index);
        if (entry != null) {
            entry.chance = clampChance(entry.chance + delta);
            save();
        }
    }

    public void remove(int index) {
        if (get(index) != null) {
            entries.remove(index);
            save();
        }
    }

    /** Sorteia de {@code min} a {@code max} itens: escolhe itens ao acaso e cada um entra com a chance dele. */
    public List<ItemStack> draw(int min, int max) {
        List<ItemStack> result = new ArrayList<>();
        if (entries.isEmpty()) {
            return result;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int wanted = min + random.nextInt(Math.max(1, max - min + 1));
        for (int attempt = 0; attempt < wanted * 40 && result.size() < wanted; attempt++) {
            Entry entry = entries.get(random.nextInt(entries.size()));
            if (random.nextInt(100) < entry.chance) {
                result.add(entry.item());
            }
        }
        return result;
    }

    /** Troca os itens que {@code upgrade} mudar (notas antigas viram maço). Retorna quantos mudaram. */
    public int migrate(java.util.function.UnaryOperator<ItemStack> upgrade) {
        int changed = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            ItemStack original = entry.item;
            ItemStack updated = upgrade.apply(original);
            if (updated != original) {
                entries.set(i, new Entry(updated, entry.chance));
                changed++;
            }
        }
        if (changed > 0) {
            save();
        }
        return changed;
    }

    public String activeChest() {
        return activeChest;
    }

    public void setActiveChest(String activeChest) {
        this.activeChest = activeChest;
        save();
    }

    private static int clampChance(int chance) {
        return Math.max(1, Math.min(100, chance));
    }

    public void load() {
        entries.clear();
        activeChest = null;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yaml.getMapList("itens")) {
            try {
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(map.get("item"))));
                entries.add(new Entry(item, ((Number) map.get("chance")).intValue()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Item inválido em airdrop.yml, ignorado: " + e.getMessage());
            }
        }
        activeChest = yaml.getString("ativo");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("chance", entry.chance);
            map.put("item", Base64.getEncoder().encodeToString(entry.item.serializeAsBytes()));
            list.add(map);
        }
        yaml.set("itens", list);
        yaml.set("ativo", activeChest);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar airdrop.yml: " + e.getMessage());
        }
    }
}
