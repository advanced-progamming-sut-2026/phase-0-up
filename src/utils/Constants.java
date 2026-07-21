package utils;

public class Constants {

    // ==========================================
    // ۱. سیستم زمان، نقشه و محیط (Time & Board)
    // ==========================================
    public static final int TICKS_PER_SECOND = 10;       // هر تیک معادل ۱۰ ثانیه درون بازی است
    public static final int BOARD_ROWS = 5;             // تعداد ردیف‌های حیاط
    public static final int BOARD_COLS = 9;             // تعداد ستون‌های حیاط

    public static final double LAWNMOWER_SPEED = 0.6;
    public static final double LAWNMOWER_ACTIVATION_THRESHOLD = 0.0;
    public static final double LAWNMOWER_END_POSITION = 9.0;

    // A zombie standing in column 0 (the tile nearest the house) sits at x in [0, 1). Killing one here
    // in a row whose mower is already spent is what the Almost Victorious quest counts.
    public static final double FIRST_COLUMN_MAX_X = 1.0;


    // ==========================================
    // ۲. مکانیک‌های خورشید (Sun Mechanics)
    // ==========================================
    public static final int NORMAL_SUN_AMOUNT = 25;
    public static final int SPECIAL_SUN_AMOUNT = 100;
    public static final int RADIOACTIVE_SUN_AMOUNT = 150;

    public static final double NORMAL_SUN_PROBABILITY = 0.80;      // ۸۰ درصد احتمال خورشید معمولی
    public static final double SPECIAL_SUN_PROBABILITY = 0.15;     // ۱۵ درصد احتمال خورشید ویژه
    public static final double RADIOACTIVE_SUN_PROBABILITY = 0.05; // ۵ درصد احتمال خورشید رادیواکتیو

    // مشخصات خورشید رادیواکتیو
    public static final int RADIOACTIVE_PLANT_DAMAGE = 150;
    public static final int RADIOACTIVE_ZOMBIE_DAMAGE = 80;
    public static final int RADIOACTIVE_PLANT_AOE_SIZE = 3;        // مربع ۳ در ۳ برای گیاهان
    public static final int RADIOACTIVE_ZOMBIE_AOE_SIZE = 5;       // مربع ۵ در ۵ برای زامبی‌ها

    public static final int SUN_FALL_DURATION_SECONDS = 5;         // زمان رسیدن خورشید از آسمان به زمین

    // ==========================================
    // ۳. امواج حمله زامبی‌ها (Wave System)
    // ==========================================
    // Global multiplier on every zombie's walking speed. 1.0 = normal; >1 = faster, <1 = slower.
    // Tweak this to speed up or slow down the whole horde to taste.
    public static final double ZOMBIE_SPEED_SCALE = 0.108;

    public static final double WAVE_DIFFICULTY_INCREMENT = 1.25;   // ۲۵ درصد سخت‌تر از موج قبلی
    public static final double FLAG_WAVE_MULTIPLIER = 2.0;         // ۲ برابر سختی برای اَبَرموج (موج آخر)
    public static final double NEXT_WAVE_HP_THRESHOLD = 0.75;      // شروع موج بعد وقتی ۷۵٪ جان موج قبل رفته باشد

    // ساحل امواج: جزر و مد چند ستون بیشتر را می‌پوشاند و پس می‌کشد
    public static final int TIDE_MAX_RISE = 2;                     // حداکثر ستون‌های اضافه‌ی زیر آب
    public static final int TIDE_SAFE_COLUMNS = 3;                 // این تعداد ستون سمت چپ هرگز غرق نمی‌شوند
    public static final double LOW_TIDE_SURFACE_PROBABILITY = 0.5; // احتمال ظهور زامبی از ساحل پست غرق‌شده

    // زامبی‌ها از یک خانه بیرون لبه‌ی راست وارد می‌شوند و به سمت چپ راه می‌روند
    public static final double ZOMBIE_SPAWN_X = 9.5;
    public static final int DEFAULT_WAVE_DELAY_SECONDS = 25;       // وقتی levels.json تاخیری تعیین نکرده
    public static final int DEFAULT_FIRST_WAVE_BUDGET = 1000;      // وقتی levels.json بودجه‌ای تعیین نکرده

    // فرصت اولیه‌ی بازیکن برای ساختن دفاع؛ عمداً بیشتر از فاصله‌ی بین دو موج (بیشترین مقدار در
    // levels.json برابر ۵۵ ثانیه است) تا موج اول دیرتر از بقیه‌ی موج‌ها برسد
    public static final int FIRST_WAVE_DELAY_SECONDS = 60;
    // زامبی‌های یک موج قطره‌ای وارد می‌شوند، نه همه با هم
    public static final int ZOMBIE_SPAWN_INTERVAL_SECONDS = 2;

    // ==========================================
    // ۴. دراپ‌ها و جوایز (Loot Drops)
    // ==========================================
    public static final double ZOMBIE_DROP_PROBABILITY = 0.10;     // ۱۰ درصد احتمال دراپ هنگام مرگ زامبی
    public static final double GLOWING_ZOMBIE_PROBABILITY = 0.05;  // ۵ درصد احتمال درخشان بودن زامبی هنگام ظهور
    public static final int DROP_COIN_AMOUNT = 50;
    public static final int DROP_DIAMOND_AMOUNT = 1;
    public static final int DROP_POT_AMOUNT = 1;                   // گلدان برای گلخانه

    // ==========================================
    // ۵. فروشگاه و اقتصاد (Shop & Economy)
    // ==========================================
    public static final int NEW_PLANT_COST_COINS = 2000;           // قیمت خرید گیاه جدید
    public static final int UPGRADE_PLANT_COST_COINS = 1000;          // هزینه سکه‌ای ارتقای گیاه (inferred)
    public static final int UPGRADE_PLANT_REQUIRED_SEED_PACKETS = 5;  // تعداد seed packet لازم برای ارتقا (inferred)
    public static final int BOOST_PLANT_COST_GEMS = 2;             // هزینه بوست کردن گیاه در صفحه انتخاب

    public static final int MAX_PLANT_FOOD_CAPACITY = 3;           // سقف نگهداری غذای گیاه
    public static final int PLANT_FOOD_COST_GEMS = 3;              // هزینه خرید غذای گیاه از فروشگاه

    // بسته‌های بذر (Seed Packets)
    public static final int RANDOM_SEED_PACKET_COST_COINS = 1000;
    public static final int RANDOM_SEED_PACKET_COUNT = 5;

    public static final int CHOSEN_SEED_PACKET_COST_GEMS = 10;
    public static final int CHOSEN_SEED_PACKET_COUNT = 10;

    public static final int DAILY_OFFER_COST_COINS = 1600;         // ۲۰ درصد تخفیف روی قیمت پایه ۲۰۰۰
    public static final int DAILY_OFFER_COUNT = 10;

    // تبدیل ارز
    public static final int GEM_TO_COIN_CONVERSION_GEMS = 5;
    public static final int GEM_TO_COIN_CONVERSION_COINS = 500;    // ۵ الماس = ۵۰۰ سکه

    // ==========================================
    // ۶. گلخانه (Greenhouse)
    // ==========================================
    public static final int GREENHOUSE_ROWS = 4;
    public static final int GREENHOUSE_COLS = 5;
    public static final int GREENHOUSE_POT_COST_COINS = 2000;      // هزینه باز کردن قفل گلدان

    public static final int NORMAL_FLOWER_GROWTH_HOURS = 2;        // زمان رشد گل معمولی (Marigold)
    public static final int RANDOM_PLANT_GROWTH_HOURS = 8;         // زمان رشد گیاه تصادفی
    public static final int GREENHOUSE_SPEED_UP_COST_PER_HOUR = 1; // ۱ الماس برای تسریع هر ساعت
    public static final int MARIGOLD_HARVEST_REWARD_COINS = 500;   // جایزه برداشت گل معمولی

    // ==========================================
    // ۷. مقاومت‌ها، آرمورها و موانع (Terrains & Armors)
    // ==========================================
    public static final int GRAVE_HP = 700;                        // سلامتی سنگ قبر
    public static final int FROZEN_HP = 600;                       // سلامتی زمین یخ‌زده
    public static final int MELT_RATE_PER_SECOND = 60;             // نرخ ذوب شدن یخ (۶۰ سلامتی در ثانیه)

    // مقاومت زره زامبی‌ها
    public static final int ARMOR_CONE_HP = 370;                   // مخروط
    public static final int ARMOR_BUCKET_HP = 1100;                // سطل
    public static final int ARMOR_KNIGHT_HELMET_HP = 1600;         // کلاه‌خود شوالیه
    public static final int ARMOR_KNIGHT_SHOULDER_HP = 1600;       // شانه‌بند شوالیه
    public static final int ARMOR_BLOCK_HP = 2200;                 // بلوک

    // ==========================================
    // ۸. مینی‌گیم‌ها و مراحل ویژه (Minigames)
    // ==========================================
    public static final int BEGHOULED_MATCH_SUN_REWARD = 50;       // پاداش خورشید در ترکیب ۳‌تایی Beghouled

    // ==========================================
    // ۹. تنظیمات سیستمی (System Config)
    // ==========================================
    public static final String PASSWORD_HASH_ALGORITHM = "SHA-256";

    public static final String[] SECURITY_QUESTIONS = {
            "1. What was the name of your first pet?",
            "2. Which city you were born in?",
            "3. What was your favorite childhood book?",
            "4. What was the model of your first car?",
            "5. What was your best friend's name in high school?"
    };

    public static final int DEFAULT_GAME_NUMBERS = 0;
    public static final int DEFAULT_INITIAL_COINS = 2000;
    public static final int DEFAULT_INITIAL_GEMS = 100;
    public static final int DEFAULT_PLANT_FOOD_COUNT = 0;
    public static final int DEFAULT_DIFFICULTY_LEVEL = 3;
    // Difficulty runs 1..5 (ChangeDifficultyCommand's accepted range); 5 is "maximum difficulty",
    // the bar the Win After Win quest measures a winning streak against.
    public static final int MAX_DIFFICULTY_LEVEL = 5;
    public static final int DEFAULT_BEST_MEOW_POINTS = 0;
    public static final int DEFAULT_LAST_CHAPTER = 1;
    public static final int DEFAULT_LAST_LEVEL = 1;
    // The campaign is 4 chapters of 4 levels each; lastChapter/lastLevel are pointers to the next
    // unlocked level, so the number of levels a profile has actually finished is
    // (lastChapter-1)*LEVELS_PER_CHAPTER + (lastLevel-1). Used by the leaderboard to show the last
    // completed stage and by ShowProfileCommand to count completed levels.
    public static final int LEVELS_PER_CHAPTER = 4;
    // Each mini-game hosts this many progressively harder levels (Travel Log). Clearing a level unlocks
    // the next, up to this cap; the leaderboard's mini-game tally is therefore 0..MINIGAME_LEVELS each.
    public static final int MINIGAME_LEVELS = 3;
    public static final int DEFAULT_DAILY_QUESTS_DONE = 0;
    public static final int DEFAULT_NONE_DAILY_QUESTS_DONE = 0;
    public static final boolean DEFAULT_HAS_BOUGHT_DAILY_OFFER = false;

    // =====================================================
    // ۱۰. منوی انتخاب گیاه (Plant Selection Menu)
    // =====================================================
    public static final int DEFAULT_SEED_SLOTS = 8;

    // گیاهان اولیه‌ی هر پروفایل (همان استخر گیاهان مرحله‌ی اول فصل اول)
    public static final String[] STARTING_PLANTS = {
            "Sunflower", "Peashooter", "Wall-nut", "Potato Mine", "Snow Pea"
    };

}