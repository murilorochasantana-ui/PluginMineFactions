package br.com.murilo.muriloplug;

import br.com.murilo.muriloplug.auth.AuthCommand;
import br.com.murilo.muriloplug.auth.AuthConnectionListener;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.auth.AuthRestrictionsListener;
import br.com.murilo.muriloplug.auth.CommandPasswordFilter;
import br.com.murilo.muriloplug.claim.ClaimCommand;
import br.com.murilo.muriloplug.claim.ClaimListener;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.claim.ClaimPreviewManager;
import br.com.murilo.muriloplug.claim.ClaimsMenuListener;
import br.com.murilo.muriloplug.claim.MyClaimsCommand;
import br.com.murilo.muriloplug.clan.ClanChatListener;
import br.com.murilo.muriloplug.clan.ClanCommand;
import br.com.murilo.muriloplug.clan.ClanFriendlyFireListener;
import br.com.murilo.muriloplug.clan.ClanInvites;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.clan.ClanMenusListener;
import br.com.murilo.muriloplug.classes.ClassCommand;
import br.com.murilo.muriloplug.classes.ClassItems;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.ClassMenusListener;
import br.com.murilo.muriloplug.classes.KitCommand;
import br.com.murilo.muriloplug.classes.KitMenusListener;
import br.com.murilo.muriloplug.classes.ClassBarManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.teste.BonecoCommand;
import br.com.murilo.muriloplug.teste.DummyListener;
import br.com.murilo.muriloplug.teste.DummyManager;
import br.com.murilo.muriloplug.teste.DummySettings;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import br.com.murilo.muriloplug.teste.TargetRelations;
import br.com.murilo.muriloplug.xp.XpListener;
import br.com.murilo.muriloplug.xp.XpSettings;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherBowListener;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherSpeedManager;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherWeaknessListener;
import br.com.murilo.muriloplug.classes.arqueiro.ArqueiroSettings;
import br.com.murilo.muriloplug.classes.arqueiro.ArrowItems;
import br.com.murilo.muriloplug.classes.arqueiro.ExplosiveArrowListener;
import br.com.murilo.muriloplug.classes.arqueiro.ShotModes;
import br.com.murilo.muriloplug.classes.combatente.CombatenteHealth;
import br.com.murilo.muriloplug.classes.combatente.CombatenteAbilities;
import br.com.murilo.muriloplug.classes.combatente.CombatenteListener;
import br.com.murilo.muriloplug.classes.combatente.CombatenteSettings;
import br.com.murilo.muriloplug.classes.combatente.FuryManager;
import br.com.murilo.muriloplug.classes.engenheiro.BuildListener;
import br.com.murilo.muriloplug.classes.engenheiro.BuildManager;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroAcoes;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroArmas;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroEnergia;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroMenusListener;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroPassivas;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroReparo;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroSidebar;
import br.com.murilo.muriloplug.classes.engenheiro.GanchoManager;
import br.com.murilo.muriloplug.classes.engenheiro.PlantaItems;
import br.com.murilo.muriloplug.classes.engenheiro.SentinelaItems;
import br.com.murilo.muriloplug.classes.engenheiro.SentinelaManager;
import br.com.murilo.muriloplug.classes.engenheiro.PlantaManager;
import br.com.murilo.muriloplug.classes.engenheiro.PlantaStore;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroSettings;
import br.com.murilo.muriloplug.classes.engenheiro.PlacementManager;
import br.com.murilo.muriloplug.classes.engenheiro.StructureBooks;
import br.com.murilo.muriloplug.classes.engenheiro.StructureStore;
import br.com.murilo.muriloplug.classes.mago.ArcanePower;
import br.com.murilo.muriloplug.classes.mago.GrimoireCommand;
import br.com.murilo.muriloplug.classes.mago.GrimoireItems;
import br.com.murilo.muriloplug.classes.mago.GrimoireManager;
import br.com.murilo.muriloplug.classes.mago.GrimoireMenusListener;
import br.com.murilo.muriloplug.classes.mago.GrimoireRestrictionsListener;
import br.com.murilo.muriloplug.classes.mago.MagoSettings;
import br.com.murilo.muriloplug.classes.mago.MagoWeaponListener;
import br.com.murilo.muriloplug.classes.mago.ManaShieldListener;
import br.com.murilo.muriloplug.classes.mago.MagoWeaknessListener;
import br.com.murilo.muriloplug.classes.mago.ManaManager;
import br.com.murilo.muriloplug.classes.mago.SpellTargets;
import br.com.murilo.muriloplug.classes.mago.StaffItems;
import br.com.murilo.muriloplug.classes.mago.StaffListener;
import br.com.murilo.muriloplug.classes.mago.StaffService;
import br.com.murilo.muriloplug.classes.suporte.FlightManager;
import br.com.murilo.muriloplug.classes.suporte.LinkManager;
import br.com.murilo.muriloplug.classes.suporte.NightVisionManager;
import br.com.murilo.muriloplug.classes.suporte.RootManager;
import br.com.murilo.muriloplug.classes.suporte.SuporteAssists;
import br.com.murilo.muriloplug.classes.suporte.SuporteCooldowns;
import br.com.murilo.muriloplug.classes.suporte.SuporteHealth;
import br.com.murilo.muriloplug.classes.suporte.SuporteItems;
import br.com.murilo.muriloplug.classes.suporte.SuporteListener;
import br.com.murilo.muriloplug.classes.suporte.SuporteSettings;
import br.com.murilo.muriloplug.classes.suporte.SuporteTargets;
import br.com.murilo.muriloplug.combat.CombatCommandListener;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.economy.MoneyCommand;
import br.com.murilo.muriloplug.economy.RewardListener;
import br.com.murilo.muriloplug.home.DelHomeCommand;
import br.com.murilo.muriloplug.home.DeleteMenusListener;
import br.com.murilo.muriloplug.home.HomeCommand;
import br.com.murilo.muriloplug.home.HomeManager;
import br.com.murilo.muriloplug.home.HomeTeleportManager;
import br.com.murilo.muriloplug.home.HomesMenuListener;
import br.com.murilo.muriloplug.home.SetHomeCommand;
import br.com.murilo.muriloplug.pvp.AttackSpeedManager;
import br.com.murilo.muriloplug.pvp.AxeDamageListener;
import br.com.murilo.muriloplug.pvp.PvpSettings;
import br.com.murilo.muriloplug.pvp.ShieldListener;
import br.com.murilo.muriloplug.pvp.SweepAttackListener;
import br.com.murilo.muriloplug.scoreboard.CombatDisplayTask;
import br.com.murilo.muriloplug.scoreboard.NametagManager;
import br.com.murilo.muriloplug.scoreboard.PlayerScoreboards;
import br.com.murilo.muriloplug.scoreboard.ScoreboardListener;
import br.com.murilo.muriloplug.scoreboard.SidebarManager;
import br.com.murilo.muriloplug.market.MarketManager;
import br.com.murilo.muriloplug.market.MarketMenusListener;
import br.com.murilo.muriloplug.market.MercadoCommand;
import br.com.murilo.muriloplug.shop.LojaCommand;
import br.com.murilo.muriloplug.shop.ShopManager;
import br.com.murilo.muriloplug.shop.ShopMenusListener;
import br.com.murilo.muriloplug.stats.DamageTracker;
import br.com.murilo.muriloplug.stats.KdaCommand;
import br.com.murilo.muriloplug.stats.StatsListener;
import br.com.murilo.muriloplug.stats.StatsManager;
import br.com.murilo.muriloplug.teleport.TeleportCountdown;
import br.com.murilo.muriloplug.tpa.TpaCommand;
import br.com.murilo.muriloplug.tpa.TpaManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class MinePlug extends JavaPlugin {

    private ClaimManager claimManager;
    private ClaimPreviewManager previewManager;
    private HomeManager homeManager;
    private TeleportCountdown teleportCountdown;
    private TpaManager tpaManager;
    private BukkitTask combatDisplayTask;
    private StatsManager statsManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private MarketManager marketManager;
    private ClanManager clanManager;
    private AttackSpeedManager attackSpeedManager;
    private AuthManager authManager;
    private ClassManager classManager;
    private ManaManager manaManager;
    private StaffService staffService;
    private DummyManager dummyManager;
    private ArcherSpeedManager archerSpeed;
    private ArcherBowListener archerBow;
    private CombatenteHealth combatenteHealth;
    private FuryManager furyManager;
    private BuildManager buildManager;
    private PlacementManager placementManager;
    private EngenheiroPassivas engenheiroPassivas;
    private SuporteHealth suporteHealth;
    private FlightManager flightManager;
    private NightVisionManager nightVision;

    @Override
    public void onEnable() {
        setupConfig();

        // Login primeiro: esconde as senhas do log e registra as restrições antes dos outros sistemas.
        if (!CommandPasswordFilter.install()) {
            getLogger().severe("NÃO foi possível instalar o filtro de senhas do log: as senhas dos comandos "
                    + "/login e /register podem aparecer no console e no latest.log!");
        }
        authManager = new AuthManager(this);
        authManager.load();
        getServer().getPluginManager().registerEvents(new AuthConnectionListener(authManager), this);
        getServer().getPluginManager().registerEvents(new AuthRestrictionsListener(authManager), this);
        AuthCommand authCommand = new AuthCommand(authManager);
        for (String name : new String[]{"register", "login", "trocarsenha", "auth"}) {
            getCommand(name).setExecutor(authCommand);
            getCommand(name).setTabCompleter(authCommand);
        }
        // Quem já estava online (ex: /reload) precisa fazer login de novo.
        getServer().getOnlinePlayers().forEach(authManager::handleJoin);

        claimManager = new ClaimManager(this);
        claimManager.load();
        previewManager = new ClaimPreviewManager(this);

        ClaimCommand claimCommand = new ClaimCommand(claimManager, previewManager);
        getCommand("claim").setExecutor(claimCommand);
        getCommand("claim").setTabCompleter(claimCommand);
        getServer().getPluginManager().registerEvents(new ClaimListener(claimManager), this);
        getServer().getPluginManager().registerEvents(previewManager, this);

        getCommand("meuclaim").setExecutor(new MyClaimsCommand(claimManager));
        getServer().getPluginManager().registerEvents(new ClaimsMenuListener(), this);

        CombatManager combatManager = new CombatManager();
        getServer().getPluginManager().registerEvents(combatManager, this);
        getServer().getPluginManager().registerEvents(new CombatCommandListener(this, combatManager), this);

        homeManager = new HomeManager(this);
        homeManager.load();
        teleportCountdown = new TeleportCountdown(this);
        getServer().getPluginManager().registerEvents(teleportCountdown, this);
        HomeTeleportManager homeTeleportManager = new HomeTeleportManager(teleportCountdown, claimManager, combatManager);

        HomeCommand homeCommand = new HomeCommand(homeManager, homeTeleportManager, claimManager);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
        getCommand("sethome").setExecutor(new SetHomeCommand(homeManager, claimManager));
        DelHomeCommand delHomeCommand = new DelHomeCommand(homeManager);
        getCommand("delhome").setExecutor(delHomeCommand);
        getCommand("delhome").setTabCompleter(delHomeCommand);
        getServer().getPluginManager().registerEvents(new HomesMenuListener(this, homeManager, homeTeleportManager), this);
        getServer().getPluginManager().registerEvents(new DeleteMenusListener(this, homeManager), this);

        statsManager = new StatsManager(this);
        statsManager.load();
        DamageTracker damageTracker = new DamageTracker();
        getServer().getPluginManager().registerEvents(new StatsListener(statsManager, damageTracker), this);
        KdaCommand kdaCommand = new KdaCommand(statsManager);
        getCommand("kda").setExecutor(kdaCommand);
        getCommand("kda").setTabCompleter(kdaCommand);
        // Salva os stats e limpa os registros de dano vencidos a cada 5 minutos.
        getServer().getScheduler().runTaskTimer(this, () -> {
            statsManager.save();
            damageTracker.purge();
        }, 5 * 60 * 20L, 5 * 60 * 20L);

        tpaManager = new TpaManager(this, combatManager, teleportCountdown);
        tpaManager.load();
        TpaCommand tpaCommand = new TpaCommand(tpaManager);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpa").setTabCompleter(tpaCommand);
        getCommand("tpaqui").setExecutor(tpaCommand);
        getCommand("tpaqui").setTabCompleter(tpaCommand);
        getServer().getPluginManager().registerEvents(tpaManager, this);

        economyManager = new EconomyManager(this);
        economyManager.load();
        getServer().getPluginManager().registerEvents(new RewardListener(this, economyManager), this);
        MoneyCommand moneyCommand = new MoneyCommand(economyManager);
        getCommand("money").setExecutor(moneyCommand);
        getCommand("money").setTabCompleter(moneyCommand);
        // Segurança extra: o saldo também é regravado a cada 5 minutos (e a cada mudança, em segundo plano).
        getServer().getScheduler().runTaskTimer(this, economyManager::requestSave, 5 * 60 * 20L, 5 * 60 * 20L);

        shopManager = new ShopManager(this);
        shopManager.load();
        getServer().getPluginManager().registerEvents(new ShopMenusListener(this, shopManager, economyManager), this);
        LojaCommand lojaCommand = new LojaCommand(shopManager, economyManager);
        getCommand("loja").setExecutor(lojaCommand);
        getCommand("loja").setTabCompleter(lojaCommand);

        marketManager = new MarketManager(this);
        marketManager.load();
        getServer().getPluginManager().registerEvents(new MarketMenusListener(this, marketManager, economyManager), this);
        getCommand("mercado").setExecutor(new MercadoCommand(marketManager, economyManager));

        classManager = new ClassManager(this);
        classManager.load();
        getServer().getPluginManager().registerEvents(new ClassMenusListener(this, classManager, combatManager), this);

        clanManager = new ClanManager(this);
        clanManager.load();
        ClanInvites clanInvites = new ClanInvites(this, clanManager);
        getServer().getPluginManager().registerEvents(clanInvites, this);
        getServer().getPluginManager().registerEvents(new ClanMenusListener(this, clanManager), this);
        ClanCommand clanCommand = new ClanCommand(clanManager, clanInvites, statsManager, classManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("c").setExecutor(clanCommand);
        claimManager.setClanManager(clanManager);
        getServer().getPluginManager().registerEvents(new ClanChatListener(clanManager), this);
        TargetRelations targetRelations = new TargetRelations(this, clanManager);
        StatusRegistry statusRegistry = new StatusRegistry();
        getServer().getPluginManager().registerEvents(new ClanFriendlyFireListener(clanManager, targetRelations), this);

        MagoSettings magoSettings = new MagoSettings(this);
        manaManager = new ManaManager(this, classManager, magoSettings, combatManager);
        getServer().getPluginManager().registerEvents(manaManager, this);
        manaManager.start();
        manaManager.initOnline();
        ArcanePower arcanePower = new ArcanePower(this, manaManager, magoSettings);
        getServer().getPluginManager().registerEvents(arcanePower, this);
        arcanePower.start();
        StaffItems staffItems = new StaffItems(this, magoSettings);
        SpellTargets spellTargets = new SpellTargets(authManager, clanManager, arcanePower, targetRelations);
        GrimoireItems grimoireItems = new GrimoireItems(this);
        GrimoireManager grimoireManager = new GrimoireManager(this, magoSettings, classManager, grimoireItems);
        grimoireManager.load();
        getServer().getPluginManager().registerEvents(grimoireManager, this);
        getServer().getPluginManager().registerEvents(new GrimoireRestrictionsListener(grimoireItems), this);
        getServer().getPluginManager().registerEvents(
                new GrimoireMenusListener(this, grimoireManager, staffItems, manaManager, combatManager, magoSettings), this);
        getCommand("grimorio").setExecutor(new GrimoireCommand(manaManager, grimoireManager, magoSettings));
        staffService = new StaffService(this, manaManager, authManager, staffItems, magoSettings,
                spellTargets, arcanePower, grimoireItems, grimoireManager, statusRegistry);
        getServer().getPluginManager().registerEvents(new MagoWeaponListener(classManager, magoSettings, arcanePower), this);
        getServer().getPluginManager().registerEvents(new StaffListener(staffService, authManager), this);
        getServer().getPluginManager().registerEvents(new MagoWeaknessListener(classManager, magoSettings), this);

        ArqueiroSettings arqueiroSettings = new ArqueiroSettings(this);
        ArrowItems arrowItems = new ArrowItems(this);
        ShotModes shotModes = new ShotModes();
        getServer().getPluginManager().registerEvents(shotModes, this);
        archerBow = new ArcherBowListener(this, classManager, arqueiroSettings, shotModes, arrowItems);
        getServer().getPluginManager().registerEvents(archerBow, this);
        getServer().getPluginManager().registerEvents(new ExplosiveArrowListener(this, arqueiroSettings, arrowItems, targetRelations), this);
        getServer().getPluginManager().registerEvents(new ArcherWeaknessListener(classManager, arqueiroSettings), this);
        archerSpeed = new ArcherSpeedManager(this, classManager, arqueiroSettings);
        getServer().getPluginManager().registerEvents(archerSpeed, this);
        archerSpeed.start();
        CombatenteSettings combatenteSettings = new CombatenteSettings(this);
        combatenteHealth = new CombatenteHealth(this, classManager, combatenteSettings);
        getServer().getPluginManager().registerEvents(combatenteHealth, this);
        combatenteHealth.applyOnline();
        furyManager = new FuryManager(this, classManager, combatenteSettings, authManager, combatManager);
        getServer().getPluginManager().registerEvents(furyManager, this);
        furyManager.start();
        getServer().getPluginManager().registerEvents(new CombatenteAbilities(this, classManager, combatenteSettings,
                furyManager, targetRelations, authManager, statusRegistry), this);
        EngenheiroSettings engenheiroSettings = new EngenheiroSettings(this);
        StructureBooks structureBooks = new StructureBooks(this, engenheiroSettings);
        StructureStore structureStore = new StructureStore(this);
        getLogger().info("Estruturas salvas em estruturas/: " + structureStore.count());
        buildManager = new BuildManager(this, classManager, claimManager, combatManager, engenheiroSettings,
                structureBooks, structureStore);
        EngenheiroEnergia engenheiroEnergia = new EngenheiroEnergia(this, classManager, engenheiroSettings);
        getServer().getPluginManager().registerEvents(engenheiroEnergia, this);
        engenheiroEnergia.start();
        EngenheiroAcoes engenheiroAcoes = new EngenheiroAcoes(classManager, engenheiroSettings);
        placementManager = new PlacementManager(this, buildManager, claimManager, combatManager, engenheiroSettings,
                structureBooks, structureStore, engenheiroAcoes);
        getServer().getPluginManager().registerEvents(placementManager, this);
        placementManager.start();
        buildManager.restoreLeftovers();
        buildManager.start();
        getServer().getPluginManager().registerEvents(
                new BuildListener(buildManager, structureBooks, engenheiroSettings, authManager, placementManager), this);
        PlantaItems plantaItems = new PlantaItems(this, engenheiroSettings);
        PlantaManager plantaManager = new PlantaManager(this, buildManager, engenheiroSettings, structureBooks,
                structureStore, plantaItems, new PlantaStore(this), engenheiroAcoes, authManager);
        buildManager.setPlantas(plantaManager);
        getServer().getPluginManager().registerEvents(plantaManager, this);
        getServer().getPluginManager().registerEvents(
                new EngenheiroMenusListener(this, buildManager, placementManager, plantaManager), this);
        engenheiroPassivas = new EngenheiroPassivas(this, classManager, engenheiroSettings);
        getServer().getPluginManager().registerEvents(engenheiroPassivas, this);
        engenheiroPassivas.start();
        getServer().getPluginManager().registerEvents(new EngenheiroReparo(this, classManager, engenheiroSettings,
                engenheiroEnergia, engenheiroAcoes, authManager), this);
        GanchoManager ganchoManager = new GanchoManager(this, classManager, engenheiroSettings, arrowItems, engenheiroAcoes);
        getServer().getPluginManager().registerEvents(ganchoManager, this);
        SentinelaItems sentinelaItems = new SentinelaItems(this, engenheiroSettings);
        SentinelaManager sentinelaManager = new SentinelaManager(this, classManager, claimManager, authManager,
                targetRelations, engenheiroSettings, sentinelaItems, engenheiroAcoes);
        sentinelaManager.load();
        getServer().getPluginManager().registerEvents(sentinelaManager, this);
        sentinelaManager.start();
        SuporteSettings suporteSettings = new SuporteSettings(this);
        SuporteItems suporteItems = new SuporteItems(this, suporteSettings);
        int oldScrolls = shopManager.migrate(suporteItems::upgradeLegacy) + marketManager.migrate(suporteItems::upgradeLegacy);
        if (oldScrolls > 0) {
            getLogger().info("Pergaminhos antigos trocados na loja e no mercado: " + oldScrolls);
        }
        SuporteCooldowns suporteCooldowns = new SuporteCooldowns();
        SuporteTargets suporteTargets = new SuporteTargets(authManager, targetRelations);
        SuporteAssists suporteAssists = new SuporteAssists();
        statsManager.setExtraAssistants(suporteAssists::supportersOf);
        suporteHealth = new SuporteHealth(this, classManager, suporteSettings);
        getServer().getPluginManager().registerEvents(suporteHealth, this);
        suporteHealth.applyOnline();
        LinkManager linkManager = new LinkManager(this, classManager, clanManager, authManager, suporteSettings,
                suporteTargets, suporteAssists, statusRegistry);
        getServer().getPluginManager().registerEvents(linkManager, this);
        linkManager.start();
        RootManager rootManager = new RootManager(this, suporteSettings, suporteTargets, combatManager, damageTracker, statusRegistry);
        getServer().getPluginManager().registerEvents(rootManager, this);
        rootManager.start();
        flightManager = new FlightManager(this, classManager, suporteSettings, suporteCooldowns);
        getServer().getPluginManager().registerEvents(flightManager, this);
        flightManager.start();
        nightVision = new NightVisionManager(this, classManager, authManager);
        getServer().getPluginManager().registerEvents(nightVision, this);
        getServer().getPluginManager().registerEvents(new SuporteListener(classManager, authManager, suporteItems,
                suporteSettings, suporteCooldowns, suporteTargets, suporteAssists, linkManager, rootManager,
                flightManager), this);
        ClassItems classItems = new ClassItems(staffItems, grimoireItems, arrowItems, structureBooks, plantaItems, sentinelaItems, suporteItems);
        ClassCommand classCommand = new ClassCommand(classManager, combatManager, classItems, manaManager);
        getCommand("classe").setExecutor(classCommand);
        getCommand("classe").setTabCompleter(classCommand);
        KitCommand kitCommand = new KitCommand(classItems);
        getCommand("kit").setExecutor(kitCommand);
        getCommand("kit").setTabCompleter(kitCommand);
        getServer().getPluginManager().registerEvents(new KitMenusListener(classItems), this);

        PvpSettings pvp = PvpSettings.from(getConfig());
        attackSpeedManager = new AttackSpeedManager(this, pvp.noAttackCooldown());
        getServer().getPluginManager().registerEvents(attackSpeedManager, this);
        attackSpeedManager.applyToOnlinePlayers();
        if (pvp.removeSweep()) {
            getServer().getPluginManager().registerEvents(new SweepAttackListener(), this);
        }
        if (pvp.axeDamage18()) {
            getServer().getPluginManager().registerEvents(new AxeDamageListener(), this);
        }
        if (pvp.disableShield()) {
            getServer().getPluginManager().registerEvents(new ShieldListener(), this);
        }

        // Depois do PvP 1.8: o HIGH do Combatente roda depois do ajuste do machado.
        getServer().getPluginManager().registerEvents(new CombatenteListener(classManager, combatenteSettings, furyManager), this);
        getServer().getPluginManager().registerEvents(new EngenheiroArmas(classManager, engenheiroSettings), this);
        // Por último entre os listeners de dano em HIGHEST: o Escudo de Mana mede o dano final já ajustado pelos outros.
        getServer().getPluginManager().registerEvents(new ManaShieldListener(manaManager, magoSettings), this);

        PlayerScoreboards scoreboards = new PlayerScoreboards();
        NametagManager nametagManager = new NametagManager(scoreboards, combatManager, clanManager);
        SidebarManager sidebarManager = new SidebarManager(scoreboards, combatManager, statsManager, economyManager, clanManager, classManager, manaManager, shotModes, furyManager, magoSettings,
                new EngenheiroSidebar(classManager, engenheiroEnergia, engenheiroSettings, ganchoManager, sentinelaManager));
        shotModes.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
            }
        });
        XpSettings xpSettings = new XpSettings(this);
        XpListener xpListener = new XpListener(xpSettings);
        getServer().getPluginManager().registerEvents(xpListener, this);
        getServer().getOnlinePlayers().forEach(xpListener::clear);
        getServer().getWorlds().forEach(xpListener::disableLocatorBar);
        // A barra de XP das classes só existe com o XP desligado: com XP real o jogo a sobrescreveria.
        ClassBarManager classBars = xpSettings.disabled() ? new ClassBarManager(this, classManager) : null;
        if (classBars != null) {
            classBars.register(PlayerClass.MAGO, player -> {
                int max = Math.max(1, manaManager.max(player.getUniqueId()));
                int current = manaManager.get(player.getUniqueId());
                return new ClassBarManager.Bar(current, (float) current / max);
            });
            getServer().getPluginManager().registerEvents(classBars, this);
            classBars.start();
        }
        if (classBars != null) {
            classBars.register(PlayerClass.COMBATENTE, player -> {
                double current = furyManager.fury(player.getUniqueId());
                return new ClassBarManager.Bar((int) Math.floor(current), (float) (current / combatenteSettings.furyMax()));
            });
        }
        if (classBars != null) {
            classBars.register(PlayerClass.ENGENHEIRO, player -> {
                double current = engenheiroEnergia.getEnergia(player.getUniqueId());
                return new ClassBarManager.Bar((int) Math.floor(current), (float) (current / engenheiroEnergia.maxEnergia()));
            });
        }
        engenheiroEnergia.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
                if (classBars != null) {
                    classBars.update(player);
                }
            }
        });
        furyManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
                if (classBars != null) {
                    classBars.update(player);
                }
            }
        });
        manaManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
                if (classBars != null) {
                    classBars.update(player);
                }
            }
        });
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onClassChange(PlayerClassChangeEvent event) {
                Player player = event.getPlayer();
                if (player != null) {
                    sidebarManager.update(player);
                }
            }
        }, this);
        combatManager.setEnterListener(player -> {
            nametagManager.update(player);
            sidebarManager.update(player);
        });
        statsManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
            }
        });
        clanManager.setMembershipListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                nametagManager.update(player);
                sidebarManager.update(player);
            }
        });
        economyManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
            }
        });
        ScoreboardListener scoreboardListener = new ScoreboardListener(scoreboards, sidebarManager, nametagManager);
        getServer().getPluginManager().registerEvents(scoreboardListener, this);
        scoreboardListener.setupOnlinePlayers();
        combatDisplayTask = new CombatDisplayTask(combatManager, nametagManager, sidebarManager)
                .runTaskTimer(this, 20L, 20L);

        // Bonecos de teste. O listener vai por último: ele cancela o dano letal em MONITOR, depois dos outros sistemas.
        DummySettings dummySettings = new DummySettings(this);
        dummyManager = new DummyManager(this, dummySettings, targetRelations, statusRegistry);
        BonecoCommand bonecoCommand = new BonecoCommand(dummyManager, dummySettings);
        getCommand("boneco").setExecutor(bonecoCommand);
        getCommand("boneco").setTabCompleter(bonecoCommand);
        getServer().getPluginManager().registerEvents(new DummyListener(this, dummyManager, dummySettings, combatManager,
                furyManager::isAreaDamage), this);
        dummyManager.start();
    }

    @Override
    public void onDisable() {
        if (dummyManager != null) {
            dummyManager.shutdown();
        }
        if (staffService != null) {
            staffService.shutdown();
        }
        if (combatDisplayTask != null) {
            combatDisplayTask.cancel();
        }
        if (placementManager != null) {
            placementManager.finishAll();
            placementManager.clearAll();
        }
        if (engenheiroPassivas != null) {
            engenheiroPassivas.removeAll();
        }
        if (buildManager != null) {
            buildManager.cancelAll();
        }
        if (flightManager != null) {
            flightManager.endAll();
        }
        if (nightVision != null) {
            nightVision.removeAll();
        }
        if (suporteHealth != null) {
            suporteHealth.removeAll();
        }
        if (furyManager != null) {
            furyManager.endAll();
        }
        if (combatenteHealth != null) {
            combatenteHealth.removeAll();
        }
        if (archerSpeed != null) {
            archerSpeed.removeAll();
        }
        if (archerBow != null) {
            archerBow.cancelAll();
        }
        if (attackSpeedManager != null) {
            attackSpeedManager.restoreOnlinePlayers();
        }
        if (authManager != null) {
            authManager.shutdown();
            authManager.save();
        }
        CommandPasswordFilter.uninstall();
        if (previewManager != null) {
            previewManager.clearAll();
        }
        if (claimManager != null) {
            claimManager.save();
        }
        if (teleportCountdown != null) {
            teleportCountdown.cancelAll();
        }
        if (tpaManager != null) {
            tpaManager.save();
        }
        if (homeManager != null) {
            homeManager.save();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        if (shopManager != null) {
            shopManager.save();
        }
        if (marketManager != null) {
            marketManager.save();
        }
        if (clanManager != null) {
            clanManager.save();
        }
        if (classManager != null) {
            classManager.save();
        }
        if (economyManager != null) {
            economyManager.close();
        }
    }

    /** Define a chave com o valor padrão se ela ainda não existir. Retorna true se alterou o config. */
    private static boolean addMissing(FileConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    /** Cria o config.yml se não existir e acrescenta as chaves que faltarem, sem apagar o que já está lá. */
    private void setupConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        boolean changed = false;
        if (!config.contains(RewardListener.KILL_PATH)) {
            config.set(RewardListener.KILL_PATH, RewardListener.DEFAULT_KILL_REWARD);
            changed = true;
        }
        if (!config.contains(RewardListener.ASSIST_PATH)) {
            config.set(RewardListener.ASSIST_PATH, RewardListener.DEFAULT_ASSIST_REWARD);
            changed = true;
        }
        if (!config.contains(ClanManager.MAX_MEMBERS_PATH)) {
            config.set(ClanManager.MAX_MEMBERS_PATH, ClanManager.DEFAULT_MAX_MEMBERS);
            changed = true;
        }
        changed |= addMissing(config, PvpSettings.NO_COOLDOWN_PATH, true);
        changed |= addMissing(config, PvpSettings.NO_SWEEP_PATH, true);
        changed |= addMissing(config, PvpSettings.AXE_PATH, true);
        changed |= addMissing(config, PvpSettings.NO_SHIELD_PATH, false);
        changed |= addMissing(config, AuthManager.TIME_PATH, 60);
        changed |= addMissing(config, AuthManager.ATTEMPTS_PATH, 5);
        changed |= addMissing(config, AuthManager.SESSION_ENABLED_PATH, false);
        changed |= addMissing(config, AuthManager.SESSION_MINUTES_PATH, 10);
        changed |= addMissing(config, CombatCommandListener.PATH, java.util.List.of("grimorio"));
        changed |= XpSettings.addMissing(config);
        changed |= DummySettings.addMissing(config);
        changed |= MagoSettings.addMissing(config);
        changed |= ArqueiroSettings.addMissing(config);
        changed |= CombatenteSettings.addMissing(config);
        changed |= EngenheiroSettings.addMissing(config);
        changed |= SuporteSettings.addMissing(config);
        if (changed) {
            saveConfig();
        }
    }
}
