package models.shop;


public class DailyOffer {
    private final int id;
    private final int date;
    private final String plantType;
    private final int basePrice;
    private final int discountPrice;
    private boolean purchased;

    public DailyOffer(int id, int date, String plantType, int basePrice, int discountPrice, boolean purchased) {
        this.id = id;
        this.date = date;
        this.plantType = plantType;
        this.basePrice = basePrice;
        this.discountPrice = discountPrice;
        this.purchased = purchased;
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

    public boolean isPurchased() {
        return purchased;
    }

    public void setPurchased(boolean purchased) {
        this.purchased = purchased;
    }
}
