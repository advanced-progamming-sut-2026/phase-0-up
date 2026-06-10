package utils.registry;

import models.templates.QuestTemplate;

import java.util.HashMap;
import java.util.Map;

public class QuestRegistry {
    private static QuestRegistry instance;
    private Map<String, QuestTemplate> questTemplatesByName = new HashMap<>();

    private QuestRegistry(){}
    public static QuestRegistry getInstance(){
        if (instance == null){
            instance = new QuestRegistry();
        }
        return instance;
    }

    public Map<String, QuestTemplate> getAllQuestTemplates(){return null;}
    public QuestTemplate getQuestTemplateByName(String name){return null;}
    public void register(QuestTemplate questTemplate){}
}
