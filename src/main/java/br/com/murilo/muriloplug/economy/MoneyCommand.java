package br.com.murilo.muriloplug.economy;

import br.com.murilo.muriloplug.economy.maco.Macos;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class MoneyCommand implements CommandExecutor, TabCompleter {

    public static final String ADMIN_PERMISSION = "mineplug.money.admin";

    private static final List<String> ADMIN_ACTIONS = List.of("dar", "tirar", "definir");

    private final EconomyManager economy;

    public MoneyCommand(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                sender.sendMessage("§eSaldo: §a" + EconomyManager.format(economy.getBalance(player.getUniqueId())));
                sender.sendMessage("§eEm maços no inventário: §a" + EconomyManager.format(macos().count(player)));
            } else {
                sender.sendMessage("Use /" + label + " <jogador>.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            admin(sender, label, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("pay")) {
            pay(sender, label, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("sacar")) {
            withdrawCash(sender, label, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("depositar")) {
            depositCash(sender, label, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("juntar")) {
            mergeCash(sender);
            return true;
        }

        if (args.length > 1) {
            sender.sendMessage("§cUso: /" + label + " [jogador]");
            return true;
        }
        OfflinePlayer target = findPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(notFound(args[0]));
            return true;
        }
        sender.sendMessage("§eSaldo de §f" + nameOf(target, args[0]) + "§e: §a"
                + EconomyManager.format(economy.getBalance(target.getUniqueId())));
        return true;
    }

    private void pay(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage("Apenas jogadores podem pagar.");
            return;
        }
        if (args.length != 3) {
            payer.sendMessage("§cUso: /" + label + " pay <jogador> <valor>");
            return;
        }
        Long amount = parseAmount(args[2], false);
        if (amount == null) {
            payer.sendMessage("§cO valor deve ser um número inteiro positivo.");
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            payer.sendMessage("§cJogador não encontrado.");
            return;
        }
        if (target.getUniqueId().equals(payer.getUniqueId())) {
            payer.sendMessage("§cVocê não pode pagar a si mesmo.");
            return;
        }
        // Evita perder dinheiro: o depósito é limitado a Long.MAX_VALUE, então recusa antes de cobrar.
        if (economy.getBalance(target.getUniqueId()) > Long.MAX_VALUE - amount) {
            payer.sendMessage("§cEste jogador não pode receber esse valor.");
            return;
        }

        // Primeiro tira do pagador; só se isso der certo soma no recebedor.
        if (!economy.withdraw(payer.getUniqueId(), amount)) {
            payer.sendMessage("§cSaldo insuficiente. Seu saldo: "
                    + EconomyManager.format(economy.getBalance(payer.getUniqueId())) + ".");
            return;
        }
        economy.deposit(target.getUniqueId(), amount);

        String name = nameOf(target, args[1]);
        payer.sendMessage("§aVocê enviou " + EconomyManager.format(amount) + " para " + name);
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage("§a" + payer.getName() + " te enviou " + EconomyManager.format(amount));
        }
    }

    private Macos macos() {
        return economy.macos();
    }

    /** /money sacar <valor> [separado]: tira do saldo e soma no maço do inventário (ou entrega um maço novo). */
    private void withdrawCash(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem sacar.");
            return;
        }
        boolean separate = args.length == 3 && args[2].equalsIgnoreCase("separado");
        if (args.length != 2 && !separate) {
            player.sendMessage("§cUso: /" + label + " sacar <valor> [separado]");
            return;
        }
        Long amount = parseAmount(args[1], false);
        if (amount == null) {
            player.sendMessage("§cO valor deve ser um número inteiro positivo.");
            return;
        }
        // Primeiro tira do saldo; só se isso der certo o dinheiro vira maço.
        if (!economy.withdraw(player.getUniqueId(), amount)) {
            player.sendMessage("§cSaldo insuficiente. Seu saldo: "
                    + EconomyManager.format(economy.getBalance(player.getUniqueId())) + ".");
            return;
        }
        if (separate) {
            macos().giveSeparate(player, amount);
        } else {
            macos().give(player, amount);
        }
        player.sendMessage("§aVocê sacou " + EconomyManager.format(amount)
                + (separate ? " em um maço novo." : " em maço."));
    }

    /** /money depositar [mao]: tira os maços do inventário (ou da mão) e soma ao saldo, menos a taxa. */
    private void depositCash(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem depositar.");
            return;
        }
        boolean hand = args.length == 2 && (args[1].equalsIgnoreCase("mao") || args[1].equalsIgnoreCase("mão"));
        if (args.length > 2 || (args.length == 2 && !hand)) {
            player.sendMessage("§cUso: /" + label + " depositar [mao]");
            return;
        }
        Macos macos = macos();
        // Os maços saem do inventário ANTES de o saldo mudar, no mesmo passo.
        long total = hand ? macos.takeMainHand(player) : macos.takeAll(player);
        if (total <= 0) {
            player.sendMessage(hand ? "§cVocê não está segurando um maço de dinheiro."
                    : "§cVocê não tem maços de dinheiro no inventário.");
            return;
        }
        long fee = Math.min(total, Math.round(total * macos.depositFee()));
        economy.deposit(player.getUniqueId(), total - fee);
        player.sendMessage("§aDepositado " + EconomyManager.format(total - fee) + " (taxa " + EconomyManager.format(fee) + ")");
    }

    /** /money juntar: junta todos os maços do inventário em um só. */
    private void mergeCash(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem juntar maços.");
            return;
        }
        int merged = macos().mergeAll(player);
        player.sendMessage(merged > 0
                ? "§aJuntei " + (merged + 1) + " maços em um só: " + EconomyManager.format(macos().count(player)) + "."
                : "§eVocê não tem mais de um maço para juntar.");
    }

    private void admin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVocê não tem permissão para usar este comando.");
            return;
        }
        if (args.length != 4 || !ADMIN_ACTIONS.contains(args[1].toLowerCase())) {
            sender.sendMessage("§cUso: /" + label + " admin <dar|tirar|definir> <jogador> <valor>");
            return;
        }

        String action = args[1].toLowerCase();
        Long amount = parseAmount(args[3], action.equals("definir"));
        if (amount == null) {
            sender.sendMessage(action.equals("definir")
                    ? "§cO valor deve ser um número inteiro maior ou igual a 0."
                    : "§cO valor deve ser um número inteiro positivo.");
            return;
        }
        OfflinePlayer target = findPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(notFound(args[2]));
            return;
        }

        String name = nameOf(target, args[2]);
        Player online = target.getPlayer();
        switch (action) {
            case "dar" -> {
                economy.deposit(target.getUniqueId(), amount);
                sender.sendMessage("§aVocê deu §f" + EconomyManager.format(amount) + "§a a §f" + name
                        + "§a. Novo saldo: " + EconomyManager.format(economy.getBalance(target.getUniqueId())));
                if (online != null) {
                    online.sendMessage("§aVocê recebeu §f" + EconomyManager.format(amount) + "§a.");
                }
            }
            case "tirar" -> {
                if (!economy.withdraw(target.getUniqueId(), amount)) {
                    sender.sendMessage("§c" + name + " não tem saldo suficiente (saldo: "
                            + EconomyManager.format(economy.getBalance(target.getUniqueId())) + ").");
                    return;
                }
                sender.sendMessage("§aVocê tirou §f" + EconomyManager.format(amount) + "§a de §f" + name
                        + "§a. Novo saldo: " + EconomyManager.format(economy.getBalance(target.getUniqueId())));
                if (online != null) {
                    online.sendMessage("§c" + EconomyManager.format(amount) + " foram removidos do seu saldo.");
                }
            }
            default -> {
                economy.setBalance(target.getUniqueId(), amount);
                sender.sendMessage("§aSaldo de §f" + name + "§a definido para §f" + EconomyManager.format(amount) + "§a.");
                if (online != null) {
                    online.sendMessage("§eSeu saldo foi definido para §a" + EconomyManager.format(amount) + "§e.");
                }
            }
        }
    }

    /** Número inteiro positivo (ou maior ou igual a zero, se {@code allowZero}); null se for inválido. */
    private static Long parseAmount(String text, boolean allowZero) {
        try {
            long value = Long.parseLong(text);
            return value > 0 || (allowZero && value == 0) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    private static String nameOf(OfflinePlayer player, String fallback) {
        return player.getName() != null ? player.getName() : fallback;
    }

    private static String notFound(String name) {
        return "§cJogador " + name + " não encontrado. Ele precisa estar online ou já ter entrado no servidor.";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (args.length == 1) {
            List<String> options = new ArrayList<>(onlineNames());
            options.add("pay");
            options.add("sacar");
            options.add("depositar");
            options.add("juntar");
            if (admin) {
                options.add("admin");
            }
            return options;
        }
        if (args[0].equalsIgnoreCase("depositar") && args.length == 2) {
            return List.of("mao");
        }
        if (args[0].equalsIgnoreCase("sacar") && args.length == 3) {
            return List.of("separado");
        }
        if (args[0].equalsIgnoreCase("pay") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(online -> !online.equals(sender))
                    .map(Player::getName)
                    .toList();
        }
        if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                return ADMIN_ACTIONS;
            }
            if (args.length == 3) {
                return onlineNames();
            }
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
