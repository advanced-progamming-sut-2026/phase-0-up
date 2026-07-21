package models.news;

public class News {
    private int id;
    private String title;
    private String description;
    private String author;
    private String date;
    private boolean read;
    // Source/category of this entry. Lets the same News structure carry system announcements today and
    // network / other-player messages in a later phase (see NewsType). Older save files predate this
    // field, so a deserialized News may have a null type -- getType() treats that as SYSTEM.
    private NewsType type;

    public News(int id, String title, String description, String author, String date) {
        this(id, title, description, author, date, NewsType.SYSTEM);
    }

    public News(int id, String title, String description, String author, String date, NewsType type) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.author = author;
        this.date = date;
        this.read = false;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getDate() {
        return date;
    }

    public NewsType getType() {
        return type == null ? NewsType.SYSTEM : type;
    }

    public boolean isRead() {
        return read;
    }
    public void markAsRead() {
        this.read = true;
    }
}
