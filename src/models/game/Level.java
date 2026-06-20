package models.game;

import models.templates.LevelTemplate;

public class Level {
    private Wave[] waves;
    private LevelTemplate template;
    private boolean unlocked;
    private boolean completed;

    public LevelTemplate getTemplate() {
        return template;
    }
}
