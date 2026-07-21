package controllers.systems;

import models.news.News;
import models.news.NewsType;
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

    private News buildNews(Profile profile, String title, String description, NewsType type) {
        return new News(nextId(profile), title, description,
                SYSTEM_AUTHOR, LocalDate.now().toString(), type);
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
        addNews(profile, buildNews(profile, "A New Sprout Joins the Fight",
                plantName + " has been added to your almanac. Give it a lawn to defend!",
                NewsType.PLANT_UNLOCK));
    }

    public void addZombieUnlockNews(Profile profile, String zombieName) {
        addNews(profile, buildNews(profile, "New Face in the Horde",
                "You've met a " + zombieName + " and lived to file the paperwork. "
                        + "It's in your almanac now.", NewsType.ZOMBIE_DISCOVERY));
    }

    public void addLevelUnlockNews(Profile profile, String levelName) {
        addNews(profile, buildNews(profile, "Fresh Turf Ahead",
                "A new level just opened up: " + levelName + ". The zombies are already queuing.",
                NewsType.LEVEL_UNLOCK));
    }

    public void addMinigameUnlockNews(Profile profile, String minigameName) {
        addNews(profile, buildNews(profile, "New Mini-Game Unlocked",
                minigameName + " is ready to play. Think you can handle it?",
                NewsType.MINIGAME_UNLOCK));
    }

}
