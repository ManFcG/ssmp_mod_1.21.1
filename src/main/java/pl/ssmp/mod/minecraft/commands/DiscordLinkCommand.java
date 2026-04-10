package pl.ssmp.mod.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.data.AccountLinkStorage;
import pl.ssmp.mod.data.PendingLinksManager;
import pl.ssmp.mod.model.AccountLink;
import pl.ssmp.mod.DiscordBridge;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Rejestruje komendy in-game obsługujące linkowanie kont Discord ↔ Minecraft:
 *
 * <ul>
 *   <li>{@code /discord link}   – generuje 6-znakowy kod do wpisania na Discordzie</li>
 *   <li>{@code /discord unlink} – usuwa powiązanie konta</li>
 * </ul>
 */
public class DiscordLinkCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private DiscordLinkCommand() {}

    /**
     * Rejestruje komendy w dyspozytorze Brigadier.
     *
     * @param dispatcher dyspozytorz komend serwera
     * @param bridge     instancja DiscordBridge (dostęp do storage)
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, DiscordBridge bridge) {
        dispatcher.register(
                literal("discord")
                        .then(literal("link")
                                .executes(ctx -> handleLink(ctx, bridge)))
                        .then(literal("unlink")
                                .executes(ctx -> handleUnlink(ctx, bridge)))
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /discord link
    // ─────────────────────────────────────────────────────────────────────────

    private static int handleLink(CommandContext<ServerCommandSource> ctx, DiscordBridge bridge) {
        ServerCommandSource source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Ta komenda jest dostępna tylko dla graczy."));
            return 0;
        }

        String uuid     = player.getUuidAsString();
        String username = player.getName().getString();
        AccountLinkStorage storage = bridge.getAccountLinkStorage();

        // Sprawdź czy gracz jest już zlinkowany
        Optional<AccountLink> existing = storage.getByMcUuid(uuid);
        if (existing.isPresent()) {
            AccountLink link = existing.get();
            String discordName = resolveDiscordName(bridge, link.discordId());
            source.sendFeedback(() -> buildAlreadyLinkedMessage(username, discordName), false);
            return 1;
        }

        // Generuj kod linkowania
        String code = PendingLinksManager.generateCode(uuid, username);
        source.sendFeedback(() -> buildLinkCodeMessage(code), false);
        LOGGER.info("[SSMP] Gracz {} poprosił o kod linkowania: {}", username, code);
        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /discord unlink
    // ─────────────────────────────────────────────────────────────────────────

    private static int handleUnlink(CommandContext<ServerCommandSource> ctx, DiscordBridge bridge) {
        ServerCommandSource source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Ta komenda jest dostępna tylko dla graczy."));
            return 0;
        }

        String uuid     = player.getUuidAsString();
        String username = player.getName().getString();
        AccountLinkStorage storage = bridge.getAccountLinkStorage();

        Optional<AccountLink> existing = storage.getByMcUuid(uuid);
        if (existing.isEmpty()) {
            source.sendFeedback(() -> Text.literal(
                    "⚠ Twoje konto Minecraft nie jest powiązane z żadnym kontem Discord."
            ).formatted(Formatting.YELLOW), false);
            return 1;
        }

        AccountLink link = existing.get();
        try {
            storage.deleteByMcUuid(uuid);
            String discordName = resolveDiscordName(bridge, link.discordId());
            source.sendFeedback(() -> Text.literal(
                    "✅ Konto zostało odłączone od konta Discord " + discordName + "."
            ).formatted(Formatting.GREEN), false);
            LOGGER.info("[SSMP] Gracz {} odlinkował konto Discord {}", username, link.discordId());
        } catch (Exception e) {
            LOGGER.error("[SSMP] Błąd odlinkowania konta gracza {}: {}", username, e.getMessage());
            source.sendError(Text.literal("⚠ Nie udało się odlinkować konta. Spróbuj ponownie."));
        }
        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Text buildLinkCodeMessage(String code) {
        MutableText prefix = Text.literal("[Discord] ").formatted(Formatting.AQUA, Formatting.BOLD);
        MutableText info   = Text.literal("Twój kod linkowania: ").formatted(Formatting.WHITE);
        MutableText codeText = Text.literal(code)
                .setStyle(Style.EMPTY
                        .withColor(Formatting.YELLOW)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code)));
        MutableText instruction = Text.literal(
                "\nNa Discordzie wpisz: /link " + code
                + "\n§7(Kod ważny przez 10 minut. Kliknij kod aby skopiować.)"
        ).formatted(Formatting.GRAY);

        return prefix.append(info).append(codeText).append(instruction);
    }

    private static Text buildAlreadyLinkedMessage(String mcUsername, String discordName) {
        MutableText prefix = Text.literal("[Discord] ").formatted(Formatting.AQUA, Formatting.BOLD);
        MutableText msg = Text.literal(
                "Twoje konto Minecraft jest już powiązane z " + discordName + ".\n"
                + "§7Aby odlinkować, wpisz: §f/discord unlink"
        ).formatted(Formatting.WHITE);
        return prefix.append(msg);
    }

    private static String resolveDiscordName(DiscordBridge bridge, String discordId) {
        try {
            if (bridge.getJda() != null) {
                var user = bridge.getJda().getUserById(discordId);
                if (user != null) return "@" + user.getEffectiveName();
            }
        } catch (Exception ignored) {}
        return "Discord ID: " + discordId;
    }
}
