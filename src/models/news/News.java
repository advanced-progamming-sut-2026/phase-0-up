package models.news;

public class News {
    private int id;
    private String title;
    private String description;
    private String author;
    private String date;
    private boolean read;

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

    public boolean isRead() {
        return read;
    }
}
