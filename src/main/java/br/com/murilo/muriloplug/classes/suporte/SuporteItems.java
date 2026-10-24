package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.shop.ShopUi;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cria os itens do Suporte e os reconhece pelo PersistentDataContainer (chave mineplug:suporte_item), nunca pelo
 * nome. As habilidades gastam Harmonia; os itens não têm mais usos. Itens antigos (com usos, ou o Pergaminho antigo
 * de livro com pena) são atualizados por {@link #upgrade}: perdem os dados de usos e ganham a lore atual.
 */
public final class SuporteItems {

    private static final NamespacedKey SCROLL_MODEL = NamespacedKey.minecraft("writable_book");
    private static final NamespacedKey SHIELD_MODEL = NamespacedKey.minecraft("shield");
    private static final int TOTEM_STACK = 64;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final SuporteSettings settings;
    private final NamespacedKey itemKey;
    /** Chaves do antigo sistema de usos: só lidas para limpar os itens antigos. */
    private final NamespacedKey usesKey;
    private final NamespacedKey maxUsesKey;
    private final NamespacedKey uuidKey;

    public SuporteItems(JavaPlugin plugin, SuporteSettings settings) {
        this.settings = settings;
        this.itemKey = new NamespacedKey(plugin, "suporte_item");
        this.usesKey = new NamespacedKey(plugin, "suporte_usos");
        this.maxUsesKey = new NamespacedKey(plugin, "suporte_usos_max");
        this.uuidKey = new NamespacedKey(plugin, "suporte_uuid");
    }

    public ItemStack create(SuporteItem type) {
        ItemStack item = ShopUi.button(type.material(), type.displayName(), lore(type));
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.id());
        if (type == SuporteItem.PERGAMINHO) {
            meta.setItemModel(SCROLL_MODEL); // papel com cara de livro com pena, sem a tela de escrever
        } else if (type == SuporteItem.ESCUDO) {
            meta.setItemModel(SHIELD_MODEL); // cara de escudo, mas não bloqueia (não é um escudo de verdade)
        } else if (type == SuporteItem.TOTEM) {
            meta.setMaxStackSize(TOTEM_STACK);
        }
        item.setItemMeta(meta);
        if (type == SuporteItem.TOTEM) {
            // Não é um Totem da Imortalidade: sem a proteção contra morte, o jogo nunca o usa para salvar ninguém.
            item.unsetData(DataComponentTypes.DEATH_PROTECTION);
        }
        return item;
    }

    /** Tipo do item, ou null se não for um item do Suporte. */
    public SuporteItem read(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        SuporteItem type = SuporteItem.byId(
                item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
        return type != null && item.getType() == type.material() ? type : null;
    }

    /** Pergaminho antigo: livro com pena (WRITABLE_BOOK) com a marca do Pergaminho no PersistentDataContainer. */
    public boolean isLegacyScroll(ItemStack item) {
        if (item == null || item.getType() != Material.WRITABLE_BOOK || !item.hasItemMeta()) {
            return false;
        }
        return SuporteItem.PERGAMINHO.id().equals(
                item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    /**
     * Versão atual do item do Suporte: o Pergaminho antigo vira o novo; os outros perdem os dados de usos (e o UUID
     * que impedia empilhar) e recebem a lore atual, com a mesma quantidade. Se nada mudou (ou não é um item do
     * Suporte), devolve a mesma instância.
     */
    public ItemStack upgrade(ItemStack item) {
        if (isLegacyScroll(item)) {
            ItemStack fresh = create(SuporteItem.PERGAMINHO);
            fresh.setAmount(item.getAmount());
            return fresh;
        }
        SuporteItem type = read(item);
        if (type == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        for (NamespacedKey old : new NamespacedKey[]{usesKey, maxUsesKey, uuidKey}) {
            if (data.has(old)) {
                data.remove(old);
                changed = true;
            }
        }
        if (type == SuporteItem.TOTEM) {
            if (!meta.hasMaxStackSize() || meta.getMaxStackSize() != TOTEM_STACK) {
                meta.setMaxStackSize(TOTEM_STACK); // o Totem de Cura empilha
                changed = true;
            }
        } else if (meta.hasMaxStackSize()) {
            meta.setMaxStackSize(null); // os antigos de usos limitados não empilhavam
            changed = true;
        }
        List<Component> lore = loreComponents(type);
        if (!sameText(meta.lore(), lore)) {
            meta.lore(lore);
            changed = true;
        }
        if (!changed) {
            return item;
        }
        ItemStack updated = item.clone();
        updated.setItemMeta(meta);
        return updated;
    }

    /** Atualiza os itens do Suporte do inventário (inclui armadura e mão secundária). Retorna quantos trocou. */
    public int migrateInventory(PlayerInventory inventory) {
        int replaced = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack current = contents[slot];
            ItemStack updated = upgrade(current);
            if (updated != current) {
                inventory.setItem(slot, updated);
                replaced++;
            }
        }
        return replaced;
    }

    /** Compara pelo texto (com cores): o item salvo e recarregado pode ter os componentes montados de outro jeito. */
    private static boolean sameText(List<Component> a, List<Component> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return a == b;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!LEGACY.serialize(a.get(i)).equals(LEGACY.serialize(b.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private List<Component> loreComponents(SuporteItem type) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore(type)) {
            lines.add(ShopUi.text(line));
        }
        return lines;
    }

    private String[] lore(SuporteItem type) {
        if (type == SuporteItem.TOTEM) {
            return new String[]{
                    "§7Coloque no chão: cura os aliados em volta.",
                    "§7Tem " + format(settings.totemHealth()) + " de vida e pode ser destruído.",
                    "§7Uso único.",
                    "§dSomente Suportes"};
        }
        List<String> lore = new ArrayList<>(description(type));
        lore.add("");
        SuporteSkill main = mainSkill(type);
        if (main == null) {
            lore.add("§7Vincular: grátis, sem recarga");
        } else {
            double cooldown = settings.cooldownSeconds(main);
            lore.add("§7Custo: §d" + format(settings.cost(main)) + " de Harmonia");
            lore.add(cooldown > 0 ? "§7Recarga: " + format(cooldown) + "s" : "§7Sem recarga");
        }
        lore.add("§dSomente Suportes podem usar.");
        return lore.toArray(new String[0]);
    }

    /** Habilidade do clique direito do item (null no Núcleo, que vincula de graça). */
    private static SuporteSkill mainSkill(SuporteItem type) {
        return switch (type) {
            case VINCULO -> null;
            case PRENDER -> SuporteSkill.PRENDER;
            case CURA -> SuporteSkill.CURA;
            case PERGAMINHO -> SuporteSkill.PERGAMINHO;
            case VOO -> SuporteSkill.VOO;
            case ESCUDO -> SuporteSkill.ESCUDO;
            case TOTEM -> null;
        };
    }

    private List<String> description(SuporteItem type) {
        return switch (type) {
            case VINCULO -> List.of(
                    "§7[Dir] mirando em um aliado (até " + format(settings.linkRange()) + " blocos): vincula.",
                    "§7Ele leva " + percent(settings.linkResistance()) + " menos dano, você recebe "
                            + percent(settings.linkPercent()) + " do",
                    "§7dano dele e ele ganha " + percent(settings.linkSharedHeal()) + " das suas autocuras.",
                    "§7[Dir] de novo ou no ar: desfaz.",
                    "§7[Shift+Esq] §5Elo Drenante§7 (" + format(settings.cost(SuporteSkill.ELO)) + "): por "
                            + format(settings.drainSeconds()) + "s,",
                    "§7seu dano vira cura no vinculado.",
                    "§7[Shift+Dir] liga/desliga a §dTroca Equivalente§7:",
                    "§7salva o vinculado de um golpe mortal, mas sua",
                    "§7vida máxima cai para " + format(settings.swapMaxHealth() / 2) + " corações por "
                            + format(settings.swapSeconds()) + "s.");
            case PRENDER -> List.of(
                    "§7[Dir] mirando em um inimigo",
                    "§7(até " + format(settings.rootRange()) + " blocos) para prendê-lo",
                    "§7no chão por " + format(settings.rootSeconds()) + "s.",
                    "§7[Shift+Esq] §8Silêncio§7 (" + format(settings.cost(SuporteSkill.SILENCIO)) + "): o inimigo",
                    "§7na mira não usa habilidades por " + format(settings.silenceSeconds()) + "s.");
            case CURA -> List.of(
                    "§7[Dir] Cura " + format(settings.healAmount() / 2) + " corações de você e dos",
                    "§7aliados a até " + format(settings.healRadius()) + " blocos.",
                    "§7[Shift+Esq] §fPurificar§7 (" + format(settings.cost(SuporteSkill.PURIFICAR)) + "): tira os",
                    "§7efeitos ruins seus e dos aliados a até " + format(settings.purifyRadius()) + " blocos.");
            case PERGAMINHO -> List.of(
                    "§7[Dir] Dá Velocidade " + settings.scrollLevel() + " por " + settings.scrollSeconds() + "s a você",
                    "§7e aos aliados a até " + format(settings.scrollRadius()) + " blocos.",
                    "§7[Shift+Esq] §5Maldição§7 (" + format(settings.cost(SuporteSkill.MALDICAO)) + "): o inimigo",
                    "§7na mira causa " + percent(settings.curseReduction()) + " menos dano por "
                            + format(settings.curseSeconds()) + "s.");
            case VOO -> List.of(
                    "§7[Dir] Permite voar por " + settings.flightSeconds() + "s.",
                    "§7Você não leva dano da queda depois.",
                    "§7[Shift+Esq] §7Troca de Lugar§7 (" + format(settings.cost(SuporteSkill.TROCA_LUGAR)) + "):",
                    "§7troca de posição com o vinculado (até " + format(settings.placeDistance()) + " blocos).");
            case ESCUDO -> List.of(
                    "§7[Dir] mirando em um aliado (até " + format(settings.shieldRange()) + " blocos) ou no ar",
                    "§7(em você): " + format(settings.shieldAbsorption() / 2) + " corações de absorção por "
                            + format(settings.shieldSeconds()) + "s,",
                    "§7com um escudo azul em volta. Não bloqueia golpes.");
            case TOTEM -> List.of(); // lore própria em lore()
        };
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
