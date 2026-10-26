package br.com.murilo.muriloplug.maquinas;

import org.bukkit.Material;

/** Situação da máquina (holograma, menus): só PRODUZINDO faz o ciclo andar. */
public enum MaquinaStatus {
    PRODUZINDO("§aProduzindo", Material.LIME_DYE),
    SEM_COMBUSTIVEL("§cSem combustível", Material.RED_DYE),
    ARMAZEM_CHEIO("§cArmazém cheio", Material.RED_DYE),
    COFRE_SEM_DINHEIRO("§cCofre sem dinheiro", Material.RED_DYE),
    CLAN_OFFLINE("§eClan offline", Material.YELLOW_DYE),
    NINGUEM_PERTO("§eNinguém por perto", Material.YELLOW_DYE),
    DESATIVADA("§8Desativada", Material.GRAY_DYE);

    private final String texto;
    private final Material icone;

    MaquinaStatus(String texto, Material icone) {
        this.texto = texto;
        this.icone = icone;
    }

    /** Texto colorido ("§aProduzindo"). */
    public String texto() {
        return texto;
    }

    public Material icone() {
        return icone;
    }
}
