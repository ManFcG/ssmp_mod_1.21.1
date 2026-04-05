package pl.ssmp.mod.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.whitelist.Whitelist;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.DiscordBridge;
import pl.ssmp.mod.config.ModConfig;
import pl.ssmp.mod.util.AvatarUtil;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Obsługuje komendy slash Discord.
 *
 * Komendy dostępne dla wszystkich:
 *   /status   – informacje o serwerze
 *   /lista    – lista graczy online
 *   /ping     – sprawdź opóźnienie bota
 *
 * Komendy tylko dla administratorów (określonych w konfiguracji):
 *   /whitelist dodaj <gracz>
 *   /whitelist usuń <gracz>
 *   /whitelist lista
 *   /ban <gracz> [powód]
 *   /unban <gracz>
 *   /op <gracz>
 *   /deop <gracz>
 *   /wykonaj <komenda>
 */
public class DiscordCommands extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private final DiscordBridge bridge;
    private final ModConfig config;

    public DiscordCommands(DiscordBridge bridge, ModConfig config) {
        this.bridge = bridge;
        this.config = config;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rejestracja komend
    // ─────────────────────────────────────────────────────────────────────────

    public static void registerSlashCommands(Guild guild) {
        guild.updateCommands().addCommands(buildCommands()).queue(
                cmds -> LOGGER.info("[SSMP] Zarejestrowano {} komend na serwerze Discord '{}'",
                        cmds.size(), guild.getName()),
                err  -> LOGGER.warn("[SSMP] Błąd rejestracji komend: {}", err.getMessage())
        );
    }

    public static void registerSlashCommandsGlobal(JDA jda) {
        jda.updateCommands().addCommands(buildCommands()).queue(
                cmds -> LOGGER.info("[SSMP] Zarejestrowano {} globalnych komend Discord", cmds.size()),
                err  -> LOGGER.warn("[SSMP] Błąd rejestracji globalnych komend: {}", err.getMessage())
        );
    }

    private static List<CommandData> buildCommands() {
        return List.of(
                // ── Publiczne ──────────────────────────────────────────────
                Commands.slash("status",  "Wyświetl informacje o serwerze Minecraft"),
                Commands.slash("lista",   "Wyświetl listę graczy online"),
                Commands.slash("ping",    "Sprawdź opóźnienie bota Discord"),

                // ── Whitelist ──────────────────────────────────────────────
                Commands.slash("whitelist", "Zarządzaj białą listą serwera")
                        .addSubcommands(
                                new SubcommandData("dodaj",  "Dodaj gracza do białej listy")
                                        .addOption(OptionType.STRING, "gracz", "Nazwa gracza", true),
                                new SubcommandData("usuń",   "Usuń gracza z białej listy")
                                        .addOption(OptionType.STRING, "gracz", "Nazwa gracza", true),
                                new SubcommandData("lista",  "Wyświetl białą listę")
                        ),

                // ── Administracja ─────────────────────────────────────────
                Commands.slash("ban",  "Zbanuj gracza")
                        .addOption(OptionType.STRING, "gracz", "Nazwa gracza", true)
                        .addOption(OptionType.STRING, "powód", "Powód bana", false),

                Commands.slash("unban", "Odbanuj gracza")
                        .addOption(OptionType.STRING, "gracz", "Nazwa gracza", true),

                Commands.slash("op",   "Nadaj uprawnienia OP")
                        .addOption(OptionType.STRING, "gracz", "Nazwa gracza", true),

                Commands.slash("deop", "Cofnij uprawnienia OP")
                        .addOption(OptionType.STRING, "gracz", "Nazwa gracza", true),

                Commands.slash("wykonaj", "Wykonaj komendę na serwerze (tylko admin)")
                        .addOption(OptionType.STRING, "komenda", "Komenda do wykonania", true)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Obsługa komend
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String cmd    = event.getName();

        // Komendy wymagające admina
        String subCmd = event.getSubcommandName(); // może być null dla komend bez subkomend
        boolean requiresAdmin = List.of("ban", "unban", "op", "deop", "wykonaj").contains(cmd)
                || (cmd.equals("whitelist") && !"lista".equals(subCmd));

        if (requiresAdmin && !config.isAdminUser(userId)) {
            event.reply("❌ Nie masz uprawnień do tej komendy.").setEphemeral(true).queue();
            return;
        }

        // Odroczone odpowiedzi – operacje mogą wymagać wątku serwerowego
        event.deferReply().queue();

        CompletableFuture.runAsync(() -> {
            try {
                switch (cmd) {
                    case "status"    -> handleStatus(event);
                    case "lista"     -> handleLista(event);
                    case "ping"      -> handlePing(event);
                    case "whitelist" -> handleWhitelist(event);
                    case "ban"       -> handleBan(event);
                    case "unban"     -> handleUnban(event);
                    case "op"        -> handleOp(event);
                    case "deop"      -> handleDeop(event);
                    case "wykonaj"   -> handleWykonaj(event);
                    default          -> event.getHook().sendMessage("❓ Nieznana komenda.").queue();
                }
            } catch (Exception e) {
                LOGGER.error("[SSMP] Błąd obsługi komendy '{}': {}", cmd, e.getMessage(), e);
                event.getHook().sendMessage("⚠️ Wystąpił błąd: " + e.getMessage()).queue();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /status
    // ─────────────────────────────────────────────────────────────────────────

    private void handleStatus(SlashCommandInteractionEvent event) {
        MinecraftServer server = bridge.getServer();
        if (server == null) {
            event.getHook().sendMessage("⛔ Serwer jest offline.").queue();
            return;
        }
        int online     = server.getCurrentPlayerCount();
        int maxPlayers = server.getMaxPlayerCount();

        // TPS – obliczane z ostatnich czasów tiku (dane serwera w milisekundach).
        // getAverageTickTimeMillis() jest dostępne w MinecraftServer 1.21.x.
        String tpsStr;
        try {
            double avgTickMs = server.getAverageTickTimeMillis();
            double tps = Math.min(20.0, 1000.0 / avgTickMs);
            tpsStr = String.format("%.1f", tps);
        } catch (Throwable t) {
            tpsStr = "N/D";
        }

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("📊 Status serwera: " + config.getServerName())
                .addField("👥 Gracze online", online + " / " + maxPlayers, true)
                .addField("⚡ TPS",            tpsStr, true)
                .addField("🟢 Status",         "Online", true)
                .setColor(DiscordBridge.COLOR_SERVER_ON)
                .setTimestamp(Instant.now())
                .build();
        event.getHook().sendMessageEmbeds(embed).queue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /lista
    // ─────────────────────────────────────────────────────────────────────────

    private void handleLista(SlashCommandInteractionEvent event) {
        MinecraftServer server = bridge.getServer();
        if (server == null) {
            event.getHook().sendMessage("⛔ Serwer jest offline.").queue();
            return;
        }
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("👥 Gracze online: " + players.size() + " / " + server.getMaxPlayerCount())
                .setColor(DiscordBridge.COLOR_JOIN)
                .setTimestamp(Instant.now());

        if (players.isEmpty()) {
            eb.setDescription("*Brak graczy online*");
        } else {
            StringBuilder sb = new StringBuilder();
            for (ServerPlayerEntity player : players) {
                String dim = friendlyDimName(player.getWorld().getRegistryKey().getValue().toString());
                sb.append("▸ **").append(player.getName().getString())
                        .append("** – ").append(dim).append("\n");
            }
            eb.setDescription(sb.toString());
        }

        if (config.isShowAvatars() && !players.isEmpty()) {
            eb.setThumbnail(AvatarUtil.getHeadUrl(players.get(0).getName().getString()));
        }

        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /ping
    // ─────────────────────────────────────────────────────────────────────────

    private void handlePing(SlashCommandInteractionEvent event) {
        long pingMs = event.getJDA().getGatewayPing();
        event.getHook().sendMessage("🏓 Pong! Opóźnienie bota: **" + pingMs + " ms**").queue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /whitelist
    // ─────────────────────────────────────────────────────────────────────────

    private void handleWhitelist(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) { event.getHook().sendMessage("❓ Nieznana subkomenda.").queue(); return; }

        MinecraftServer server = bridge.getServer();
        if (server == null) { event.getHook().sendMessage("⛔ Serwer jest offline.").queue(); return; }

        switch (sub) {
            case "dodaj"  -> whitelistAdd(event, server);
            case "usuń"   -> whitelistRemove(event, server);
            case "lista"  -> whitelistList(event, server);
            default       -> event.getHook().sendMessage("❓ Nieznana subkomenda.").queue();
        }
    }

    private void whitelistAdd(SlashCommandInteractionEvent event, MinecraftServer server) {
        String playerName = requireOption(event, "gracz");
        if (playerName == null) return;

        executeServerCommand(server, "whitelist add " + playerName);
        event.getHook().sendMessage("✅ Gracz **" + playerName + "** został dodany do białej listy.").queue();
        LOGGER.info("[SSMP] Whitelist add: {} (przez {})", playerName, event.getUser().getEffectiveName());
    }

    private void whitelistRemove(SlashCommandInteractionEvent event, MinecraftServer server) {
        String playerName = requireOption(event, "gracz");
        if (playerName == null) return;

        executeServerCommand(server, "whitelist remove " + playerName);
        event.getHook().sendMessage("✅ Gracz **" + playerName + "** został usunięty z białej listy.").queue();
        LOGGER.info("[SSMP] Whitelist remove: {} (przez {})", playerName, event.getUser().getEffectiveName());
    }

    private void whitelistList(SlashCommandInteractionEvent event, MinecraftServer server) {
        Whitelist whitelist = server.getPlayerManager().getWhitelist();
        String[] names = whitelist.getNames();
        if (names.length == 0) {
            event.getHook().sendMessage("📋 Biała lista jest pusta.").queue();
        } else {
            String list = String.join(", ", names);
            event.getHook().sendMessage("📋 **Biała lista** (" + names.length + "): " + list).queue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /ban
    // ─────────────────────────────────────────────────────────────────────────

    private void handleBan(SlashCommandInteractionEvent event) {
        MinecraftServer server = bridge.getServer();
        if (server == null) { event.getHook().sendMessage("⛔ Serwer jest offline.").queue(); return; }

        String playerName = requireOption(event, "gracz");
        if (playerName == null) return;

        OptionMapping reasonOpt = event.getOption("powód");
        String reason  = reasonOpt != null ? reasonOpt.getAsString() : "Zbanowany przez administrację";
        String fullCmd = "ban " + playerName + " " + reason;

        executeServerCommand(server, fullCmd);
        event.getHook().sendMessage("🔨 Gracz **" + playerName + "** został zbanowany. Powód: " + reason).queue();
        LOGGER.info("[SSMP] Ban: {} | powód: {} (przez {})", playerName, reason, event.getUser().getEffectiveName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /unban
    // ─────────────────────────────────────────────────────────────────────────

    private void handleUnban(SlashCommandInteractionEvent event) {
        MinecraftServer server = bridge.getServer();
        if (server == null) { event.getHook().sendMessage("⛔ Serwer jest offline.").queue(); return; }

        String playerName = requireOption(event, "gracz");
        if (playerName == null) return;

        executeServerCommand(server, "pardon " + playerName);
        event.getHook().sendMessage("✅ Gracz **" + playerName + "** został odbanowany.").queue();
        LOGGER.info("[SSMP] Unban: {} (przez {})", playerName, event.getUser().getEffectiveName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /op
    // ─────────────────────────────────────────────────────────────────────────

    private void handleOp(SlashCommandInteractionEvent event) {
        MinecraftServer server = bridge.getServer();
        if (server == null) { event.getHook().sendMessage("⛔ Serwer jest offline.").queue(); return; }

        String playerName = requireOption(event, "gracz");
        if (playerName == null) return;

        executeServerCommand(server, "op " + playerName);
        event.getHook().sendMessage("⭐ Gracz **" + playerName + "** otrzymał uprawnienia OP.").queue();
        LOGGER.info("[SSMP] Op: {} (przez {})", playerName, event.getUser().getEffectiveName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /deop
    // ─────────────────────────────────────────────────────────────────────────

    private void handleDeop(SlashCommandInteractionEvent event) {
        MinecraftServer server = bridge.getServer();
        if (server == null) { event.getHook().sendMessage("⛔ Serwer jest offline.").queue(); return; }

        String playerName = requireOption(event, "gracz");
        if (playerName == null) return;

        executeServerCommand(server, "deop " + playerName);
        event.getHook().sendMessage("🔕 Gracz **" + playerName + "** utracił uprawnienia OP.").queue();
        LOGGER.info("[SSMP] Deop: {} (przez {})", playerName, event.getUser().getEffectiveName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /wykonaj
    // ─────────────────────────────────────────────────────────────────────────

    private void handleWykonaj(SlashCommandInteractionEvent event) {
        MinecraftServer server = bridge.getServer();
        if (server == null) { event.getHook().sendMessage("⛔ Serwer jest offline.").queue(); return; }

        String command = requireOption(event, "komenda");
        if (command == null) return;

        server.execute(() -> {
            try {
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
            } catch (Exception e) {
                LOGGER.warn("[SSMP] Błąd wykonywania komendy '{}': {}", command, e.getMessage());
            }
        });
        event.getHook().sendMessage("✅ Komenda **`" + command + "`** została wysłana do serwera.").queue();
        LOGGER.info("[SSMP] Wykonano komendę: '{}' (przez {})", command, event.getUser().getEffectiveName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wykonuje komendę Minecrafta w wątku serwera.
     */
    private static void executeServerCommand(MinecraftServer server, String command) {
        server.execute(() -> {
            try {
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
            } catch (Exception e) {
                LOGGER.warn("[SSMP] Błąd wykonywania komendy '{}': {}", command, e.getMessage());
            }
        });
    }

    private String requireOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        if (opt == null) {
            event.getHook().sendMessage("❌ Brakuje parametru: " + name).queue();
            return null;
        }
        return opt.getAsString();
    }

    private static String friendlyDimName(String dimId) {
        return switch (dimId) {
            case "minecraft:overworld"  -> "Zwykły Świat";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end"    -> "Kres";
            default -> dimId.replace("minecraft:", "").replace("_", " ");
        };
    }
}
