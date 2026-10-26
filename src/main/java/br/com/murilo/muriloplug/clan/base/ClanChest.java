package br.com.murilo.muriloplug.clan.base;

/** Um Baú do Clan colocado no mundo (os itens ficam em baus/&lt;id&gt;.yml, pelo {@link ClanChestStore}). */
public final class ClanChest {

    final String id;
    final BaseLoc loc;
    int hp;
    long shieldUntil;
    long lastAttack;

    ClanChest(String id, BaseLoc loc, int hp) {
        this.id = id;
        this.loc = loc;
        this.hp = hp;
    }

    public String id() {
        return id;
    }

    public BaseLoc loc() {
        return loc;
    }

    public int hp() {
        return hp;
    }
}
