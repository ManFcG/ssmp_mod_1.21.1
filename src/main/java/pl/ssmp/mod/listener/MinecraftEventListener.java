package pl.ssmp.mod.listener;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.DiscordBridge;
import pl.ssmp.mod.config.ModConfig;
import pl.ssmp.mod.minecraft.commands.DiscordLinkCommand;

/**
 * Nasłuchuje zdarzeń serwera Minecraft i przekazuje je do Discorda przez {@link DiscordBridge}.
 *
 * Obsługiwane zdarzenia:
 * - Cykl życia serwera (start/stop)
 * - Czat graczy
 * - Komendy /me, /say
 * - Dołączenie/wyjście gracza
 * - Śmierć gracza (przez wiadomość systemową)
 * - Śmierć nazwanego stworzenia
 * - Osiągnięcia gracza
 * - Zmiana wymiaru
 */
public class MinecraftEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private final DiscordBridge bridge;
    private final ModConfig config;

    public MinecraftEventListener(DiscordBridge bridge, ModConfig config) {
        this.bridge = bridge;
        this.config = config;
    }

    /** Rejestruje wszystkie handlery. */
    public void register() {
        registerLifecycle();
        registerChat();
        registerConnectionEvents();
        registerGameMessages();
        registerEntityEvents();
        registerDimensionChange();
        registerMinecraftCommands();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cykl życia serwera
    // ─────────────────────────────────────────────────────────────────────────

    private void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            bridge.setServer(server);
            bridge.sendServerStarting();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            bridge.setServer(server);
            bridge.sendServerStarted();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                bridge.sendServerStopping());

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            bridge.sendServerStopped();
            bridge.shutdown();
            if (bridge.getAccountLinkStorage() != null) {
                bridge.getAccountLinkStorage().close();
            }
            pl.ssmp.mod.data.PendingLinksManager.shutdown();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Czat gracza
    // ─────────────────────────────────────────────────────────────────────────

    private void registerChat() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String playerName = sender.getName().getString();
            String content    = message.getContent().getString();
            String dimensionId = sender.getWorld().getRegistryKey().getValue().toString();
            bridge.sendPlayerChat(playerName, content, dimensionId);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Połączenia graczy
    // ─────────────────────────────────────────────────────────────────────────

    private void registerConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String playerName = handler.getPlayer().getName().getString();
            String dimensionId = handler.getPlayer().getWorld().getRegistryKey().getValue().toString();
            bridge.sendPlayerJoin(playerName, dimensionId);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String playerName = handler.getPlayer().getName().getString();
            String dimensionId = handler.getPlayer().getWorld().getRegistryKey().getValue().toString();
            bridge.sendPlayerLeave(playerName, dimensionId);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wiadomości systemowe (śmierć, osiągnięcia, /me, /say)
    // ─────────────────────────────────────────────────────────────────────────

    private void registerGameMessages() {
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            // Ignoruj wiadomości nakładki (actionbar)
            if (overlay) return;

            TextContent content = message.getContent();
            if (!(content instanceof TranslatableTextContent translatable)) return;

            String key  = translatable.getKey();
            String text = message.getString();

            if (key.startsWith("death.")) {
                // Wyodrębnij nazwę gracza z argumentów (pierwszy argument to gracz)
                Object[] args = translatable.getArgs();
                String playerName = extractPlayerName(args, message);
                bridge.sendPlayerDeath(playerName, text);

            } else if (key.startsWith("chat.type.advancement.")) {
                // chat.type.advancement.task / .goal / .challenge
                String type = key.replace("chat.type.advancement.", "");
                Object[] args = translatable.getArgs();
                String playerName = extractPlayerName(args, message);
                // Drugi argument to nazwa osiągnięcia
                String advName = (args.length >= 2) ? textToString(args[1]) : text;
                bridge.sendAdvancement(playerName, advName, type);

            } else if ("chat.type.emote".equals(key)) {
                // /me <akcja>
                bridge.sendMeCommand(text);

            } else if ("chat.type.announcement".equals(key)) {
                // /say <wiadomość>
                bridge.sendSayCommand(text);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Zdarzenia encji (śmierć nazwanych stworzeń)
    // ─────────────────────────────────────────────────────────────────────────

    private void registerEntityEvents() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            // Tylko nazwane stworzenia (nie gracze – obsługiwani przez GAME_MESSAGE)
            if (!(entity instanceof ServerPlayerEntity)
                    && entity instanceof MobEntity mob
                    && mob.hasCustomName()
                    && entity.getWorld().getRegistryKey() != null) {
                String mobName  = mob.getCustomName() != null ? mob.getCustomName().getString() : "Nieznane";
                Text deathMsgText = source.getDeathMessage(entity);
                String deathMsg = deathMsgText != null ? deathMsgText.getString() : mobName + " zginął";
                bridge.sendNamedMobDeath(mobName, deathMsg);
            }
            return true; // zawsze zezwalaj na śmierć
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Zmiana wymiaru
    // ─────────────────────────────────────────────────────────────────────────

    private void registerDimensionChange() {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            String playerName = player.getName().getString();
            String fromDim    = origin.getRegistryKey().getValue().toString();
            String toDim      = destination.getRegistryKey().getValue().toString();
            bridge.sendDimensionChange(playerName, fromDim, toDim);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Komendy Minecraft (/discord link, /discord unlink)
    // ─────────────────────────────────────────────────────────────────────────

    private void registerMinecraftCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DiscordLinkCommand.register(dispatcher, bridge));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String extractPlayerName(Object[] args, Text fallback) {
        if (args != null && args.length > 0) {
            return textToString(args[0]);
        }
        return fallback.getString();
    }

    private static String textToString(Object obj) {
        if (obj instanceof Text t) return t.getString();
        if (obj instanceof String s) return s;
        return obj != null ? obj.toString() : "";
    }
}
