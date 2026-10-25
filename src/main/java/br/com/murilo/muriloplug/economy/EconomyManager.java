package br.com.murilo.muriloplug.economy;

import br.com.murilo.muriloplug.economy.maco.Macos;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Saldo de cada jogador (números inteiros, nunca negativo). Todos os métodos de saldo são thread-safe.
 *
 * <p>O dinheiro também existe como item ({@link Macos}, o Maço de Dinheiro). Para COBRAR algo (loja, mercado,
 * reparo...) use sempre {@link #cobrar}: gasta primeiro o saldo e completa tirando de dentro dos maços do inventário.
 * Para PAGAR um ganho (kill, venda ao servidor, eventos) use {@link #pagarGanho}: soma no maço do inventário (ou vai
 * para o saldo, conforme o config).
 *
 * <p>O arquivo é gravado em uma thread própria (uma gravação por vez, em ordem), então salvar não trava o servidor.
 */
public final class EconomyManager {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MinePlug-Economy");
        thread.setDaemon(true);
        return thread;
    });
    /** Já existe uma gravação na fila: pedidos repetidos se juntam nela. */
    private final AtomicBoolean savePending = new AtomicBoolean();
    private volatile boolean closed;
    private volatile Consumer<UUID> changeListener = id -> { };
    private Macos macos;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
    }

    /** Chamado (na thread principal) depois de cada mudança de saldo, para atualizar o sidebar. */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    /** Liga os maços de dinheiro (criados depois do EconomyManager). */
    public void setMacos(Macos macos) {
        this.macos = macos;
    }

    public Macos macos() {
        return macos;
    }

    // ------------------------------------------------------------ dinheiro + notas (thread principal)

    /** Saldo + maços no inventário: o total que o jogador pode gastar agora. */
    public long available(Player player) {
        return saturatedAdd(getBalance(player.getUniqueId()), macos == null ? 0 : macos.count(player));
    }

    /**
     * Cobra {@code amount}: primeiro do saldo; se não bastar, completa tirando de dentro dos maços do inventário
     * (o maço que zera some). Retorna false, sem tirar nada, se saldo + maços não bastarem.
     * Chamar só na thread principal.
     */
    public boolean cobrar(Player player, long amount) {
        requireNotNegative(amount);
        if (amount == 0) {
            return true;
        }
        UUID id = player.getUniqueId();
        long fromBalance = Math.min(getBalance(id), amount);
        long fromNotes = amount - fromBalance;
        if (fromNotes > 0 && (macos == null || macos.count(player) < fromNotes)) {
            return false;
        }
        if (!withdraw(id, fromBalance)) {
            return false;
        }
        if (fromNotes > 0 && !macos.take(player, fromNotes)) {
            deposit(id, fromBalance); // não deve acontecer (os maços foram conferidos no mesmo tick)
            return false;
        }
        return true;
    }

    /**
     * Paga um ganho: soma no maço do inventário (sem maço, entrega um novo; o que não couber cai no chão) se
     * economia.notas.recompensas-em-notas estiver ligado e o jogador estiver online; senão, no saldo. Retorna true se
     * foi em maço.
     */
    public boolean pagarGanho(UUID player, long amount) {
        requireNotNegative(amount);
        if (amount == 0) {
            return false;
        }
        Player online = Bukkit.isPrimaryThread() ? Bukkit.getPlayer(player) : null;
        if (online != null && macos != null && macos.rewardsAsCash()) {
            macos.give(online, amount);
            changeListener.accept(player);
            return true;
        }
        deposit(player, amount);
        return false;
    }

    // ------------------------------------------------------------ API pública

    public long getBalance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    public boolean has(UUID player, long amount) {
        requireNotNegative(amount);
        return getBalance(player) >= amount;
    }

    /** Soma ao saldo (limitado a {@link Long#MAX_VALUE}). */
    public void deposit(UUID player, long amount) {
        requireNotNegative(amount);
        if (amount == 0) {
            return;
        }
        balances.merge(player, amount, EconomyManager::saturatedAdd);
        changed(player);
    }

    /** Tira do saldo. Retorna false, sem alterar nada, se o jogador não tiver saldo suficiente. */
    public boolean withdraw(UUID player, long amount) {
        requireNotNegative(amount);
        boolean[] done = {false};
        balances.compute(player, (id, current) -> {
            long balance = current == null ? 0L : current;
            if (balance < amount) {
                return current;
            }
            done[0] = true;
            return balance - amount;
        });
        if (done[0] && amount > 0) {
            changed(player);
        }
        return done[0];
    }

    /** Define o saldo (uso administrativo). */
    public void setBalance(UUID player, long amount) {
        requireNotNegative(amount);
        balances.put(player, amount);
        changed(player);
    }

    /** Formata com separador de milhar: {@code $1.250.000}. */
    public static String format(long amount) {
        return "$" + String.format(PT_BR, "%,d", amount);
    }

    // ------------------------------------------------------------ persistência

    public void load() {
        balances.clear();
        if (!file.exists()) {
            return;
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("balances");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), Math.max(0L, section.getLong(key)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em economy.yml, ignorado: " + key);
            }
        }
    }

    /** Pede uma gravação em segundo plano (várias chamadas seguidas viram uma só). */
    public void requestSave() {
        if (closed || !savePending.compareAndSet(false, true)) {
            return;
        }
        writer.execute(() -> {
            savePending.set(false);
            write();
        });
    }

    /** Termina as gravações pendentes e grava o estado final. Chamar no onDisable. */
    public void close() {
        closed = true;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("A gravação do economy.yml demorou demais; gravando de novo agora.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        write();
    }

    /** Grava um retrato dos saldos: primeiro num arquivo temporário, depois troca o arquivo de uma vez. */
    private synchronized void write() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(balances).entrySet()) {
            yaml.set("balances." + entry.getKey(), entry.getValue());
        }
        File temp = new File(plugin.getDataFolder(), "economy.yml.tmp");
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar economy.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ internos

    private void changed(UUID player) {
        requestSave();
        if (Bukkit.isPrimaryThread()) {
            changeListener.accept(player);
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> changeListener.accept(player));
        }
    }

    private static void requireNotNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("O valor não pode ser negativo: " + amount);
        }
    }

    private static long saturatedAdd(long a, long b) {
        long result = a + b;
        return ((a ^ result) & (b ^ result)) < 0 ? Long.MAX_VALUE : result;
    }
}
