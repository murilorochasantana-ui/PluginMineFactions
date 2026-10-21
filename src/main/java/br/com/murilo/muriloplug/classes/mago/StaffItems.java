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
        lore.addAll(comboLore(type));
        lore.add("");
        lore.add("§7Clique esquerdo: Míssil Arcano (devolve mana).");
        lore.add("§7Clique direito: lança a magia.");
        lore.add("§7Passivas: Poder Arcano e Escudo de Mana.");
        lore.add("§cFraqueza: +" + percent(settings.extraDamage()) + "% de dano recebido e");
        lore.add("§c" + percent(settings.weaponPenalty()) + "% menos dano com armas.");
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

    /** Linhas de combo da staff (só dos combos ligados no config). */
    private List<String> comboLore(StaffType type) {
        String base = "mago.combos.";
        List<String> lines = new ArrayList<>();
        switch (type) {
            case GELO -> {
                if (settings.flag(base + "choque-termico.ativado")) {
                    lines.add("§3Combo: alvos congelados levam "
                            + MagoSettings.format(settings.number(base + "choque-termico.multiplicador-dano"))
                            + "x do Fogo");
                }
                if (settings.flag(base + "condutor.ativado")) {
                    lines.add("§3Combo: o Raio pula mais em alvos congelados");
                }
            }
            case FOGO -> {
                if (settings.flag(base + "choque-termico.ativado")) {
                    lines.add("§6Combo: alvos congelados levam dano em dobro");
                }
                if (settings.flag(base + "tempestade-fogo.ativado")) {
                    lines.add("§6Combo: alvos empurrados pelo Vento queimam mais e a explosão cresce");
                }
            }
            case RAIO -> {
                if (settings.flag(base + "condutor.ativado")) {
                    lines.add("§eCombo: alvo congelado = o raio pula para mais alvos");
                }
            }
            case VENTO -> {
                if (settings.flag(base + "tempestade-fogo.ativado")) {
                    lines.add("§fCombo: alvos empurrados queimam mais com o Fogo");
                }
            }
            case TELEPORTE -> {
                if (settings.flag(base + "passo-arcano.ativado")) {
                    lines.add("§bCombo: a próxima magia em "
                            + MagoSettings.format(settings.number(base + "passo-arcano.segundos"))
                            + "s não gasta mana");
                }
            }
        }
        if (!lines.isEmpty()) {
            lines.add(0, "");
        }
        return lines;
    }

    private static String percent(double fraction) {
        return MagoSettings.format(Math.round(fraction * 1000) / 10.0);
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
