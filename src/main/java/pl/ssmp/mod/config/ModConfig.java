package pl.ssmp.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Konfiguracja moda SSMP Discord Bridge.
 * Wartości mogą być wczytane z pliku JSON lub nadpisane zmiennymi środowiskowymi.
 *
 * Zmienne środowiskowe:
 *   DISCORD_TOKEN            – token bota Discord
 *   DISCORD_GUILD_ID         – ID serwera Discord
 *   DISCORD_CHAT_CHANNEL_ID  – ID kanału czatu
 *   DISCORD_EVENTS_CHANNEL_ID– ID kanału zdarzeń (opcjonalnie, domyślnie = chat)
 *   DISCORD_CONSOLE_CHANNEL_ID – ID kanału konsoli (opcjonalnie)
 *   DISCORD_ADMIN_USER_IDS   – ID adminów oddzielone przecinkami
 *   SERVER_NAME              – nazwa serwera w wiadomościach
 *
 * Dostępne placeholdery w szablonach wiadomości:
 *   {player}      – nazwa gracza
 *   {message}     – treść wiadomości (śmierć, czat itp.)
 *   {dimension}   – przyjazna nazwa wymiaru
 *   {from}        – wymiar źródłowy (zmiana wymiaru)
 *   {to}          – wymiar docelowy (zmiana wymiaru)
 *   {advancement} – nazwa osiągnięcia
 *   {server}      – nazwa serwera
 *   {mob}         – nazwa nazwanego stworzenia
 *   {count}       – liczba graczy online
 */
public class ModConfig {

    // ── Wewnętrzna klasa szablonów wiadomości ────────────────────────────────

    /**
     * Szablony wiadomości wysyłanych na Discord.
     * Obsługiwane placeholdery: {player}, {message}, {dimension}, {from}, {to},
     * {advancement}, {server}, {mob}, {count}.
     */
    public static class Messages {

        // Połączenia
        @SerializedName("player_join")
        public String playerJoin = "➡️ Dołączył do serwera";

        @SerializedName("player_leave")
        public String playerLeave = "⬅️ Opuścił serwer";

        // Śmierć
        @SerializedName("player_death")
        public String playerDeath = "{message}";

        @SerializedName("named_mob_death")
        public String namedMobDeath = "☠️ **{mob}** – {message}";

        // Osiągnięcia (task / goal / challenge)
        @SerializedName("advancement_task")
        public String advancementTask = "Odblokował osiągnięcie: **{advancement}**";

        @SerializedName("advancement_goal")
        public String advancementGoal = "Odblokował osiągnięcie: **{advancement}**";

        @SerializedName("advancement_challenge")
        public String advancementChallenge = "Odblokował osiągnięcie: **{advancement}**";

        // Zmiana wymiaru
        @SerializedName("dimension_change")
        public String dimensionChange = "Teleportował się z **{from}** do **{to}**";

        // Cykl życia serwera
        @SerializedName("server_starting")
        public String serverStarting = "🚀 **{server}** uruchamia się…";

        @SerializedName("server_started")
        public String serverStarted = "✅ **{server}** jest gotowy! Możesz dołączyć.";

        @SerializedName("server_stopping")
        public String serverStopping = "🛑 **{server}** zatrzymuje się…";

        @SerializedName("server_stopped")
        public String serverStopped = "⛔ **{server}** jest offline.";

        // Komendy czatu
        @SerializedName("me_command")
        public String meCommand = "✍️ {message}";

        @SerializedName("say_command")
        public String sayCommand = "📢 {message}";

        // Stopki embeds
        @SerializedName("chat_footer")
        public String chatFooter = "📍 Znajduje się w: {dimension}";

        @SerializedName("death_footer")
        public String deathFooter = "📍 Świat: {dimension}";

        @SerializedName("advancement_footer")
        public String advancementFooter = "📍 Świat: {dimension}";

        // Aktywność (status) bota
        @SerializedName("player_count_presence")
        public String playerCountPresence = "Obecnie graczy: {count}";

        // Prefix autora dla śmierci gracza (np. "💀 {player}")
        @SerializedName("death_author_prefix")
        public String deathAuthorPrefix = "💀 {player}";

        // Prefix autora dla osiągnięć (emoji zależy od typu, następnie "{player}")
        @SerializedName("advancement_author_prefix_task")
        public String advancementAuthorPrefixTask = "🥇 {player}";

        @SerializedName("advancement_author_prefix_goal")
        public String advancementAuthorPrefixGoal = "🎯 {player}";

        @SerializedName("advancement_author_prefix_challenge")
        public String advancementAuthorPrefixChallenge = "🏆 {player}";

        // Prefix autora dla zmiany wymiaru
        @SerializedName("dimension_change_author_prefix")
        public String dimensionChangeAuthorPrefix = "🌀 {player}";
    }

    // ── Wewnętrzna klasa włączania/wyłączania zdarzeń ────────────────────────

    /**
     * Flagi włączające poszczególne typy zdarzeń wysyłanych na Discord.
     * Ustaw na {@code false}, aby wyłączyć dane zdarzenie.
     */
    public static class Events {

        @SerializedName("player_join")
        public boolean playerJoin = true;

        @SerializedName("player_leave")
        public boolean playerLeave = true;

        @SerializedName("player_death")
        public boolean playerDeath = true;

        @SerializedName("named_mob_death")
        public boolean namedMobDeath = true;

        @SerializedName("advancement")
        public boolean advancement = true;

        @SerializedName("dimension_change")
        public boolean dimensionChange = true;

        @SerializedName("server_lifecycle")
        public boolean serverLifecycle = true;

        @SerializedName("me_command")
        public boolean meCommand = true;

        @SerializedName("say_command")
        public boolean sayCommand = true;

        @SerializedName("chat")
        public boolean chat = true;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Szablony wiadomości i zdarzenia ──────────────────────────────────────

    @SerializedName("messages")
    private Messages messages = new Messages();

    @SerializedName("events")
    private Events events = new Events();

    /**
     * Mapa nadpisań przyjaznych nazw wymiarów.
     * Klucz: identyfikator wymiaru (np. "minecraft:overworld")
     * Wartość: wyświetlana nazwa (np. "Nadświat")
     */
    @SerializedName("dimension_names")
    private Map<String, String> dimensionNames = new HashMap<>();

    // ── Discord ──────────────────────────────────────────────────────────────

    @SerializedName("token")
    private String token = "";

    @SerializedName("guild_id")
    private String guildId = "";

    /** Główny kanał czatu MC ↔ Discord */
    @SerializedName("chat_channel_id")
    private String chatChannelId = "";

    /** Kanał zdarzeń (dołączenia, śmierci, postępy…). Jeśli pusty – używa chatChannelId */
    @SerializedName("events_channel_id")
    private String eventsChannelId = "";

    /** Opcjonalny kanał konsoli (komendy admina) */
    @SerializedName("console_channel_id")
    private String consoleChannelId = "";

    /** Lista ID użytkowników Discord, którzy mają uprawnienia administratora */
    @SerializedName("admin_user_ids")
    private List<String> adminUserIds = new ArrayList<>();

    /**
     * Mapowanie wymiarów Minecrafta na ID kanałów Discord.
     * Klucz: identyfikator wymiaru (np. "minecraft:overworld")
     * Wartość: ID kanału Discord
     */
    @SerializedName("channel_dimensions")
    private Map<String, String> channelDimensions = new HashMap<>();

    // ── Serwer ───────────────────────────────────────────────────────────────

    @SerializedName("server_name")
    private String serverName = "SSMP";

    @SerializedName("show_avatars")
    private boolean showAvatars = true;

    @SerializedName("relay_discord_to_minecraft")
    private boolean relayDiscordToMinecraft = true;

    @SerializedName("show_discord_prefix")
    private boolean showDiscordPrefix = true;

    /** Prefix wyświetlany przed wiadomościami z Discorda w grze */
    @SerializedName("discord_prefix")
    private String discordPrefix = "§9[Discord]§r";

    // ── Ładowanie ────────────────────────────────────────────────────────────

    public static ModConfig load(Path configPath) {
        ModConfig config = new ModConfig();

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ModConfig fromFile = GSON.fromJson(reader, ModConfig.class);
                if (fromFile != null) {
                    config = fromFile;
                }
            } catch (IOException e) {
                LOGGER.error("[SSMP] Nie udało się wczytać konfiguracji z {}: {}", configPath, e.getMessage());
            }
        } else {
            LOGGER.info("[SSMP] Tworzenie domyślnego pliku konfiguracji: {}", configPath);
            config.save(configPath);
        }

        config.applyEnvOverrides();
        return config;
    }

    private void applyEnvOverrides() {
        String v;
        if ((v = env("DISCORD_TOKEN"))             != null) token             = v;
        if ((v = env("DISCORD_GUILD_ID"))           != null) guildId           = v;
        if ((v = env("DISCORD_CHAT_CHANNEL_ID"))    != null) chatChannelId     = v;
        if ((v = env("DISCORD_EVENTS_CHANNEL_ID"))  != null) eventsChannelId   = v;
        if ((v = env("DISCORD_CONSOLE_CHANNEL_ID")) != null) consoleChannelId  = v;
        if ((v = env("SERVER_NAME"))                != null) serverName        = v;

        String ids = env("DISCORD_ADMIN_USER_IDS");
        if (ids != null && !ids.isBlank()) {
            adminUserIds = new ArrayList<>(Arrays.asList(ids.split(",")));
            adminUserIds.replaceAll(String::trim);
        }
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return (v != null && !v.isBlank()) ? v.trim() : null;
    }

    public void save(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[SSMP] Nie udało się zapisać konfiguracji do {}: {}", configPath, e.getMessage());
        }
    }

    // ── Gettery ──────────────────────────────────────────────────────────────

    public String getToken() { return token; }
    public String getGuildId() { return guildId; }
    public String getChatChannelId() { return chatChannelId; }

    /** Kanał zdarzeń – jeśli nie skonfigurowany, używa kanału czatu */
    public String getEventsChannelId() {
        return (eventsChannelId == null || eventsChannelId.isBlank()) ? chatChannelId : eventsChannelId;
    }

    public String getConsoleChannelId() { return consoleChannelId; }
    public List<String> getAdminUserIds() { return adminUserIds; }
    public Map<String, String> getChannelDimensions() { return channelDimensions; }
    public String getServerName() { return serverName; }
    public boolean isShowAvatars() { return showAvatars; }
    public boolean isRelayDiscordToMinecraft() { return relayDiscordToMinecraft; }
    public boolean isShowDiscordPrefix() { return showDiscordPrefix; }
    public String getDiscordPrefix() { return discordPrefix; }

    public boolean isAdminUser(String userId) {
        return adminUserIds.contains(userId);
    }

    /**
     * Zwraca ID kanału Discord dla danego wymiaru lub domyślny kanał czatu.
     *
     * @param dimensionId np. "minecraft:overworld"
     */
    public String getChannelForDimension(String dimensionId) {
        return channelDimensions.getOrDefault(dimensionId, chatChannelId);
    }

    public Messages getMessages() { return messages; }
    public Events getEvents() { return events; }
    public Map<String, String> getDimensionNames() { return dimensionNames; }

    /**
     * Formatuje szablon wiadomości, zastępując placeholdery wartościami.
     * Placeholdery podawane są w parach: klucz, wartość (np. "player", "Steve").
     *
     * @param template  szablon z placeholderami w postaci {klucz}
     * @param kvPairs   pary klucz-wartość (musi być parzysta liczba elementów)
     * @return          sformatowany tekst
     */
    public static String format(String template, String... kvPairs) {
        if (template == null) return "";
        String result = template;
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            result = result.replace("{" + kvPairs[i] + "}", kvPairs[i + 1] != null ? kvPairs[i + 1] : "");
        }
        return result;
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank()
                && chatChannelId != null && !chatChannelId.isBlank();
    }
}
