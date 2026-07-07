package models.templates;

import java.util.List;

public class ZombieTemplate {
   private int id;
   private String category;
   private int baseHp;
   private List<String> armorTypes;
   private String alias;
   private int eatDamage;
   private int eatSpeed;
   private double speed;
   private int wavePointCost;
   private boolean canSpawnPlantFood;
   private String behavior;
   private List<String> specialAbilities;

   public int getId() {
      return id;
   }

   public String getCategory() {
      return category;
   }

   public int getBaseHp() {
      return baseHp;
   }

   public List<String> getArmorHp() {
      return armorTypes;
   }

   public String getAlias() {
      return alias;
   }

   public int getEatDamage() {
      return eatDamage;
   }

   public int getEatSpeed() {
      return eatSpeed;
   }

   public double getSpeed() {
      return speed;
   }

   public int getWavePointCost() {
      return wavePointCost;
   }

   public boolean isCanSpawnPlantFood() {
      return canSpawnPlantFood;
   }

   public String getBehavior() {
      return behavior;
   }

   public List<String> getSpecialAbilities() {
      return specialAbilities;
   }
}
