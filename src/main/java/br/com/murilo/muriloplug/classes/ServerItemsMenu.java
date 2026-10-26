package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * "Itens do Servidor" do /kit admin (54 slots): os itens avulsos (clique = 1, Shift + clique = um pack), "Pegar 1 de
 * cada", os ícones das categorias e "Voltar". Uma categoria aberta mostra uma linha por tipo (nível I à esquerda).
 * Os itens do menu são só amostras: o {@link KitMenusListener} cancela todo clique e entrega um item NOVO da fábrica.
 */
public final class ServerItemsMenu implements InventoryHolder {

    static final int ALL_SLOT = 45;
    static final int BACK_SLOT = 49;
    static final int PREVIOUS_SLOT = 45;
    static final int NEXT_SLOT = 53;
    private static final int ROWS_PER_PAGE = 5;
    private static final int[] CATEGORY_SLOTS = {47, 51, 46, 52, 48, 50};
    private static final int ITEM_SLOTS = 45;

    private final Inventory inventory;
    /** Categoria aberta (null = tela principal dos itens do servidor). */
    private final ServerItems.Category category;
    private final int page;
    private final Map<Integer, Supplier<ItemStack>> factories = new HashMap<>();
    private final Map<Integer, ServerItems.Category> categoryButtons = new HashMap<>();

    private ServerItemsMenu(ServerItems serverItems, ServerItems.Category category, int page) {
        this.category = category;
        int current = 0;
        String title = category == null ? "Itens do Servidor" : category.name().replaceAll("§.", "");
        this.inventory = Bukkit.createInventory(this, 54, Component.text(title));
        if (category == null) {
            List<Supplier<ItemStack>> items = serverItems.factories();
            for (int i = 0; i < items.size() && i < ITEM_SLOTS; i++) {
                put(i, items.get(i));
            }
            inventory.setItem(ALL_SLOT, ShopUi.button(Material.CHEST, "§ePegar 1 de cada",
                    "§7Todos os itens avulsos acima", "§7(sem as categorias)."));
            List<ServerItems.Category> categories = serverItems.categories();
            for (int i = 0; i < categories.size() && i < CATEGORY_SLOTS.length; i++) {
                ServerItems.Category entry = categories.get(i);
                inventory.setItem(CATEGORY_SLOTS[i], ShopUi.button(entry.icon(), entry.name(), "§eClique para abrir"));
                categoryButtons.put(CATEGORY_SLOTS[i], entry);
            }
        } else {
            // Uma linha por tipo; mais de 5 tipos viram páginas.
            List<List<Supplier<ItemStack>>> rows = category.rows().get();
            int pages = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
            current = Math.max(0, Math.min(pages - 1, page));
            for (int row = 0; row < ROWS_PER_PAGE && current * ROWS_PER_PAGE + row < rows.size(); row++) {
                List<Supplier<ItemStack>> line = rows.get(current * ROWS_PER_PAGE + row);
                for (int column = 0; column < line.size() && column < 9; column++) {
                    put(row * 9 + column, line.get(column));
                }
            }
            if (current > 0) {
                inventory.setItem(PREVIOUS_SLOT, ShopUi.button(Material.ARROW, "§7Página anterior"));
            }
            if (current + 1 < pages) {
                inventory.setItem(NEXT_SLOT, ShopUi.button(Material.ARROW, "§7Próxima página §8("
                        + (current + 2) + "/" + pages + ")"));
            }
        }
        this.page = current;
        for (int slot = ITEM_SLOTS; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, ShopUi.filler());
            }
        }
        inventory.setItem(BACK_SLOT, ShopUi.button(Material.OAK_DOOR, "§7Voltar"));
    }

    private void put(int slot, Supplier<ItemStack> factory) {
        ItemStack sample = factory.get();
        if (sample == null || sample.getType().isAir()) {
            return;
        }
        inventory.setItem(slot, sample);
        factories.put(slot, factory);
    }

    public static void open(Player player, ServerItems serverItems, ServerItems.Category category) {
        open(player, serverItems, category, 0);
    }

    public static void open(Player player, ServerItems serverItems, ServerItems.Category category, int page) {
        player.openInventory(new ServerItemsMenu(serverItems, category, page).getInventory());
    }

    int page() {
        return page;
    }

    /** Categoria aberta (null = tela principal). */
    ServerItems.Category category() {
        return category;
    }

    /** Fábrica do item no slot, ou null. */
    Supplier<ItemStack> factoryAt(int slot) {
        return factories.get(slot);
    }

    /** Categoria do botão no slot (só na tela principal), ou null. */
    ServerItems.Category categoryAt(int slot) {
        return categoryButtons.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
