package models.shop;


// What is on offer today, and for how much.
//
// Deliberately carries no "purchased" flag. It used to, and that was the whole of the daily-deal bug:
// the Shop that owns this object is built fresh with the AppSession and never written to the save file,
// so the flag forgot every purchase on exit -- and nothing read it before selling either, so one deal
// could be bought as many times a day as the player had coins for.
//
// Whether the deal has been taken is per-PLAYER state and lives where the rest of the player's daily
// state already does, on the Profile: see Profile.isHasBoughtDailyOfferToday, which is persisted through
// ProfileRecord and cleared by the same LocalDate rollover that resets the daily quests.
public class DailyOffer {
    private final int id;
    private final int date;
    private final String plantType;
    private final int basePrice;
    private final int discountPrice;
    // How many seed packets the deal hands over. Part of the offer, so it is stated once here rather
    // than as a 10 written into the command that grants them and a "Ten packets" written into the
    // screen that advertises them.
    private final int packets;

    public DailyOffer(int id, int date, String plantType, int basePrice, int discountPrice, int packets) {
        this.id = id;
        this.date = date;
        this.plantType = plantType;
        this.basePrice = basePrice;
        this.discountPrice = discountPrice;
        this.packets = packets;
    }

    public int getPackets() {
        return packets;
    }

    public int getId() {
        return id;
    }

    public int getDate() {
        return date;
    }

    public String getPlantType() {
        return plantType;
    }

    public int getBasePrice() {
        return basePrice;
    }

    public int getDiscountPrice() {
        return discountPrice;
    }

}
