package controllers.commands.ingame;

import controllers.commands.Command;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Components.StateComponent;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;
import utils.Constants;
import utils.Result;
import views.renderers.InGameRenderer;

// "zombies info": prints a status block for every active zombie on the board. Two indent levels: the
// four fields sit four spaces under the zombie's name, and each armor / effect entry sits four further
// spaces under its own header. Blocks are separated by a blank line.
//
//   <alias>:
//       position: <x>, <y>
//       health: <baseBodyHp>
//       armor:
//           <armorName>: <armorHp>
//       effects:
//           <effectName>: <secondsRemaining>s
//
//   <next alias>:
//       ...
public class ZombiesInfoCommand implements Command {
    private static final String FIELD_INDENT = "    ";       // fields under the zombie's name
    private static final String ENTRY_INDENT = "        ";   // armor / effect entries under their header

    private final GameSession gameSession;
    private final InGameRenderer renderer;

    public ZombiesInfoCommand(GameSession gameSession, InGameRenderer renderer) {
        this.gameSession = gameSession;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (Row row : gameSession.getMap().getRows()) {
            for (Zombie zombie : row.getZombies()) {
                if (zombie == null) {
                    continue;
                }
                if (any) {
                    sb.append('\n');   // blank line between blocks
                }
                appendZombie(sb, zombie);
                any = true;
            }
        }
        if (!any) {
            renderer.render(new Result(true, "Not a zombie in sight. Suspiciously quiet..."));
            return;
        }
        // Drop the trailing newline so the block ends cleanly on the last effect line.
        if (sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        renderer.render(new Result(true, sb.toString()));
    }

    private void appendZombie(StringBuilder sb, Zombie zombie) {
        int column = (int) Math.floor(zombie.getX());
        sb.append(zombie.getAlias()).append(":\n");
        sb.append(FIELD_INDENT).append("position: ").append(column).append(", ")
                .append(zombie.getY()).append("\n");
        sb.append(FIELD_INDENT).append("health: ").append(baseBodyHp(zombie)).append("\n");

        sb.append(FIELD_INDENT).append("armor:\n");
        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() == ArmorType.BASE_BODY) {
                continue;   // the base body is the "health" line, not an armor entry
            }
            sb.append(ENTRY_INDENT).append(layer.getType().getDisplayName())
                    .append(": ").append(layer.getCurrentHp()).append("\n");
        }

        sb.append(FIELD_INDENT).append("effects:\n");
        appendEffects(sb, zombie.getState());
    }

    // The zombie's body HP: the current HP of the BASE_BODY layer at the bottom of the health stack.
    private int baseBodyHp(Zombie zombie) {
        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() == ArmorType.BASE_BODY) {
                return layer.getCurrentHp();
            }
        }
        return 0;
    }

    // Timed status effects with their remaining time in seconds, in a fixed order. Only effects that
    // are actually active (timer > 0, or a permanent frostbite freeze) are listed.
    private void appendEffects(StringBuilder sb, StateComponent state) {
        if (state.getFrozenTimer() > 0) {
            appendEffect(sb, "frozen", state.getFrozenTimer());
        } else if (state.isPermanentlyFrozen()) {
            sb.append(ENTRY_INDENT).append("frozen: ").append("permanent").append("\n");
        }
        if (state.getChilledTimer() > 0) {
            appendEffect(sb, "chilled", state.getChilledTimer());
        }
        if (state.getButteredTimer() > 0) {
            appendEffect(sb, "buttered", state.getButteredTimer());
        }
    }

    private void appendEffect(StringBuilder sb, String name, int remainingTicks) {
        sb.append(ENTRY_INDENT).append(name).append(": ").append(formatSeconds(remainingTicks))
                .append("s\n");
    }

    // Ticks -> seconds, trimming a whole number to no decimal ("2s") while keeping a fraction ("3.2s").
    private String formatSeconds(int ticks) {
        double seconds = ticks / (double) Constants.TICKS_PER_SECOND;
        String text = String.format("%.1f", seconds);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text;
    }
}
