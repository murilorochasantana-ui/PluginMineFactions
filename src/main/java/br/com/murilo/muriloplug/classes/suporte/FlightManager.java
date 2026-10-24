package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pena: voo por alguns segundos (só em sobrevivência/aventura). Ao acabar o tempo, tira o voo, cancela o dano da
 * próxima queda e só então começa a recarga. Morte, saída ou deixar de ser Suporte acabam o voo na hora.
 *
 * <p>O voo é mais lento que o do criativo: a velocidade de voo do jogador vira {@code voo.velocidade} (0.04; o padrão
 * do jogo é 0.1) e volta para a de antes quando o voo acaba, de qualquer jeito. Durante o voo, a velocidade
 * horizontal (correndo, inclusive) é cortada em {@code voo.velocidade-maxima} blocos por tick e o Suporte não sobe
 * mais que {@code voo.altura-maxima} blocos acima de onde começou. Uma marca no PersistentDataContainer do jogador
 * (mineplug:suporte_voo) diz que o voo está em andamento: se o servidor cair no meio de um voo, a velocidade de voo
 * volta para 0.1 quando ele entrar de novo.
 */
public final class FlightManager implements Listener {

    private static final long TICK_PERIOD = 5L;
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    /** Voo em andamento: até quando, a velocidade de voo de antes e a altura em que começou. */
    private record Flight(long until, float previousSpeed, double startY) {
    }

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final SuporteSettings settings;
    private final SuporteCooldowns cooldowns;
    private final NamespacedKey markKey;
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<UUID, Long> fallProtectionUntil = new HashMap<>();

    public FlightManager(JavaPlugin plugin, ClassManager classes, SuporteSettings settings,
                         SuporteCooldowns cooldowns) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.cooldowns = cooldowns;
        this.markKey = new NamespacedKey(plugin, "suporte_voo");
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        // /reload ou sobras de um desligamento brusco: quem está online com a marca volta ao normal.
        Bukkit.getOnlinePlayers().forEach(this::fixLeftover);
    }

    public boolean isActive(UUID id) {
        return flights.containsKey(id);
    }

    /** Começa o voo. Retorna false (sem gastar nada) se o modo de jogo não permite. */
    public boolean begin(Player player) {
        if (!survivalLike(player)) {
            player.sendActionBar(ShopUi.text("§cVocê não pode usar a Pena neste modo de jogo."));
            return false;
        }
        UUID id = player.getUniqueId();
        float previous = player.getFlySpeed();
        flights.put(id, new Flight(System.currentTimeMillis() + settings.flightSeconds() * 1000L, previous,
                player.getLocation().getY()));
        fallProtectionUntil.remove(id);
        player.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
        player.setFlySpeed(settings.flightSpeed());
        player.setAllowFlight(true);
        player.setFlying(true);
        showTime(player);
        return true;
    }

    /** Tira o voo de todos (onDisable). */
    public void endAll() {
        for (UUID id : Set.copyOf(flights.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                end(player, false);
            }
        }
        flights.clear();
    }

    private static boolean survivalLike(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private void showTime(Player player) {
        Flight flight = flights.get(player.getUniqueId());
        if (flight != null) {
            int seconds = (int) Math.ceil(Math.max(0, flight.until() - System.currentTimeMillis()) / 1000.0);
            player.sendActionBar(ShopUi.text("§dVoo: " + seconds + "s"));
        }
    }

    /** Acaba o voo. {@code natural}: o tempo acabou (dá proteção de queda e inicia a recarga). */
    private void end(Player player, boolean natural) {
        UUID id = player.getUniqueId();
        Flight flight = flights.remove(id);
        if (flight == null) {
            return;
        }
        // A velocidade de voo volta sempre (mesmo se ele passou para o criativo durante o voo).
        player.setFlySpeed(validSpeed(flight.previousSpeed()));
        player.getPersistentDataContainer().remove(markKey);
        if (survivalLike(player)) { // criativo e espectador não são mexidos
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (natural) {
            fallProtectionUntil.put(id, System.currentTimeMillis() + settings.fallProtectionSeconds() * 1000L);
            cooldowns.start(player, SuporteSkill.VOO, settings.cooldownSeconds(SuporteSkill.VOO));
            player.sendActionBar(ShopUi.text("§dO voo acabou."));
        }
    }

    /** A velocidade guardada, ou 0.1 se ela já era a lenta da Pena (de um voo antigo que não terminou certo). */
    private float validSpeed(float previous) {
        boolean leftover = Math.abs(previous - settings.flightSpeed()) < 1.0E-4;
        return leftover || previous <= 0 || previous > 1 ? DEFAULT_FLY_SPEED : previous;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(flights.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                flights.remove(id);
            } else if (player.isDead() || !classes.hasClass(id, PlayerClass.SUPORTE)) {
                end(player, false);
            } else if (flights.get(id).until() <= now) {
                end(player, true);
            } else {
                showTime(player);
            }
        }
    }

    /**
     * Durante o voo da Pena: corta a velocidade horizontal acima do limite (sem travar, só o excesso) e segura o
     * Suporte na altura máxima acima de onde começou a voar.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Flight flight = flights.get(event.getPlayer().getUniqueId());
        if (flight == null || !survivalLike(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (!from.getWorld().equals(to.getWorld())) {
            return;
        }
        boolean changed = false;
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double maxSpeed = settings.flightMaxSpeed();
        if (horizontal > maxSpeed) {
            double scale = maxSpeed / horizontal;
            to = to.clone();
            to.setX(from.getX() + dx * scale);
            to.setZ(from.getZ() + dz * scale);
            changed = true;
        }
        double limit = flight.startY() + settings.flightMaxHeight();
        if (to.getY() > limit && to.getY() > from.getY()) {
            to = changed ? to : to.clone();
            // Abaixo do limite: para nele. Já acima (ex: teleportado): não sobe mais.
            to.setY(Math.max(limit, Math.min(from.getY(), to.getY())));
            changed = true;
        }
        if (changed) {
            event.setTo(to);
        }
    }

    /** Cancela o dano da próxima queda depois do voo (até o prazo da proteção). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = fallProtectionUntil.remove(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity(), false);
        fallProtectionUntil.remove(event.getEntity().getUniqueId());
    }

    /** Tira o voo (e devolve a velocidade de voo) antes de o jogador ser salvo. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), false);
        fallProtectionUntil.remove(event.getPlayer().getUniqueId());
    }

    /** Se o servidor caiu no meio de um voo, o jogador não fica com voo (nem com o voo lento) para sempre. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (flights.containsKey(player.getUniqueId())) {
            return;
        }
        fixLeftover(player);
        if (survivalLike(player) && player.getAllowFlight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /** Marca de um voo antigo que não terminou certo: a velocidade de voo volta para 0.1. */
    private void fixLeftover(Player player) {
        if (flights.containsKey(player.getUniqueId())
                || !player.getPersistentDataContainer().has(markKey, PersistentDataType.BYTE)) {
            return;
        }
        player.getPersistentDataContainer().remove(markKey);
        player.setFlySpeed(DEFAULT_FLY_SPEED);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getOldClass() == PlayerClass.SUPORTE && player != null) {
            end(player, false);
            fallProtectionUntil.remove(event.getPlayerId());
        }
    }
}
