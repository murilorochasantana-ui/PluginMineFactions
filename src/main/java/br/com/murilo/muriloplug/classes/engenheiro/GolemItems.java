package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
        ItemStack item = ShopUi.button(Material.COPPER_GOLEM_STATUE,"§6Golem de Cobre",
                "§7Clique direito em um bloco para invocar um",
                "§7Golem de Cobre (" + (int) settings.golemEnergyCost() + " de energia).",
                "§7Ele te segue e ataca seus inimigos em combate.",
                "§7Máximo de " + settings.golemMax() + " ao mesmo tempo. Clique nele para consertar.",
                "§7Bata no seu golem fora de combate para recolher.",
                "§5Somente Engenheiros");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** O item do plugin (estátua nova ou bloco de cobre antigo, ainda não convertido), pela marca e nunca pelo nome. */
    public boolean isGolemItem(ItemStack item) {
        return item != null && (item.getType() == Material.COPPER_GOLEM_STATUE || item.getType() == Material.COPPER_BLOCK)
                && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Golem de Cobre no formato antigo (bloco de cobre com a marca). */
    public boolean isLegacyGolemItem(ItemStack item) {
        return isGolemItem(item) && item.getType() == Material.COPPER_BLOCK;
    }

    /** Item novo no lugar do antigo, com a mesma quantidade. Qualquer outro item volta como veio, a mesma instância. */
    public ItemStack upgradeLegacy(ItemStack item) {
        return isLegacyGolemItem(item) ? create(item.getAmount()) : item;
    }

    /** Troca os Golems de Cobre antigos do inventário (inclui armadura e mão secundária). Retorna quantos trocou. */
    public int migrateInventory(PlayerInventory inventory) {
        int replaced = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isLegacyGolemItem(contents[slot])) {
                inventory.setItem(slot, upgradeLegacy(contents[slot]));
                replaced++;
            }
        }
        return replaced;
    }
}
