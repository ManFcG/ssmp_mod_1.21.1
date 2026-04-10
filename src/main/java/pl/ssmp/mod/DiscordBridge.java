package pl.ssmp.mod;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.command.DiscordCommands;
import pl.ssmp.mod.config.ModConfig;
import pl.ssmp.mod.data.AccountLinkStorage;
import pl.ssmp.mod.data.PlayerStatsReader;
import pl.ssmp.mod.data.UserCacheReader;
import pl.ssmp.mod.listener.DiscordEventListener;

import java.awt.Color;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zarządza połączeniem JDA (bot Discord) i dostarcza metody
 * wysyłania wiadomości z serwera Minecraft do Discord.
 */
public class DiscordBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private final ModConfig config;
    private JDA jda;
    private MinecraftServer server;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    // Dane linkowania kont i statystyk
    private final AccountLinkStorage accountLinkStorage;
    private PlayerStatsReader playerStatsReader;
    private UserCacheReader   userCacheReader;

    // Kolory embeds
    public static final Color COLOR_JOIN      = new Color(0x57F287); // zielony
    public static final Color COLOR_LEAVE     = new Color(0xED4245); // czerwony
    public static final Color COLOR_DEATH     = new Color(0x2C2F33); // ciemny szary
    public static final Color COLOR_ADV_TASK  = new Color(0xFEE75C); // żółty
    public static final Color COLOR_ADV_GOAL  = new Color(0x5865F2); // fioletowy
    public static final Color COLOR_ADV_CHAL  = new Color(0xEB459E); // różowy
    public static final Color COLOR_SERVER_ON = new Color(0x57F287); // zielony
    public static final Color COLOR_SERVER_OFF = new Color(0xED4245); // czerwony
    public static final Color COLOR_CHAT      = new Color(0x99AAB5); // szary
    public static final Color COLOR_DIM       = new Color(0x5865F2); // niebieski

    public DiscordBridge(ModConfig config, AccountLinkStorage accountLinkStorage) {
        this.config = config;
        this.accountLinkStorage = accountLinkStorage;
        initJDA();
    }

    private void initJDA() {
        try {
            jda = JDABuilder.createDefault(config.getToken())
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ONLINE)
                    .addEventListeners(
                            new DiscordEventListener(this, config),
                            new DiscordCommands(this, config)
                    )
                    .build();
            LOGGER.info("[SSMP] Bot Discord uruchamiany...");
        } catch (Exception e) {
            LOGGER.error("[SSMP] Nie udało się uruchomić bota Discord: {}", e.getMessage());
        }
    }

    /** Wywoływane przez DiscordEventListener po zdarzeniu Ready */
    public void onReady() {
        ready.set(true);
        LOGGER.info("[SSMP] Bot Discord gotowy!");
        jda.getPresence().setStatus(OnlineStatus.IDLE);
        // Rejestracja slash-komend dla konkretnego serwera (guild)
        if (!config.getGuildId().isBlank()) {
            Guild guild = jda.getGuildById(config.getGuildId());
            if (guild != null) {
                DiscordCommands.registerSlashCommands(guild);
            } else {
                LOGGER.warn("[SSMP] Nie znaleziono serwera Discord o ID: {}", config.getGuildId());
            }
        } else {
            // Globalne komendy (aktualizacja może zająć do 1 godziny)
            DiscordCommands.registerSlashCommandsGlobal(jda);
        }
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        // Zainicjalizuj czytelniki danych zależnych od ścieżki świata
        if (this.playerStatsReader == null) {
            try {
                java.nio.file.Path statsDir = server.getSavePath(WorldSavePath.ROOT).resolve("stats");
                this.playerStatsReader = new PlayerStatsReader(statsDir);
                java.nio.file.Path serverDir = FabricLoader.getInstance().getGameDir();
                this.userCacheReader = new UserCacheReader(serverDir);
                LOGGER.info("[SSMP] Inicjalizacja odczytu statystyk gracza: {}", statsDir);
            } catch (Exception e) {
                LOGGER.warn("[SSMP] Nie udało się zainicjalizować odczytu statystyk: {}", e.getMessage());
            }
        }
    }

    public MinecraftServer getServer() {
        return server;
    }

    public boolean isReady() {
        return ready.get() && jda != null;
    }

    public JDA getJda() {
        return jda;
    }

    public ModConfig getConfig() {
        return config;
    }

    public AccountLinkStorage getAccountLinkStorage() {
        return accountLinkStorage;
    }

    public PlayerStatsReader getPlayerStatsReader() {
        return playerStatsReader;
    }

    public UserCacheReader getUserCacheReader() {
        return userCacheReader;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metody wysyłania zdarzeń do Discord
    // ─────────────────────────────────────────────────────────────────────────

    /** Wiadomość czatu gracza → Discord */
    public void sendPlayerChat(String playerName, String message, String dimensionId) {
        if (!isReady()) return;
        if (!config.getEvents().chat) return;
        String channelId = config.getChannelForDimension(dimensionId);
        TextChannel channel = getTextChannel(channelId);
        if (channel == null) return;

        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String dimName = friendlyDimName(dimensionId);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(pl.ssmp.mod.util.EmojiUtil.unicodeToText(message))
                .setColor(COLOR_CHAT)
                .setFooter(ModConfig.format(config.getMessages().chatFooter, "dimension", dimName))
                .build();
        channel.sendMessageEmbeds(embed).queue(null, e ->
                LOGGER.warn("[SSMP] Błąd wysyłania czatu: {}", e.getMessage()));
    }

    /** Komenda /me → Discord */
    public void sendMeCommand(String formattedMessage) {
        if (!isReady()) return;
        if (!config.getEvents().meCommand) return;
        String text = ModConfig.format(config.getMessages().meCommand,
                "message", pl.ssmp.mod.util.EmojiUtil.unicodeToText(formattedMessage));
        sendToEventsChannel(text, COLOR_CHAT);
    }

    /** Komenda /say → Discord */
    public void sendSayCommand(String formattedMessage) {
        if (!isReady()) return;
        if (!config.getEvents().sayCommand) return;
        String text = ModConfig.format(config.getMessages().sayCommand,
                "message", pl.ssmp.mod.util.EmojiUtil.unicodeToText(formattedMessage));
        sendToEventsChannel(text, COLOR_CHAT);
    }

    /** Gracz dołączył → Discord */
    public void sendPlayerJoin(String playerName, String dimensionId) {
        if (!isReady()) return;
        if (!config.getEvents().playerJoin) return;
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String desc = ModConfig.format(config.getMessages().playerJoin, "player", playerName);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(desc)
                .setThumbnail(config.isShowAvatars() ? avatarUrl : null)
                .setColor(COLOR_JOIN)
                .setTimestamp(Instant.now())
                .build();
        sendEmbedsToEventsChannel(embed);
        updatePlayerCountPresence(1);
    }

    /** Gracz wyszedł → Discord */
    public void sendPlayerLeave(String playerName, String dimensionId) {
        if (!isReady()) return;
        if (!config.getEvents().playerLeave) return;
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String desc = ModConfig.format(config.getMessages().playerLeave, "player", playerName);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(desc)
                .setThumbnail(config.isShowAvatars() ? avatarUrl : null)
                .setColor(COLOR_LEAVE)
                .setTimestamp(Instant.now())
                .build();
        sendEmbedsToEventsChannel(embed);
        updatePlayerCountPresence(-1);
    }

    /** Gracz zginął → Discord */
    public void sendPlayerDeath(String playerName, String deathMessage) {
        if (!isReady()) return;
        if (!config.getEvents().playerDeath) return;
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String dimId = getDimensionForPlayer(playerName);
        String author = ModConfig.format(config.getMessages().deathAuthorPrefix, "player", playerName);
        String desc = ModConfig.format(config.getMessages().playerDeath,
                "player", playerName,
                "message", pl.ssmp.mod.util.EmojiUtil.unicodeToText(deathMessage));
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(author, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(desc)
                .setThumbnail(config.isShowAvatars() ? avatarUrl : null)
                .setColor(COLOR_DEATH)
                .setTimestamp(Instant.now());
        if (dimId != null) {
            eb.setFooter(ModConfig.format(config.getMessages().deathFooter,
                    "dimension", friendlyDimName(dimId)));
        }
        sendEmbedsToEventsChannel(eb.build());
    }

    /** Nazwane stworzenie zginęło → Discord */
    public void sendNamedMobDeath(String mobName, String deathMessage) {
        if (!isReady()) return;
        if (!config.getEvents().namedMobDeath) return;
        String desc = ModConfig.format(config.getMessages().namedMobDeath,
                "mob", mobName,
                "message", deathMessage);
        MessageEmbed embed = new EmbedBuilder()
                .setDescription(desc)
                .setColor(COLOR_DEATH)
                .setTimestamp(Instant.now())
                .build();
        sendEmbedsToEventsChannel(embed);
    }

    /** Gracz zdobył osiągnięcie → Discord */
    public void sendAdvancement(String playerName, String advancementName, String type) {
        if (!isReady()) return;
        if (!config.getEvents().advancement) return;
        Color color;
        String descTemplate;
        String authorTemplate;
        switch (type) {
            case "goal"      -> {
                color = COLOR_ADV_GOAL;
                descTemplate   = config.getMessages().advancementGoal;
                authorTemplate = config.getMessages().advancementAuthorPrefixGoal;
            }
            case "challenge" -> {
                color = COLOR_ADV_CHAL;
                descTemplate   = config.getMessages().advancementChallenge;
                authorTemplate = config.getMessages().advancementAuthorPrefixChallenge;
            }
            default          -> {
                color = COLOR_ADV_TASK;
                descTemplate   = config.getMessages().advancementTask;
                authorTemplate = config.getMessages().advancementAuthorPrefixTask;
            }
        }
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String dimId = getDimensionForPlayer(playerName);
        String author = ModConfig.format(authorTemplate, "player", playerName);
        String desc   = ModConfig.format(descTemplate,
                "player", playerName,
                "advancement", advancementName);
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(author, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(desc)
                .setThumbnail(config.isShowAvatars() ? avatarUrl : null)
                .setColor(color)
                .setTimestamp(Instant.now());
        if (dimId != null) {
            eb.setFooter(ModConfig.format(config.getMessages().advancementFooter,
                    "dimension", friendlyDimName(dimId)));
        }
        sendEmbedsToEventsChannel(eb.build());
    }

    /** Gracz zmienił wymiar → Discord */
    public void sendDimensionChange(String playerName, String fromDimension, String toDimension) {
        if (!isReady()) return;
        if (!config.getEvents().dimensionChange) return;
        String fromName = friendlyDimName(fromDimension);
        String toName   = friendlyDimName(toDimension);
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String author = ModConfig.format(config.getMessages().dimensionChangeAuthorPrefix,
                "player", playerName);
        String desc = ModConfig.format(config.getMessages().dimensionChange,
                "player", playerName,
                "from", fromName,
                "to", toName);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(author, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(desc)
                .setColor(COLOR_DIM)
                .setTimestamp(Instant.now())
                .build();
        sendEmbedsToEventsChannel(embed);
    }

    /** Serwer zaczyna się uruchamiać */
    public void sendServerStarting() {
        if (!isReady()) return;
        if (!config.getEvents().serverLifecycle) return;
        updatePresence(OnlineStatus.IDLE, null);
        sendToEventsChannel(
                ModConfig.format(config.getMessages().serverStarting, "server", config.getServerName()),
                COLOR_SERVER_ON);
    }

    /** Serwer jest gotowy */
    public void sendServerStarted() {
        if (!isReady()) return;
        if (!config.getEvents().serverLifecycle) return;
        updatePlayerCountPresence();
        sendToEventsChannel(
                ModConfig.format(config.getMessages().serverStarted, "server", config.getServerName()),
                COLOR_SERVER_ON);
    }

    /** Serwer zaczyna się zatrzymywać */
    public void sendServerStopping() {
        if (!isReady()) return;
        if (!config.getEvents().serverLifecycle) return;
        updatePresence(OnlineStatus.IDLE, null);
        sendToEventsChannel(
                ModConfig.format(config.getMessages().serverStopping, "server", config.getServerName()),
                COLOR_SERVER_OFF);
    }

    /** Serwer zatrzymany */
    public void sendServerStopped() {
        if (!isReady()) return;
        if (!config.getEvents().serverLifecycle) return;
        TextChannel channel = getTextChannel(config.getEventsChannelId());
        if (channel == null) return;
        MessageEmbed embed = new EmbedBuilder()
                .setDescription(ModConfig.format(config.getMessages().serverStopped,
                        "server", config.getServerName()))
                .setColor(COLOR_SERVER_OFF)
                .setTimestamp(Instant.now())
                .build();
        // Blokujące wysłanie – po tej operacji JDA zostanie zamknięte
        channel.sendMessageEmbeds(embed).complete();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Relay Discord → Minecraft
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wysyła wiadomość tekstową do wszystkich graczy na serwerze Minecraft.
     */
    public void relayToMinecraft(net.minecraft.text.Text text) {
        if (server == null) return;
        server.execute(() ->
                server.getPlayerManager().broadcast(text, false));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void updatePresence(OnlineStatus status, String activityText) {
        if (jda == null) return;
        if (activityText != null) {
            jda.getPresence().setPresence(status, Activity.playing(activityText));
        } else {
            jda.getPresence().setStatus(status);
        }
    }

    private void updatePlayerCountPresence() {
        updatePlayerCountPresence(0);
    }

    private void updatePlayerCountPresence(int delta) {
        if (!isReady() || server == null) return;
        int count = server.getCurrentPlayerCount() + delta;
        String activityText = ModConfig.format(config.getMessages().playerCountPresence,
                "count", String.valueOf(count));
        updatePresence(OnlineStatus.ONLINE, activityText);
    }

    private String getDimensionForPlayer(String playerName) {
        if (server == null) return null;
        var player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) return null;
        return player.getWorld().getRegistryKey().getValue().toString();
    }

    private void sendToEventsChannel(String message, Color color) {
        TextChannel channel = getTextChannel(config.getEventsChannelId());
        if (channel == null) return;
        MessageEmbed embed = new EmbedBuilder()
                .setDescription(message)
                .setColor(color)
                .setTimestamp(Instant.now())
                .build();
        channel.sendMessageEmbeds(embed).queue(null, e ->
                LOGGER.warn("[SSMP] Błąd wysyłania zdarzenia: {}", e.getMessage()));
    }

    private void sendEmbedsToEventsChannel(MessageEmbed embed) {
        TextChannel channel = getTextChannel(config.getEventsChannelId());
        if (channel == null) return;
        channel.sendMessageEmbeds(embed).queue(null, e ->
                LOGGER.warn("[SSMP] Błąd wysyłania zdarzenia: {}", e.getMessage()));
    }

    private TextChannel getTextChannel(String channelId) {
        if (channelId == null || channelId.isBlank() || jda == null) return null;
        return jda.getTextChannelById(channelId);
    }

    private String friendlyDimName(String dimId) {
        if (dimId == null) return "Nieznany";
        // Sprawdź nadpisanie w konfiguracji
        String override = config.getDimensionNames().get(dimId);
        if (override != null && !override.isBlank()) return override;
        return switch (dimId) {
            case "minecraft:overworld"  -> "Zwykły Świat";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end"    -> "Kres";
            default -> dimId.replace("minecraft:", "").replace("_", " ");
        };
    }

    /** Zamknięcie JDA (wywołaj przy zatrzymaniu serwera) */
    public void shutdown() {
        if (jda != null) {
            try {
                jda.shutdown();
            } catch (Exception e) {
                LOGGER.warn("[SSMP] Błąd zamykania bota: {}", e.getMessage());
            }
        }
    }
}
