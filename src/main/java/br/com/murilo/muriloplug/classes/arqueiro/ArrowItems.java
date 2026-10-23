package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Cria a Flecha Explosiva e a reconhece pelo PersistentDataContainer (chave mineplug:flecha), nunca pelo nome. */
public final class ArrowItems {

    public static final String EXPLOSIVE_ID = "explosiva";
    public static final String GRAPPLE_ID = "gancho";

    private final NamespacedKey arrowKey;

    public ArrowItems(JavaPlugin plugin) {
        this.arrowKey = new NamespacedKey(plugin, "flecha");
    }

    public ItemStack createExplosive(int amount) {
        ItemStack item = ShopUi.button(Material.ARROW, "§cFlecha Explosiva",
                "§7Dano 3x maior ao acertar um jogador.",
                "§7Explode como um creeper, sem quebrar blocos.",
                "§cSomente Arqueiros podem usar.");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, EXPLOSIVE_ID);
        item.setItemMeta(meta);
        return item;
    }

    /** Flecha Gancho do Engenheiro (só funciona na besta). */
    public ItemStack createGrapple(int amount) {
        ItemStack item = ShopUi.button(Material.ARROW, "§7Flecha Gancho",
                "§7Só funciona na besta.",
                "§5Somente Engenheiros");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, GRAPPLE_ID);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGrapple(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW || !item.hasItemMeta()) {
            return false;
        }
        return GRAPPLE_ID.equals(item.getItemMeta().getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING));
    }

    public void markGrappleProjectile(PersistentDataContainer container) {
        container.set(arrowKey, PersistentDataType.STRING, GRAPPLE_ID);
    }

    public boolean isGrappleProjectile(PersistentDataContainer container) {
        return GRAPPLE_ID.equals(container.get(arrowKey, PersistentDataType.STRING));
    }

    public boolean isExplosive(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW || !item.hasItemMeta()) {
            return false;
        }
        return EXPLOSIVE_ID.equals(item.getItemMeta().getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING));
    }

    /** Marca a flecha disparada (entidade) como Flecha Explosiva. */
    public void markProjectile(PersistentDataContainer container) {
        container.set(arrowKey, PersistentDataType.STRING, EXPLOSIVE_ID);
    }

    public boolean isExplosiveProjectile(PersistentDataContainer container) {
        return EXPLOSIVE_ID.equals(container.get(arrowKey, PersistentDataType.STRING));
    }
}
