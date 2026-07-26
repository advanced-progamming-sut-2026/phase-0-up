package controllers.commands.ingame;

import controllers.commands.Command;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.SeedPacket;
import models.game.gamemodes.GameMode;
import models.map.Cell;
import models.map.Terrains.GraveInDarkAgesTerrain;
import models.map.Terrains.GraveTerrain;
import models.map.Terrains.Terrain;
import models.templates.PlantTemplate;
import utils.Result;
import utils.registry.PlantRegistry;
import views.renderers.InGameRenderer;
import views.renderers.MapRenderer;

import java.util.Map;

public class ShowMapStatusCommand implements Command {
    private ShowMapStatusAction action;
    private GameSession gameSession;
    private final MapRenderer mapRenderer;
    private final InGameRenderer renderer;
    private int tileX;
    private int tileY;

    public ShowMapStatusCommand(ShowMapStatusAction action, GameSession gameSession, MapRenderer mapRenderer,
                                InGameRenderer renderer, int tileX, int tileY) {
        this.action = action;
        this.gameSession = gameSession;
        this.mapRenderer = mapRenderer;
        this.renderer = renderer;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public void execute() {
        switch (action) {
            case SHOW_MAP -> mapRenderer.renderGameSession(gameSession);
            case SHOW_PLANTS_STATUS -> showPlantsStatus();
            case SHOW_TILE_STATUS -> showTileStatus();
        }
    }

    private void showPlantsStatus() {
        // Vasebreaker keeps its own roster (plants come from vases, not seed packets), so report that
        // instead. A plant leaves the roster the moment it is placed, so it disappears from here too.
        GameMode mode = gameSession.getMode();
        if (mode != null && mode.managesPlantInventory()) {
            showModeInventoryStatus(mode);
            return;
        }
        if (gameSession.getSelectedSeeds().isEmpty()) {
            renderer.render(new Result(true, "Your seed bar is empty for this lawn."));
            return;
        }

        StringBuilder status = new StringBuilder();
        for (SeedPacket seed : gameSession.getSelectedSeeds()) {
            if (status.length() > 0) {
                status.append('\n');
            }
            status.append(formatSeedStatus(seed));
        }
        renderer.render(new Result(true, status.toString()));
    }

    // The plants the player is currently holding in a mode that hands them out itself (Vasebreaker).
    private void showModeInventoryStatus(GameMode mode) {
        Map<String, Integer> inventory = mode.plantInventory();
        if (inventory == null || inventory.isEmpty()) {
            renderer.render(new Result(true,
                    "Empty-handed! Crack open a vase and grab whatever falls out."));
            return;
        }
        StringBuilder status = new StringBuilder();
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            if (status.length() > 0) {
                status.append('\n');
            }
            status.append(entry.getKey()).append(" x").append(entry.getValue())
                    .append(" - ready to plant");
        }
        renderer.render(new Result(true, status.toString()));
    }

    private String formatSeedStatus(SeedPacket seed) {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(seed.getPlantType());
        int cost = template != null ? template.getCost() : 0;
        long currentTick = gameSession.getTimeTicks();

        StringBuilder line = new StringBuilder();
        line.append(seed.getPlantType()).append(" - cost: ").append(cost).append(" sun - ");
        if (gameSession.isCooldownRemoved() || seed.isReady(currentTick)) {
            line.append("ready to plant");
        } else {
            line.append("recharging, ")
                    .append(String.format("%.1f", seed.getRemainingCooldownSeconds(currentTick)))
                    .append("s remaining");
        }
        return line.toString();
    }
    private void showTileStatus() {
        if (!gameSession.getMap().isValidCoordinate(tileX, tileY)) {
            renderer.render(new Result(false, "Invalid coordinates (" + tileX + ", " + tileY + ")."));
            return;
        }
        Cell cell = gameSession.getMap().getCell(tileX, tileY);
        renderer.render(new Result(true, buildTileStatus(cell)));
    }
    private String buildTileStatus(Cell cell) {
        StringBuilder status = new StringBuilder();
        status.append("Tile (").append(tileX).append(", ").append(tileY).append("):");

        status.append("\n  Terrain: ").append(formatTerrain(cell));
        appendGraveStatus(status, cell);

        Plant plant = cell.getCurrentPlant();
        if (plant != null) {
            status.append("\n  Plant: ").append(plant.getName())
                    .append(" - health: ").append(plant.getHealth().getCurrentHp()).append('/')
                    .append(plant.getHealth().getMaxHp());
        } else {
            status.append("\n  Plant: none");
        }

        if (cell.hasProtector()) {
            status.append("\n  Protector: ").append(cell.getProtector().getName());
        }

        Zombie zombieHere = findZombieAt(cell);
        if (zombieHere != null) {
            status.append("\n  Zombie: ").append(zombieHere.getAlias())
                    .append(" - health: ").append(zombieHere.getHealth().getTotalHP());
        } else {
            status.append("\n  Zombie: none");
        }

        return status.toString();
    }

    private String formatTerrain(Cell cell) {
        for (Terrain terrain : cell.getTerrain()) {
            if (!terrain.isDestroyed()) {
                return terrain.getClass().getSimpleName() + " (" + terrain.getSymbol() + ")";
            }
        }
        return cell.isPlantable() ? "normal ground" : "not plantable";
    }

    // A grave is the one terrain with health the player can whittle down, so the tile report calls it
    // out explicitly and shows what is left of it -- that is how you know whether one more shot (or a
    // Grave Buster) will clear the tile. Dark Ages headstones also say whether they are holding loot.
    private void appendGraveStatus(StringBuilder status, Cell cell) {
        for (Terrain terrain : cell.getTerrain()) {
            if (!(terrain instanceof GraveTerrain grave) || grave.isDestroyed()) {
                continue;
            }
            status.append("\n  Grave: yes -- health: ").append(grave.getHp())
                    .append('/').append(grave.getMaxHp());
            if (terrain instanceof GraveInDarkAgesTerrain darkAges) {
                status.append(darkAges.hasLoot()
                        ? " (something is buried in there)"
                        : " (nothing buried in there)");
            }
            status.append("\n  Grave blocks: planting on this tile")
                    .append(grave.doesBlockProjectiles() ? " and shots passing through it" : "");
            return;
        }
        status.append("\n  Grave: none");
    }

    private Zombie findZombieAt(Cell cell) {
        for (Zombie zombie : gameSession.getMap().getRow(tileY).getZombies()) {
            if (!zombie.getHealth().isDead() && (int) zombie.getMovement().getPositionX() == tileX) {
                return zombie;
            }
        }
        return null;
    }

}
