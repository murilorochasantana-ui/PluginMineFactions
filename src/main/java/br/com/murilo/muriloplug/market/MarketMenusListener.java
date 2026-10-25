package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.InventorySpace;
import br.com.murilo.muriloplug.shop.PriceButtons;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Cliques em todos os menus do mercado. REGRA DE OURO: todo clique e todo arrasto com um menu do mercado aberto
 * é cancelado (inclusive shift-click, teclas 1-9, tecla F, clique duplo e cliques no inventário do jogador).
 * Itens só mudam de lugar por código, depois de conferir de novo o anúncio e o inventário, e o anúncio é
 * removido ({@link MarketManager#remove}) ANTES de qualquer item ser entregue ou devolvido.
 */
public final class MarketMenusListener implements Listener {

    private final JavaPlugin plugin;
    private final MarketManager market;
    private final EconomyManager economy;

    public MarketMenusListener(JavaPlugin plugin, MarketManager market, EconomyManager economy) {
        this.plugin = plugin;
        this.market = market;
        this.economy = economy;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof MarketHolder)) {
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
            // Clique no inventário do próprio jogador: só serve para escolher o item no editor de venda.
            if (holder instanceof SellMenu sell && !sell.editing()) {
                ItemStack picked = event.getCurrentItem();
                if (picked != null && !picked.getType().isAir()) {
                    sell.choose(picked, event.getSlot());
                }
            }
            return;
        }

        int slot = event.getSlot();
        switch (holder) {
            case MarketMenu menu -> onMarketClick(player, menu, slot);
            case MarketPurchaseMenu menu -> onPurchaseClick(player, menu, slot);
            case SellMenu menu -> onSellClick(player, menu, click, slot);
            case MyListingsMenu menu -> onMyListingsClick(player, menu, slot);
            case ListingRemoveMenu menu -> onRemoveClick(player, menu, slot);
            default -> { }
        }
    }

    // ----------------------------------------------------------- tela principal

    private void onMarketClick(Player player, MarketMenu menu, int slot) {
        if (slot < MarketMenu.LISTINGS_PER_PAGE) {
            long id = menu.listingIdAt(slot);
            if (id < 0) {
                return;
            }
            Listing listing = market.get(id);
            if (listing == null) {
                player.sendMessage("§cEste anúncio não existe mais.");
                menu.render();
            } else if (listing.seller().equals(player.getUniqueId())) {
                later(() -> SellMenu.openEdit(player, listing, menu.page()));
            } else {
                later(() -> MarketPurchaseMenu.open(player, listing, menu.page(), economy));
            }
        } else if (slot == MarketMenu.PREVIOUS_SLOT && menu.page() > 0) {
            menu.changePage(-1);
        } else if (slot == MarketMenu.NEXT_SLOT && menu.page() < menu.pageCount() - 1) {
            menu.changePage(1);
        } else if (slot == MarketMenu.SELL_SLOT) {
            later(() -> SellMenu.openNew(player, menu.page()));
        } else if (slot == MarketMenu.MY_LISTINGS_SLOT) {
            later(() -> MyListingsMenu.open(player, market));
        }
    }

    // ------------------------------------------------------------------ compra

    private void onPurchaseClick(Player player, MarketPurchaseMenu menu, int slot) {
        if (slot == MarketPurchaseMenu.CANCEL_SLOT) {
            later(() -> openMarket(player, menu.page()));
        } else if (slot == MarketPurchaseMenu.CONFIRM_SLOT && !menu.used()) {
            purchase(player, menu);
        }
    }

    private void purchase(Player buyer, MarketPurchaseMenu menu) {
        UUID buyerId = buyer.getUniqueId();

        // 1. O anúncio ainda existe? (outro jogador pode ter comprado antes)
        Listing listing = market.get(menu.listingId());
        if (listing == null) {
            menu.markUsed();
            buyer.sendMessage("§cEste anúncio não existe mais (talvez já tenha sido vendido). Nada foi cobrado.");
            later(() -> openMarket(buyer, menu.page()));
            return;
        }
        // 2. Não é o próprio anúncio?
        if (listing.seller().equals(buyerId)) {
            menu.markUsed();
            buyer.sendMessage("§cVocê não pode comprar o seu próprio anúncio.");
            later(() -> openMarket(buyer, menu.page()));
            return;
        }
        // O preço também não pode ter mudado desde que o jogador o viu.
        if (listing.price() != menu.price()) {
            menu.markUsed();
            buyer.sendMessage("§cO preço deste anúncio mudou. Nada foi cobrado.");
            later(() -> openMarket(buyer, menu.page()));
            return;
        }

        long price = listing.price();
        // 3. Saldo + maços e 4. espaço no inventário (nesta ordem, sem cobrar nada ainda).
        if (economy.available(buyer) < price) {
            notEnoughMoney(buyer, price);
            return;
        }
        ItemStack copy = listing.item();
        if (!InventorySpace.fits(buyer.getInventory(), copy)) {
            buyer.sendMessage("§cSeu inventário não tem espaço para este item. Nada foi cobrado.");
            return;
        }

        // 5. Cobra o comprador (saldo primeiro; o que faltar sai de dentro dos maços).
        if (!economy.cobrar(buyer, price)) {
            notEnoughMoney(buyer, price);
            return;
        }
        // 6. Remove o anúncio: quem recebe o objeto de volta é o único dono do item.
        Listing sold = market.remove(listing.id());
        if (sold == null) {
            economy.deposit(buyerId, price);
            menu.markUsed();
            buyer.sendMessage("§cEste anúncio acabou de ser vendido. Nada foi cobrado.");
            later(() -> openMarket(buyer, menu.page()));
            return;
        }
        menu.markUsed();

        // 7. Entrega o item e 8. paga o vendedor (mesmo offline, pelo UUID).
        giveItem(buyer, copy);
        economy.deposit(sold.seller(), price);

        buyer.sendMessage(ShopUi.text("§aVocê comprou ").append(MarketUi.itemLabel(copy))
                .append(ShopUi.text("§a por " + EconomyManager.format(price))));
        Player seller = Bukkit.getPlayer(sold.seller());
        if (seller != null) {
            seller.sendMessage(ShopUi.text("§aSeu ").append(MarketUi.itemLabel(copy))
                    .append(ShopUi.text("§a foi vendido por " + EconomyManager.format(price)
                            + " para " + buyer.getName())));
        }
        later(() -> openMarket(buyer, menu.page()));
    }

    private void notEnoughMoney(Player player, long price) {
        player.sendMessage("§cVocê não tem dinheiro suficiente. Preço: " + EconomyManager.format(price)
                + ", disponível (saldo + maços): " + EconomyManager.format(economy.available(player)) + ".");
    }

    // ------------------------------------------------------- vender / editar

    private void onSellClick(Player player, SellMenu menu, ClickType click, int slot) {
        long delta = PriceButtons.delta(slot - SellMenu.FIRST_PRICE_SLOT, click);
        if (delta != 0) {
            menu.addToPrice(delta);
        } else if (slot == SellMenu.RESET_SLOT) {
            menu.resetPrice();
        } else if (slot == SellMenu.CANCEL_SLOT) {
            later(() -> openMarket(player, menu.backPage()));
        } else if (slot == SellMenu.CONFIRM_SLOT) {
            if (menu.editing()) {
                savePrice(player, menu);
            } else {
                announce(player, menu);
            }
        } else if (slot == SellMenu.REMOVE_SLOT && menu.editing()) {
            Listing listing = market.get(menu.editingId());
            if (listing == null || !listing.seller().equals(player.getUniqueId())) {
                player.sendMessage("§cEste anúncio não existe mais.");
                later(() -> openMarket(player, menu.backPage()));
            } else {
                later(() -> ListingRemoveMenu.open(player, listing, menu.backPage()));
            }
        }
    }

    private void announce(Player player, SellMenu menu) {
        if (menu.used()) {
            return;
        }
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
        UUID id = player.getUniqueId();

        // O item do slot guardado tem que ser EXATAMENTE o que foi escolhido (tipo, dados e quantidade).
        PlayerInventory inventory = player.getInventory();
        ItemStack inSlot = menu.chosenSlot() < 0 ? null : inventory.getItem(menu.chosenSlot());
        if (inSlot == null || !inSlot.isSimilar(chosen) || inSlot.getAmount() != chosen.getAmount()) {
            player.sendMessage("§cO item escolhido mudou ou saiu do seu inventário. Escolha o item de novo.");
            menu.clearChosen();
            return;
        }

        menu.markUsed();
        inventory.setItem(menu.chosenSlot(), null);
        Listing listing = market.add(id, player.getName(), chosen, menu.price());

        Bukkit.broadcast(ShopUi.text("§e" + player.getName() + " §7anunciou ")
                .append(MarketUi.itemLabel(listing.item()))
                .append(ShopUi.text(" §7por §a" + EconomyManager.format(listing.price()) + " §7no /mercado")));
        later(() -> openMarket(player, 0));
    }

    private void savePrice(Player player, SellMenu menu) {
        if (menu.used()) {
            return;
        }
        if (menu.price() <= 0) {
            player.sendMessage("§cDefina um preço maior que 0.");
            return;
        }
        // Confere de novo: o anúncio pode ter sido vendido enquanto o menu estava aberto.
        Listing listing = market.get(menu.editingId());
        if (listing == null || !listing.seller().equals(player.getUniqueId())) {
            menu.markUsed();
            player.sendMessage("§cEste anúncio não existe mais (talvez já tenha sido vendido).");
            later(() -> openMarket(player, menu.backPage()));
            return;
        }
        menu.markUsed();
        market.updatePrice(listing.id(), menu.price());
        player.sendMessage("§aPreço do anúncio atualizado para " + EconomyManager.format(menu.price()) + ".");
        later(() -> openMarket(player, menu.backPage()));
    }

    // ---------------------------------------------------------- meus anúncios

    private void onMyListingsClick(Player player, MyListingsMenu menu, int slot) {
        if (slot == MyListingsMenu.PREVIOUS_SLOT && menu.page() > 0) {
            menu.changePage(-1);
            return;
        }
        if (slot == MyListingsMenu.NEXT_SLOT && menu.page() < menu.pageCount() - 1) {
            menu.changePage(1);
            return;
        }
        if (slot == MyListingsMenu.BACK_SLOT) {
            later(() -> openMarket(player, 0));
            return;
        }

        long id = menu.listingIdAt(slot);
        if (id < 0) {
            return;
        }
        Listing listing = market.get(id);
        if (listing == null || !listing.seller().equals(player.getUniqueId())) {
            player.sendMessage("§cEste anúncio não existe mais.");
            menu.render();
        } else {
            later(() -> SellMenu.openEdit(player, listing, 0));
        }
    }

    // ---------------------------------------------------------------- remover

    private void onRemoveClick(Player player, ListingRemoveMenu menu, int slot) {
        if (slot == ListingRemoveMenu.CANCEL_SLOT) {
            later(() -> openMarket(player, menu.backPage()));
        } else if (slot == ListingRemoveMenu.CONFIRM_SLOT && !menu.used()) {
            removeListing(player, menu);
        }
    }

    private void removeListing(Player player, ListingRemoveMenu menu) {
        Listing listing = market.get(menu.listingId());
        if (listing == null || !listing.seller().equals(player.getUniqueId())) {
            menu.markUsed();
            player.sendMessage("§cEste anúncio não existe mais (talvez já tenha sido vendido).");
            later(() -> openMarket(player, menu.backPage()));
            return;
        }
        ItemStack copy = listing.item();
        if (!InventorySpace.fits(player.getInventory(), copy)) {
            player.sendMessage("§cSeu inventário não tem espaço para receber o item. O anúncio não foi removido.");
            return;
        }
        // Remove primeiro; só quem recebe o anúncio de volta devolve o item (nunca duas vezes).
        Listing removed = market.remove(listing.id());
        menu.markUsed();
        if (removed == null) {
            player.sendMessage("§cEste anúncio acabou de ser vendido.");
        } else {
            giveItem(player, copy);
            player.sendMessage(ShopUi.text("§aAnúncio removido. Você recebeu ").append(MarketUi.itemLabel(copy))
                    .append(ShopUi.text("§a de volta.")));
        }
        later(() -> openMarket(player, menu.backPage()));
    }

    // -------------------------------------------------------------- auxiliares

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        // Não deve acontecer (o espaço foi conferido), mas se sobrar algo, cai aos pés do jogador em vez de sumir.
        leftover.values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    private void openMarket(Player player, int page) {
        MarketMenu.open(player, market, economy, page);
    }

    /** Abrir ou fechar menus no tick seguinte, fora do evento de clique. */
    private void later(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }
}
