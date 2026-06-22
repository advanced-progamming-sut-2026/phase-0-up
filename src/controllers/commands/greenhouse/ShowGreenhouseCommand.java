package controllers.commands.greenhouse;

import controllers.commands.Command;
import models.greenhouse.GreenHouse;
import models.greenhouse.Pot;
import models.greenhouse.PotState;
import views.renderers.MenuRenderer.GreenhouseRenderer;

public class ShowGreenhouseCommand implements Command {
    private GreenHouse greenHouse;
    private GreenhouseRenderer greenhouseRenderer;

    public ShowGreenhouseCommand(GreenHouse greenHouse, GreenhouseRenderer greenhouseRenderer) {
        this.greenHouse = greenHouse;
        this.greenhouseRenderer = greenhouseRenderer;
    }

    @Override
    public void execute() {
        greenhouseRenderer.showGreenhouse(showStatus(greenHouse));
    }

    private String showStatus(GreenHouse greenHouse) {
        StringBuilder status = new StringBuilder();
        for (int i = 0; i < greenHouse.getRows(); i++) {
            for (int j = 0; j < greenHouse.getCols(); j++) {
                Pot pot = greenHouse.getPot(j, i);
                status.append(String.format("Pot(%d,%d): ", j + 1, i + 1));

                pot.updateState();
                if (pot.isReady()) {
                    pot.setState(PotState.READY);
                }

                switch (pot.getState()) {
                    case LOCKED  -> status.append("LOCKED\n");
                    case EMPTY   -> status.append("EMPTY\n");
                    case GROWING -> status.append(pot.getOnPot().getName())
                            .append(" - ")
                            .append(pot.getRemainingTimeFormatted())
                            .append("\n");
                    case READY   -> status.append(pot.getOnPot().getName()).append(" - ready\n");
                }
            }
        }
        return status.toString();
    }
}


