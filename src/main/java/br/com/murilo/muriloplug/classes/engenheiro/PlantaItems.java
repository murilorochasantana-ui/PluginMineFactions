package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Cria a Planta de Engenharia e a reconhece pelo PersistentDataContainer (chave mineplug:planta = UUID da Planta),
 * nunca pelo nome. Os projetos ficam ligados a esse UUID. A Planta que sai da loja (cópias iguais) nasce "nova" e
 * ganha um UUID só dela no primeiro uso, para duas Plantas compradas não dividirem os mesmos projetos.
 */
public final class PlantaItems {

    private final EngenheiroSettings settings;
    private final NamespacedKey idKey;
    private final NamespacedKey freshKey;

    public PlantaItems(JavaPlugin plugin, EngenheiroSettings settings) {
        this.settings = settings;
        this.idKey = new NamespacedKey(plugin, "planta");
        this.freshKey = new NamespacedKey(plugin, "planta_nova");
    }

    /** Planta nova, sem projetos. */
    public ItemStack create() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ShopUi.text("§9Planta de Engenharia"));
        meta.setItemModel(NamespacedKey.minecraft("map"));
        meta.setMaxStackSize(1);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.set(freshKey, PersistentDataType.BYTE, (byte) 1);
        meta.lore(lore(0));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> lore(int projects) {
        return List.of(ShopUi.text("§7Projetos: " + projects + "/" + settings.plantaMaxProjects()),
                ShopUi.text("§7Clique direito para abrir."),
                ShopUi.text("§5Somente Engenheiros"));
    }

    public boolean isPlanta(ItemStack item) {
        return id(item) != null;
    }

    /** UUID da Planta, ou null se o item não for uma Planta. */
    public UUID id(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Planta que veio de uma cópia (loja, kit) e ainda não foi usada. */
    public boolean isFresh(ItemStack item) {
        return isPlanta(item) && item.getItemMeta().getPersistentDataContainer().has(freshKey, PersistentDataType.BYTE);
    }

    /** A mesma Planta com um UUID novo e sem a marca de "nova". */
    public ItemStack claim(ItemStack item) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.remove(freshKey);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Atualiza a linha "Projetos: X/9" do item. */
    public void setProjectCount(ItemStack item, int projects) {
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore(projects));
        item.setItemMeta(meta);
    }
}
