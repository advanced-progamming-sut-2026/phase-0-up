package controllers.systems;

import models.news.News;
import models.user.Profile;

public class NewsSystem {
    private static NewsSystem instance;

    private NewsSystem() {}
    public static NewsSystem getInstance() {
        if (instance == null) {
            instance = new NewsSystem();
        }
        return instance;
    }

    public void addNews(Profile profile, News news) {}
    public void readNews(Profile profile, News news) {}

}
