package utils.gameinitializers;

import models.game.GameSession;
import models.map.Cell;
import models.map.GameMap;
import models.map.Row;
import models.map.Terrains.FrozenTerrain;
import models.map.Terrains.GraveInDarkAgesTerrain;
import models.map.Terrains.GravesInDarkAgesTypes;
import models.map.Terrains.LowSandTerrain;
import models.map.Terrains.NecromancyTerrain;
import models.map.Terrains.NormalGrave;
import models.map.Terrains.SlipDirection;
import models.map.Terrains.SlipTerrain;
import models.map.Terrains.WaterTerrain;

import java.util.Random;

// Turns a level's authored terrain layout into real Terrain objects on the session's map.
// Runs once per session, after the GameMap exists (most terrains need the session or their own cell),
// which is why this is a session-time step rather than part of Level construction.
public final class MapInitializer {
    private static final Random RANDOM = new Random();

    private MapInitializer() { }

    public static void applyTerrain(GameSession gameSession, char[][] layout) {
        if (gameSession == null || layout == null) {
            return;
        }
        GameMap map = gameSession.getMap();
        for (int y = 0; y < layout.length && y < map.getRows().size(); y++) {
            char[] rowLayout = layout[y];
            if (rowLayout == null) {
                continue;
            }
            Row row = map.getRow(y);
            for (int x = 0; x < rowLayout.length && x < row.getCells().size(); x++) {
                applyToCell(gameSession, row, row.cellAt(x), x, rowLayout[x]);
            }
        }
    }

    private static void applyToCell(GameSession session, Row row, Cell cell, int x, char symbol) {
        switch (symbol) {
            case '#': // Ancient Egypt headstone: blocks shots until destroyed
                cell.addTerrain(new NormalGrave(session, cell));
                break;
            case '?': // Dark Ages grave: drops sun or plant food when broken
                cell.addTerrain(new GraveInDarkAgesTerrain(randomGraveType(), session, cell));
                break;
            case '0': // Dark Ages tile a Necromancer can raise a zombie from
                cell.addTerrain(new NecromancyTerrain(session, cell));
                break;
            case '&': // Frostbite ice block
                cell.addTerrain(new FrozenTerrain());
                break;
            case '^':
                cell.addTerrain(new SlipTerrain(SlipDirection.UP));
                break;
            case 'v':
                cell.addTerrain(new SlipTerrain(SlipDirection.DOWN));
                break;
            case '!': // Beach water: only aquatic plants or a Lily Pad platform may sit here
                cell.setFlooded(true);
                cell.addTerrain(new WaterTerrain());
                break;
            case '-': // Beach low sand: zombies can surface here once it floods
                cell.addTerrain(new LowSandTerrain(row, x));
                break;
            case '.':
            default:
                break;
        }
    }

    private static GravesInDarkAgesTypes randomGraveType() {
        return RANDOM.nextBoolean() ? GravesInDarkAgesTypes.SUNNY : GravesInDarkAgesTypes.FOODY;
    }
}
