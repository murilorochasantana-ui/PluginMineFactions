package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cria o Grimório e o reconhece pelo PersistentDataContainer (chave mineplug:grimorio), nunca pelo nome. Cada um leva
 * um UUID próprio (não empilha). As magias aprendidas ficam em {@link GrimoireManager}, não no item: a lore só mostra
 * as magias equipadas de quem o carrega e é atualizada por {@link #refresh}.
 */
public final class GrimoireItems {

    private final NamespacedKey key;

    public GrimoireItems(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "grimorio");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ShopUi.text("§5Grimório"));
        meta.lore(lore(List.of(), null, 0));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGrimoire(ItemStack item) {
        return item != null && item.getType() == Material.BOOK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /** Atualiza a lore de todos os Grimórios do inventário com as magias equipadas e a selecionada deste jogador. */
    public void refresh(Player player, List<StaffType> equipped, StaffType selected, int max) {
        PlayerInventory inventory = player.getInventory();
        List<Component> lore = lore(equipped, selected, max);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isGrimoire(stack)) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (lore.equals(meta.lore())) {
                continue;
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
            inventory.setItem(slot, stack);
        }
    }

    private static List<Component> lore(List<StaffType> equipped, StaffType selected, int max) {
        List<String> lines = new ArrayList<>();
        lines.add(max > 0 ? "§7Magias equipadas (" + equipped.size() + "/" + max + "):" : "§7Magias equipadas:");
        if (equipped.isEmpty()) {
            lines.add("  §8Nenhuma - use /grimorio");
        }
        for (StaffType type : equipped) {
            lines.add(type == selected ? "  §5▶ " + type.coloredSpellName() : "  §7" + type.spellName());
        }
        lines.add("");
        lines.add("§7Clique direito: lança a magia selecionada.");
        lines.add("§7Clique esquerdo: Míssil Arcano.");
        lines.add("§7Shift + clique esquerdo: troca de magia.");
        lines.add("§7/grimorio: aprender e equipar magias.");
        lines.add("§5Somente Magos podem usar");
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            result.add(ShopUi.text(line));
        }
        return result;
    }
}
