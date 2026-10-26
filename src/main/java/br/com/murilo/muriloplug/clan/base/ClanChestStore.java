package br.com.murilo.muriloplug.clan.base;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Itens dos Baús do Clan: 54 slots por baú, salvos em plugins/MinePlug/baus/&lt;id&gt;.yml (cada item em Base64). Quem
 * abre o mesmo baú vê o MESMO inventário (fica em memória enquanto alguém usa e é gravado a cada fechamento).
 */
public final class ClanChestStore implements Listener {

    public static final int SIZE = 54;

    /** Marca o inventário de um Baú do Clan. */
    public static final class Holder implements InventoryHolder {

        private final String id;
        private Inventory inventory;

        private Holder(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final JavaPlugin plugin;
    private final File folder;
    private final Map<String, Inventory> loaded = new HashMap<>();

    public ClanChestStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "baus");
    }

    /** O inventário do baú (o mesmo para todos que abrirem). */
    public Inventory inventory(String id, String tag) {
        return loaded.computeIfAbsent(id, key -> {
            Holder holder = new Holder(key);
            Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Baú de [" + tag + "]"));
            holder.inventory = inventory;
            ItemStack[] contents = read(key);
            for (int slot = 0; slot < Math.min(SIZE, contents.length); slot++) {
                inventory.setItem(slot, contents[slot]);
            }
            return inventory;
        });
    }

    /**
     * Põe itens no baú (o mesmo inventário de quem estiver com ele aberto) e grava na hora. Devolve o que não coube.
     */
    public List<ItemStack> addItems(String id, String tag, List<ItemStack> items) {
        Inventory inventory = inventory(id, tag);
        List<ItemStack> leftover = new ArrayList<>(inventory.addItem(items.toArray(ItemStack[]::new)).values());
        write(id, inventory.getContents());
        return leftover;
    }

    /** Fecha o baú para quem estiver com ele aberto e grava (antes de recolher). */
    public void closeAndSave(String id) {
        Inventory inventory = loaded.get(id);
        if (inventory != null) {
            closeViewers(inventory);
            write(id, inventory.getContents());
            loaded.remove(id);
        }
    }

    /** Tira TODOS os itens do baú (para soltar no chão) e apaga o arquivo. Fecha antes para ninguém mexer junto. */
    public List<ItemStack> takeAll(String id) {
        Inventory inventory = loaded.remove(id);
        ItemStack[] contents;
        if (inventory != null) {
            closeViewers(inventory);
            contents = inventory.getContents();
            inventory.clear();
        } else {
            contents = read(id);
        }
        new File(folder, id + ".yml").delete();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        return items;
    }

    /** onDisable: grava todos os baús abertos. */
    public void saveAll() {
        for (Map.Entry<String, Inventory> entry : loaded.entrySet()) {
            closeViewers(entry.getValue());
            write(entry.getKey(), entry.getValue().getContents());
        }
        loaded.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder && loaded.get(holder.id()) == event.getInventory()) {
            write(holder.id(), event.getInventory().getContents());
        }
    }

    private static void closeViewers(Inventory inventory) {
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    private ItemStack[] read(String id) {
        ItemStack[] contents = new ItemStack[SIZE];
        File file = new File(folder, id + ".yml");
        if (!file.exists()) {
            return contents;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (int slot = 0; slot < SIZE; slot++) {
            String data = yaml.getString("slots." + slot);
            if (data != null) {
                try {
                    contents[slot] = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Item inválido no Baú do Clan " + id + " (slot " + slot + "): " + e.getMessage());
                }
            }
        }
        return contents;
    }

    private void write(String id, ItemStack[] contents) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                yaml.set("slots." + slot, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
        }
        try {
            folder.mkdirs();
            yaml.save(new File(folder, id + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o Baú do Clan " + id + ": " + e.getMessage());
        }
    }

    /** Cópia dos itens do baú (para o backup), sem mexer neles. */
    public List<ItemStack> peek(String id) {
        Inventory inventory = loaded.get(id);
        ItemStack[] contents = inventory != null ? inventory.getContents() : read(id);
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }
}
