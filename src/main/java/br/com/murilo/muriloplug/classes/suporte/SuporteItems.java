package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
import java.util.UUID;

/**
 * Cria os itens do Suporte e os reconhece pelo PersistentDataContainer (chave mineplug:suporte_item), nunca pelo
 * nome. Os itens de usos limitados guardam os usos restantes no próprio item e levam um UUID, então não empilham.
 */
public final class SuporteItems {

    private static final NamespacedKey SCROLL_MODEL = NamespacedKey.minecraft("writable_book");

    private final SuporteSettings settings;
    private final NamespacedKey itemKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey maxUsesKey;
    private TestMode test = TestMode.inactive();
    private final NamespacedKey uuidKey;

    public SuporteItems(JavaPlugin plugin, SuporteSettings settings) {
        this.settings = settings;
        this.itemKey = new NamespacedKey(plugin, "suporte_item");
        this.usesKey = new NamespacedKey(plugin, "suporte_usos");
        this.maxUsesKey = new NamespacedKey(plugin, "suporte_usos_max");
        this.uuidKey = new NamespacedKey(plugin, "suporte_uuid");
    }

    public ItemStack create(SuporteItem type) {
        int uses = settings.uses(type);
        ItemStack item = ShopUi.button(type.material(), type.displayName(), lore(type, uses, uses));
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(itemKey, PersistentDataType.STRING, type.id());
        if (type == SuporteItem.PERGAMINHO) {
            meta.setItemModel(SCROLL_MODEL); // papel com cara de livro com pena, sem a tela de escrever
        }
        if (uses > 0) {
            meta.setMaxStackSize(1);
            data.set(usesKey, PersistentDataType.INTEGER, uses);
            data.set(maxUsesKey, PersistentDataType.INTEGER, uses);
            data.set(uuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        item.setItemMeta(meta);
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
     * Pergaminho novo no lugar do antigo, com a mesma quantidade (e os mesmos usos e UUID, se o antigo tinha).
     * Qualquer outro item volta como veio, a mesma instância.
     */
    public ItemStack upgradeLegacy(ItemStack item) {
        if (!isLegacyScroll(item)) {
            return item;
        }
        PersistentDataContainer old = item.getItemMeta().getPersistentDataContainer();
        ItemStack fresh = create(SuporteItem.PERGAMINHO);
        fresh.setAmount(item.getAmount());
        Integer left = old.get(usesKey, PersistentDataType.INTEGER);
        Integer max = old.get(maxUsesKey, PersistentDataType.INTEGER);
        if (left != null && max != null && max > 0) {
            ItemMeta meta = fresh.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            meta.setMaxStackSize(1);
            data.set(usesKey, PersistentDataType.INTEGER, left);
            data.set(maxUsesKey, PersistentDataType.INTEGER, max);
            String uuid = old.get(uuidKey, PersistentDataType.STRING);
            data.set(uuidKey, PersistentDataType.STRING, uuid != null ? uuid : UUID.randomUUID().toString());
            setLore(meta, SuporteItem.PERGAMINHO, left, max);
            fresh.setItemMeta(meta);
        }
        return fresh;
    }

    /** Troca os Pergaminhos antigos do inventário (inclui armadura e mão secundária). Retorna quantos trocou. */
    public int migrateInventory(PlayerInventory inventory) {
        int replaced = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isLegacyScroll(contents[slot])) {
                inventory.setItem(slot, upgradeLegacy(contents[slot]));
                replaced++;
            }
        }
        return replaced;
    }

    /**
     * Gasta 1 uso do item que está na mão principal (se for de usos limitados), atualiza a lore e remove o item
     * quando chega a 0.
     */
    /** Modo Teste dos administradores: sem custos nem recargas. */
    public void setTestMode(TestMode test) {
        this.test = test;
    }

    public void consumeUse(Player player) {
        if (test.isActive(player)) {
            return; // Modo Teste: os usos não diminuem
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        SuporteItem type = read(hand);
        if (type == null) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Integer max = data.get(maxUsesKey, PersistentDataType.INTEGER);
        if (max == null || max <= 0) {
            return; // item de usos ilimitados
        }
        int left = data.getOrDefault(usesKey, PersistentDataType.INTEGER, max) - 1;
        if (left <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.sendActionBar(ShopUi.text("§7Seu item " + type.displayName() + " §7acabou os usos."));
            return;
        }
        data.set(usesKey, PersistentDataType.INTEGER, left);
        setLore(meta, type, left, max);
        hand.setItemMeta(meta);
        player.getInventory().setItemInMainHand(hand);
    }

    private void setLore(ItemMeta meta, SuporteItem type, int usesLeft, int usesMax) {
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        for (String line : lore(type, usesLeft, usesMax)) {
            lines.add(ShopUi.text(line));
        }
        meta.lore(lines);
    }

    private String[] lore(SuporteItem type, int usesLeft, int usesMax) {
        List<String> lore = new ArrayList<>(description(type));
        lore.add("");
        double cooldown = settings.cooldownSeconds(type);
        lore.add(cooldown > 0 ? "§7Recarga: " + format(cooldown) + "s" : "§7Sem recarga");
        lore.add(usesMax > 0 ? "§7Usos: " + usesLeft + "/" + usesMax : "§7Usos: ilimitados");
        lore.add("§dSomente Suportes podem usar.");
        return lore.toArray(new String[0]);
    }

    private List<String> description(SuporteItem type) {
        return switch (type) {
            case VINCULO -> List.of(
                    "§7Clique direito mirando em um aliado",
                    "§7para se vincular a ele (até " + format(settings.linkRange()) + " blocos).",
                    "§7Ele leva " + Math.round(settings.linkPercent() * 100) + "% menos dano e você recebe essa parte.",
                    "§7Clique de novo para desfazer.");
            case PRENDER -> List.of(
                    "§7Clique direito mirando em um inimigo",
                    "§7(até " + format(settings.rootRange()) + " blocos) para prendê-lo",
                    "§7no chão por " + format(settings.rootSeconds()) + "s.");
            case CURA -> List.of(
                    "§7Cura " + format(settings.healAmount() / 2) + " corações de você e dos",
                    "§7aliados a até " + format(settings.healRadius()) + " blocos.");
            case PERGAMINHO -> List.of(
                    "§7Dá Velocidade " + settings.scrollLevel() + " por " + settings.scrollSeconds() + "s a você",
                    "§7e aos aliados a até " + format(settings.scrollRadius()) + " blocos.");
            case VOO -> List.of(
                    "§7Permite voar por " + settings.flightSeconds() + "s.",
                    "§7Você não leva dano da queda depois.");
        };
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
