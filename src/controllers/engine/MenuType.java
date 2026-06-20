package controllers.engine;

public enum MenuType {
    LOGIN_MENU("login"),
    MAIN_MENU("main"),
    PROFILE_MENU("profile"),
    SHOP_MENU("shop"),
    COLLECTION_MENU("collection"),
    PLAY_MENU("play"),
    IN_GAME("game"),
    GREENHOUSE_MENU("greenhouse"),
    NEWS_MENU("news"),
    SIGNUP_MENU("signup"),
    PLANTS_MENU("plants"),
    SETTINGS_MENU("settings"),
    ONLINE_MENU("online"),
    TRAVEL_LOG_MENU("travel-log"),
    LEADERBOARD("leaderboard");

    private final String menuName;

    MenuType(String menuName) {
        this.menuName = menuName;
    }

    public String getMenuName() {
        return menuName;
    }

    public static MenuType fromName(String name) {
        if (name == null) {
            return null;
        }

        for (MenuType menu : MenuType.values()) {
            if (menu.getMenuName().equalsIgnoreCase(name)) {

                return menu;
            }
        }
        return null;
    }
}