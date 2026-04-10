package pl.ssmp.mod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.config.ModConfig;
import pl.ssmp.mod.data.AccountLinkStorage;
import pl.ssmp.mod.listener.MinecraftEventListener;

import java.nio.file.Path;

/**
 * Punkt wejścia moda SSMP Discord Bridge.
 *
 * Inicjalizuje konfigurację, łączy się z Discordem (JDA)
 * i rejestruje wszystkie handlery zdarzeń Fabric.
 */
public class SsmpMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "ssmp_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SsmpMod instance;
    private static DiscordBridge discordBridge;
    private ModConfig config;

    @Override
    public void onInitializeServer() {
        instance = this;

        // Ładowanie konfiguracji z pliku + nadpisanie zmiennymi środowiskowymi
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ssmp_mod.json");
        config = ModConfig.load(configPath);

        if (!config.isConfigured()) {
            LOGGER.warn("[SSMP] ⚠️  Discord Bridge nie jest skonfigurowany.");
            LOGGER.warn("[SSMP]     Edytuj plik: {}", configPath);
            LOGGER.warn("[SSMP]     lub ustaw zmienne środowiskowe: DISCORD_TOKEN, DISCORD_CHAT_CHANNEL_ID");
            return;
        }

        // Inicjalizacja bazy danych SQLite dla powiązań kont
        Path dbPath = FabricLoader.getInstance().getConfigDir().resolve("discord_links.db");
        AccountLinkStorage accountLinkStorage;
        try {
            accountLinkStorage = new AccountLinkStorage(dbPath);
        } catch (Exception e) {
            LOGGER.error("[SSMP] Nie udało się zainicjalizować bazy danych account_links: {}", e.getMessage(), e);
            return;
        }

        // Inicjalizacja bota Discord (asynchronicznie przez JDA)
        discordBridge = new DiscordBridge(config, accountLinkStorage);

        // Rejestracja handlerów zdarzeń Minecraft → Discord
        MinecraftEventListener listener = new MinecraftEventListener(discordBridge, config);
        listener.register();

        LOGGER.info("[SSMP] ✅ Discord Bridge załadowany pomyślnie!");
        LOGGER.info("[SSMP]    Kanał czatu:    {}", config.getChatChannelId());
        LOGGER.info("[SSMP]    Kanał zdarzeń:  {}", config.getEventsChannelId());
        LOGGER.info("[SSMP]    Admini Discord: {}", config.getAdminUserIds());
    }

    public static SsmpMod getInstance()         { return instance; }
    public static DiscordBridge getDiscordBridge() { return discordBridge; }
    public ModConfig getConfig()                { return config; }
}
