package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Cria as staffs e as reconhece pelo PersistentDataContainer (chave mineplug:staff), nunca pelo nome. */
public final class StaffItems {

    private final JavaPlugin plugin;
    private final MagoSettings settings;
    private final NamespacedKey staffKey;

    public StaffItems(JavaPlugin plugin, MagoSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.staffKey = new NamespacedKey(plugin, "staff");
    }

    public ItemStack create(StaffType type) {
        List<String> lore = new ArrayList<>(type.description());
        lore.add("");
        lore.add("§7Dano: " + (type.dealsDamage() ? MagoSettings.format(settings.staff(type, "dano")) : "nenhum"));
        lore.add("§7Custo: " + settings.manaCost(type) + " mana");
        lore.add("§7Recarga: " + MagoSettings.format(settings.cooldownSeconds(type)) + "s");
        lore.add("§5Somente Magos podem usar");

        ItemStack item = ShopUi.button(StaffType.MATERIAL, type.displayName(), lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(staffKey, PersistentDataType.STRING, type.id());
        if (settings.customTexture()) {
            meta.setItemModel(new NamespacedKey(plugin, "staff_" + type.id()));
        }
        // Grupo de recarga próprio: a animação de recarga de uma staff não afeta as outras.
        UseCooldownComponent cooldown = meta.getUseCooldown();
        cooldown.setCooldownSeconds(1f);
        cooldown.setCooldownGroup(new NamespacedKey(plugin, "staff_" + type.id()));
        meta.setUseCooldown(cooldown);
        item.setItemMeta(meta);
        return item;
    }

    /** Tipo da staff, ou null se o item não for uma staff. */
    public StaffType read(ItemStack item) {
        if (item == null || item.getType() != StaffType.MATERIAL || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(staffKey, PersistentDataType.STRING);
        return StaffType.byId(id);
    }
}
