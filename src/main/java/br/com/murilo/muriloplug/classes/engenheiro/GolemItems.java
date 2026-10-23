package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Cria o item Golem de Cobre e o reconhece pelo PersistentDataContainer (chave mineplug:golem_item), nunca pelo nome. */
public final class GolemItems {

    private final EngenheiroSettings settings;
    private final NamespacedKey key;

    public GolemItems(JavaPlugin plugin, EngenheiroSettings settings) {
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "golem_item");
    }

    public ItemStack create(int amount) {
        ItemStack item = ShopUi.button(Material.COPPER_BLOCK, "§6Golem de Cobre",
                "§7Clique direito em um bloco para invocar um",
                "§7Golem de Cobre (" + (int) settings.golemEnergyCost() + " de energia).",
                "§7Ele te segue e ataca seus inimigos em combate.",
                "§7Máximo de " + settings.golemMax() + " ao mesmo tempo. Clique nele para consertar.",
                "§5Somente Engenheiros");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGolemItem(ItemStack item) {
        return item != null && item.getType() == Material.COPPER_BLOCK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
