package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clique direito com os itens do Suporte. Confere login e classe, aplica a habilidade e só então gasta o uso e
 * inicia a recarga (habilidade que não achou alvo não custa nada). Também faz a cura e o Pergaminho.
 */
public final class SuporteListener implements Listener {

    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;

    private final ClassManager classes;
    private final AuthManager auth;
    private final SuporteItems items;
    private final SuporteSettings settings;
    private final SuporteCooldowns cooldowns;
    private final SuporteTargets targets;
    private final SuporteAssists assists;
    private final LinkManager links;
    private final RootManager roots;
    private final FlightManager flight;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public SuporteListener(ClassManager classes, AuthManager auth, SuporteItems items, SuporteSettings settings,
                           SuporteCooldowns cooldowns, SuporteTargets targets, SuporteAssists assists,
                           LinkManager links, RootManager roots, FlightManager flight) {
        this.classes = classes;
        this.auth = auth;
        this.items = items;
        this.settings = settings;
        this.cooldowns = cooldowns;
        this.targets = targets;
        this.assists = assists;
        this.links = links;
        this.roots = roots;
        this.flight = flight;
    }

    // Não usa ignoreCancelled: um claim alheio cancela o clique em blocos, mas as habilidades não mexem em blocos.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        SuporteItem type = items.read(event.getItem());
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return; // deixa abrir baú, porta etc. normalmente
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (type == SuporteItem.PERGAMINHO) {
            event.setCancelled(true); // não abre a tela de escrever do livro
        }
        if (event.getHand() != EquipmentSlot.HAND || !auth.isLoggedIn(player.getUniqueId())) {
            return;
        }
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.SUPORTE)) {
            warnNotSupport(player);
            return;
        }
        use(player, type);
    }

    private void use(Player player, SuporteItem type) {
        switch (type) {
            case VINCULO -> links.use(player);
            case PRENDER -> {
                if (cooldowns.check(player, type) && roots.root(player)) {
                    commit(player, type);
                }
            }
            case CURA -> {
                if (cooldowns.check(player, type)) {
                    heal(player);
                    commit(player, type);
                }
            }
            case PERGAMINHO -> {
                if (cooldowns.check(player, type)) {
                    scroll(player);
                    commit(player, type);
                }
            }
            case VOO -> {
                // A recarga do voo começa quando ele acaba (FlightManager).
                if (!flight.isActive(player.getUniqueId()) && cooldowns.check(player, type)) {
                    flight.begin(player);
                }
            }
        }
    }

    /** Inicia a recarga e gasta 1 uso (os itens ilimitados não gastam nada). */
    private void commit(Player player, SuporteItem type) {
        cooldowns.start(player, type, settings.cooldownSeconds(type));
        items.consumeUse(player);
    }

    private void warnNotSupport(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage("§cSomente Suportes podem usar este item.");
        }
    }

    // ------------------------------------------------------------ Coração do Mar

    /** Cura na hora o Suporte e os aliados em volta, sem passar da vida máxima de cada um. */
    private void heal(Player support) {
        double amount = settings.healAmount();
        List<Player> group = targets.group(support, settings.healRadius());
        for (Player target : group) {
            AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
            double limit = max == null ? 20 : max.getValue();
            target.setHealth(Math.min(limit, target.getHealth() + amount));
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1.8, 0), 6, 0.4, 0.3, 0.4, 0);
            if (!target.equals(support)) {
                assists.record(support.getUniqueId(), target.getUniqueId());
                target.sendActionBar(ShopUi.text("§aVocê foi curado por " + support.getName() + "."));
            }
        }
        support.getWorld().playSound(support.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        support.sendActionBar(ShopUi.text("§aCura em " + group.size() + " jogador(es)."));
    }

    // ------------------------------------------------------------ Pergaminho

    /** Velocidade para o Suporte e para os aliados em volta. */
    private void scroll(Player support) {
        int ticks = settings.scrollSeconds() * 20;
        int amplifier = settings.scrollLevel() - 1;
        List<Player> group = targets.group(support, settings.scrollRadius());
        for (Player target : group) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, amplifier, false, true, true));
            target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.6);
            if (!target.equals(support)) {
                assists.record(support.getUniqueId(), target.getUniqueId());
                target.sendActionBar(ShopUi.text("§eVocê ganhou velocidade de " + support.getName() + "."));
            }
        }
        support.getWorld().playSound(support.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.4f);
        support.sendActionBar(ShopUi.text("§eVelocidade em " + group.size() + " jogador(es)."));
    }

    // ------------------------------------------------------------ estado

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.forget(id);
        lastWarning.remove(id);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.SUPORTE) {
            cooldowns.forget(event.getPlayerId());
        }
    }
}
