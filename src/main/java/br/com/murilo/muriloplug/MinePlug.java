package br.com.murilo.muriloplug;

import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.admin.ResetarContaCommand;
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
import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.teste.BonecoCommand;
import br.com.murilo.muriloplug.teste.DummyListener;
import br.com.murilo.muriloplug.teste.DummyManager;
import br.com.murilo.muriloplug.teste.DummySettings;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import br.com.murilo.muriloplug.teste.TargetRelations;
import br.com.murilo.muriloplug.xp.XpListener;
import br.com.murilo.muriloplug.xp.XpSettings;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherBowListener;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherDamageListener;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherMobility;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherSkills;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherSpeedCleanup;
import br.com.murilo.muriloplug.classes.arqueiro.ArcherTargets;
import br.com.murilo.muriloplug.classes.arqueiro.ArqueiroSidebar;
import br.com.murilo.muriloplug.classes.arqueiro.FocusManager;
import br.com.murilo.muriloplug.classes.arqueiro.HunterMark;
import br.com.murilo.muriloplug.classes.arqueiro.ShotRegistry;
import br.com.murilo.muriloplug.classes.arqueiro.SpecialArrowListener;
import br.com.murilo.muriloplug.classes.GrapplePull;
import br.com.murilo.muriloplug.classes.arqueiro.UltraFocus;
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
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroSidebar;
import br.com.murilo.muriloplug.classes.engenheiro.GanchoManager;
import br.com.murilo.muriloplug.classes.engenheiro.GolemItems;
import br.com.murilo.muriloplug.classes.engenheiro.GolemManager;
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
import br.com.murilo.muriloplug.classes.suporte.EloDrenante;
import br.com.murilo.muriloplug.classes.suporte.EscudoSagrado;
import br.com.murilo.muriloplug.classes.suporte.FlightManager;
import br.com.murilo.muriloplug.classes.suporte.HarmoniaManager;
import br.com.murilo.muriloplug.classes.suporte.LinkManager;
import br.com.murilo.muriloplug.classes.suporte.Maldicao;
import br.com.murilo.muriloplug.classes.suporte.NightVisionManager;
import br.com.murilo.muriloplug.classes.suporte.Purificar;
import br.com.murilo.muriloplug.classes.suporte.RootManager;
import br.com.murilo.muriloplug.classes.suporte.Silencio;
import br.com.murilo.muriloplug.classes.suporte.Socorrista;
import br.com.murilo.muriloplug.classes.suporte.SuporteArmas;
import br.com.murilo.muriloplug.classes.suporte.SuporteAssists;
import br.com.murilo.muriloplug.classes.suporte.SuporteCooldowns;
import br.com.murilo.muriloplug.classes.suporte.SuporteHealth;
import br.com.murilo.muriloplug.classes.suporte.SuporteItems;
import br.com.murilo.muriloplug.classes.suporte.SuporteListener;
import br.com.murilo.muriloplug.classes.suporte.SuporteSettings;
import br.com.murilo.muriloplug.classes.suporte.SuporteSidebar;
import br.com.murilo.muriloplug.classes.suporte.SuporteTargets;
import br.com.murilo.muriloplug.classes.suporte.TrocaEquivalente;
import br.com.murilo.muriloplug.classes.suporte.TotemCura;
import br.com.murilo.muriloplug.classes.suporte.TrocaLugar;
import br.com.murilo.muriloplug.combat.CombatCommandListener;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.economy.MoneyCommand;
import br.com.murilo.muriloplug.economy.RewardListener;
import br.com.murilo.muriloplug.economy.maco.Macos;
import br.com.murilo.muriloplug.economy.reparo.ReparoCommand;
import br.com.murilo.muriloplug.economy.vender.SellPrices;
import br.com.murilo.muriloplug.economy.vender.VenderCommand;
import br.com.murilo.muriloplug.clan.base.BaseBlocks;
import br.com.murilo.muriloplug.clan.base.BaseItems;
import br.com.murilo.muriloplug.clan.base.BaseSettings;
import br.com.murilo.muriloplug.clan.base.ClanBaseCommands;
import br.com.murilo.muriloplug.clan.base.ClanBaseManager;
import br.com.murilo.muriloplug.clan.base.ClanChestStore;
import br.com.murilo.muriloplug.classes.ServerItems;
import br.com.murilo.muriloplug.clan.base.ExplosionOwners;
import br.com.murilo.muriloplug.eventos.AirdropManager;
import br.com.murilo.muriloplug.eventos.AirdropTable;
import br.com.murilo.muriloplug.eventos.BountyManager;
import br.com.murilo.muriloplug.eventos.ContractItems;
import br.com.murilo.muriloplug.maquinas.MaquinaHologramas;
import br.com.murilo.muriloplug.maquinas.MaquinaItens;
import br.com.murilo.muriloplug.maquinas.MaquinaListener;
import br.com.murilo.muriloplug.maquinas.MaquinaManager;
import br.com.murilo.muriloplug.maquinas.MaquinasCommand;
import br.com.murilo.muriloplug.maquinas.MaquinasConfig;
import br.com.murilo.muriloplug.maquinas.MaquinasMenus;
import br.com.murilo.muriloplug.maquinas.MaquinasMenusListener;
import br.com.murilo.muriloplug.maquinas.PainelMaquinas;
import br.com.murilo.muriloplug.upgrade.UpgradeCommand;
import br.com.murilo.muriloplug.upgrade.UpgradeManager;
import br.com.murilo.muriloplug.raid.RaidManager;
import br.com.murilo.muriloplug.shop.ShopUi;
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
    private ArcherMobility archerMobility;
    private TestMode testMode;
    private HunterMark hunterMark;
    private FocusManager focusManager;
    private ShotRegistry shotRegistry;
    private ArcherBowListener archerBow;
    private CombatenteHealth combatenteHealth;
    private FuryManager furyManager;
    private BuildManager buildManager;
    private PlacementManager placementManager;
    private EngenheiroPassivas engenheiroPassivas;
    private SuporteHealth suporteHealth;
    private FlightManager flightManager;
    private NightVisionManager nightVision;
    private TrocaEquivalente trocaEquivalente;
    private EscudoSagrado escudoSagrado;
    private TotemCura totemCura;
    private GolemManager golemManager;
    private VenderCommand venderCommand;
    private BaseBlocks baseBlocks;
    private RaidManager raidManager;
    private AirdropManager airdropManager;
    private BountyManager bountyManager;
    private UpgradeManager upgradeManager;
    private MaquinaManager maquinaManager;

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
        testMode = new TestMode(this);
        getServer().getPluginManager().registerEvents(testMode, this);
        testMode.start();
        getCommand("teste").setExecutor(testMode);
        getCommand("teste").setTabCompleter(testMode);
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
        Macos macos = new Macos(this);
        economyManager.setMacos(macos);
        getServer().getPluginManager().registerEvents(macos, this);
        getServer().getPluginManager().registerEvents(new RewardListener(this, economyManager), this);
        MoneyCommand moneyCommand = new MoneyCommand(economyManager);
        getCommand("money").setExecutor(moneyCommand);
        getCommand("money").setTabCompleter(moneyCommand);
        // Segurança extra: o saldo também é regravado a cada 5 minutos (e a cada mudança, em segundo plano).
        getServer().getScheduler().runTaskTimer(this, economyManager::requestSave, 5 * 60 * 20L, 5 * 60 * 20L);

        shopManager = new ShopManager(this);
        shopManager.load();
        ShopMenusListener shopMenusListener = new ShopMenusListener(this, shopManager, economyManager);
        getServer().getPluginManager().registerEvents(shopMenusListener, this);
        LojaCommand lojaCommand = new LojaCommand(shopManager, economyManager);
        getCommand("loja").setExecutor(lojaCommand);
        getCommand("loja").setTabCompleter(lojaCommand);

        marketManager = new MarketManager(this);
        marketManager.load();
        MarketMenusListener marketMenusListener = new MarketMenusListener(this, marketManager, economyManager);
        getServer().getPluginManager().registerEvents(marketMenusListener, this);
        getCommand("mercado").setExecutor(new MercadoCommand(marketManager, economyManager));

        SellPrices sellPrices = new SellPrices(this);
        sellPrices.load();
        venderCommand = new VenderCommand(this, sellPrices, economyManager);
        getServer().getPluginManager().registerEvents(venderCommand, this);
        getCommand("vender").setExecutor(venderCommand);
        getCommand("vender").setTabCompleter(venderCommand);
        venderCommand.start();
        ReparoCommand reparoCommand = new ReparoCommand(this, economyManager);
        getServer().getPluginManager().registerEvents(reparoCommand, this);
        getCommand("reparar").setExecutor(reparoCommand);
        getCommand("reparar").setTabCompleter(reparoCommand);

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
        // Base do clan (Núcleo, Cofre, Poder, manutenção): os dados vão no mesmo clans.yml, gravados pelo ClanManager.
        ClanBaseManager clanBaseManager = new ClanBaseManager(this, clanManager, claimManager, economyManager,
                new BaseSettings(this));
        clanBaseManager.load();
        clanManager.setExtraWriter(clanBaseManager::write);
        getServer().getPluginManager().registerEvents(clanBaseManager, this);
        clanBaseManager.start();
        // /upgrade: menu "Em breve"; o UpgradeManager já sabe cobrar do Cofre para os upgrades futuros.
        upgradeManager = new UpgradeManager(clanBaseManager);
        UpgradeCommand upgradeCommand = new UpgradeCommand();
        getServer().getPluginManager().registerEvents(upgradeCommand, this);
        getCommand("upgrade").setExecutor(upgradeCommand);
        claimManager.setWeakness(clanBaseManager::weakness);
        ExplosionOwners explosionOwners = new ExplosionOwners(this);
        getServer().getPluginManager().registerEvents(explosionOwners, this);
        BaseItems baseItems = new BaseItems(this);
        // Núcleo e Cofre só saem pelo /clan itens: nunca na loja nem no mercado (entradas antigas são removidas).
        shopMenusListener.setForbidden(baseItems::isCoreOrVault);
        marketMenusListener.setForbidden(baseItems::isCoreOrVault);
        for (br.com.murilo.muriloplug.shop.ShopItem shopItem : shopManager.list()) {
            if (baseItems.isCoreOrVault(shopItem.item()) && shopManager.remove(shopItem.id())) {
                getLogger().warning("Núcleo/Cofre do clan removido da loja (só pode ser pego pelo /clan itens).");
            }
        }
        for (br.com.murilo.muriloplug.market.Listing listing : marketManager.newestFirst()) {
            if (baseItems.isCoreOrVault(listing.item()) && marketManager.remove(listing.id()) != null) {
                getLogger().warning("Anúncio de Núcleo/Cofre do clan removido do mercado (vendedor "
                        + listing.sellerName() + "; o item não vale mais nada).");
            }
        }
        ClanChestStore clanChestStore = new ClanChestStore(this);
        getServer().getPluginManager().registerEvents(clanChestStore, this);
        baseBlocks = new BaseBlocks(this, clanBaseManager, claimManager, combatManager, baseItems, explosionOwners,
                clanChestStore);
        moneyCommand.setClanVault(new MoneyCommand.ClanVault() {
            @Override
            public String tagOf(Player player) {
                br.com.murilo.muriloplug.clan.Clan clan = clanManager.getClan(player.getUniqueId());
                return clan == null ? null : clan.tag();
            }

            @Override
            public void deposit(Player player, long amount) {
                br.com.murilo.muriloplug.clan.Clan clan = clanManager.getClan(player.getUniqueId());
                if (clan != null) {
                    clanBaseManager.depositToVault(clan, amount);
                }
            }
        });
        getServer().getPluginManager().registerEvents(baseBlocks, this);
        baseBlocks.start();
        clanManager.setDisbandListener(clan -> {
            baseBlocks.onDisband(clan);
            if (maquinaManager != null) {
                maquinaManager.onDisband(clan);
            }
        });
        clanManager.setCreateListener(baseBlocks::onClanCreated);
        claimCommand.setSizeMenu(clanBaseManager.settings()::sizeMenu, clanBaseManager::upkeepPreview);
        getServer().getPluginManager().registerEvents(new br.com.murilo.muriloplug.claim.ClaimSizeMenu.Clicks(this), this);
        new ClanBaseCommands(clanBaseManager, baseBlocks, baseItems).registerOn(clanCommand);
        clanCommand.setExtraInfo(baseBlocks::infoLines);
        raidManager = new RaidManager(this, claimManager, clanManager, clanBaseManager, explosionOwners);
        raidManager.load();
        getServer().getPluginManager().registerEvents(raidManager, this);
        raidManager.start();
        raidManager.registerOn(clanCommand);
        TargetRelations targetRelations = new TargetRelations(this, clanManager);
        StatusRegistry statusRegistry = new StatusRegistry();
        getServer().getPluginManager().registerEvents(new ClanFriendlyFireListener(clanManager, targetRelations), this);

        MagoSettings magoSettings = new MagoSettings(this);
        manaManager = new ManaManager(this, classManager, magoSettings, combatManager);
        manaManager.setTestMode(testMode);
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
        staffService.setTestMode(testMode);
        testMode.addEnableHook(staffService::clearCooldowns);
        getServer().getPluginManager().registerEvents(new StaffListener(staffService, authManager), this);
        getServer().getPluginManager().registerEvents(new MagoWeaknessListener(classManager, magoSettings), this);

        ArqueiroSettings arqueiroSettings = new ArqueiroSettings(this);
        ArrowItems arrowItems = new ArrowItems(this);
        ShotModes shotModes = new ShotModes();
        getServer().getPluginManager().registerEvents(shotModes, this);
        focusManager = new FocusManager(this, classManager, arqueiroSettings, combatManager);
        focusManager.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(focusManager, this);
        focusManager.start();
        shotRegistry = new ShotRegistry();
        getServer().getPluginManager().registerEvents(shotRegistry, this);
        hunterMark = new HunterMark(statusRegistry);
        getServer().getPluginManager().registerEvents(hunterMark, this);
        UltraFocus ultraFocus = new UltraFocus(this, classManager, arqueiroSettings, focusManager, authManager);
        getServer().getPluginManager().registerEvents(ultraFocus, this);
        ultraFocus.start();
        archerBow = new ArcherBowListener(this, classManager, arqueiroSettings, shotModes, arrowItems, shotRegistry,
                ultraFocus, focusManager);
        archerBow.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(archerBow, this);
        getServer().getPluginManager().registerEvents(new ExplosiveArrowListener(this, arqueiroSettings, arrowItems, targetRelations), this);
        getServer().getPluginManager().registerEvents(new ArcherWeaknessListener(classManager, arqueiroSettings), this);
        ArcherDamageListener archerDamage = new ArcherDamageListener(this, classManager, arqueiroSettings, shotRegistry,
                hunterMark, focusManager, targetRelations);
        getServer().getPluginManager().registerEvents(archerDamage, this);
        ArcherTargets archerTargets = new ArcherTargets(targetRelations, authManager);
        ArcherSkills archerSkills = new ArcherSkills(this, classManager, arqueiroSettings, focusManager, hunterMark,
                shotRegistry, archerDamage, archerTargets, authManager);
        getServer().getPluginManager().registerEvents(archerSkills, this);
        archerSkills.start();
        archerMobility = new ArcherMobility(this, classManager, arqueiroSettings, focusManager, authManager);
        getServer().getPluginManager().registerEvents(archerMobility, this);
        archerMobility.start();
        ArcherSpeedCleanup archerSpeedCleanup = new ArcherSpeedCleanup(this);
        getServer().getPluginManager().registerEvents(archerSpeedCleanup, this);
        archerSpeedCleanup.start();
        ArqueiroSidebar arqueiroSidebar = new ArqueiroSidebar(classManager, arqueiroSettings, focusManager, shotModes, ultraFocus);
        CombatenteSettings combatenteSettings = new CombatenteSettings(this);
        combatenteHealth = new CombatenteHealth(this, classManager, combatenteSettings);
        getServer().getPluginManager().registerEvents(combatenteHealth, this);
        combatenteHealth.applyOnline();
        furyManager = new FuryManager(this, classManager, combatenteSettings, authManager, combatManager);
        furyManager.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(furyManager, this);
        furyManager.start();
        CombatenteAbilities combatenteAbilities = new CombatenteAbilities(this, classManager, combatenteSettings,
                furyManager, targetRelations, authManager, statusRegistry);
        getServer().getPluginManager().registerEvents(combatenteAbilities, this);
        EngenheiroSettings engenheiroSettings = new EngenheiroSettings(this);
        StructureBooks structureBooks = new StructureBooks(this, engenheiroSettings);
        StructureStore structureStore = new StructureStore(this);
        getLogger().info("Estruturas salvas em estruturas/: " + structureStore.count());
        buildManager = new BuildManager(this, classManager, claimManager, combatManager, engenheiroSettings,
                structureBooks, structureStore);
        EngenheiroEnergia engenheiroEnergia = new EngenheiroEnergia(this, classManager, engenheiroSettings);
        engenheiroEnergia.setTestMode(testMode);
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
        GrapplePull grapplePull = new GrapplePull(this);
        getServer().getPluginManager().registerEvents(grapplePull, this);
        GanchoManager ganchoManager = new GanchoManager(this, classManager, engenheiroSettings, arrowItems, engenheiroAcoes, grapplePull);
        ganchoManager.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(ganchoManager, this);
        SentinelaItems sentinelaItems = new SentinelaItems(this, engenheiroSettings);
        GolemItems golemItems = new GolemItems(this, engenheiroSettings);
        golemManager = new GolemManager(this, classManager, authManager, targetRelations, combatManager,
                engenheiroSettings, golemItems, engenheiroEnergia, engenheiroAcoes);
        golemManager.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(golemManager, this);
        golemManager.start();
        SentinelaManager sentinelaManager = new SentinelaManager(this, classManager, claimManager, authManager,
                targetRelations, engenheiroSettings, sentinelaItems, engenheiroAcoes);
        sentinelaManager.load();
        getServer().getPluginManager().registerEvents(sentinelaManager, this);
        sentinelaManager.start();

        // Máquinas do clan: valores em maquinas.yml (MaquinasConfig), máquinas colocadas em maquinas-dados.yml.
        MaquinasConfig maquinasConfig = new MaquinasConfig(this);
        maquinasConfig.load();
        MaquinaHologramas maquinaHologramas = new MaquinaHologramas(this);
        getServer().getPluginManager().registerEvents(maquinaHologramas, this);
        maquinaManager = new MaquinaManager(this, maquinasConfig, new MaquinaItens(this, maquinasConfig),
                maquinaHologramas, clanManager, clanBaseManager, claimManager, baseBlocks, clanChestStore, sellPrices,
                combatManager, classManager, engenheiroEnergia, engenheiroAcoes, raidManager);
        maquinaManager.load();
        getServer().getPluginManager().registerEvents(maquinaManager, this);
        maquinaManager.start();
        MaquinasMenus maquinasMenus = new MaquinasMenus(this, maquinaManager);
        getServer().getPluginManager().registerEvents(new MaquinasMenusListener(this), this);
        getServer().getPluginManager().registerEvents(new MaquinaListener(maquinaManager, maquinasMenus), this);
        PainelMaquinas painelMaquinas = new PainelMaquinas(this, maquinaManager, maquinasMenus);
        getServer().getPluginManager().registerEvents(painelMaquinas, this);
        MaquinasCommand maquinasCommand = new MaquinasCommand(maquinaManager, maquinasMenus, painelMaquinas);
        getCommand("maquinas").setExecutor(maquinasCommand);
        getCommand("maquinas").setTabCompleter(maquinasCommand);
        SuporteSettings suporteSettings = new SuporteSettings(this);
        SuporteItems suporteItems = new SuporteItems(this, suporteSettings);
        // Itens antigos do Suporte (Pergaminho de livro com pena, itens com usos): atualizados na loja e no mercado.
        int oldSupportItems = shopManager.migrate(suporteItems::upgrade) + marketManager.migrate(suporteItems::upgrade);
        if (oldSupportItems > 0) {
            getLogger().info("Itens antigos do Suporte atualizados na loja e no mercado: " + oldSupportItems);
        }
        int oldNotes = shopManager.migrate(macos::convert) + marketManager.migrate(macos::convert);
        if (oldNotes > 0) {
            getLogger().info("Notas de dinheiro antigas convertidas em maços na loja e no mercado: " + oldNotes);
        }
        int oldGolems = shopManager.migrate(golemItems::upgradeLegacy) + marketManager.migrate(golemItems::upgradeLegacy);
        if (oldGolems > 0) {
            getLogger().info("Golems de Cobre antigos trocados na loja e no mercado: " + oldGolems);
        }
        SuporteCooldowns suporteCooldowns = new SuporteCooldowns();
        suporteCooldowns.setTestMode(testMode);
        testMode.addEnableHook(suporteCooldowns::clearAll);
        SuporteTargets suporteTargets = new SuporteTargets(authManager, targetRelations);
        SuporteAssists suporteAssists = new SuporteAssists();
        statsManager.setExtraAssistants(suporteAssists::supportersOf);
        suporteHealth = new SuporteHealth(this, classManager, suporteSettings);
        getServer().getPluginManager().registerEvents(suporteHealth, this);
        suporteHealth.applyOnline();
        HarmoniaManager harmonia = new HarmoniaManager(this, classManager, suporteSettings);
        harmonia.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(harmonia, this);
        harmonia.start();
        trocaEquivalente = new TrocaEquivalente(this, classManager, suporteSettings, suporteCooldowns, statusRegistry,
                suporteItems);
        getCommand("troca").setExecutor(trocaEquivalente);
        trocaEquivalente.setTestMode(testMode);
        trocaEquivalente.load();
        getServer().getPluginManager().registerEvents(trocaEquivalente, this);
        trocaEquivalente.start();
        LinkManager linkManager = new LinkManager(this, classManager, clanManager, authManager, suporteSettings,
                suporteTargets, suporteAssists, statusRegistry, harmonia, trocaEquivalente);
        getServer().getPluginManager().registerEvents(linkManager, this);
        linkManager.start();
        EloDrenante eloDrenante = new EloDrenante(suporteSettings, linkManager, harmonia, statusRegistry);
        getServer().getPluginManager().registerEvents(eloDrenante, this);
        TrocaLugar trocaLugar = new TrocaLugar(suporteSettings, linkManager);
        getServer().getPluginManager().registerEvents(trocaLugar, this);
        Socorrista socorrista = new Socorrista(this, classManager, suporteSettings, suporteTargets);
        getServer().getPluginManager().registerEvents(socorrista, this);
        socorrista.start();
        escudoSagrado = new EscudoSagrado(this, suporteSettings, suporteTargets, suporteAssists, statusRegistry);
        getServer().getPluginManager().registerEvents(escudoSagrado, this);
        escudoSagrado.start();
        Purificar purificar = new Purificar(suporteSettings, suporteTargets, suporteAssists, statusRegistry);
        totemCura = new TotemCura(this, classManager, claimManager, suporteSettings, suporteTargets, targetRelations,
                statusRegistry, suporteItems);
        totemCura.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(totemCura, this);
        totemCura.start();
        Silencio silencio = new Silencio(this, suporteSettings, suporteTargets, combatManager, damageTracker,
                statusRegistry);
        getServer().getPluginManager().registerEvents(silencio, this);
        silencio.start();
        Maldicao maldicao = new Maldicao(this, suporteSettings, suporteTargets, combatManager, damageTracker,
                statusRegistry);
        getServer().getPluginManager().registerEvents(maldicao, this);
        maldicao.start();
        RootManager rootManager = new RootManager(this, suporteSettings, suporteTargets, combatManager, damageTracker, statusRegistry);
        getServer().getPluginManager().registerEvents(rootManager, this);
        rootManager.start();
        SpecialArrowListener specialArrows = new SpecialArrowListener(this, classManager, arqueiroSettings,
                arrowItems, shotRegistry, ultraFocus, focusManager, archerTargets, targetRelations, rootManager,
                statusRegistry, grapplePull);
        specialArrows.setTestMode(testMode);
        getServer().getPluginManager().registerEvents(specialArrows, this);
        flightManager = new FlightManager(this, classManager, suporteSettings, suporteCooldowns);
        getServer().getPluginManager().registerEvents(flightManager, this);
        flightManager.start();
        nightVision = new NightVisionManager(this, classManager, authManager);
        getServer().getPluginManager().registerEvents(nightVision, this);
        getCommand("visao").setExecutor(nightVision);
        getServer().getPluginManager().registerEvents(new SuporteListener(classManager, authManager, suporteItems,
                suporteSettings, suporteCooldowns, suporteTargets, suporteAssists, harmonia, linkManager, rootManager,
                flightManager, trocaEquivalente, eloDrenante, trocaLugar, escudoSagrado, purificar, totemCura, silencio,
                maldicao), this);
        SuporteSidebar suporteSidebar = new SuporteSidebar(classManager, suporteSettings, harmonia, suporteCooldowns,
                linkManager, trocaEquivalente, eloDrenante, suporteItems, totemCura);
        // Purificar: como terminar o efeito real de cada marca negativa (além de tirar a marca).
        statusRegistry.registerCleanser("Preso", entity -> rootManager.release(entity.getUniqueId()));
        statusRegistry.registerCleanser("Marcado", entity -> hunterMark.clearTarget(entity.getUniqueId()));
        statusRegistry.registerCleanser("Congelado", entity -> staffService.clearComboMarks(entity.getUniqueId()));
        statusRegistry.registerCleanser("Empurrado", entity -> staffService.clearComboMarks(entity.getUniqueId()));
        statusRegistry.registerCleanser("Provocado", combatenteAbilities::clearProvoked);
        statusRegistry.registerCleanser("Silenciado", entity -> SilenceManager.clear(entity.getUniqueId()));
        statusRegistry.registerCleanser("Amaldiçoado", entity -> maldicao.clear(entity.getUniqueId()));
        ClassItems classItems = new ClassItems(staffItems, grimoireItems, arrowItems, structureBooks, plantaItems, sentinelaItems, golemItems, suporteItems);
        ClassCommand classCommand = new ClassCommand(classManager, combatManager, classItems, manaManager);
        getCommand("classe").setExecutor(classCommand);
        getCommand("classe").setTabCompleter(classCommand);
        // Itens do Servidor (não são de classe): o /kit admin entrega 1 de cada. Item novo: registrar aqui.
        ServerItems serverItems = new ServerItems();
        serverItems.register("clan.nucleo", () -> baseItems.create(BaseItems.Kind.CORE));
        serverItems.register("clan.cofre", () -> baseItems.create(BaseItems.Kind.VAULT));
        serverItems.register("clan.bau", () -> baseItems.create(BaseItems.Kind.CHEST));
        KitCommand kitCommand = new KitCommand(classItems, serverItems);
        getCommand("kit").setExecutor(kitCommand);
        getCommand("kit").setTabCompleter(kitCommand);
        getServer().getPluginManager().registerEvents(new KitMenusListener(classItems, serverItems), this);

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
        getServer().getPluginManager().registerEvents(new SuporteArmas(classManager, suporteSettings), this);
        // Por último entre os listeners de dano em HIGHEST: o Escudo de Mana mede o dano final já ajustado pelos outros.
        getServer().getPluginManager().registerEvents(new ManaShieldListener(manaManager, magoSettings), this);

        // Eventos: Contratos de Recompensa e Airdrop.
        bountyManager = new BountyManager(this, economyManager, clanManager, authManager, statusRegistry);
        bountyManager.setTestMode(testMode);
        bountyManager.load();
        ContractItems contractItems = new ContractItems(this, bountyManager);
        for (int level = 1; level <= ContractItems.LEVELS; level++) {
            int contractLevel = level;
            serverItems.register("contrato." + level, () -> contractItems.create(contractLevel, 1));
        }
        serverItems.register("dinheiro.maco", () -> macos.create(10_000));
        bountyManager.setContracts(contractItems);
        getServer().getPluginManager().registerEvents(bountyManager, this);
        bountyManager.start();
        AirdropTable airdropTable = new AirdropTable(this);
        airdropTable.load();
        airdropManager = new AirdropManager(this, claimManager, airdropTable, contractItems, macos);
        getServer().getPluginManager().registerEvents(airdropManager, this);
        getCommand("airdrop").setExecutor(airdropManager);
        getCommand("airdrop").setTabCompleter(airdropManager);
        airdropManager.start();

        PlayerScoreboards scoreboards = new PlayerScoreboards();
        NametagManager nametagManager = new NametagManager(scoreboards, combatManager, clanManager);
        nametagManager.setExtraSuffix(bountyManager::nameSuffix);
        SidebarManager sidebarManager = new SidebarManager(scoreboards, combatManager, statsManager, economyManager, clanManager, classManager, manaManager, arqueiroSidebar, furyManager, magoSettings,
                new EngenheiroSidebar(classManager, engenheiroEnergia, engenheiroSettings, ganchoManager, sentinelaManager, golemManager),
                suporteSidebar, testMode);
        // Logo abaixo do dinheiro: recompensa na cabeça (a manutenção fica só no Núcleo e no /clan manutencao).
        sidebarManager.addMoneyLine(bountyManager::sidebarLine);
        clanBaseManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
            }
        });
        bountyManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                nametagManager.update(player);
                sidebarManager.update(player);
            }
        });
        golemManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
            }
        });
        shotModes.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
            }
        });
        ultraFocus.setChangeListener(id -> {
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
        testMode.setChangeListener(player -> {
            sidebarManager.update(player);
            if (classBars != null) {
                classBars.update(player);
            }
        });
        if (classBars != null) {
            classBars.register(PlayerClass.SUPORTE, player -> {
                double current = harmonia.getHarmonia(player.getUniqueId());
                return new ClassBarManager.Bar((int) Math.floor(current), (float) (current / harmonia.maxHarmonia()));
            });
        }
        trocaEquivalente.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
            }
        });
        harmonia.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
                if (classBars != null) {
                    classBars.update(player);
                }
            }
        });
        if (classBars != null) {
            classBars.register(PlayerClass.ARQUEIRO, player -> {
                int current = focusManager.get(player.getUniqueId());
                return new ClassBarManager.Bar(current, (float) current / focusManager.max());
            });
        }
        focusManager.setChangeListener(id -> {
            Player player = getServer().getPlayer(id);
            if (player != null) {
                sidebarManager.update(player);
                if (classBars != null) {
                    classBars.update(player);
                }
            }
        });
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

        // /resetarconta: apaga os dados de um jogador em todos os sistemas (com backup antes).
        ResetarContaCommand resetarConta = new ResetarContaCommand(this, new ResetarContaCommand.Systems(authManager,
                claimManager, homeManager, clanManager, clanInvites, clanBaseManager, baseBlocks, clanChestStore,
                economyManager, statsManager, classManager, grimoireManager, trocaEquivalente, marketManager, tpaManager,
                sentinelaManager, buildManager, golemManager, bountyManager, raidManager, testMode));
        getServer().getPluginManager().registerEvents(resetarConta, this);
        getCommand("resetarconta").setExecutor(resetarConta);
        getCommand("resetarconta").setTabCompleter(resetarConta);
    }

    @Override
    public void onDisable() {
        if (testMode != null) {
            testMode.clear();
        }
        // Itens do baú de venda voltam aos donos antes de qualquer coisa ser salva.
        if (venderCommand != null) {
            venderCommand.shutdown();
        }
        if (airdropManager != null) {
            airdropManager.shutdown();
        }
        if (maquinaManager != null) {
            maquinaManager.shutdown();
        }
        if (raidManager != null) {
            raidManager.shutdown();
        }
        if (baseBlocks != null) {
            baseBlocks.shutdown();
        }
        if (bountyManager != null) {
            bountyManager.save();
        }
        if (dummyManager != null) {
            dummyManager.shutdown();
        }
        if (staffService != null) {
            staffService.shutdown();
        }
        if (combatDisplayTask != null) {
            combatDisplayTask.cancel();
        }
        if (golemManager != null) {
            golemManager.shutdown();
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
        // A penalidade da Troca Equivalente sai antes da vida extra (senão a vida máxima passaria de 1 para baixo).
        if (trocaEquivalente != null) {
            trocaEquivalente.endAll();
        }
        if (suporteHealth != null) {
            suporteHealth.removeAll();
        }
        if (escudoSagrado != null) {
            escudoSagrado.endAll();
        }
        if (totemCura != null) {
            totemCura.endAll();
        }
        SilenceManager.clearAll();
        if (furyManager != null) {
            furyManager.endAll();
        }
        if (combatenteHealth != null) {
            combatenteHealth.removeAll();
        }
        if (archerMobility != null) {
            archerMobility.removeAll();
        }
        if (hunterMark != null) {
            hunterMark.clearAll();
        }
        if (focusManager != null) {
            focusManager.endAll();
        }
        if (shotRegistry != null) {
            shotRegistry.clear();
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
        changed |= TestMode.addMissing(config);
        changed |= MagoSettings.addMissing(config);
        changed |= ArqueiroSettings.addMissing(config);
        changed |= CombatenteSettings.addMissing(config);
        changed |= EngenheiroSettings.addMissing(config);
        changed |= SuporteSettings.addMissing(config);
        changed |= Macos.addMissing(config);
        changed |= SellPrices.addMissing(config);
        changed |= ReparoCommand.addMissing(config);
        changed |= BaseSettings.addMissing(config);
        // O Núcleo não é mais removido ao chegar a 0 (fica desativado): a espera para recolocar não existe mais.
        if (config.contains(BaseSettings.OLD_CORE_REPLACE_WAIT)) {
            config.set(BaseSettings.OLD_CORE_REPLACE_WAIT, null);
            changed = true;
        }
        changed |= RaidManager.addMissing(config);
        changed |= AirdropManager.addMissing(config);
        changed |= BountyManager.addMissing(config);
        // Vulnerabilidade do Núcleo passou de 30 para 10 minutos: só troca se ainda estiver no padrão antigo.
        if (config.getInt(BaseSettings.CORE_VULNERABLE, 0) == 30) {
            config.set(BaseSettings.CORE_VULNERABLE, 10);
            changed = true;
        }
        // /reparar é bloqueado em combate: entra na lista sem apagar o que já está lá.
        java.util.List<String> blocked = new java.util.ArrayList<>(config.getStringList(CombatCommandListener.PATH));
        if (blocked.stream().noneMatch(command -> command.trim().replaceFirst("^/", "").equalsIgnoreCase("reparar"))) {
            blocked.add("reparar");
            config.set(CombatCommandListener.PATH, blocked);
            changed = true;
        }
        if (changed) {
            saveConfig();
        }
    }
}
