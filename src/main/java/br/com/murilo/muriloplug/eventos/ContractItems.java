package br.com.murilo.muriloplug.eventos;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** "Contrato de Recompensa Nível I a V" (PAPER com {@code mineplug:contrato} = nível), vendido na /loja. */
public final class ContractItems {

    public static final int LEVELS = 5;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    private final JavaPlugin plugin;
    private final BountyManager bounties;
    private final NamespacedKey key;

    public ContractItems(JavaPlugin plugin, BountyManager bounties) {
        this.plugin = plugin;
        this.bounties = bounties;
        this.key = new NamespacedKey(plugin, "contrato");
    }

    public static String roman(int level) {
        return ROMAN[Math.max(1, Math.min(LEVELS, level)) - 1];
    }

    public ItemStack create(int level, int amount) {
        level = Math.max(1, Math.min(LEVELS, level));
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ShopUi.text("§cContrato de Recompensa §7Nível " + roman(level)));
        meta.lore(List.of(
                ShopUi.text("§7Clique direito: o servidor coloca uma"),
                ShopUi.text("§7recompensa de §a" + EconomyManager.format(bounties.value(level))),
                ShopUi.text("§7na cabeça de um jogador aleatório"),
                ShopUi.text("§7(nunca do seu clan). Você não paga nada.")));
        NamespacedKey model = NamespacedKey.fromString(
                plugin.getConfig().getString(BountyManager.MODEL_PATH, "minecraft:map"));
        if (model != null) {
            meta.setItemModel(model);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return item;
    }

    /** Nível do contrato (1 a 5), ou 0 se o item não for um contrato. */
    public int level(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return level == null || level < 1 || level > LEVELS ? 0 : level;
    }
}
