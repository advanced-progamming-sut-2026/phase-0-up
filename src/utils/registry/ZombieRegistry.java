package utils.registry;

import models.templates.ZombieTemplate;

import java.util.HashMap;
import java.util.Map;

public class ZombieRegistry {
    private static ZombieRegistry instance;
    private Map<String, ZombieTemplate> zombieTemplatesByAlias = new HashMap<>();

    private ZombieRegistry(){}
    public static ZombieRegistry getInstance(){
        if(instance == null){
            instance = new ZombieRegistry();
        }
        return instance;
    }

    public Map<String, ZombieTemplate> getZombieTemplatesByAlias(){return zombieTemplatesByAlias;}
    public ZombieTemplate getZombieTemplateByAlias(String alias){
        for (String key : zombieTemplatesByAlias.keySet()){
            if(key.equalsIgnoreCase(alias)){
                return zombieTemplatesByAlias.get(key);
            }
        }
        return null;
    }
    public void register(ZombieTemplate zombieTemplate){
        if (zombieTemplate != null && zombieTemplate.getAlias() != null){
            zombieTemplatesByAlias.put(zombieTemplate.getAlias(), zombieTemplate);
        }
    }
}
