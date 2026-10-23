package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Cria o item Sentinela e o reconhece pelo PersistentDataContainer (chave mineplug:sentinela), nunca pelo nome. */
public final class SentinelaItems {

    private final EngenheiroSettings settings;
    private final NamespacedKey key;

    public SentinelaItems(JavaPlugin plugin, EngenheiroSettings settings) {
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "sentinela");
    }

    public ItemStack create(int amount) {
        ItemStack item = ShopUi.button(Material.OBSERVER, "§cSentinela",
                "§7Coloque como um bloco. Avisa na sua tela",
                "§7quando um inimigo chega a " + settings.sentinelRadius() + " blocos dela.",
                "§7Máximo de " + settings.sentinelMax() + " por Engenheiro.",
                "§5Somente Engenheiros");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSentinela(ItemStack item) {
        return item != null && item.getType() == Material.OBSERVER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
