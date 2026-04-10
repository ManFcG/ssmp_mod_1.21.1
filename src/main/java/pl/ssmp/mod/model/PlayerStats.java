package pl.ssmp.mod.model;

/**
 * Statystyki gracza Minecraft odczytane bezpośrednio z pliku stats JSON.
 *
 * Wartości czasowe (playTimeTicks, timeSinceDeathTicks) są w tickach (20 tps).
 * Wartości odległości (*Cm) są w centymetrach gry (100 jednostek = 1 blok).
 * Wartości obrażeń (damageDealt, damageTaken) są w 0.1 pkt HP (podziel przez 10 → HP).
 */
public class PlayerStats {

    public long playTimeTicks;
    public int  deaths;
    public int  mobKills;
    public int  playerKills;
    public long damageDealt;   // w 0.1 pkt HP
    public long damageTaken;   // w 0.1 pkt HP
    public long walkCm;
    public long sprintCm;
    public long swimCm;
    public long flyCm;
    public int  jumps;
    public int  itemsEnchanted;
    public int  tradedWithVillager;
    public int  diamondOresMined;
    public int  ancientDebrisMined;
    public int  coalOresMined;
    public int  enderDragonKills;
    public int  witherKills;
    public long timeSinceDeathTicks;

    public PlayerStats() {}
}
