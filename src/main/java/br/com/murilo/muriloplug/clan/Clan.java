package br.com.murilo.muriloplug.clan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Um clan. Só o {@link ClanManager} altera os dados (e salva depois de cada mudança). */
public final class Clan {

    private final String tag;
    private final String name;
    private final long createdAt;
    private final List<UUID> members = new ArrayList<>();
    private UUID leader;

    Clan(String tag, String name, UUID leader, long createdAt) {
        this.tag = tag;
        this.name = name;
        this.leader = leader;
        this.createdAt = createdAt;
        this.members.add(leader);
    }

    /** TAG em MAIÚSCULAS. */
    public String tag() {
        return tag;
    }

    public String name() {
        return name;
    }

    public UUID leader() {
        return leader;
    }

    public long createdAt() {
        return createdAt;
    }

    /** Membros (inclui o líder), na ordem de entrada. */
    public List<UUID> members() {
        return Collections.unmodifiableList(members);
    }

    public int size() {
        return members.size();
    }

    public boolean isMember(UUID player) {
        return members.contains(player);
    }

    void addMember(UUID player) {
        members.add(player);
    }

    void removeMember(UUID player) {
        members.remove(player);
    }

    void setLeader(UUID player) {
        leader = player;
    }
}
