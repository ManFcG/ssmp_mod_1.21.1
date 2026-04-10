package pl.ssmp.mod.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.ssmp.mod.model.AccountLink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Obsługuje operacje na bazie danych SQLite przechowującej powiązania kont Discord ↔ Minecraft.
 *
 * Schemat bazy:
 * <pre>
 * CREATE TABLE account_links (
 *     discord_id   TEXT PRIMARY KEY,
 *     mc_uuid      TEXT UNIQUE NOT NULL,
 *     mc_username  TEXT NOT NULL,
 *     linked_at    INTEGER NOT NULL   -- Unix timestamp (sekundy)
 * );
 * </pre>
 *
 * Wszystkie operacje są synchronizowane – instancja jest thread-safe.
 */
public class AccountLinkStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssmp_mod");

    private final Connection connection;

    /**
     * Otwiera (lub tworzy) bazę danych SQLite pod podaną ścieżką.
     *
     * @param dbPath ścieżka do pliku .db
     * @throws SQLException jeśli nie można otworzyć lub zainicjalizować bazy
     * @throws IOException  jeśli nie można utworzyć katalogu nadrzędnego
     */
    public AccountLinkStorage(Path dbPath) throws SQLException, IOException {
        Files.createDirectories(dbPath.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        initSchema();
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS account_links (
                        discord_id   TEXT PRIMARY KEY,
                        mc_uuid      TEXT UNIQUE NOT NULL,
                        mc_username  TEXT NOT NULL,
                        linked_at    INTEGER NOT NULL
                    );
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mc_uuid ON account_links(mc_uuid);");
        }
        LOGGER.info("[SSMP] Baza danych account_links zainicjalizowana.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Odczyt
    // ─────────────────────────────────────────────────────────────────────────

    /** Pobiera powiązanie według ID Discord. Zwraca {@link Optional#empty()} jeśli brak. */
    public synchronized Optional<AccountLink> getByDiscordId(String discordId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT discord_id, mc_uuid, mc_username, linked_at FROM account_links WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            return mapFirst(ps.executeQuery());
        } catch (SQLException e) {
            LOGGER.error("[SSMP] Błąd odczytu account_links (discord_id={}): {}", discordId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Pobiera powiązanie według UUID Minecraft. Zwraca {@link Optional#empty()} jeśli brak. */
    public synchronized Optional<AccountLink> getByMcUuid(String mcUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT discord_id, mc_uuid, mc_username, linked_at FROM account_links WHERE mc_uuid = ?")) {
            ps.setString(1, mcUuid);
            return mapFirst(ps.executeQuery());
        } catch (SQLException e) {
            LOGGER.error("[SSMP] Błąd odczytu account_links (mc_uuid={}): {}", mcUuid, e.getMessage());
            return Optional.empty();
        }
    }

    /** Pobiera powiązanie według nazwy gracza Minecraft (case-insensitive). */
    public synchronized Optional<AccountLink> getByMcUsername(String mcUsername) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT discord_id, mc_uuid, mc_username, linked_at FROM account_links WHERE lower(mc_username) = lower(?)")) {
            ps.setString(1, mcUsername);
            return mapFirst(ps.executeQuery());
        } catch (SQLException e) {
            LOGGER.error("[SSMP] Błąd odczytu account_links (mc_username={}): {}", mcUsername, e.getMessage());
            return Optional.empty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Zapis / Usuwanie
    // ─────────────────────────────────────────────────────────────────────────

    /** Wstawia nowe powiązanie do bazy. */
    public synchronized void insert(AccountLink link) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO account_links (discord_id, mc_uuid, mc_username, linked_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, link.discordId());
            ps.setString(2, link.mcUuid());
            ps.setString(3, link.mcUsername());
            ps.setLong(4, link.linkedAt());
            ps.executeUpdate();
        }
    }

    /** Usuwa powiązanie według ID Discord. */
    public synchronized void deleteByDiscordId(String discordId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM account_links WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            ps.executeUpdate();
        }
    }

    /** Usuwa powiązanie według UUID Minecraft. */
    public synchronized void deleteByMcUuid(String mcUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM account_links WHERE mc_uuid = ?")) {
            ps.setString(1, mcUuid);
            ps.executeUpdate();
        }
    }

    /** Zamyka połączenie z bazą danych. */
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.warn("[SSMP] Błąd zamykania bazy danych: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private static Optional<AccountLink> mapFirst(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return Optional.of(new AccountLink(
                    rs.getString("discord_id"),
                    rs.getString("mc_uuid"),
                    rs.getString("mc_username"),
                    rs.getLong("linked_at")
            ));
        }
        return Optional.empty();
    }
}
