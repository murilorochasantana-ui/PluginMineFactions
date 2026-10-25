package br.com.murilo.muriloplug.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Clans do servidor, salvos em clans.yml logo depois de cada mudança. Um jogador só pode estar em um clan. */
public final class ClanManager {

    public static final String MAX_MEMBERS_PATH = "clan.max-membros";
    public static final int DEFAULT_MAX_MEMBERS = 2;

    private final JavaPlugin plugin;
    private final File file;
    /** Clans pela TAG em maiúsculas, na ordem de criação. */
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    /** Clan de cada jogador. */
    private final Map<UUID, Clan> byPlayer = new ConcurrentHashMap<>();
    private Consumer<UUID> membershipListener = id -> { };
    private Consumer<Clan> disbandListener = clan -> { };
    /** Grava dados de outros sistemas (bases dos clans) no mesmo clans.yml, a cada save. */
    private Consumer<FileConfiguration> extraWriter = yaml -> { };

    public ClanManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    /** Chamado para cada jogador cujo clan mudou (entrou, saiu, foi expulso, clan criado ou desfeito). */
    public void setMembershipListener(Consumer<UUID> membershipListener) {
        this.membershipListener = membershipListener;
    }

    /** Chamado depois que um clan é desfeito (os membros já saíram dele). */
    public void setDisbandListener(Consumer<Clan> disbandListener) {
        this.disbandListener = disbandListener;
    }

    /** Quem mais grava no clans.yml: chamado em cada {@link #save()}, antes de gravar o arquivo. */
    public void setExtraWriter(Consumer<FileConfiguration> extraWriter) {
        this.extraWriter = extraWriter;
    }

    /** O arquivo clans.yml (para outros sistemas lerem os dados deles ao ligar). */
    public File file() {
        return file;
    }

    // ------------------------------------------------------------ consulta (API para outros sistemas)

    /** Clan do jogador, ou null se ele não tiver. */
    public Clan getClan(UUID player) {
        return byPlayer.get(player);
    }

    /** Clan pela TAG (sem diferenciar maiúsculas), ou null. */
    public Clan getByTag(String tag) {
        return clans.get(tag.toUpperCase(Locale.ROOT));
    }

    /** Se os dois jogadores estão no mesmo clan. */
    public boolean isSameClan(UUID a, UUID b) {
        Clan clan = byPlayer.get(a);
        return clan != null && clan == byPlayer.get(b);
    }

    public List<UUID> getMembers(Clan clan) {
        return List.copyOf(clan.members());
    }

    /** Se o jogador é líder do clan dele. */
    public boolean isLeader(UUID player) {
        Clan clan = byPlayer.get(player);
        return clan != null && clan.leader().equals(player);
    }

    public Collection<Clan> all() {
        return List.copyOf(clans.values());
    }

    /** Máximo de membros por clan (config.yml: clan.max-membros). */
    public int maxMembers() {
        return Math.max(1, plugin.getConfig().getInt(MAX_MEMBERS_PATH, DEFAULT_MAX_MEMBERS));
    }

    public boolean isFull(Clan clan) {
        return clan.size() >= maxMembers();
    }

    // ------------------------------------------------------------ mudanças

    /** Cria o clan com o jogador como líder. A validação de TAG e nome é feita antes, pelo comando. */
    public Clan create(String tag, String name, UUID leader) {
        Clan clan = new Clan(tag.toUpperCase(Locale.ROOT), name, leader, System.currentTimeMillis());
        clans.put(clan.tag(), clan);
        byPlayer.put(leader, clan);
        save();
        membershipListener.accept(leader);
        return clan;
    }

    /** Adiciona o jogador ao clan. Retorna false se ele já tiver clan ou se o clan estiver cheio. */
    public boolean addMember(Clan clan, UUID player) {
        if (byPlayer.containsKey(player) || isFull(clan)) {
            return false;
        }
        clan.addMember(player);
        byPlayer.put(player, clan);
        save();
        membershipListener.accept(player);
        return true;
    }

    /** Remove um membro que não é o líder. Retorna false se ele não for membro ou for o líder. */
    public boolean removeMember(Clan clan, UUID player) {
        if (!clan.isMember(player) || clan.leader().equals(player)) {
            return false;
        }
        clan.removeMember(player);
        byPlayer.remove(player);
        save();
        membershipListener.accept(player);
        return true;
    }

    /** Passa a liderança para outro membro. */
    public boolean transferLeader(Clan clan, UUID newLeader) {
        if (!clan.isMember(newLeader) || clan.leader().equals(newLeader)) {
            return false;
        }
        clan.setLeader(newLeader);
        save();
        return true;
    }

    /** Apaga o clan. */
    public void disband(Clan clan) {
        List<UUID> members = List.copyOf(clan.members());
        clans.remove(clan.tag());
        members.forEach(byPlayer::remove);
        disbandListener.accept(clan);
        save();
        members.forEach(membershipListener);
    }

    // ------------------------------------------------------------ mensagens

    /** Manda a mensagem (com códigos §) para os membros online do clan. */
    public void broadcast(Clan clan, String legacyMessage) {
        send(clan, LegacyComponentSerializer.legacySection().deserialize(legacyMessage));
    }

    public void send(Clan clan, Component message) {
        for (UUID id : clan.members()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    /** Nome conhecido do jogador (online ou offline), ou o UUID se o servidor não o conhecer. */
    public static String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }

    // ------------------------------------------------------------ persistência

    public void load() {
        clans.clear();
        byPlayer.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yaml.getMapList("clans")) {
            try {
                UUID leader = UUID.fromString(String.valueOf(map.get("leader")));
                Clan clan = new Clan(String.valueOf(map.get("tag")).toUpperCase(Locale.ROOT),
                        String.valueOf(map.get("name")), leader, ((Number) map.get("created")).longValue());
                if (map.get("members") instanceof List<?> raw) {
                    for (Object item : raw) {
                        UUID member = UUID.fromString(String.valueOf(item));
                        if (!member.equals(leader) && !byPlayer.containsKey(member)) {
                            clan.addMember(member);
                        }
                    }
                }
                if (byPlayer.containsKey(leader)) {
                    plugin.getLogger().warning("Líder já está em outro clan, clan ignorado: " + clan.tag());
                    continue;
                }
                clans.put(clan.tag(), clan);
                clan.members().forEach(id -> byPlayer.put(id, clan));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Clan inválido em clans.yml, ignorado: " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Clan clan : clans.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tag", clan.tag());
            map.put("name", clan.name());
            map.put("leader", clan.leader().toString());
            map.put("created", clan.createdAt());
            map.put("members", clan.members().stream().map(UUID::toString).toList());
            list.add(map);
        }
        yaml.set("clans", list);
        extraWriter.accept(yaml);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar clans.yml: " + e.getMessage());
        }
    }
}
