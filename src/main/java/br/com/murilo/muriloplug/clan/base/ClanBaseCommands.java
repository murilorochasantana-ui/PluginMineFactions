package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanCommand;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** Subcomandos do /clan da base: nucleo, top, pagar, manutencao e admin (juros, manutencao, item). */
public final class ClanBaseCommands {

    public static final String ADMIN_PERMISSION = "mineplug.clan.admin";

    private final ClanBaseManager manager;
    private final BaseBlocks blocks;
    private final BaseItems items;
    private final ClanManager clans;

    public ClanBaseCommands(ClanBaseManager manager, BaseBlocks blocks, BaseItems items) {
        this.manager = manager;
        this.blocks = blocks;
        this.items = items;
        this.clans = manager.clans();
    }

    public void registerOn(ClanCommand command) {
        command.register("nucleo", new ClanCommand.Extra("pega o Núcleo do clan (líder) ou o recolhe",
                this::core, (player, args) -> args.length == 2 ? List.of("recolher") : List.of()));
        command.register("top", new ClanCommand.Extra("ranking dos clans por Poder",
                (player, args) -> top(player), (player, args) -> List.of()));
        command.register("pagar", new ClanCommand.Extra("paga a dívida da manutenção dos claims",
                (player, args) -> player.sendMessage(manager.payDebt(player)), (player, args) -> List.of()));
        command.register("manutencao", new ClanCommand.Extra("custo da manutenção dos claims e dívida",
                (player, args) -> upkeep(player), (player, args) -> List.of()));
        command.register("admin", new ClanCommand.Extra("", this::admin, (player, args) -> {
            if (!player.hasPermission(ADMIN_PERMISSION)) {
                return List.of();
            }
            if (args.length == 2) {
                return List.of("juros", "manutencao", "item");
            }
            return args.length == 3 && args[1].equalsIgnoreCase("item") ? List.of("nucleo", "cofre") : List.of();
        }));
    }

    private void core(Player player, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("recolher")) {
            player.sendMessage(blocks.pickUpCore(player));
            return;
        }
        if (args.length != 1) {
            player.sendMessage("§cUso: /clan nucleo [recolher]");
            return;
        }
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.leader().equals(player.getUniqueId())) {
            player.sendMessage("§cSó o líder do clan pode pegar o Núcleo.");
            return;
        }
        ClanBase base = manager.of(clan);
        if (base.freeCoreTaken) {
            player.sendMessage("§cSeu clan já pegou o Núcleo grátis. Compre outro na /loja.");
            return;
        }
        base.freeCoreTaken = true;
        manager.save();
        give(player, items.create(BaseItems.Kind.CORE));
        player.sendMessage("§aVocê recebeu o §6Núcleo do Clan§a. Coloque-o dentro de um claim de um membro do clan.");
    }

    private void top(Player player) {
        List<Clan> ranking = manager.ranking();
        player.sendMessage("§6§lClans por Poder:");
        if (ranking.isEmpty()) {
            player.sendMessage("§7Nenhum clan ainda.");
            return;
        }
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            Clan clan = ranking.get(i);
            player.sendMessage("§6#" + (i + 1) + " §e[" + clan.tag() + "] " + clan.name() + " §7- Poder §f"
                    + manager.of(clan).power());
        }
    }

    private void upkeep(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        String time = manager.settings().upkeepTime().toString();
        if (clan == null) {
            long cost = manager.upkeepCost(player.getUniqueId());
            long debt = manager.playerDebt(player.getUniqueId());
            player.sendMessage("§6Manutenção dos seus claims §7(todo dia às " + time + ", paga com o seu saldo):");
            player.sendMessage("§7Custo da próxima: §f" + EconomyManager.format(cost));
            player.sendMessage(debt > 0 ? "§c§lEM DÍVIDA: §c" + EconomyManager.format(debt) + " §7(use /clan pagar)"
                    : "§aEm dia.");
            return;
        }
        ClanBase base = manager.of(clan);
        player.sendMessage("§6Manutenção dos claims do clan §7(todo dia às " + time + ", paga pelo Cofre):");
        player.sendMessage("§7Custo da próxima: §f" + EconomyManager.format(manager.upkeepCost(clan))
                + " §8(área × " + manager.settings().upkeepPerBlock() + " × (1 + Poder "
                + base.power() + " × " + manager.settings().upkeepPowerWeight() + "))");
        player.sendMessage("§7Cofre: " + (base.vault() == null ? "§cnão colocado" : "§a" + EconomyManager.format(base.vaultBalance())));
        player.sendMessage(base.debt() > 0 ? "§c§lEM DÍVIDA: §c" + EconomyManager.format(base.debt()) + " §7(use /clan pagar)"
                : "§aEm dia.");
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "juros" -> {
                manager.applyInterestHour();
                player.sendMessage("§aAplicada 1 hora de juros aos Cofres.");
            }
            case "manutencao" -> {
                manager.runUpkeep();
                player.sendMessage("§aManutenção cobrada agora de todos os clans e jogadores.");
            }
            case "item" -> {
                if (args.length != 3 || !(args[2].equalsIgnoreCase("nucleo") || args[2].equalsIgnoreCase("cofre"))) {
                    player.sendMessage("§cUso: /clan admin item <nucleo|cofre>");
                    return;
                }
                give(player, items.create(args[2].equalsIgnoreCase("nucleo") ? BaseItems.Kind.CORE : BaseItems.Kind.VAULT));
                player.sendMessage("§aItem entregue (para vender na /loja admin, clique nele no editor).");
            }
            default -> player.sendMessage("§cUso: /clan admin <juros|manutencao|item <nucleo|cofre>>");
        }
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }
}
