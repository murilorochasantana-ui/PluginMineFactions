package br.com.murilo.muriloplug.classes.suporte;

/**
 * Habilidades do Suporte que têm recarga própria. As que pertencem ao clique principal de um item ({@code item} não
 * nulo) também mostram a recarga como animação nesse item; as de Shift só aparecem na sidebar, para não "travar"
 * visualmente o clique principal do item.
 */
public enum SuporteSkill {

    PRENDER("Prender", SuporteItem.PRENDER),
    CURA("Cura", SuporteItem.CURA),
    PERGAMINHO("Velocidade", SuporteItem.PERGAMINHO),
    VOO("Voo", SuporteItem.VOO),
    ELO("Elo Drenante", null),
    TROCA_LUGAR("Troca de Lugar", null),
    TROCA_EQUIVALENTE("Troca Equivalente", null),
    ESCUDO("Escudo Sagrado", SuporteItem.ESCUDO),
    PURIFICAR("Purificar", null),
    SILENCIO("Silêncio", null),
    MALDICAO("Maldição", null);

    private final String displayName;
    private final SuporteItem item;

    SuporteSkill(String displayName, SuporteItem item) {
        this.displayName = displayName;
        this.item = item;
    }

    public String displayName() {
        return displayName;
    }

    /** Item que mostra a recarga como animação, ou null. */
    public SuporteItem item() {
        return item;
    }
}
