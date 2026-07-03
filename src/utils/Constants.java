package utils;

public class Constants {

    // ==========================================
    // ۱. سیستم زمان، نقشه و محیط (Time & Board)
    // ==========================================
    public static final int TICK_IN_SECONDS = 10;       // هر تیک معادل ۱۰ ثانیه درون بازی است
    public static final int BOARD_ROWS = 5;             // تعداد ردیف‌های حیاط
    public static final int BOARD_COLS = 9;             // تعداد ستون‌های حیاط

    public static final double LAWNMOWER_SPEED = 0.6;
    public static final double LAWNMOWER_ACTIVATION_THRESHOLD = 0.0;
    public static final double LAWNMOWER_END_POSITION = 9.0;


    // ==========================================
    // ۲. مکانیک‌های خورشید (Sun Mechanics)
    // ==========================================
    public static final int NORMAL_SUN_AMOUNT = 25;
    public static final int SPECIAL_SUN_AMOUNT = 100;

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
    public static final double WAVE_DIFFICULTY_INCREMENT = 1.25;   // ۲۵ درصد سخت‌تر از موج قبلی
    public static final double FLAG_WAVE_MULTIPLIER = 2.0;         // ۲ برابر سختی برای اَبَرموج (موج آخر)
    public static final double NEXT_WAVE_HP_THRESHOLD = 0.75;      // شروع موج بعد وقتی ۷۵٪ جان موج قبل رفته باشد

    // ==========================================
    // ۴. دراپ‌ها و جوایز (Loot Drops)
    // ==========================================
    public static final double ZOMBIE_DROP_PROBABILITY = 0.10;     // ۱۰ درصد احتمال دراپ هنگام مرگ زامبی
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
    public static final int CONVEYOR_BELT_INTERVAL_SECONDS = 12;   // زمان رسیدن گیاه جدید روی تسمه نقاله
    public static final int BEGHOULED_MATCH_SUN_REWARD = 50;       // پاداش خورشید در ترکیب ۳‌تایی Beghouled

    // ==========================================
    // ۹. تنظیمات سیستمی (System Config)
    // ==========================================
    public static final String PASSWORD_HASH_ALGORITHM = "SHA-256";

    public static final String[] SECURITY_QUESTIONS = {
            "1. What was the name of your first pet?",
            "2. In what city were you born?",
            "3. What was your favorite childhood book?",
            "4. What was the model of your first car?",
            "5. What was your best friend's name in high school?"
    };

    public static final int DEFAULT_GAME_NUMBERS = 0;
    public static final int DEFAULT_INITIAL_COINS = 2000;
    public static final int DEFAULT_INITIAL_GEMS = 100;
    public static final int DEFAULT_PLANT_FOOD_COUNT = 0;
    public static final int DEFAULT_DIFFICULTY_LEVEL = 3;
    public static final int DEFAULT_BEST_MEOW_POINTS = 0;
    public static final int DEFAULT_LAST_CHAPTER = 1;
    public static final int DEFAULT_LAST_LEVEL = 1;
    public static final int DEFAULT_DAILY_QUESTS_DONE = 0;
    public static final int DEFAULT_NONE_DAILY_QUESTS_DONE = 0;
    public static final boolean DEFAULT_HAS_BOUGHT_DAILY_OFFER = false;

    // =====================================================
    // ۱۰. منوی انتخاب گیاه (Plant Selection Menu)
    // =====================================================
    public static final int DEFAULT_SEED_SLOTS = 8;

}