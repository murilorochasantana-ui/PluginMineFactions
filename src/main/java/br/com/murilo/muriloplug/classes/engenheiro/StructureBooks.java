package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Cria os Livros de Estrutura e os reconhece pelo PersistentDataContainer (chave mineplug:livro_estrutura, valor
 * "vazio" ou "cheio"), nunca pelo nome. Cada livro leva um UUID próprio, então nunca empilha.
 */
public final class StructureBooks {

    public static final String EMPTY = "vazio";
    public static final String FULL = "cheio";

    private static final String ONLY_ENGINEERS = "§5Somente Engenheiros podem usar.";

    private final EngenheiroSettings settings;
    private final NamespacedKey stateKey;
    private final NamespacedKey idKey;
    private final NamespacedKey structureKey;

    public StructureBooks(JavaPlugin plugin, EngenheiroSettings settings) {
        this.settings = settings;
        this.stateKey = new NamespacedKey(plugin, "livro_estrutura");
        this.idKey = new NamespacedKey(plugin, "livro_id");
        this.structureKey = new NamespacedKey(plugin, "estrutura_id");
    }

    /** Livro vazio com um UUID novo. */
    public ItemStack createEmpty() {
        int size = settings.areaSize();
        ItemStack item = ShopUi.button(Material.BOOK, "§eLivro de Estrutura §7(vazio)",
                "§7Clique direito para marcar uma área",
                "§7de construção de " + size + "x" + size + ".",
                ONLY_ENGINEERS);
        return tag(item, EMPTY, UUID.randomUUID(), null, false);
    }

    /** Livro cheio, ligado à estrutura salva em estruturas/&lt;structureId&gt;.nbt. */
    public ItemStack createFull(UUID bookId, String structureId, int sizeX, int sizeY, int sizeZ, int blocks,
                                String builder) {
        ItemStack item = ShopUi.button(Material.BOOK, "§6Livro de Estrutura: " + structureId.substring(0, 8),
                "§7Tamanho: " + sizeX + "x" + sizeY + "x" + sizeZ,
                "§7Blocos: " + blocks,
                "§7Construído por: " + builder,
                "§7Clique direito para colocar no mundo.",
                ONLY_ENGINEERS);
        return tag(item, FULL, bookId, structureId, true);
    }

    private ItemStack tag(ItemStack item, String state, UUID bookId, String structureId, boolean glint) {
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(stateKey, PersistentDataType.STRING, state);
        data.set(idKey, PersistentDataType.STRING, bookId.toString());
        if (structureId != null) {
            data.set(structureKey, PersistentDataType.STRING, structureId);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String state(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(stateKey, PersistentDataType.STRING);
    }

    public boolean isBook(ItemStack item) {
        return state(item) != null;
    }

    public boolean isEmpty(ItemStack item) {
        return EMPTY.equals(state(item));
    }

    public boolean isFull(ItemStack item) {
        return FULL.equals(state(item));
    }

    /** UUID do livro, ou null se o item não for um Livro de Estrutura. */
    public UUID bookId(ItemStack item) {
        if (!isBook(item)) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Id da estrutura de um livro cheio (nome do arquivo em estruturas/), ou null. */
    public String structureId(ItemStack item) {
        if (!isFull(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(structureKey, PersistentDataType.STRING);
    }
}
