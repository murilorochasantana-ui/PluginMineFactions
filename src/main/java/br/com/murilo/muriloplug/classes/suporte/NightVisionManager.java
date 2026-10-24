package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Passiva do Suporte: o comando /visao liga/desliga a Visão Noturna (efeito infinito, sem partículas nem ícone). A
 * escolha fica na memória e o efeito volta ao renascer, ao tomar leite e ao entrar; sai ao trocar de classe.
 */
public final class NightVisionManager implements Listener, CommandExecutor {

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final AuthManager auth;
    private final Set<UUID> enabled = new HashSet<>();

    public NightVisionManager(JavaPlugin plugin, ClassManager classes, AuthManager auth) {
        this.plugin = plugin;
        this.classes = classes;
        this.auth = auth;
    }

    /** Tira o efeito de todos os jogadores online (onDisable). */
    public void removeAll() {
        Bukkit.getOnlinePlayers().forEach(this::remove);
    }

    private void apply(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0,
                false, false, false));
    }

    /** Só tira o efeito infinito (uma poção normal de visão noturna não é mexida). */
    private void remove(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (effect != null && effect.getDuration() == PotionEffect.INFINITE_DURATION) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    private void reapplyLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && enabled.contains(player.getUniqueId())
                    && classes.hasClass(player.getUniqueId(), PlayerClass.SUPORTE)) {
                apply(player);
            }
        });
    }

    /** /visao: liga ou desliga (só para Suportes). */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSó jogadores podem usar este comando.");
            return true;
        }
        UUID id = player.getUniqueId();
        if (!auth.isLoggedIn(id)) {
            return true;
        }
        if (!classes.hasClass(id, PlayerClass.SUPORTE)) {
            player.sendMessage("§cSomente Suportes podem usar a visão noturna.");
            return true;
        }
        if (enabled.remove(id)) {
            remove(player);
            player.sendMessage("§cVisão noturna desligada");
        } else {
            enabled.add(id);
            apply(player);
            player.sendMessage("§aVisão noturna ligada");
        }
        return true;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        reapplyLater(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reapplyLater(event.getPlayer());
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            reapplyLater(event.getPlayer());
        }
    }

    /** O efeito infinito é salvo junto com o jogador: tira ao sair (a escolha continua na memória). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.SUPORTE) {
            enabled.remove(event.getPlayerId());
            Player player = event.getPlayer();
            if (player != null) {
                remove(player);
            }
        }
    }
}
