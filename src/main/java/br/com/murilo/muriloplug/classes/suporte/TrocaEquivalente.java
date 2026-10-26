package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import br.com.murilo.muriloplug.teste.TestMode;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Troca Equivalente (Shift + clique direito com o Núcleo liga/desliga; padrão: ligada). Quando o aliado vinculado
 * levaria um golpe mortal, o Suporte o salva com 4 corações (efeito de totem, sem gastar totem) e fica com a vida
 * máxima limitada a 3 corações por 60s. A escolha liga/desliga é salva em {@code suporte.yml}. O limite é um
 * AttributeModifier temporário (mineplug:suporte_troca) que soma com os outros; como é salvo junto com o jogador, ele
 * é removido ao acabar, ao sair e ao desligar o plugin.
 */
public final class TrocaEquivalente implements Listener, CommandExecutor {

    public static final String STATUS = "Troca Equivalente";
    private static final long TICK_PERIOD = 20L;
    private static final double EPSILON = 0.001;

    private final JavaPlugin plugin;
    private final SuporteSettings settings;
    private final SuporteCooldowns cooldowns;
    private final StatusRegistry statuses;
    private static final long TOGGLE_DEBOUNCE_MILLIS = 300L;

    private final ClassManager classes;
    private final SuporteItems items;
    private final NamespacedKey key;
    private final File file;
    /** Último liga/desliga de cada Suporte (um clique não conta duas vezes). */
    private final Map<UUID, Long> lastToggle = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    /** Suportes que DESLIGARAM a troca (o padrão é ligada, então só os desligados são guardados). */
    private final Set<UUID> disabled = new HashSet<>();
    /** Fim (ms) da penalidade de vida máxima de cada Suporte. */
    private final Map<UUID, Long> penaltyUntil = new HashMap<>();
    private TestMode test = TestMode.inactive();

    public TrocaEquivalente(JavaPlugin plugin, ClassManager classes, SuporteSettings settings, SuporteCooldowns cooldowns,
                            StatusRegistry statuses, SuporteItems items) {
        this.plugin = plugin;
        this.classes = classes;
        this.items = items;
        this.settings = settings;
        this.cooldowns = cooldowns;
        this.statuses = statuses;
        this.key = new NamespacedKey(plugin, "suporte_troca");
        this.file = new File(plugin.getDataFolder(), "suporte.yml");
    }

    public void setTestMode(TestMode test) {
        this.test = test;
    }

    /** Chamado quando a troca é ligada ou desligada (sidebar). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        // Sobras de um desligamento brusco: ninguém online está com penalidade agora.
        Bukkit.getOnlinePlayers().forEach(this::removeModifier);
    }

    /** Tira a penalidade de todos (onDisable). */
    public void endAll() {
        Bukkit.getOnlinePlayers().forEach(this::removeModifier);
        penaltyUntil.clear();
    }

    // ------------------------------------------------------------ liga/desliga (salvo)

    public boolean isEnabled(UUID support) {
        return !disabled.contains(support);
    }

    /** Shift + clique direito com o Núcleo, ou /troca. Um clique só conta uma vez (intervalo de 300ms). */
    public void toggle(Player support) {
        UUID id = support.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastToggle.get(id);
        if (last != null && now - last < TOGGLE_DEBOUNCE_MILLIS) {
            return;
        }
        lastToggle.put(id, now);
        // Estava desligada (estava na lista): liga. Estava ligada: desliga (entra na lista).
        boolean nowEnabled = disabled.remove(id);
        if (!nowEnabled) {
            disabled.add(id);
        }
        save();
        changeListener.accept(id);
        support.sendMessage(nowEnabled ? "§dTroca Equivalente: §aLIGADA" : "§dTroca Equivalente: §cDESLIGADA");
        support.getWorld().playSound(support.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, nowEnabled ? 1.4f : 0.8f);
    }

    public void load() {
        disabled.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("troca-equivalente-desligada")) {
            try {
                disabled.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("suporte.yml: UUID inválido ignorado: " + raw);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        disabled.forEach(id -> ids.add(id.toString()));
        yaml.set("troca-equivalente-desligada", ids);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar suporte.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ penalidade

    public boolean isPenalized(UUID support) {
        Long until = penaltyUntil.get(support);
        return until != null && until > System.currentTimeMillis();
    }

    /** True se a troca está ligada e pronta (sem recarga e sem penalidade ativa). */
    public boolean isReady(UUID support) {
        return isEnabled(support) && cooldowns.remainingMillis(support, SuporteSkill.TROCA_EQUIVALENTE) <= 0
                && (test.isActive(support) || !isPenalized(support));
    }

    /**
     * Chamado pelo vínculo quando o aliado vinculado levaria um golpe mortal (as causas isentas já ficaram de fora).
     * Retorna true se o Suporte o salvou; quem chama cancela o dano.
     */
    public boolean tryRescue(Player support, LivingEntity ally) {
        UUID id = support.getUniqueId();
        if (!isReady(id) || holdsTotem(ally)) {
            return false;
        }
        AttributeInstance allyMax = ally.getAttribute(Attribute.MAX_HEALTH);
        double allyLimit = allyMax == null ? 20 : allyMax.getValue();
        ally.setHealth(Math.min(allyLimit, settings.swapAllyHealth()));
        ally.setFireTicks(0);
        ally.setNoDamageTicks(20);
        // Efeito do totem: a animação na tela do aliado e as partículas para todos (nenhum totem é gasto).
        ally.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
        ally.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, ally.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.4);
        ally.getWorld().playSound(ally.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);

        double seconds = settings.swapSeconds();
        penaltyUntil.put(id, System.currentTimeMillis() + (long) (seconds * 1000));
        statuses.add(support, STATUS, seconds);
        applyModifier(support);
        cooldowns.start(support, SuporteSkill.TROCA_EQUIVALENTE, settings.cooldownSeconds(SuporteSkill.TROCA_EQUIVALENTE));
        support.getWorld().playSound(support.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.6f);

        if (ally instanceof Player allyPlayer) {
            allyPlayer.sendMessage("§d" + support.getName() + " fez uma Troca Equivalente por você!");
        }
        support.sendMessage("§cTroca Equivalente: sua vida máxima é " + SuporteItems.format(settings.swapMaxHealth() / 2)
                + " corações por " + SuporteItems.format(seconds) + "s.");
        return true;
    }

    /** Segurando um Totem da Imortalidade de verdade (o Totem de Cura do Suporte não conta). */
    private boolean holdsTotem(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        return equipment != null && (isRealTotem(equipment.getItemInMainHand())
                || isRealTotem(equipment.getItemInOffHand()));
    }

    private boolean isRealTotem(ItemStack item) {
        return item.getType() == Material.TOTEM_OF_UNDYING && items.read(item) != SuporteItem.TOTEM;
    }

    /** /troca: liga ou desliga a Troca Equivalente (só para Suportes). */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSó jogadores podem usar este comando.");
            return true;
        }
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.SUPORTE)) {
            player.sendMessage("§cSomente Suportes podem usar a Troca Equivalente.");
            return true;
        }
        toggle(player);
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(penaltyUntil.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                penaltyUntil.remove(id);
            } else if (penaltyUntil.get(id) <= now) {
                penaltyUntil.remove(id);
                statuses.remove(id, STATUS);
                removeModifier(player);
                player.sendMessage("§aA penalidade da Troca Equivalente acabou.");
            } else if (!player.isDead()) {
                applyModifier(player); // refaz depois de renascer ou de a vida máxima mudar por outro motivo
            }
        }
    }

    /** Ajusta o modificador para a vida máxima ficar em {@code troca.vida-maxima} (se ela for maior que isso). */
    private void applyModifier(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        AttributeModifier current = find(max);
        double currentAmount = current == null ? 0 : current.getAmount();
        double cap = settings.swapMaxHealth();
        // Vida máxima sem este modificador: a base mais os outros modificadores de soma (os de vida do plugin e da
        // Vida Extra são todos de soma). Não usa o valor atual, que o jogo segura em no mínimo 1.
        double without = max.getBaseValue();
        for (AttributeModifier modifier : max.getModifiers()) {
            if (!key.equals(modifier.getKey()) && modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                without += modifier.getAmount();
            }
        }
        double wanted = Math.min(0, cap - without);
        if (Math.abs(wanted - currentAmount) > EPSILON) {
            if (current != null) {
                max.removeModifier(current);
            }
            if (wanted < 0) {
                max.addModifier(new AttributeModifier(key, wanted, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        if (player.getHealth() > max.getValue()) {
            player.setHealth(max.getValue());
        }
    }

    private void removeModifier(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        AttributeModifier current = max == null ? null : find(max);
        if (current != null) {
            max.removeModifier(current);
        }
    }

    private AttributeModifier find(AttributeInstance max) {
        for (AttributeModifier modifier : new ArrayList<>(max.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                return modifier;
            }
        }
        return null;
    }

    /**
     * Troca de classe durante a penalidade: tira o limite antes de a vida extra da classe sair (senão a vida máxima
     * passaria de 1 para baixo e o jogo derrubaria a vida) e recoloca depois, já com a vida máxima nova.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClassChangeBefore(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (player != null && isPenalized(player.getUniqueId())) {
            removeModifier(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClassChangeAfter(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (player != null && isPenalized(player.getUniqueId())) {
            applyModifier(player);
        }
    }

    /** Sobra de um desligamento brusco (o modificador foi salvo junto com o jogador). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isPenalized(event.getPlayer().getUniqueId())) {
            removeModifier(event.getPlayer());
        }
    }

    /**
     * O modificador é salvo junto com o jogador: sai com ele, e a penalidade acaba. Roda antes da vida extra sair
     * (LOWEST), para a vida máxima não passar de 1 para baixo e o jogador ser salvo sem vida.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastToggle.remove(player.getUniqueId());
        penaltyUntil.remove(player.getUniqueId());
        statuses.remove(player.getUniqueId(), STATUS);
        removeModifier(player);
    }

    /** Apaga o que está salvo do jogador (reset de conta): volta a ficar ligada e sem penalidade guardada. */
    public void forget(UUID support) {
        penaltyUntil.remove(support);
        lastToggle.remove(support);
        if (disabled.remove(support)) {
            save();
        }
    }
}
