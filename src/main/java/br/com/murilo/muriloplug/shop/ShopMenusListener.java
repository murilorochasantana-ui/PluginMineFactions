package br.com.murilo.muriloplug.shop;

import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Cliques em todos os menus da loja. REGRA DE OURO: todo clique e todo arrasto com um menu da loja aberto
 * é cancelado (inclusive shift-click, teclas 1-9, tecla F, clique duplo e cliques no inventário do jogador).
 * Nenhum item se move: a loja só entrega itens por {@code addItem} depois de cobrar, e o editor só COPIA itens.
 */
public final class ShopMenusListener implements Listener {

    public static final String ADMIN_PERMISSION = "mineplug.loja.admin";

    private final JavaPlugin plugin;
    private final ShopManager shop;
    private final EconomyManager economy;

    public ShopMenusListener(JavaPlugin plugin, ShopManager shop, EconomyManager economy) {
        this.plugin = plugin;
        this.shop = shop;
        this.economy = economy;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof ShopHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Só clique esquerdo ou direito faz alguma coisa (teclas 1-9, F, Q, etc. são apenas cancelados).
        ClickType click = event.getClick();
        if (!click.isLeftClick() && !click.isRightClick()) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }

        if (clicked != top) {
            // Clique no inventário do próprio jogador: só serve para escolher o item no editor.
            if (holder instanceof ItemEditorMenu editor && requireAdmin(player)) {
                ItemStack picked = event.getCurrentItem();
                if (picked != null && !picked.getType().isAir()) {
                    editor.choose(picked);
                }
            }
            return;
        }

        int slot = event.getSlot();
        switch (holder) {
            case ShopMenu menu -> onShopClick(player, menu, slot);
            case PurchaseConfirmMenu menu -> onPurchaseClick(player, menu, slot);
            case ItemEditorMenu menu -> onEditorClick(player, menu, click, slot);
            case RemoveConfirmMenu menu -> onRemoveClick(player, menu, slot);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ loja

    private void onShopClick(Player player, ShopMenu menu, int slot) {
        boolean admin = menu.mode() == ShopMenu.Mode.ADMIN;
        if (admin && !requireAdmin(player)) {
            return;
        }

        if (slot < ShopMenu.ITEMS_PER_PAGE) {
            int id = menu.itemIdAt(slot);
            if (id < 0) {
                return;
            }
            ShopItem shopItem = shop.get(id);
            if (shopItem == null) {
                player.sendMessage("§cEste item não está mais na loja.");
                menu.render();
            } else if (admin) {
                later(() -> ItemEditorMenu.openEdit(player, shopItem, menu.page()));
            } else {
                later(() -> PurchaseConfirmMenu.open(player, shopItem, menu.page(), economy));
            }
        } else if (slot == ShopMenu.PREVIOUS_SLOT && menu.page() > 0) {
            menu.changePage(-1);
        } else if (slot == ShopMenu.NEXT_SLOT && menu.page() < menu.pageCount() - 1) {
            menu.changePage(1);
        } else if (slot == ShopMenu.ADD_SLOT && admin) {
            later(() -> ItemEditorMenu.openNew(player, menu.page()));
        }
    }

    // ---------------------------------------------------------------- compra

    private void onPurchaseClick(Player player, PurchaseConfirmMenu menu, int slot) {
        if (slot == PurchaseConfirmMenu.CANCEL_SLOT) {
            later(() -> openShop(player, ShopMenu.Mode.BUY, menu.page()));
        } else if (slot == PurchaseConfirmMenu.CONFIRM_SLOT && !menu.used()) {
            purchase(player, menu);
        }
    }

    private void purchase(Player player, PurchaseConfirmMenu menu) {
        // Confere de novo: o item pode ter sido removido ou ter mudado de preço desde que o jogador o viu.
        ShopItem current = shop.get(menu.itemId());
        if (current == null || current.price() != menu.price()) {
            menu.markUsed();
            player.sendMessage("§cEste item mudou ou foi removido da loja. Nada foi cobrado.");
            later(() -> openShop(player, ShopMenu.Mode.BUY, menu.page()));
            return;
        }

        long price = current.price();
        if (economy.available(player) < price) {
            notEnoughMoney(player, price);
            return;
        }
        ItemStack copy = current.item();
        if (!InventorySpace.fits(player.getInventory(), copy)) {
            player.sendMessage("§cSeu inventário não tem espaço para este item. Nada foi cobrado.");
            return;
        }
        // Saldo primeiro; o que faltar sai das notas (com troco em notas).
        if (!economy.cobrar(player, price)) {
            notEnoughMoney(player, price);
            return;
        }

        menu.markUsed();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(copy);
        // Não deve acontecer (o espaço foi conferido), mas se sobrar algo, cai aos pés do jogador em vez de sumir.
        leftover.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));

        String amount = copy.getAmount() > 1 ? copy.getAmount() + "x " : "";
        player.sendMessage(ShopUi.text("§aVocê comprou " + amount).append(copy.displayName())
                .append(ShopUi.text("§a por " + EconomyManager.format(price))));
        later(() -> openShop(player, ShopMenu.Mode.BUY, menu.page()));
    }

    private void notEnoughMoney(Player player, long price) {
        player.sendMessage("§cVocê não tem dinheiro suficiente. Preço: " + EconomyManager.format(price)
                + ", disponível (saldo + notas): " + EconomyManager.format(economy.available(player)) + ".");
    }

    // ---------------------------------------------------------------- editor

    private void onEditorClick(Player player, ItemEditorMenu menu, ClickType click, int slot) {
        if (!requireAdmin(player)) {
            return;
        }

        long delta = PriceButtons.delta(slot - ItemEditorMenu.FIRST_PRICE_SLOT, click);
        if (delta != 0) {
            menu.addToPrice(delta);
        } else if (slot == ItemEditorMenu.RESET_SLOT) {
            menu.resetPrice();
        } else if (slot == ItemEditorMenu.CANCEL_SLOT) {
            later(() -> openShop(player, ShopMenu.Mode.ADMIN, menu.backPage()));
        } else if (slot == ItemEditorMenu.SAVE_SLOT) {
            save(player, menu);
        } else if (slot == ItemEditorMenu.REMOVE_SLOT && menu.editingId() != null) {
            ShopItem shopItem = shop.get(menu.editingId());
            if (shopItem == null) {
                player.sendMessage("§cEste item não está mais na loja.");
                later(() -> openShop(player, ShopMenu.Mode.ADMIN, menu.backPage()));
            } else {
                later(() -> RemoveConfirmMenu.open(player, shopItem, menu.backPage()));
            }
        }
    }

    private void save(Player player, ItemEditorMenu menu) {
        ItemStack chosen = menu.chosen();
        if (chosen == null || menu.price() <= 0) {
            if (chosen == null && menu.price() <= 0) {
                player.sendMessage("§cEscolha um item (clique em um item do seu inventário) e defina um preço maior que 0.");
            } else if (chosen == null) {
                player.sendMessage("§cEscolha um item: clique em um item do seu inventário.");
            } else {
                player.sendMessage("§cDefina um preço maior que 0.");
            }
            return;
        }
        if (menu.used()) {
            return;
        }
        menu.markUsed();

        if (menu.editingId() == null) {
            shop.add(chosen, menu.price());
            player.sendMessage("§aItem adicionado à loja por " + EconomyManager.format(menu.price()) + ".");
        } else if (shop.update(menu.editingId(), chosen, menu.price())) {
            player.sendMessage("§aItem atualizado na loja (" + EconomyManager.format(menu.price()) + ").");
        } else {
            player.sendMessage("§cEste item não está mais na loja.");
        }
        later(() -> openShop(player, ShopMenu.Mode.ADMIN, menu.backPage()));
    }

    // --------------------------------------------------------------- remover

    private void onRemoveClick(Player player, RemoveConfirmMenu menu, int slot) {
        if (!requireAdmin(player)) {
            return;
        }
        if (slot == RemoveConfirmMenu.CANCEL_SLOT) {
            later(() -> openShop(player, ShopMenu.Mode.ADMIN, menu.backPage()));
        } else if (slot == RemoveConfirmMenu.CONFIRM_SLOT && !menu.used()) {
            menu.markUsed();
            player.sendMessage(shop.remove(menu.itemId())
                    ? "§aItem removido da loja."
                    : "§cEste item não está mais na loja.");
            later(() -> openShop(player, ShopMenu.Mode.ADMIN, menu.backPage()));
        }
    }

    // --------------------------------------------------------------- auxiliares

    /** Confere a permissão a cada ação do modo admin; sem ela, fecha o menu. */
    private boolean requireAdmin(Player player) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        player.sendMessage("§cVocê não tem permissão.");
        later(() -> player.closeInventory());
        return false;
    }

    private void openShop(Player player, ShopMenu.Mode mode, int page) {
        ShopMenu.open(player, shop, economy, mode, page);
    }

    /** Abrir ou fechar menus no tick seguinte, fora do evento de clique. */
    private void later(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }
}
