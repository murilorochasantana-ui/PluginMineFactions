package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passiva Socorrista: quando um aliado por perto fica com pouca vida, o Suporte ganha Velocidade por alguns segundos
 * e um aviso na action bar. Tem recarga própria (vale também no Modo Teste, só para não repetir o aviso sem parar).
 */
public final class Socorrista implements Listener {

    private static final long TICK_PERIOD = 10L;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final SuporteSettings settings;
    private final SuporteTargets targets;
    /** Fim (ms) da recarga de cada Suporte. */
    private final Map<UUID, Long> readyAt = new HashMap<>();

    public Socorrista(JavaPlugin plugin, ClassManager classes, SuporteSettings settings, SuporteTargets targets) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.targets = targets;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player support : Bukkit.getOnlinePlayers()) {
            UUID id = support.getUniqueId();
            if (support.isDead() || !classes.hasClass(id, PlayerClass.SUPORTE) || readyAt.getOrDefault(id, 0L) > now) {
                continue;
            }
            LivingEntity hurt = lowestAlly(support);
            if (hurt == null) {
                continue;
            }
            readyAt.put(id, now + (long) (settings.rescuerCooldownSeconds() * 1000));
            support.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, settings.rescuerSeconds() * 20,
                    settings.rescuerLevel() - 1, false, true, true));
            support.sendActionBar(ShopUi.text("§c" + nameOf(hurt) + " está com pouca vida!"));
            support.playSound(support.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.8f);
        }
    }

    /** O aliado por perto com menos vida, se algum estiver abaixo do limite; senão null. */
    private LivingEntity lowestAlly(Player support) {
        LivingEntity found = null;
        double lowest = Double.MAX_VALUE;
        for (LivingEntity ally : targets.group(support, settings.rescuerRadius())) {
            if (ally.equals(support)) {
                continue;
            }
            AttributeInstance max = ally.getAttribute(Attribute.MAX_HEALTH);
            double fraction = ally.getHealth() / (max == null ? 20 : max.getValue());
            if (fraction < settings.rescuerHealth() && fraction < lowest) {
                lowest = fraction;
                found = ally;
            }
        }
        return found;
    }

    private static String nameOf(LivingEntity entity) {
        return entity instanceof Player player ? player.getName() : "Boneco de Teste";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        readyAt.remove(event.getPlayer().getUniqueId());
    }
}
