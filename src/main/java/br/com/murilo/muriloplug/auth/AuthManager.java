package br.com.murilo.muriloplug.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login do servidor (modo offline). Quem entra começa NÃO logado; as restrições ficam no
 * {@link AuthRestrictionsListener}. As senhas só existem como hash em auth.yml, e o hash é calculado fora da
 * thread principal.
 */
public final class AuthManager {

    public static final String TIME_PATH = "login.tempo-para-logar-segundos";
    public static final String ATTEMPTS_PATH = "login.tentativas-maximas";
    public static final String SESSION_ENABLED_PATH = "login.sessao.ativada";
    public static final String SESSION_MINUTES_PATH = "login.sessao.minutos";

    public static final int MIN_PASSWORD = 6;
    public static final int MAX_PASSWORD = 32;
    private static final int REMINDER_SECONDS = 5;

    private record Session(String ip, long expiresAt) {}

    private final JavaPlugin plugin;
    private final File file;
    private final int timeLimitSeconds;
    private final int maxAttempts;
    private final boolean sessionEnabled;
    private final long sessionMillis;

    /** Lidos também fora da thread principal (pré-login e chat), por isso são concorrentes. */
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Set<UUID> loggedIn = ConcurrentHashMap.newKeySet();
    private final Set<String> onlineNicks = ConcurrentHashMap.newKeySet();

    // Só a thread principal mexe nestes.
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, BukkitTask> loginTimers = new HashMap<>();
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();
    /** Jogadores com um hash em andamento: ignoram novos comandos de senha até terminar. */
    private final Set<UUID> busy = new HashSet<>();

    public AuthManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auth.yml");
        this.timeLimitSeconds = Math.max(5, plugin.getConfig().getInt(TIME_PATH, 60));
        this.maxAttempts = Math.max(1, plugin.getConfig().getInt(ATTEMPTS_PATH, 5));
        this.sessionEnabled = plugin.getConfig().getBoolean(SESSION_ENABLED_PATH, false);
        this.sessionMillis = Math.max(1, plugin.getConfig().getLong(SESSION_MINUTES_PATH, 10)) * 60_000L;
    }

    // ------------------------------------------------------------ consulta

    public boolean isLoggedIn(UUID player) {
        return loggedIn.contains(player);
    }

    public boolean isRegistered(UUID player) {
        return accounts.containsKey(player);
    }

    /** Nicks registrados (para o tab-complete do admin). */
    public List<String> registeredNicks() {
        List<String> nicks = new ArrayList<>();
        accounts.values().forEach(account -> nicks.add(account.nick()));
        return nicks;
    }

    /**
     * Confere um nick que está tentando conectar (roda fora da thread principal). Retorna a mensagem de recusa,
     * ou null se puder entrar.
     */
    public String checkConnection(String name) {
        if (onlineNicks.contains(name.toLowerCase(Locale.ROOT))) {
            return "Este jogador já está online.";
        }
        for (Account account : accounts.values()) {
            if (account.nick().equalsIgnoreCase(name) && !account.nick().equals(name)) {
                return "Use o nick exatamente como foi registrado: " + account.nick();
            }
        }
        return null;
    }

    // ------------------------------------------------------------ entrada e saída

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        loggedIn.remove(id);
        failedAttempts.remove(id);
        busy.remove(id);
        onlineNicks.add(player.getName().toLowerCase(Locale.ROOT));

        Session session = sessions.remove(id);
        String ip = ipOf(player);
        if (sessionEnabled && session != null && isRegistered(id) && session.ip().equals(ip)
                && System.currentTimeMillis() < session.expiresAt()) {
            completeLogin(player, "§aSessão restaurada. Logado com sucesso!");
            return;
        }
        startPrompt(player);
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask timer = loginTimers.remove(id);
        if (timer != null) {
            timer.cancel();
        }
        if (sessionEnabled && loggedIn.contains(id)) {
            sessions.put(id, new Session(ipOf(player), System.currentTimeMillis() + sessionMillis));
        }
        loggedIn.remove(id);
        failedAttempts.remove(id);
        busy.remove(id);
        onlineNicks.remove(player.getName().toLowerCase(Locale.ROOT));
    }

    /** Cancela os temporizadores (onDisable). */
    public void shutdown() {
        loginTimers.values().forEach(BukkitTask::cancel);
        loginTimers.clear();
    }

    /** Lembrete a cada 5 segundos e expulsão quando o tempo acaba. */
    private void startPrompt(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask old = loginTimers.remove(id);
        if (old != null) {
            old.cancel();
        }
        int[] elapsed = {0};
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || loggedIn.contains(id)) {
                    cancel();
                    loginTimers.remove(id);
                    return;
                }
                if (elapsed[0] >= timeLimitSeconds) {
                    cancel();
                    loginTimers.remove(id);
                    player.kick(Component.text("Tempo para login esgotado."));
                    return;
                }
                if (elapsed[0] % REMINDER_SECONDS == 0) {
                    remind(player);
                }
                elapsed[0]++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        loginTimers.put(id, task);
    }

    private void remind(Player player) {
        String text = isRegistered(player.getUniqueId())
                ? "Use /login <senha>"
                : "Use /register <senha> <senha>";
        player.sendMessage("§e" + text);
        player.showTitle(Title.title(Component.text(text, NamedTextColor.YELLOW), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(REMINDER_SECONDS + 1), Duration.ZERO)));
    }

    private void completeLogin(Player player, String message) {
        UUID id = player.getUniqueId();
        loggedIn.add(id);
        failedAttempts.remove(id);
        BukkitTask timer = loginTimers.remove(id);
        if (timer != null) {
            timer.cancel();
        }
        Account account = accounts.get(id);
        if (account != null) {
            accounts.put(id, account.withLogin(ipOf(player), System.currentTimeMillis()));
            save();
        }
        player.clearTitle();
        player.sendMessage(message);
    }

    // ------------------------------------------------------------ comandos de senha

    /** Erro de validação da senha, ou null se estiver boa. */
    public static String validatePassword(String password, String nick) {
        if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            return "A senha deve ter de " + MIN_PASSWORD + " a " + MAX_PASSWORD + " caracteres.";
        }
        if (password.equalsIgnoreCase(nick)) {
            return "A senha não pode ser igual ao seu nick.";
        }
        return null;
    }

    public void register(Player player, String password, String confirmation) {
        UUID id = player.getUniqueId();
        if (loggedIn.contains(id) || isRegistered(id)) {
            player.sendMessage(loggedIn.contains(id)
                    ? "§cVocê já está logado."
                    : "§cVocê já está registrado. Use /login <senha>.");
            return;
        }
        if (!password.equals(confirmation)) {
            player.sendMessage("§cAs senhas não coincidem.");
            return;
        }
        String error = validatePassword(password, player.getName());
        if (error != null) {
            player.sendMessage("§c" + error);
            return;
        }
        if (!busy.add(id)) {
            player.sendMessage("§cAguarde, ainda estou processando o seu comando anterior.");
            return;
        }

        runAsync(() -> PasswordHasher.hash(password), hashed -> {
            busy.remove(id);
            if (!player.isOnline()) {
                return;
            }
            if (isRegistered(id)) {
                player.sendMessage("§cVocê já está registrado. Use /login <senha>.");
                return;
            }
            long now = System.currentTimeMillis();
            accounts.put(id, new Account(id, player.getName(), hashed.algorithm(), hashed.iterations(),
                    hashed.salt(), hashed.hash(), now, ipOf(player), now));
            save();
            completeLogin(player, "§aLogado com sucesso!");
        }, () -> busy.remove(id));
    }

    public void login(Player player, String password) {
        UUID id = player.getUniqueId();
        if (loggedIn.contains(id)) {
            player.sendMessage("§cVocê já está logado.");
            return;
        }
        Account account = accounts.get(id);
        if (account == null) {
            player.sendMessage("§cVocê ainda não está registrado. Use /register <senha> <senha>.");
            return;
        }
        if (!busy.add(id)) {
            player.sendMessage("§cAguarde, ainda estou processando o seu comando anterior.");
            return;
        }

        runAsync(() -> PasswordHasher.verify(password, account), correct -> {
            busy.remove(id);
            if (!player.isOnline()) {
                return;
            }
            if (correct) {
                completeLogin(player, "§aLogado com sucesso!");
                return;
            }
            int failures = failedAttempts.merge(id, 1, Integer::sum);
            if (failures >= maxAttempts) {
                player.kick(Component.text("Senha incorreta muitas vezes."));
            } else {
                player.sendMessage("§cSenha incorreta. Tentativas restantes: " + (maxAttempts - failures) + ".");
            }
        }, () -> busy.remove(id));
    }

    public void changePassword(Player player, String oldPassword, String newPassword) {
        UUID id = player.getUniqueId();
        Account account = accounts.get(id);
        if (!loggedIn.contains(id) || account == null) {
            player.sendMessage("§cVocê precisa estar logado.");
            return;
        }
        String error = validatePassword(newPassword, player.getName());
        if (error != null) {
            player.sendMessage("§c" + error);
            return;
        }
        if (!busy.add(id)) {
            player.sendMessage("§cAguarde, ainda estou processando o seu comando anterior.");
            return;
        }

        runAsync(() -> PasswordHasher.verify(oldPassword, account) ? PasswordHasher.hash(newPassword) : null, hashed -> {
            busy.remove(id);
            if (!player.isOnline()) {
                return;
            }
            if (hashed == null) {
                player.sendMessage("§cA senha antiga está incorreta.");
                return;
            }
            Account current = accounts.get(id);
            if (current == null) {
                return;
            }
            accounts.put(id, current.withPassword(hashed));
            save();
            player.sendMessage("§aSenha alterada com sucesso!");
        }, () -> busy.remove(id));
    }

    /** Apaga o registro do jogador (pelo nick, sem diferenciar maiúsculas). Retorna false se não houver registro. */
    public boolean reset(String nick) {
        Account target = null;
        for (Account account : accounts.values()) {
            if (account.nick().equalsIgnoreCase(nick)) {
                target = account;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        accounts.remove(target.id());
        sessions.remove(target.id());
        loggedIn.remove(target.id());
        save();

        Player online = Bukkit.getPlayer(target.id());
        if (online != null) {
            online.kick(Component.text("Sua senha foi resetada. Entre de novo e use /register."));
        }
        return true;
    }

    /** Roda {@code work} fora da thread principal e entrega o resultado de volta nela. */
    private <T> void runAsync(java.util.function.Supplier<T> work, java.util.function.Consumer<T> done, Runnable onFailure) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Erro ao processar senha: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, onFailure);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> done.accept(result));
        });
    }

    // ------------------------------------------------------------ persistência

    public void load() {
        accounts.clear();
        if (!file.exists()) {
            return;
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("accounts");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                accounts.put(id, new Account(id, entry.getString("nick", ""), entry.getString("algorithm", PasswordHasher.ALGORITHM),
                        entry.getInt("iterations", PasswordHasher.ITERATIONS), entry.getString("salt", ""),
                        entry.getString("hash", ""), entry.getLong("registered"), entry.getString("last-ip", ""),
                        entry.getLong("last-login")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em auth.yml, ignorado: " + key);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Account account : accounts.values()) {
            String path = "accounts." + account.id();
            yaml.set(path + ".nick", account.nick());
            yaml.set(path + ".algorithm", account.algorithm());
            yaml.set(path + ".iterations", account.iterations());
            yaml.set(path + ".salt", account.salt());
            yaml.set(path + ".hash", account.hash());
            yaml.set(path + ".registered", account.registeredAt());
            yaml.set(path + ".last-ip", account.lastIp());
            yaml.set(path + ".last-login", account.lastLogin());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar auth.yml: " + e.getMessage());
        }
    }

    private static String ipOf(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null ? "desconhecido" : address.getAddress().getHostAddress();
    }
}
