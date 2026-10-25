package br.com.murilo.muriloplug.economy.vender;

import br.com.murilo.muriloplug.economy.ChatConfirmations;
import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /vender (baú de venda), /vender mao, /vender precos e /vender admin. Os itens do baú de venda só saem dele de
 * dois jeitos: vendidos (tirados do baú e só então pagos) ou devolvidos ao fechar (ou no onDisable).
 */
public final class VenderCommand implements CommandExecutor, TabCompleter, Listener {

    public static final String ADMIN_PERMISSION = "mineplug.vender.admin";

    private final JavaPlugin plugin;
    private final SellPrices prices;
    private final EconomyManager economy;
    private final ChatConfirmations confirmations = new ChatConfirmations("vender", 30);
    /** Baús de venda abertos (para devolver os itens no onDisable). */
    private final Map<UUID, SellChestMenu> open = new HashMap<>();

    public VenderCommand(JavaPlugin plugin, SellPrices prices, EconomyManager economy) {
        this.plugin = plugin;
        this.prices = prices;
        this.economy = economy;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, prices::tick, 20L * 60, 20L * 60);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length == 0) {
            SellChestMenu menu = new SellChestMenu(prices);
            player.openInventory(menu.getInventory());
            open.put(player.getUniqueId(), menu);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mao", "mão" -> sellHand(player);
            case "precos", "preços" -> PricesMenu.open(player, prices);
            case "confirmar" -> {
                if (args.length == 2) {
                    confirmations.confirm(player, args[1]);
                }
            }
            case "admin" -> admin(player, label, args);
            default -> player.sendMessage("§cUso: /" + label + " [mao|precos]");
        }
        return true;
    }

    // ------------------------------------------------------------------ /vender mao

    private void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§cSegure na mão o item que quer vender.");
            return;
        }
        String refusal = prices.refusal(hand);
        if (refusal != null) {
            player.sendMessage("§cEste item não pode ser vendido (" + refusal + ").");
            return;
        }
        Material material = hand.getType();
        int amount = hand.getAmount();
        int slot = player.getInventory().getHeldItemSlot();
        long payout = new SellPrices.Quote(amount, prices.quote(material, amount)).payout();
        if (payout <= 0) {
            player.sendMessage("§cEsta quantidade vale menos de $1. Junte mais itens.");
            return;
        }
        confirmations.ask(player, "§eVender §f" + amount + "x " + material.name().toLowerCase(Locale.ROOT)
                + " §epor §a" + EconomyManager.format(payout) + "§e?", p -> confirmHand(p, material, amount, slot));
    }

    private void confirmHand(Player player, Material material, int amount, int slot) {
        // Confere de novo: o item tem que ser o mesmo, no mesmo slot, com a mesma quantidade.
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType() != material || item.getAmount() != amount || prices.refusal(item) != null) {
            player.sendMessage("§cO item da mão mudou. Use /vender mao de novo.");
            return;
        }
        player.getInventory().setItem(slot, null); // tira primeiro, paga depois
        long payout = new SellPrices.Quote(amount, prices.sell(material, amount)).payout();
        pay(player, amount, payout);
    }

    private void pay(Player player, int units, long payout) {
        boolean notes = economy.pagarGanho(player.getUniqueId(), payout);
        player.sendMessage("§aVocê vendeu " + units + " itens por " + EconomyManager.format(payout)
                + (notes ? " §7(em maço)" : ""));
    }

    // ------------------------------------------------------------------ admin

    private void admin(Player player, String label, String[] args) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return;
        }
        Material material = player.getInventory().getItemInMainHand().getType();
        if (args.length >= 2 && args[1].equalsIgnoreCase("preco") && args.length == 3) {
            if (material.isAir()) {
                player.sendMessage("§cSegure na mão o item que quer colocar na tabela.");
                return;
            }
            double price;
            try {
                price = Double.parseDouble(args[2].replace(',', '.'));
            } catch (NumberFormatException e) {
                price = -1;
            }
            if (!(price > 0) || price > 1_000_000) {
                player.sendMessage("§cO preço deve ser um número maior que 0 (ex: 2.5).");
                return;
            }
            prices.setBasePrice(material, price);
            player.sendMessage("§aPreço base de " + material.name() + " definido para " + SellPrices.formatPrice(price) + ".");
        } else if (args.length == 2 && args[1].equalsIgnoreCase("remover")) {
            player.sendMessage(prices.remove(material)
                    ? "§a" + material.name() + " saiu da tabela de venda."
                    : "§c" + material.name() + " não está na tabela de venda.");
        } else {
            player.sendMessage("§cUso: /" + label + " admin preco <valor> | admin remover §7(item da mão)");
        }
    }

    // ------------------------------------------------------------------ menus

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof PricesMenu) {
            event.setCancelled(true);
        } else if (holder instanceof SellChestMenu menu) {
            for (int raw : event.getRawSlots()) {
                if (menu.confirming() || (raw >= SellChestMenu.AREA_SIZE && raw < 54)) {
                    event.setCancelled(true);
                    return;
                }
            }
            refreshLater(menu);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof PricesMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top) {
                if (event.getSlot() == PricesMenu.PREVIOUS_SLOT) {
                    menu.changePage(-1);
                } else if (event.getSlot() == PricesMenu.NEXT_SLOT) {
                    menu.changePage(1);
                }
            }
            return;
        }
        if (!(holder instanceof SellChestMenu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (menu.finished()) {
            event.setCancelled(true);
            return;
        }
        // Juntar no cursor (clique duplo) pegaria itens de qualquer lugar do baú: sempre bloqueado.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        boolean inTop = event.getClickedInventory() == top;
        if (menu.confirming()) {
            event.setCancelled(true);
            if (inTop && event.getSlot() == SellChestMenu.CANCEL_SLOT) {
                menu.setConfirming(false);
            } else if (inTop && event.getSlot() == SellChestMenu.CONFIRM_SLOT) {
                sell(player, menu);
            }
            return;
        }
        if (inTop && event.getSlot() >= SellChestMenu.AREA_SIZE) {
            event.setCancelled(true);
            if (event.getSlot() == SellChestMenu.SELL_SLOT && event.getClick() != ClickType.DOUBLE_CLICK) {
                SellChestMenu.Summary summary = menu.summary();
                if (summary.totalUnits() == 0) {
                    player.sendMessage("§cColoque itens com preço nas linhas de cima (veja /vender precos).");
                } else if (summary.payout() <= 0) {
                    player.sendMessage("§cOs itens valem menos de $1. Junte mais itens.");
                } else {
                    menu.setConfirming(true);
                }
            }
            return;
        }
        refreshLater(menu); // itens entraram ou saíram: atualiza o total
    }

    private void sell(Player player, SellChestMenu menu) {
        SellChestMenu.Summary summary = menu.summary();
        if (summary.totalUnits() == 0 || summary.payout() <= 0) {
            menu.setConfirming(false);
            return;
        }
        // Tira os itens do baú; só então baixa o preço e paga.
        SellChestMenu.Summary taken = menu.takeSellable();
        double total = 0;
        for (Map.Entry<Material, Integer> entry : taken.units().entrySet()) {
            total += prices.sell(entry.getKey(), entry.getValue());
        }
        pay(player, taken.totalUnits(), new SellPrices.Quote(taken.totalUnits(), total).payout());
        menu.setConfirming(false);
    }

    private void refreshLater(SellChestMenu menu) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!menu.finished()) {
                menu.render();
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SellChestMenu menu && event.getPlayer() instanceof Player player) {
            menu.giveBack(player);
            open.remove(player.getUniqueId(), menu);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        confirmations.forget(event.getPlayer().getUniqueId());
    }

    /** onDisable: devolve os itens de todos os baús de venda abertos e fecha os menus. */
    public void shutdown() {
        for (Map.Entry<UUID, SellChestMenu> entry : Map.copyOf(open).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().giveBack(player);
                player.closeInventory();
            }
        }
        open.clear();
        prices.save();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (args.length == 1) {
            return admin ? List.of("mao", "precos", "admin") : List.of("mao", "precos");
        }
        if (admin && args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("preco", "remover");
        }
        return List.of();
    }
}
