package controllers.systems;

import models.news.News;
import models.user.Profile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class NewsSystem {
    private static NewsSystem instance;
    private static final String SYSTEM_AUTHOR = "System";

    private NewsSystem() {}
    public static NewsSystem getInstance() {
        if (instance == null) {
            instance = new NewsSystem();
        }
        return instance;
    }

    public void addNews(Profile profile, News news) {
        if (profile == null || news == null) {
            return;
        }
        profile.addNews(news);
    }

    public void readNews(Profile profile, News news) {
        if (profile == null || news == null) {
            return;
        }
        news.markAsRead();
    }

    public List<News> getUnreadNews(Profile profile) {
        List<News> unread = new ArrayList<>();
        if (profile == null || profile.getNewsList() == null) {
            return unread;
        }
        for (News news : profile.getNewsList()) {
            if (!news.isRead()) {
                unread.add(news);
            }
        }
        return unread;
    }

    public void markAllRead(Profile profile) {
        if (profile == null || profile.getNewsList() == null) {
            return;
        }
        for (News news : profile.getNewsList()) {
            news.markAsRead();
        }
    }

    public boolean hasUnreadNews(Profile profile) {
        return !getUnreadNews(profile).isEmpty();
    }

    private News buildNews(Profile profile, String title, String description) {
        return new News(nextId(profile), title, description,
                SYSTEM_AUTHOR, LocalDate.now().toString());
    }

    private int nextId(Profile profile) {
        if (profile == null || profile.getNewsList() == null || profile.getNewsList().isEmpty()) {
            return 1;
        }
        int maxId = 0;
        for (News news : profile.getNewsList()) {
            if (news.getId() > maxId) {
                maxId = news.getId();
            }
        }
        return maxId + 1;
    }

    public void addPlantUnlockNews(Profile profile, String plantName) {
        addNews(profile, buildNews(profile, "New Plant Unlocked",
                "You have unlocked a new plant: " + plantName + "."));
    }

    public void addZombieUnlockNews(Profile profile, String zombieName) {
        addNews(profile, buildNews(profile, "New Zombie Encountered",
                "A new zombie has been added to your collection: " + zombieName + "."));
    }

    public void addLevelUnlockNews(Profile profile, String levelName) {
        addNews(profile, buildNews(profile, "New Level Unlocked",
                "A new level is now available: " + levelName + "."));
    }

    public void addMinigameUnlockNews(Profile profile, String minigameName) {
        addNews(profile, buildNews(profile, "New Minigame Unlocked",
                "A new minigame is now available: " + minigameName + "."));
    }

}
