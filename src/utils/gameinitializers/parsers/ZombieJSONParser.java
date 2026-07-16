package utils.gameinitializers.parsers;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import models.entities.zombies.Components.ArmorType;
import models.templates.ZombieTemplate;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Reads the raw PopCap zombie dump (each entry is {aliases, objclass, objdata}) and distills each one
// into a clean ZombieTemplate. Only the fields the engine needs are pulled from objdata; the rest of
// the (very large) source objects are ignored. Armor RTID strings are resolved to ArmorType constants.
public class ZombieJSONParser implements Parser<ZombieTemplate> {
    private final Gson gson = new Gson();

    @Override
    public List<ZombieTemplate> parse(String filePath) {
        try (Reader reader = new FileReader(filePath)) {
            Type listType = new TypeToken<List<RawZombie>>() { }.getType();
            List<RawZombie> raws = gson.fromJson(reader, listType);
            if (raws == null) {
                return Collections.emptyList();
            }
            List<ZombieTemplate> templates = new ArrayList<>();
            for (RawZombie raw : raws) {
                ZombieTemplate template = toTemplate(raw);
                if (template != null) {
                    templates.add(template);
                }
            }
            return templates;
        } catch (IOException e) {
            System.err.println("Failed to load zombie data from " + filePath + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private ZombieTemplate toTemplate(RawZombie raw) {
        if (raw == null || raw.aliases == null || raw.aliases.isEmpty() || raw.objdata == null) {
            return null;
        }
        ObjData d = raw.objdata;
        return new ZombieTemplate(
                raw.aliases.get(0),
                raw.objclass,
                d.hitpoints,
                d.speed,
                d.eatDps,
                d.wavePointCost,
                d.canSpawnPlantFood,
                parseArmors(d.zombieArmorProps));
    }

    // Turns ["RTID(ConeDefault@ArmorTypes)", ...] into [CONE, ...]. Base body is added by the factory.
    private List<ArmorType> parseArmors(List<String> rtids) {
        List<ArmorType> armors = new ArrayList<>();
        if (rtids == null) {
            return armors;
        }
        for (String rtid : rtids) {
            ArmorType armor = parseArmor(rtid);
            if (armor != null) {
                armors.add(armor);
            }
        }
        return armors;
    }

    private ArmorType parseArmor(String rtid) {
        if (rtid == null) {
            return null;
        }
        // "RTID(ConeDefault@ArmorTypes)" -> "ConeDefault" -> "Cone"
        int open = rtid.indexOf('(');
        int at = rtid.indexOf('@');
        if (open < 0 || at < 0 || at <= open + 1) {
            return null;
        }
        String token = rtid.substring(open + 1, at).replace("Default", "");
        switch (token) {
            case "Cone": return ArmorType.CONE;
            case "Bucket": return ArmorType.BUCKET;
            case "Brick": return ArmorType.BRICK;
            case "ShoulderArmor": return ArmorType.SHOULDER_ARMOR;
            case "Crown": return ArmorType.CROWN;
            case "Newspaper": return ArmorType.NEWSPAPER;
            default: return null; // unknown / not-yet-modelled armor
        }
    }

    // --- Raw JSON shapes (only the fields we consume) ---

    private static class RawZombie {
        private List<String> aliases;
        private String objclass;
        private ObjData objdata;
    }

    private static class ObjData {
        @SerializedName("Hitpoints") private int hitpoints;
        @SerializedName("Speed") private double speed;
        @SerializedName("EatDPS") private int eatDps;
        @SerializedName("WavePointCost") private int wavePointCost;
        @SerializedName("CanSpawnPlantFood") private boolean canSpawnPlantFood;
        @SerializedName("ZombieArmorProps") private List<String> zombieArmorProps;
    }
}
