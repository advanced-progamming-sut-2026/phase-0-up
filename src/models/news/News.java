package models.news;

public class News {
    private int id;
    private String title;
    private String description;
    private String author;
    private String date;
    private boolean read;

    public News(int id, String title, String description, String author, String date) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.author = author;
        this.date = date;
        this.read = false;
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

    public boolean isRead() {
        return read;
    }
    public void markAsRead() {
        this.read = true;
    }
}
