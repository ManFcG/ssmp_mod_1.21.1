package pl.ssmp.mod.model;

/**
 * Reprezentuje powiązanie konta Discord z kontem Minecraft.
 *
 * @param discordId  ID użytkownika Discord
 * @param mcUuid     UUID gracza Minecraft (z myślnikami)
 * @param mcUsername Nazwa gracza Minecraft
 * @param linkedAt   Czas powiązania jako Unix timestamp (sekundy)
 */
public record AccountLink(
        String discordId,
        String mcUuid,
        String mcUsername,
        long linkedAt
) {}
