package br.com.murilo.muriloplug.maquinas;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /maquinas: menu das máquinas (comprar, minhas máquinas, juntar upgrades).
 * /maquinas admin: painel de controle; admin dar &lt;tipo&gt; [jogador]; admin upgrade &lt;tipo&gt; &lt;nível&gt;
 * [quantidade] [jogador] (permissão mineplug.maquinas.admin).
 */
public final class MaquinasCommand implements CommandExecutor, TabCompleter {

    private final MaquinaManager manager;
    private final MaquinasMenus menus;
    private final PainelMaquinas painel;

    public MaquinasCommand(MaquinaManager manager, MaquinasMenus menus, PainelMaquinas painel) {
        this.manager = manager;
        this.menus = menus;
        this.painel = painel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Apenas jogadores podem usar este comando.");
                return true;
            }
            menus.abrirLoja(player);
            return true;
        }
        if (!args[0].equalsIgnoreCase("admin")) {
            sender.sendMessage("§cUso: /" + label + (sender.hasPermission(MaquinasMenusListener.ADMIN_PERMISSION)
                    ? " [admin [dar <tipo> [jogador] | upgrade <tipo> <nível> [quantidade] [jogador]]]" : ""));
            return true;
        }
        if (!sender.hasPermission(MaquinasMenusListener.ADMIN_PERMISSION)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        if (args.length == 1) {
            if (sender instanceof Player player) {
                painel.abrirPrincipal(player);
            } else {
                sender.sendMessage("Apenas jogadores abrem o painel.");
            }
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "dar" -> dar(sender, label, args);
            case "upgrade" -> upgrade(sender, label, args);
            default -> sender.sendMessage("§cUso: /" + label + " admin [dar|upgrade]");
        }
        return true;
    }

    private void dar(CommandSender sender, String label, String[] args) {
        MaquinaTipo tipo = args.length >= 3 ? MaquinaTipo.parse(args[2]) : null;
        if (tipo == null) {
            sender.sendMessage("§cUso: /" + label + " admin dar <" + tipos() + "> [jogador]");
            return;
        }
        Player alvo = alvo(sender, args.length >= 4 ? args[3] : null);
        if (alvo == null) {
            return;
        }
        MaquinaManager.dar(alvo, List.of(manager.itens().criarMaquina(tipo)));
        sender.sendMessage("§aEntregue: " + manager.config().nome(tipo) + " §apara " + alvo.getName() + ".");
    }

    private void upgrade(CommandSender sender, String label, String[] args) {
        UpgradeTipo tipo = args.length >= 3 ? UpgradeTipo.parse(args[2]) : null;
        Integer nivel = args.length >= 4 ? inteiro(args[3]) : null;
        if (tipo == null || nivel == null || nivel < 1) {
            sender.sendMessage("§cUso: /" + label + " admin upgrade <tipo> <nível> [quantidade] [jogador]");
            sender.sendMessage("§7Tipos: " + String.join(", ", Arrays.stream(UpgradeTipo.values()).map(Enum::name).toList()));
            return;
        }
        Integer quantidade = args.length >= 5 ? inteiro(args[4]) : Integer.valueOf(1);
        if (quantidade == null || quantidade < 1) {
            sender.sendMessage("§cQuantidade inválida.");
            return;
        }
        Player alvo = alvo(sender, args.length >= 6 ? args[5] : null);
        if (alvo == null) {
            return;
        }
        Upgrade upgrade = new Upgrade(tipo, nivel);
        List<org.bukkit.inventory.ItemStack> lista = new ArrayList<>();
        int resto = Math.min(quantidade, 64 * 36);
        while (resto > 0) {
            int agora = Math.min(64, resto);
            lista.add(manager.itens().criarUpgrade(upgrade, agora));
            resto -= agora;
        }
        MaquinaManager.dar(alvo, lista);
        sender.sendMessage("§aEntregue: " + quantidade + "x " + upgrade.nomeColorido() + " §apara " + alvo.getName() + ".");
    }

    private static Player alvo(CommandSender sender, String nome) {
        if (nome == null) {
            if (sender instanceof Player player) {
                return player;
            }
            sender.sendMessage("§cInforme o jogador.");
            return null;
        }
        Player alvo = Bukkit.getPlayerExact(nome);
        if (alvo == null) {
            sender.sendMessage("§cJogador não encontrado: " + nome);
        }
        return alvo;
    }

    private static Integer inteiro(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String tipos() {
        return String.join("|", Arrays.stream(MaquinaTipo.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(MaquinasMenusListener.ADMIN_PERMISSION)) {
            return List.of();
        }
        List<String> opcoes = switch (args.length) {
            case 1 -> List.of("admin");
            case 2 -> args[0].equalsIgnoreCase("admin") ? List.of("dar", "upgrade") : List.of();
            case 3 -> args[1].equalsIgnoreCase("dar")
                    ? Arrays.stream(MaquinaTipo.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList()
                    : args[1].equalsIgnoreCase("upgrade")
                    ? Arrays.stream(UpgradeTipo.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList() : List.of();
            case 4 -> args[1].equalsIgnoreCase("dar") ? nomes() : args[1].equalsIgnoreCase("upgrade")
                    ? List.of("1", "2", "3", "4", "5") : List.of();
            case 5 -> args[1].equalsIgnoreCase("upgrade") ? List.of("1", "16", "64") : List.of();
            case 6 -> args[1].equalsIgnoreCase("upgrade") ? nomes() : List.of();
            default -> List.of();
        };
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(opcao -> opcao.toLowerCase(Locale.ROOT).startsWith(prefixo)).toList();
    }

    private static List<String> nomes() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
