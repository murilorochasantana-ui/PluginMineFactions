package br.com.murilo.muriloplug.shop;

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
import java.util.function.UnaryOperator;

/** Itens da loja do servidor, salvos em loja.yml (cada ItemStack em bytes Base64, sem perder dados customizados). */
public final class ShopManager {

    public static final long MAX_PRICE = 1_000_000_000L;

    private final JavaPlugin plugin;
    private final File file;
    /** Itens na ordem de criação. */
    private final Map<Integer, ShopItem> items = new LinkedHashMap<>();
    private int nextId = 1;

    public ShopManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loja.yml");
    }

    public List<ShopItem> list() {
        return List.copyOf(items.values());
    }

    public int size() {
        return items.size();
    }

    public ShopItem get(int id) {
        return items.get(id);
    }

    /** Adiciona um item novo e devolve o id dele. */
    public int add(ItemStack item, long price) {
        requireValidPrice(price);
        int id = nextId++;
        items.put(id, new ShopItem(id, item, price));
        save();
        return id;
    }

    /** Atualiza o item e o preço. Retorna false se o id não existir mais. */
    public boolean update(int id, ItemStack item, long price) {
        requireValidPrice(price);
        if (!items.containsKey(id)) {
            return false;
        }
        items.put(id, new ShopItem(id, item, price));
        save();
        return true;
    }

    public boolean remove(int id) {
        if (items.remove(id) == null) {
            return false;
        }
        save();
        return true;
    }

    public void load() {
        items.clear();
        nextId = 1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yaml.getMapList("items")) {
            try {
                int id = ((Number) map.get("id")).intValue();
                long price = ((Number) map.get("price")).longValue();
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(map.get("item"))));
                items.put(id, new ShopItem(id, item, price));
                nextId = Math.max(nextId, id + 1);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Item inválido em loja.yml, ignorado: " + e.getMessage());
            }
        }
        nextId = Math.max(nextId, yaml.getInt("next-id", 1));
    }

    /**
     * Passa cada item por {@code upgrade} (que devolve a mesma instância se não há o que trocar) e salva se algum
     * mudou. Retorna quantos mudaram.
     */
    public int migrate(UnaryOperator<ItemStack> upgrade) {
        int[] changed = {0};
        items.replaceAll((id, shopItem) -> {
            ItemStack original = shopItem.item();
            ItemStack updated = upgrade.apply(original);
            if (updated == original) {
                return shopItem;
            }
            changed[0]++;
            return new ShopItem(id, updated, shopItem.price());
        });
        if (changed[0] > 0) {
            save();
        }
        return changed[0];
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShopItem shopItem : items.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", shopItem.id());
            map.put("price", shopItem.price());
            map.put("item", Base64.getEncoder().encodeToString(shopItem.item().serializeAsBytes()));
            list.add(map);
        }
        yaml.set("items", list);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar loja.yml: " + e.getMessage());
        }
    }

    private static void requireValidPrice(long price) {
        if (price < 1 || price > MAX_PRICE) {
            throw new IllegalArgumentException("Preço fora do limite (1 a " + MAX_PRICE + "): " + price);
        }
    }
}
