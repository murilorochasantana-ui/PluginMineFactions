package br.com.murilo.muriloplug.teste;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modo Teste dos administradores (/teste, permissão mineplug.teste): recursos infinitos (mana, Foco, Fúria, energia),
 * sem recargas, sem gasto de usos de itens do Suporte e sem consumir flechas. Só tira custos e recargas: o dano das
 * habilidades, o KDA, o dinheiro e o combate não mudam. Fica só em memória e desliga sozinho ao sair ou perder a
 * permissão. As classes consultam {@link #isActive(UUID)} nos pontos em que checam ou gastam recurso, recarga ou
 * item; o que elas não consultam (livros, plantas, staffs do Grimório, loja) continua consumido normalmente.
 */
public final class TestMode implements Listener, CommandExecutor, TabCompleter {

    public static final String PERMISSION = "mineplug.teste";

    private static final String BERSERKER_SECONDS = "teste.berserker-segundos";
    private static final String LAST_BREATH = "teste.ultimo-suspiro-ativo";
    private static final long PERMISSION_CHECK_TICKS = 20L;
    private static final long REMINDER_TICKS = 200L;

    private final JavaPlugin plugin;
    private final Set<UUID> active = new LinkedHashSet<>();
    private Consumer<Player> changeListener = player -> { };
    /** Chamados ao ligar o Modo Teste, para as classes limparem recargas já em andamento (inclusive a animação). */
    private final List<Consumer<Player>> enableHooks = new ArrayList<>();

    /** {@code plugin} pode ser null só para a instância "desligada" usada como padrão nas classes. */
    public TestMode(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Instância que nunca está ativa (padrão das classes antes de receberem o Modo Teste real). */
    public static TestMode inactive() {
        return new TestMode(null);
    }

    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        if (!config.contains(BERSERKER_SECONDS)) {
            config.set(BERSERKER_SECONDS, 8);
            changed = true;
        }
        if (!config.contains(LAST_BREATH)) {
            config.set(LAST_BREATH, false);
            changed = true;
        }
        return changed;
    }

    // ------------------------------------------------------------ consulta

    public boolean isActive(UUID id) {
        return active.contains(id);
    }

    public boolean isActive(Player player) {
        return active.contains(player.getUniqueId());
    }

    /** Segundos fixos do Berserker no Modo Teste. */
    public int berserkerSeconds() {
        return plugin == null ? 8 : Math.max(1, plugin.getConfig().getInt(BERSERKER_SECONDS, 8));
    }

    /** Se o Último Suspiro do Combatente ativa no Modo Teste (padrão: não). */
    public boolean lastBreathActive() {
        return plugin != null && plugin.getConfig().getBoolean(LAST_BREATH, false);
    }

    /** Chamado quando alguém liga ou desliga (sidebar e barra de XP). */
    public void setChangeListener(Consumer<Player> changeListener) {
        this.changeListener = changeListener;
    }

    public void addEnableHook(Consumer<Player> hook) {
        enableHooks.add(hook);
    }

    // ------------------------------------------------------------ ligar e desligar

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkPermissions, PERMISSION_CHECK_TICKS,
                PERMISSION_CHECK_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::remind, REMINDER_TICKS, REMINDER_TICKS);
    }

    /** Desliga todos (onDisable). */
    public void clear() {
        active.clear();
    }

    private void checkPermissions() {
        for (UUID id : List.copyOf(active)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                active.remove(id);
            } else if (!player.hasPermission(PERMISSION)) {
                disable(player);
                player.sendMessage("§7Modo Teste desligado (sem permissão).");
            }
        }
    }

    private void remind() {
        for (UUID id : active) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendActionBar(ShopUi.text("§dModo Teste ativo"));
            }
        }
    }

    private void enable(Player player) {
        active.add(player.getUniqueId());
        enableHooks.forEach(hook -> hook.accept(player));
        changeListener.accept(player);
    }

    private void disable(Player player) {
        active.remove(player.getUniqueId());
        changeListener.accept(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ flechas: nunca gastam no Modo Teste

    /** Flecha de arco que sai de quem está no Modo Teste não é gasta, nem recolhida (evita criar flechas). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isActive(player)) {
            return;
        }
        event.setConsumeArrow(false);
        if (event.getProjectile() instanceof AbstractArrow arrow
                && arrow.getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        }
    }

    /** A besta gasta a flecha ao carregar: no Modo Teste não gasta. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoadCrossbow(EntityLoadCrossbowEvent event) {
        if (event.getEntity() instanceof Player player && isActive(player)) {
            event.setConsumeItem(false);
        }
    }

    // ------------------------------------------------------------ comando

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("lista")) {
            list(sender);
            return true;
        }
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage("§cUso no console: /teste <jogador> | /teste lista");
                return true;
            }
            target = self;
        } else if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cJogador não encontrado.");
                return true;
            }
        } else {
            sender.sendMessage("§cUso: /teste [jogador|lista]");
            return true;
        }
        toggle(sender, target);
        return true;
    }

    private void toggle(CommandSender sender, Player target) {
        boolean turnOn = !isActive(target);
        if (turnOn) {
            if (!target.hasPermission(PERMISSION)) {
                sender.sendMessage("§c" + target.getName() + " não tem a permissão " + PERMISSION + ".");
                return;
            }
            enable(target);
            target.sendMessage("§d§lMODO TESTE LIGADO");
        } else {
            disable(target);
            target.sendMessage("§7Modo Teste desligado");
        }
        String by = sender instanceof ConsoleCommandSender ? "o console" : sender.getName();
        plugin.getLogger().info("Modo Teste " + (turnOn ? "LIGADO" : "desligado") + " para " + target.getName()
                + " por " + by + ".");
        if (sender != target) {
            sender.sendMessage("§7Modo Teste de " + target.getName() + ": " + (turnOn ? "§dligado" : "§7desligado"));
        }
    }

    private void list(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (UUID id : active) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                names.add(player.getName());
            }
        }
        sender.sendMessage(names.isEmpty() ? "§7Ninguém está com o Modo Teste ligado."
                : "§dModo Teste ligado (" + names.size() + "): §f" + String.join(", ", names));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        options.add("lista");
        Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }

    /** Desliga o Modo Teste do jogador (reset de conta). Retorna true se estava ligado. */
    public boolean deactivate(UUID player) {
        if (!active.remove(player)) {
            return false;
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            changeListener.accept(online);
        }
        return true;
    }
}
