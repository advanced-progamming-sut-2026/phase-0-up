package models.shop;

public class ShopItem {
    private final int id;
    private final String name;
    private final int price;
    private final Currency currency;
    private final int capacity;

    public ShopItem(int id, String name, int price, Currency currency, int capacity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.capacity = capacity;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public Currency getCurrency() {
        return currency;
    }

    public int getCapacity() {
        return capacity;
    }
}
