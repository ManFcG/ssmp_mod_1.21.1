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
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.command.DiscordCommands;
import pl.ssmp.mod.config.ModConfig;
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

    public DiscordBridge(ModConfig config) {
        this.config = config;
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

    // ─────────────────────────────────────────────────────────────────────────
    // Metody wysyłania zdarzeń do Discord
    // ─────────────────────────────────────────────────────────────────────────

    /** Wiadomość czatu gracza → Discord */
    public void sendPlayerChat(String playerName, String message, String dimensionId) {
        if (!isReady()) return;
        String channelId = config.getChannelForDimension(dimensionId);
        TextChannel channel = getTextChannel(channelId);
        if (channel == null) return;

        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(pl.ssmp.mod.util.EmojiUtil.unicodeToText(message))
                .setColor(COLOR_CHAT)
                .setFooter("📍 Znajduje się w: " + friendlyDimName(dimensionId))
                .build();
        channel.sendMessageEmbeds(embed).queue(null, e ->
                LOGGER.warn("[SSMP] Błąd wysyłania czatu: {}", e.getMessage()));
    }

    /** Komenda /me → Discord */
    public void sendMeCommand(String formattedMessage) {
        if (!isReady()) return;
        sendToEventsChannel("✍️ " + pl.ssmp.mod.util.EmojiUtil.unicodeToText(formattedMessage), COLOR_CHAT);
    }

    /** Komenda /say → Discord */
    public void sendSayCommand(String formattedMessage) {
        if (!isReady()) return;
        sendToEventsChannel("📢 " + pl.ssmp.mod.util.EmojiUtil.unicodeToText(formattedMessage), COLOR_CHAT);
    }

    /** Gracz dołączył → Discord */
    public void sendPlayerJoin(String playerName, String dimensionId) {
        if (!isReady()) return;
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription("➡️ Dołączył do serwera")
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
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription("⬅️ Opuścił serwer")
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
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String dimId = getDimensionForPlayer(playerName);
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor("💀 " + playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription(pl.ssmp.mod.util.EmojiUtil.unicodeToText(deathMessage))
                .setThumbnail(config.isShowAvatars() ? avatarUrl : null)
                .setColor(COLOR_DEATH)
                .setTimestamp(Instant.now());
        if (dimId != null) eb.setFooter("📍 Świat: " + friendlyDimName(dimId));
        sendEmbedsToEventsChannel(eb.build());
    }

    /** Nazwane stworzenie zginęło → Discord */
    public void sendNamedMobDeath(String mobName, String deathMessage) {
        if (!isReady()) return;
        MessageEmbed embed = new EmbedBuilder()
                .setDescription("☠️ **" + mobName + "** – " + deathMessage)
                .setColor(COLOR_DEATH)
                .setTimestamp(Instant.now())
                .build();
        sendEmbedsToEventsChannel(embed);
    }

    /** Gracz zdobył osiągnięcie → Discord */
    public void sendAdvancement(String playerName, String advancementName, String type) {
        if (!isReady()) return;
        Color color;
        String emoji;
        switch (type) {
            case "goal"      -> { color = COLOR_ADV_GOAL; emoji = "🎯"; }
            case "challenge" -> { color = COLOR_ADV_CHAL; emoji = "🏆"; }
            default          -> { color = COLOR_ADV_TASK; emoji = "🥇"; }
        }
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        String dimId = getDimensionForPlayer(playerName);
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(emoji + " " + playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription("Odblokował osiągnięcie: **" + advancementName + "**")
                .setThumbnail(config.isShowAvatars() ? avatarUrl : null)
                .setColor(color)
                .setTimestamp(Instant.now());
        if (dimId != null) eb.setFooter("📍 Świat: " + friendlyDimName(dimId));
        sendEmbedsToEventsChannel(eb.build());
    }

    /** Gracz zmienił wymiar → Discord */
    public void sendDimensionChange(String playerName, String fromDimension, String toDimension) {
        if (!isReady()) return;
        String fromName = friendlyDimName(fromDimension);
        String toName   = friendlyDimName(toDimension);
        String avatarUrl = pl.ssmp.mod.util.AvatarUtil.getHeadUrl(playerName);
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor("🌀 " + playerName, null, config.isShowAvatars() ? avatarUrl : null)
                .setDescription("Teleportował się z **" + fromName + "** do **" + toName + "**")
                .setColor(COLOR_DIM)
                .setTimestamp(Instant.now())
                .build();
        sendEmbedsToEventsChannel(embed);
    }

    /** Serwer zaczyna się uruchamiać */
    public void sendServerStarting() {
        if (!isReady()) return;
        updatePresence(OnlineStatus.IDLE, null);
        sendToEventsChannel("🚀 **" + config.getServerName() + "** uruchamia się…", COLOR_SERVER_ON);
    }

    /** Serwer jest gotowy */
    public void sendServerStarted() {
        if (!isReady()) return;
        updatePlayerCountPresence();
        sendToEventsChannel("✅ **" + config.getServerName() + "** jest gotowy! Możesz dołączyć.", COLOR_SERVER_ON);
    }

    /** Serwer zaczyna się zatrzymywać */
    public void sendServerStopping() {
        if (!isReady()) return;
        updatePresence(OnlineStatus.IDLE, null);
        sendToEventsChannel("🛑 **" + config.getServerName() + "** zatrzymuje się…", COLOR_SERVER_OFF);
    }

    /** Serwer zatrzymany */
    public void sendServerStopped() {
        if (!isReady()) return;
        TextChannel channel = getTextChannel(config.getEventsChannelId());
        if (channel == null) return;
        MessageEmbed embed = new EmbedBuilder()
                .setDescription("⛔ **" + config.getServerName() + "** jest offline.")
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
        updatePresence(OnlineStatus.ONLINE, "Obecnie graczy: " + count);
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
