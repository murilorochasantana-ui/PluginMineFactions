package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.classes.arqueiro.SpecialArrow;
import br.com.murilo.muriloplug.classes.mago.ManaManager;
import br.com.murilo.muriloplug.classes.mago.StaffType;
import br.com.murilo.muriloplug.combat.CombatManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /classe (abre o menu) e /classe trocar (confirma e remove a classe atual). */
public final class ClassCommand implements CommandExecutor, TabCompleter {

    private final ClassManager classes;
    private final CombatManager combat;

    public static final String ADMIN_PERMISSION = "mineplug.classe.admin";

    private static final int MAX_MANA_LIMIT = 1000;

    private static final int MAX_ARROWS_GIVEN = 64;
    private static final int MAX_BOOKS_GIVEN = 36;

    private final ClassItems items;
    private final ManaManager mana;

    public ClassCommand(ClassManager classes, CombatManager combat, ClassItems items, ManaManager mana) {
        this.items = items;
        this.mana = mana;
        this.classes = classes;
        this.combat = combat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            admin(player, args);
            return true;
        }
        if (combat.isInCombat(player)) {
            player.sendMessage("§cVocê não pode usar /classe em combate.");
            return true;
        }
        if (args.length == 0) {
            ClassMenu.open(player, classes);
        } else if (args.length == 1 && args[0].toLowerCase(Locale.ROOT).equals("trocar")) {
            PlayerClass current = classes.getClass(player.getUniqueId());
            if (current == null) {
                player.sendMessage("§eVocê ainda não tem uma classe. Escolha uma:");
                ClassMenu.open(player, classes);
            } else {
                ClassConfirmMenu.open(player, ClassConfirmMenu.Kind.LEAVE, current);
            }
        } else {
            player.sendMessage("§cUso: /" + label + " [trocar]");
        }
        return true;
    }

    /** /classe admin staff <tipo>: dá a staff ao administrador (para colocar na /loja). */
    private void admin(Player player, String[] args) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("mana")) {
            adminMana(player, args, false);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("manamaxima")) {
            adminMana(player, args, true);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("livro")) {
            adminBook(player, args);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("flecha")) {
            adminArrow(player, args);
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("sentinela")) {
            int amount = args[2].matches("\\d{1,2}") ? Integer.parseInt(args[2]) : 0;
            if (amount < 1 || amount > MAX_ARROWS_GIVEN) {
                player.sendMessage("§cUso: /classe admin sentinela <quantidade de 1 a " + MAX_ARROWS_GIVEN + ">");
            } else {
                var leftover = player.getInventory().addItem(items.create(ClassItems.SENTINEL, amount));
                player.sendMessage(leftover.isEmpty() ? "§aVocê recebeu " + amount + " Sentinela(s)."
                        : "§cSeu inventário está cheio.");
            }
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("golem")) {
            int amount = args[2].matches("\\d{1,2}") ? Integer.parseInt(args[2]) : 0;
            if (amount < 1 || amount > MAX_ARROWS_GIVEN) {
                player.sendMessage("§cUso: /classe admin golem <quantidade de 1 a " + MAX_ARROWS_GIVEN + ">");
            } else {
                var leftover = player.getInventory().addItem(items.create(ClassItems.GOLEM, amount));
                player.sendMessage(leftover.isEmpty() ? "§aVocê recebeu " + amount + " Golem(ns) de Cobre."
                        : "§cSeu inventário está cheio.");
            }
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("planta")) {
            var leftover = player.getInventory().addItem(items.create(ClassItems.BLUEPRINT, 1));
            player.sendMessage(leftover.isEmpty() ? "§aVocê recebeu uma Planta de Engenharia."
                    : "§cSeu inventário está cheio.");
            return;
        }
        StaffType type = args.length == 3 && args[1].equalsIgnoreCase("staff") ? StaffType.byId(args[2]) : null;
        if (type == null) {
            player.sendMessage("§cUso: /classe admin staff <" + String.join("|", staffIds()) + ">");
            return;
        }
        var leftover = player.getInventory().addItem(items.create(ClassItems.STAFF_PREFIX + type.id(), 1));
        if (leftover.isEmpty()) {
            player.sendMessage("§aVocê recebeu a " + type.displayName() + "§a.");
        } else {
            player.sendMessage("§cSeu inventário está cheio.");
        }
    }

    /** /classe admin livro <quantidade>: dá Livros de Estrutura vazios (cada um único) ao administrador. */
    private void adminBook(Player player, String[] args) {
        int amount = -1;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                // cai na mensagem de uso abaixo
            }
        }
        if (amount < 1 || amount > MAX_BOOKS_GIVEN) {
            player.sendMessage("§cUso: /classe admin livro <quantidade de 1 a " + MAX_BOOKS_GIVEN + ">");
            return;
        }
        int given = 0;
        for (int i = 0; i < amount; i++) {
            if (!player.getInventory().addItem(items.create(ClassItems.STRUCTURE_BOOK, 1)).isEmpty()) {
                break;
            }
            given++;
        }
        player.sendMessage(given == amount ? "§aVocê recebeu " + given + " Livro(s) de Estrutura vazio(s)."
                : "§cSeu inventário está cheio. Você recebeu " + given + " de " + amount + " livro(s).");
    }

    /**
     * /classe admin flecha <tipo> <quantidade> (tipos: explosiva, perfurante, gelo, fumaca, corrente, corda).
     * Também aceita o formato antigo: /classe admin flecha <quantidade> [gancho].
     */
    private void adminArrow(Player player, String[] args) {
        SpecialArrow type = args.length >= 3 ? SpecialArrow.byId(args[2]) : null;
        boolean grapple = type == null && args.length == 4 && args[3].equalsIgnoreCase("gancho");
        String amountText = type != null ? (args.length == 4 ? args[3] : "") : (args.length == 3 || grapple ? args[2] : "");
        int amount = -1;
        try {
            amount = Integer.parseInt(amountText);
        } catch (NumberFormatException ignored) {
            // cai na mensagem de uso abaixo
        }
        if (amount < 1 || amount > MAX_ARROWS_GIVEN) {
            player.sendMessage("§cUso: /classe admin flecha <explosiva|perfurante|gelo|fumaca|corrente|corda> <quantidade de 1 a "
                    + MAX_ARROWS_GIVEN + ">  (ou: flecha <quantidade> [gancho])");
            return;
        }
        String itemId = grapple ? ClassItems.GRAPPLE_ARROW
                : ClassItems.arrowItemId(type == null ? SpecialArrow.EXPLOSIVA : type);
        var leftover = player.getInventory().addItem(items.create(itemId, amount));
        if (leftover.isEmpty()) {
            player.sendMessage("§aVocê recebeu " + amount + " " + (grapple ? "Flecha(s) Gancho."
                    : (type == null ? SpecialArrow.EXPLOSIVA : type).displayName() + "§a."));
        } else {
            player.sendMessage("§cSeu inventário está cheio.");
        }
    }

    /**
     * /classe admin mana <definir|dar> <jogador> <valor> e
     * /classe admin manamaxima <definir <jogador> <valor> | resetar <jogador>>.
     */
    private void adminMana(Player admin, String[] args, boolean maxMode) {
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        boolean reset = maxMode && action.equals("resetar");
        boolean valid = reset ? args.length == 4
                : args.length == 5 && (action.equals("definir") || (!maxMode && action.equals("dar")));
        if (!valid) {
            admin.sendMessage(maxMode
                    ? "§cUso: /classe admin manamaxima definir <jogador> <valor> | resetar <jogador>"
                    : "§cUso: /classe admin mana <definir|dar> <jogador> <valor>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null) {
            admin.sendMessage("§cJogador não encontrado: " + args[3]);
            return;
        }
        UUID id = target.getUniqueId();
        if (!mana.isMage(id)) {
            admin.sendMessage("§c" + target.getName() + " não é Mago.");
            return;
        }

        if (reset) {
            mana.setMax(id, null);
        } else {
            int value;
            try {
                value = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                admin.sendMessage("§cValor inválido: " + args[4]);
                return;
            }
            if (maxMode) {
                if (value < 1 || value > MAX_MANA_LIMIT) {
                    admin.sendMessage("§cA mana máxima deve ficar entre 1 e " + MAX_MANA_LIMIT + ".");
                    return;
                }
                mana.setMax(id, value);
            } else if (action.equals("definir")) {
                if (value < 0 || value > mana.max(id)) {
                    admin.sendMessage("§cA mana deve ficar entre 0 e " + mana.max(id) + ".");
                    return;
                }
                mana.set(id, value);
            } else {
                mana.set(id, mana.get(id) + value);
            }
        }

        String report = "§bMana de " + target.getName() + ": " + mana.get(id) + "/" + mana.max(id);
        admin.sendMessage(report);
        if (!target.equals(admin)) {
            target.sendMessage(report);
        }
    }

    private static List<String> staffIds() {
        return Arrays.stream(StaffType.values()).map(StaffType::id).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = List.of();
        if (args.length == 1) {
            options = admin ? List.of("trocar", "admin") : List.of("trocar");
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options = List.of("staff", "mana", "manamaxima", "flecha", "livro", "planta", "sentinela", "golem");
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "staff" -> options = staffIds();
                case "flecha" -> options = Arrays.stream(SpecialArrow.values()).map(SpecialArrow::id).toList();
                case "sentinela" -> options = List.of("1", "16", "64");
                case "golem" -> options = List.of("1", "3", "16");
                case "livro" -> options = List.of("1", "5", "10");
                case "mana" -> options = List.of("definir", "dar");
                case "manamaxima" -> options = List.of("definir", "resetar");
                default -> { }
            }
        } else if (admin && args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("flecha")
                && SpecialArrow.byId(args[2]) != null) {
            options = List.of("1", "16", "64");
        } else if (admin && args.length == 4 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("mana") || args[1].equalsIgnoreCase("manamaxima"))) {
            options = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
