package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Cliques com os itens do Suporte. Confere login e classe, depois recarga e Harmonia, aplica a habilidade e só então
 * gasta a Harmonia e inicia a recarga (habilidade que não achou alvo não custa nada). Também faz a cura, o
 * Pergaminho, a dica das teclas ao pegar cada item e a atualização dos itens antigos.
 *
 * <p>Botões: [Dir] = habilidade principal do item; [Shift+Dir] e [Shift+Esq] = habilidades extras. O clique esquerdo
 * em uma entidade chega como golpe, não como PlayerInteractEvent; por isso o golpe agachado com um item do Suporte
 * que tem [Shift+Esq] também aciona a habilidade (e não causa dano).
 */
public final class SuporteListener implements Listener {

    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;
    private static final long CLICK_DEBOUNCE_MILLIS = 250L;

    private final ClassManager classes;
    private final AuthManager auth;
    private final SuporteItems items;
    private final SuporteSettings settings;
    private final SuporteCooldowns cooldowns;
    private final SuporteTargets targets;
    private final SuporteAssists assists;
    private final HarmoniaManager harmony;
    private final LinkManager links;
    private final RootManager roots;
    private final FlightManager flight;
    private final TrocaEquivalente swap;
    private final EloDrenante drain;
    private final TrocaLugar place;
    private final EscudoSagrado shield;
    private final Purificar purify;
    private final TotemCura totem;
    private final Silencio silence;
    private final Maldicao curse;
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    private final Map<UUID, Long> lastLeftClick = new HashMap<>();

    public SuporteListener(ClassManager classes, AuthManager auth, SuporteItems items, SuporteSettings settings,
                           SuporteCooldowns cooldowns, SuporteTargets targets, SuporteAssists assists,
                           HarmoniaManager harmony, LinkManager links, RootManager roots, FlightManager flight,
                           TrocaEquivalente swap, EloDrenante drain, TrocaLugar place, EscudoSagrado shield,
                           Purificar purify, TotemCura totem, Silencio silence, Maldicao curse) {
        this.classes = classes;
        this.auth = auth;
        this.items = items;
        this.settings = settings;
        this.cooldowns = cooldowns;
        this.targets = targets;
        this.assists = assists;
        this.harmony = harmony;
        this.links = links;
        this.roots = roots;
        this.flight = flight;
        this.swap = swap;
        this.drain = drain;
        this.place = place;
        this.shield = shield;
        this.purify = purify;
        this.totem = totem;
        this.silence = silence;
        this.curse = curse;
    }

    // ------------------------------------------------------------ cliques

    // Não usa ignoreCancelled: um claim alheio cancela o clique em blocos, mas as habilidades não mexem em blocos.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        ItemStack upgraded = items.upgrade(held);
        SuporteItem type = items.read(upgraded);
        if (type == null) {
            return;
        }
        if (upgraded != held && event.getHand() != null) {
            boolean legacyScroll = items.isLegacyScroll(held);
            player.getInventory().setItem(event.getHand(), upgraded); // item antigo: lore nova, sem usos
            if (legacyScroll) {
                event.setCancelled(true); // não deixa a tela de escrever do livro abrir neste clique
            }
        }
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (left) {
            if (!player.isSneaking() || shiftLeftSkill(type) == null || event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            event.setUseInteractedBlock(Event.Result.DENY); // Shift + clique esquerdo não quebra o bloco mirado
            event.setCancelled(true);
            if (checkUser(player) && debounce(player)) {
                useShiftLeft(player, type);
            }
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return; // deixa abrir baú, porta etc. normalmente
        }
        // Nenhum item do Suporte é usado de verdade: o Núcleo (bloco) não é colocado, o Totem não salva ninguém.
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !checkUser(player)) {
            return;
        }
        if (player.isSneaking() && useShiftRight(player, type)) {
            return;
        }
        use(player, type, clicked);
    }

    /** Golpe agachado com um item que tem [Shift+Esq]: vira a habilidade (e o golpe não acontece). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player)
                || !player.isSneaking()) {
            return;
        }
        SuporteItem type = items.read(player.getInventory().getItemInMainHand());
        if (type == null || shiftLeftSkill(type) == null) {
            return;
        }
        event.setCancelled(true);
        if (checkUser(player) && debounce(player)) {
            useShiftLeft(player, type);
        }
    }

    /** Logado, Suporte (avisa quem não é) e não silenciado. */
    private boolean checkUser(Player player) {
        if (!auth.isLoggedIn(player.getUniqueId())) {
            return false;
        }
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.SUPORTE)) {
            warnNotSupport(player);
            return false;
        }
        return !SilenceManager.blocks(player);
    }

    /** O clique esquerdo pode chegar duas vezes (balanço do braço + golpe): só vale um a cada 250ms. */
    private boolean debounce(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastLeftClick.get(player.getUniqueId());
        if (last != null && now - last < CLICK_DEBOUNCE_MILLIS) {
            return false;
        }
        lastLeftClick.put(player.getUniqueId(), now);
        return true;
    }

    /** Habilidade do [Shift+Esq] de cada item, ou null se o item não tem. */
    static SuporteSkill shiftLeftSkill(SuporteItem type) {
        return switch (type) {
            case VINCULO -> SuporteSkill.ELO;
            case VOO -> SuporteSkill.TROCA_LUGAR;
            case CURA -> SuporteSkill.PURIFICAR;
            case PRENDER -> SuporteSkill.SILENCIO;
            case PERGAMINHO -> SuporteSkill.MALDICAO;
            case ESCUDO, TOTEM -> null;
        };
    }

    // ------------------------------------------------------------ habilidades

    /** [Dir]: habilidade principal do item. */
    private void use(Player player, SuporteItem type, Block clicked) {
        switch (type) {
            case VINCULO -> links.use(player);
            case PRENDER -> run(player, SuporteSkill.PRENDER, () -> roots.root(player));
            case CURA -> run(player, SuporteSkill.CURA, () -> {
                heal(player);
                return true;
            });
            case PERGAMINHO -> run(player, SuporteSkill.PERGAMINHO, () -> {
                scroll(player);
                return true;
            });
            case VOO -> {
                // A recarga do voo começa quando ele acaba (FlightManager); a Harmonia sai na hora.
                if (flight.isActive(player.getUniqueId()) || !cooldowns.check(player, SuporteSkill.VOO)
                        || !harmony.check(player, settings.cost(SuporteSkill.VOO))) {
                    return;
                }
                if (flight.begin(player)) {
                    harmony.gastarHarmonia(player.getUniqueId(), settings.cost(SuporteSkill.VOO));
                }
            }
            case ESCUDO -> run(player, SuporteSkill.ESCUDO, () -> shield.cast(player));
            case TOTEM -> {
                // Item de uso único: sem Harmonia e sem recarga; o TotemCura gasta o item se colocar.
                if (clicked == null) {
                    player.sendActionBar(ShopUi.text("§cClique em um bloco para colocar o Totem de Cura."));
                } else {
                    totem.place(player, clicked);
                }
            }
        }
    }

    /**
     * [Shift+Dir], no ar ou em um bloco. Retorna false se o item não tem habilidade de Shift+Dir (aí vale o [Dir]
     * normal).
     */
    private boolean useShiftRight(Player player, SuporteItem type) {
        if (type == SuporteItem.VINCULO) {
            swap.toggle(player);
            return true;
        }
        return false;
    }

    /** [Shift+Esq]. */
    private void useShiftLeft(Player player, SuporteItem type) {
        switch (type) {
            case VINCULO -> run(player, SuporteSkill.ELO, () -> drain.activate(player));
            case VOO -> run(player, SuporteSkill.TROCA_LUGAR, () -> place.swap(player));
            case CURA -> run(player, SuporteSkill.PURIFICAR, () -> purify.cast(player));
            case PRENDER -> run(player, SuporteSkill.SILENCIO, () -> silence.cast(player));
            case PERGAMINHO -> run(player, SuporteSkill.MALDICAO, () -> curse.cast(player));
            default -> {
            }
        }
    }

    /**
     * Recarga e Harmonia conferidas antes; a habilidade roda e, se aconteceu de fato (retornou true), gasta a
     * Harmonia e inicia a recarga.
     */
    private void run(Player player, SuporteSkill skill, BooleanSupplier ability) {
        double cost = settings.cost(skill);
        if (!cooldowns.check(player, skill) || !harmony.check(player, cost)) {
            return;
        }
        if (ability.getAsBoolean()) {
            harmony.gastarHarmonia(player.getUniqueId(), cost);
            cooldowns.start(player, skill, settings.cooldownSeconds(skill));
        }
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

    /**
     * Cura na hora o Suporte e os aliados em volta, sem passar da vida máxima de cada um. A vida curada dá Harmonia,
     * e a parte que o Suporte curou em si mesmo também vai (pela metade) para o aliado vinculado.
     */
    private void heal(Player support) {
        double amount = settings.healAmount();
        List<LivingEntity> group = targets.group(support, settings.healRadius());
        double healedTotal = 0;
        for (LivingEntity target : group) {
            double gained = healEntity(target, amount);
            healedTotal += gained;
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1.8, 0), 6, 0.4, 0.3, 0.4, 0);
            if (target.equals(support)) {
                healedTotal += links.shareSelfHeal(support, gained);
            } else if (target instanceof Player player) {
                assists.record(support.getUniqueId(), player.getUniqueId());
                player.sendActionBar(ShopUi.text("§aVocê foi curado por " + support.getName() + "."));
            }
        }
        harmony.addForHeal(support.getUniqueId(), healedTotal);
        support.getWorld().playSound(support.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        support.sendActionBar(ShopUi.text("§aCura em " + group.size() + " jogador(es)."));
    }

    /** Cura sem passar da vida máxima. Retorna quanto curou de fato. */
    private static double healEntity(LivingEntity target, double amount) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double limit = max == null ? 20 : max.getValue();
        double before = target.getHealth();
        target.setHealth(Math.min(limit, before + amount));
        return Math.max(0, target.getHealth() - before);
    }

    // ------------------------------------------------------------ Pergaminho

    /** Velocidade para o Suporte e para os aliados em volta. */
    private void scroll(Player support) {
        int ticks = settings.scrollSeconds() * 20;
        int amplifier = settings.scrollLevel() - 1;
        List<LivingEntity> group = targets.group(support, settings.scrollRadius());
        for (LivingEntity target : group) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, amplifier, false, true, true));
            target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.6);
            if (target instanceof Player player && !player.equals(support)) {
                assists.record(support.getUniqueId(), player.getUniqueId());
                player.sendActionBar(ShopUi.text("§eVocê ganhou velocidade de " + support.getName() + "."));
            }
        }
        support.getWorld().playSound(support.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.4f);
        support.sendActionBar(ShopUi.text("§eVelocidade em " + group.size() + " jogador(es)."));
    }

    // ------------------------------------------------------------ dica das teclas

    /** Ao passar a segurar um item do Suporte, mostra os botões daquele item na action bar (~3s). */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.SUPORTE) || !auth.isLoggedIn(id) || flight.isActive(id)) {
            return;
        }
        SuporteItem type = items.read(player.getInventory().getItem(event.getNewSlot()));
        if (type != null) {
            player.sendActionBar(ShopUi.text(tip(type)));
        }
    }

    static String tip(SuporteItem type) {
        return switch (type) {
            case VINCULO -> "§7[Dir] Vincular §8| §7[Shift+Esq] Elo Drenante §8| §7[Shift+Dir] Troca Equivalente";
            case PRENDER -> "§7[Dir] Prender §8| §7[Shift+Esq] Silêncio";
            case CURA -> "§7[Dir] Cura em área §8| §7[Shift+Esq] Purificar";
            case PERGAMINHO -> "§7[Dir] Velocidade §8| §7[Shift+Esq] Maldição";
            case VOO -> "§7[Dir] Voar §8| §7[Shift+Esq] Troca de Lugar";
            case ESCUDO -> "§7[Dir] Escudo Sagrado §8(aliado na mira, ou você)";
            case TOTEM -> "§7[Dir no bloco] Colocar o Totem de Cura §8(uso único)";
        };
    }

    // ------------------------------------------------------------ itens antigos

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        items.migrateInventory(event.getPlayer().getInventory());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            items.migrateInventory(player.getInventory());
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemStack stack = event.getItem().getItemStack();
            ItemStack upgraded = items.upgrade(stack);
            if (upgraded != stack) {
                event.getItem().setItemStack(upgraded);
            }
        }
    }

    // ------------------------------------------------------------ estado

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.forget(id);
        lastWarning.remove(id);
        lastLeftClick.remove(id);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.SUPORTE) {
            cooldowns.forget(event.getPlayerId());
            drain.end(event.getPlayerId());
        }
    }
}
