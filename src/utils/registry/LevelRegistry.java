package utils.registry;

import models.templates.LevelTemplate;

import java.util.HashMap;
import java.util.Map;

public class LevelRegistry {
    private static LevelRegistry instance;
    private Map<String, LevelTemplate> levelTemplatesById =  new HashMap<>();

    private LevelRegistry(){}
    public static LevelRegistry getInstance(){
        if(instance == null){
            instance = new LevelRegistry();
        }
        return instance;
    }

    public LevelTemplate getLevelTemplateById(String id){return null;}
    public void register(LevelTemplate levelTemplate){}
}
