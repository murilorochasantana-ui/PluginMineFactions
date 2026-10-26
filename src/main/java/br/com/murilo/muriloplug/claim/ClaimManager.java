package br.com.murilo.muriloplug.claim;

import br.com.murilo.muriloplug.clan.ClanManager;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class ClaimManager {

    /** Situação dos claims de um dono, vinda do sistema de bases dos clans. */
    public enum Weakness {
        /** Protegido normalmente. */
        NONE,
        /** Em dívida (enfraquecido): inimigos podem abrir baús e containers. */
        IN_DEBT,
        /** Núcleo desativado (ou não recolocado no prazo): inimigos podem quebrar, colocar e abrir. */
        VULNERABLE
    }

    public static final int MAX_CLAIMS = 9;

    private static final int FORMAT_VERSION = 2;

    private final JavaPlugin plugin;
    private final File file;
    /** Claims de cada jogador, na ordem de criação. */
    private final Map<UUID, List<Claim>> claims = new LinkedHashMap<>();
    private ClanManager clans;
    private Function<UUID, Weakness> weakness = owner -> Weakness.NONE;

    public ClaimManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
    }

    public List<Claim> getByOwner(UUID owner) {
        return List.copyOf(claims.getOrDefault(owner, List.of()));
    }

    /** Busca um claim do jogador pelo nome, sem diferenciar maiúsculas. */
    public Claim getByName(UUID owner, String name) {
        for (Claim claim : claims.getOrDefault(owner, List.of())) {
            if (claim.name().equalsIgnoreCase(name)) {
                return claim;
            }
        }
        return null;
    }

    public Claim getAt(Location location) {
        for (List<Claim> list : claims.values()) {
            for (Claim claim : list) {
                if (claim.contains(location)) {
                    return claim;
                }
            }
        }
        return null;
    }

    /** Se o jogador pode construir (ou usar home) em {@code location}: sem claim, dono, trusted ou bypass. */
    public boolean canBuild(Player player, Location location) {
        Claim claim = getAt(location);
        return claim == null
                || claim.canBuild(player.getUniqueId())
                || isSameClanAsOwner(player, claim)
                || player.hasPermission(ClaimListener.BYPASS_PERMISSION);
    }

    /** Membros do mesmo clan do dono usam o claim como se tivessem trust (nada é salvo: sair do clan tira o acesso). */
    private boolean isSameClanAsOwner(Player player, Claim claim) {
        return clans != null && clans.isSameClan(player.getUniqueId(), claim.owner());
    }

    /** Liga a situação (vulnerável/em dívida) dos claims de cada dono. */
    public void setWeakness(Function<UUID, Weakness> weakness) {
        this.weakness = weakness;
    }

    public Weakness weaknessOf(UUID owner) {
        return weakness.apply(owner);
    }

    /**
     * Se um inimigo (quem não pode construir no claim) pode mexer nele mesmo assim: quebrar/colocar só com o claim
     * vulnerável; abrir containers ({@code container}) com o claim vulnerável ou em dívida.
     */
    public boolean isOpenToEnemy(Claim claim, boolean container) {
        Weakness state = weakness.apply(claim.owner());
        return state == Weakness.VULNERABLE || (container && state == Weakness.IN_DEBT);
    }

    /** Todos os jogadores que têm pelo menos um claim. */
    public Set<UUID> owners() {
        return Set.copyOf(claims.keySet());
    }

    /** Soma das áreas (blocos²) dos claims do jogador. */
    public long totalArea(UUID owner) {
        long area = 0;
        for (Claim claim : claims.getOrDefault(owner, List.of())) {
            area += (long) claim.width() * claim.depth();
        }
        return area;
    }

    /** Liga o sistema de clans (criado depois do ClaimManager). */
    public void setClanManager(ClanManager clans) {
        this.clans = clans;
    }

    /** Retorna um claim (de qualquer jogador, inclusive do próprio) que colide com o informado, ou null. */
    public Claim findOverlap(Claim candidate) {
        for (List<Claim> list : claims.values()) {
            for (Claim claim : list) {
                if (claim.overlaps(candidate)) {
                    return claim;
                }
            }
        }
        return null;
    }

    public void add(Claim claim) {
        claims.computeIfAbsent(claim.owner(), k -> new ArrayList<>()).add(claim);
        save();
    }

    /** Substitui o claim de mesmo dono e nome, mantendo a ordem de criação. */
    public void update(Claim updated) {
        List<Claim> list = claims.get(updated.owner());
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(updated.name())) {
                list.set(i, updated);
                save();
                return;
            }
        }
    }

    public boolean remove(UUID owner, String name) {
        List<Claim> list = claims.get(owner);
        if (list == null || !list.removeIf(c -> c.name().equalsIgnoreCase(name))) {
            return false;
        }
        if (list.isEmpty()) {
            claims.remove(owner);
        }
        save();
        return true;
    }

    public void load() {
        claims.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("version", 1) >= FORMAT_VERSION) {
            loadCurrent(yaml);
        } else {
            migrateLegacy(yaml);
        }
    }

    private void loadCurrent(YamlConfiguration yaml) {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID owner = parseUuid(key);
            if (owner == null) {
                continue;
            }
            List<Claim> list = new ArrayList<>();
            for (Map<?, ?> map : players.getMapList(key)) {
                list.add(new Claim(owner, String.valueOf(map.get("name")), String.valueOf(map.get("world")),
                        number(map, "minX"), number(map, "minZ"), number(map, "maxX"), number(map, "maxZ"),
                        trustedFrom(map)));
            }
            if (!list.isEmpty()) {
                claims.put(owner, list);
            }
        }
    }

    /** Formato antigo: um claim por UUID, sem nome. Os claims antigos viram "claim1". */
    private void migrateLegacy(YamlConfiguration yaml) {
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            UUID owner = parseUuid(key);
            if (section == null || owner == null || !section.contains("world")) {
                continue;
            }
            List<Claim> list = new ArrayList<>();
            list.add(new Claim(owner, "claim1", section.getString("world"),
                    section.getInt("minX"), section.getInt("minZ"),
                    section.getInt("maxX"), section.getInt("maxZ")));
            claims.put(owner, list);
        }
        if (claims.isEmpty()) {
            return;
        }
        try {
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "claims.yml.bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível criar o backup de claims.yml: " + e.getMessage());
        }
        save();
        plugin.getLogger().info("claims.yml migrado para o novo formato (" + claims.size()
                + " jogador(es)); cópia do arquivo antigo em claims.yml.bak.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", FORMAT_VERSION);
        for (Map.Entry<UUID, List<Claim>> entry : claims.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Claim claim : entry.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", claim.name());
                map.put("world", claim.world());
                map.put("minX", claim.minX());
                map.put("minZ", claim.minZ());
                map.put("maxX", claim.maxX());
                map.put("maxZ", claim.maxZ());
                map.put("trusted", claim.trusted().stream().map(UUID::toString).toList());
                list.add(map);
            }
            yaml.set("players." + entry.getKey(), list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar claims.yml: " + e.getMessage());
        }
    }

    private UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("UUID inválido em claims.yml, ignorado: " + text);
            return null;
        }
    }

    /** Lê a lista de trusted; claims salvos sem ela carregam com lista vazia. */
    private List<UUID> trustedFrom(Map<?, ?> map) {
        List<UUID> result = new ArrayList<>();
        if (map.get("trusted") instanceof List<?> raw) {
            for (Object item : raw) {
                UUID id = parseUuid(String.valueOf(item));
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private static int number(Map<?, ?> map, String key) {
        return ((Number) map.get(key)).intValue();
    }

    // ------------------------------------------------------------ reset de conta

    /** Remove todos os claims do jogador e devolve os que foram removidos. */
    public List<Claim> removeAll(UUID owner) {
        List<Claim> removed = claims.remove(owner);
        if (removed == null) {
            return List.of();
        }
        save();
        return removed;
    }

    /** Claims de outros jogadores em que o jogador tem confiança (trust). */
    public List<Claim> trustedIn(UUID player) {
        List<Claim> list = new ArrayList<>();
        for (List<Claim> owned : claims.values()) {
            for (Claim claim : owned) {
                if (claim.isTrusted(player)) {
                    list.add(claim);
                }
            }
        }
        return list;
    }

    /** Tira o jogador da lista de confiança de todos os claims. Retorna em quantos ele estava. */
    public int removeTrustEverywhere(UUID player) {
        int count = 0;
        for (List<Claim> owned : claims.values()) {
            for (int i = 0; i < owned.size(); i++) {
                if (owned.get(i).isTrusted(player)) {
                    owned.set(i, owned.get(i).withoutTrusted(player));
                    count++;
                }
            }
        }
        if (count > 0) {
            save();
        }
        return count;
    }
}
