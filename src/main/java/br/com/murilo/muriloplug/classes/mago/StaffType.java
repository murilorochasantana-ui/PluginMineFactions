package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** Os tipos de staff do Mago. O id é o valor guardado no PersistentDataContainer do item. */
public enum StaffType {

    FOGO("fogo", "§6Staff de Fogo", true,
            "§7Lança uma bola de fogo que explode",
            "§7em área, queima e cega."),
    GELO("gelo", "§bStaff de Gelo", true,
            "§7Lança gelo que explode em área,",
            "§7deixando os alvos lentos e cansados."),
    RAIO("raio", "§eStaff de Raio", true,
            "§7Raio na mira que pula em cadeia",
            "§7entre até 3 alvos próximos."),
    TELEPORTE("teleporte", "§5Staff de Teleporte", false,
            "§7Teleporta na direção da mira.",
            "§7SHIFT + clique direito (segure 3s): leva",
            "§7até 2 jogadores próximos junto."),
    VENTO("vento", "§fStaff de Vento", true,
            "§7Empurra para longe tudo ao redor."),
    VENENO("veneno", "§2Staff de Veneno", false,
            "§7Lança um projétil que cria uma nuvem",
            "§7de veneno onde acerta."),
    METEORO("meteoro", "§cStaff de Meteoro", true,
            "§7Marca o chão na mira e, após um aviso,",
            "§7faz cair um meteoro em área e lento."),
    SANGUE("sangue", "§4Staff de Sangue", true,
            "§7Fere o alvo na mira e cura o Mago",
            "§7com metade do dano causado."),
    GRAVIDADE("gravidade", "§8Staff de Gravidade", true,
            "§7Puxa para perto do Mago os inimigos",
            "§7ao redor, ferindo-os."),
    TEMPO("tempo", "§9Staff do Tempo", false,
            "§7Cria uma área no chão da mira onde",
            "§7os inimigos ficam lentos e sem pular."),
    ESCUDO("escudo", "§eStaff de Escudo", false,
            "§7Dá ao Mago corações de absorção",
            "§7por alguns segundos."),
    LUZ("luz", "§fStaff de Luz", false,
            "§7Clarão em volta do Mago: cega os inimigos",
            "§7e revela quem está invisível."),
    INVOCACAO("invocacao", "§aStaff de Invocação", true,
            "§7Invoca espíritos que atacam o inimigo",
            "§7mais próximo por alguns segundos."),
    ILUSAO("ilusao", "§dStaff de Ilusão", false,
            "§7Fica invisível para os inimigos e deixa",
            "§7cópias falsas no lugar onde estava.");

    /** Material das staffs. As lanças só existem em versões mais novas do jogo que a 1.21.4. */
    public static final Material MATERIAL = Material.BLAZE_ROD;

    private final String id;
    private final String displayName;
    private final boolean dealsDamage;
    private final List<String> description;

    StaffType(String id, String displayName, boolean dealsDamage, String... description) {
        this.id = id;
        this.displayName = displayName;
        this.dealsDamage = dealsDamage;
        this.description = List.of(description);
    }

    public String id() {
        return id;
    }

    /** Nome com cor, como aparece no item. */
    public String displayName() {
        return displayName;
    }

    /** Nome da magia sem cor ("Fogo"). */
    public String spellName() {
        return displayName.substring(2).replace("Staff de ", "").replace("Staff do ", "");
    }

    /** Nome da magia com a cor da staff ("§6Fogo"). */
    public String coloredSpellName() {
        return displayName.substring(0, 2) + spellName();
    }

    public boolean dealsDamage() {
        return dealsDamage;
    }

    public List<String> description() {
        return description;
    }

    public static StaffType byId(String id) {
        if (id == null) {
            return null;
        }
        for (StaffType type : values()) {
            if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }
}
