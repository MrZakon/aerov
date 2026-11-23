package net.aero.visuals.localization;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.Setting;
import net.minecraft.client.resources.language.I18n;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Complete Bilingual localization engine (Russian by default & English toggle). */
public final class AeroI18n {
    public static final String PREFIX = "aerovisuals.";

    /** 1 = Russian (Default for AeroVisuals), 0 = English. */
    public static int languageMode = 1;

    private static final Map<String, String> RU_MAP = new HashMap<>();
    private static final Map<String, String> EN_MAP = new HashMap<>();
    private static final Map<String, String> UA_MAP = new HashMap<>();

    private static final Map<String, String> RU_MODULE_NAMES = new HashMap<>();
    private static final Map<String, String> RU_MODULE_DESCS = new HashMap<>();
    private static final Map<String, String> EN_MODULE_NAMES = new HashMap<>();
    private static final Map<String, String> EN_MODULE_DESCS = new HashMap<>();
    private static final Map<String, String> UA_MODULE_NAMES = new HashMap<>();
    private static final Map<String, String> UA_MODULE_DESCS = new HashMap<>();

    static {
        RU_MODULE_NAMES.put("frame_time_hud", "График плавности");
        RU_MODULE_DESCS.put("frame_time_hud", "Время последних кадров в миллисекундах");
        RU_MODULE_NAMES.put("adaptive_effects", "Адаптивные эффекты");
        RU_MODULE_NAMES.put("ambience", "Освещение и окружение");
        RU_MODULE_NAMES.put("animated_hotbar", "Анимированный хотбар");
        RU_MODULE_NAMES.put("armor_hud", "Броня на экране");
        RU_MODULE_NAMES.put("armor_status_dura", "Прочность брони");
        RU_MODULE_NAMES.put("beacon_waypoints", "Метки маяков");
        RU_MODULE_NAMES.put("bioluminescent_foliage", "Биолюминесцентная листва");
        RU_MODULE_NAMES.put("biome_atmosphere", "Атмосфера биомов");
        RU_MODULE_NAMES.put("block_place_ripple", "Волна установки блока");
        RU_MODULE_NAMES.put("blood_splatter", "Брызги крови");
        RU_MODULE_NAMES.put("body_aura", "Аура тела");
        RU_MODULE_NAMES.put("boss_bar_eclipsed", "Затменный боссбар");
        RU_MODULE_NAMES.put("bow_laser", "Лазерный прицел лука");
        RU_MODULE_NAMES.put("cape_wings", "Крылья на плаще");
        RU_MODULE_NAMES.put("celestial_bodies", "Кастомные светила");
        RU_MODULE_NAMES.put("chat_mention_glow", "Подсветка в чате");
        RU_MODULE_NAMES.put("china_hat", "Коническая шляпа");
        RU_MODULE_NAMES.put("chromatic_aberration", "Хроматическая аберрация");
        RU_MODULE_NAMES.put("cinematic_glide", "Кинематографичная камера");
        RU_MODULE_NAMES.put("combo_counter", "Счетчик комбо");
        RU_MODULE_NAMES.put("companion_spirit", "Дух-спутник");
        RU_MODULE_NAMES.put("inventory_hud", "Инвентарь");
        RU_MODULE_NAMES.put("container_preview", "Инвентарь");
        RU_MODULE_NAMES.put("notifications", "Уведомления");
        RU_MODULE_NAMES.put("cosmetic_preview", "Превью косметики");
        RU_MODULE_NAMES.put("crosshair_physics", "Физика прицела");
        RU_MODULE_NAMES.put("custom_chat", "Кастомный чат");
        RU_MODULE_NAMES.put("custom_crosshair", "Кастомный прицел");
        RU_MODULE_NAMES.put("custom_hitboxes", "Кастомные хитбоксы");
        RU_MODULE_NAMES.put("cyber_aurora", "Кибер-северное сияние");
        RU_MODULE_NAMES.put("cyber_crt", "Ретро Cyber CRT");
        RU_MODULE_NAMES.put("damage_indicators", "Индикаторы урона");
        RU_MODULE_NAMES.put("death_effects", "Эффекты смерти");
        RU_MODULE_NAMES.put("depth_fog", "Атмосферный туман глубины");
        RU_MODULE_NAMES.put("dynamic_fov", "Динамический FOV");
        RU_MODULE_NAMES.put("elytra_banking", "Крен элитр");
        RU_MODULE_NAMES.put("emissive_eyes", "Светящиеся глаза");
        RU_MODULE_NAMES.put("energy_shield", "Энергетический щит");
        RU_MODULE_NAMES.put("enhanced_tooltips", "Улучшенные подсказки");
        RU_MODULE_NAMES.put("fluid_waves", "Волны жидкостей");
        RU_MODULE_NAMES.put("footprint_decals", "Следы на земле");
        RU_MODULE_NAMES.put("fractal_lightning", "Фрактальные молнии");
        RU_MODULE_NAMES.put("freelook", "Свободный обзор");
        RU_MODULE_NAMES.put("frostbite_vignette", "Виньетка обморожения");
        RU_MODULE_NAMES.put("fullbright", "Яркость (FullBright)");
        RU_MODULE_NAMES.put("ghost_afterimages", "Призрачные фантомы");
        RU_MODULE_NAMES.put("glow_block_outline", "Светящаяся обводка блоков");
        RU_MODULE_NAMES.put("god_rays", "Объемные лучи солнца");
        RU_MODULE_NAMES.put("ground_aura", "Круг под ногами");
        RU_MODULE_NAMES.put("heartbeat_vignette", "Пульсация при низком HP");
        RU_MODULE_NAMES.put("hit_color", "Цвет удара");
        RU_MODULE_NAMES.put("holographic_drone", "Голографический дрон");
        RU_MODULE_NAMES.put("holographic_wings", "Голографические крылья");
        RU_MODULE_NAMES.put("item_glint_overlay", "Блеск зачарований");
        RU_MODULE_NAMES.put("item_inspect", "Осмотр предмета");
        RU_MODULE_NAMES.put("item_physics_3d", "3D Физика предметов");
        RU_MODULE_NAMES.put("item_rarity_beams", "Лучи редкости предметов");
        RU_MODULE_NAMES.put("item_scroller", "Быстрая прокрутка");
        RU_MODULE_NAMES.put("jump_hit_particles", "Частицы крит-ударов");
        RU_MODULE_NAMES.put("jump_ripples", "Кинетические кольца");
        RU_MODULE_NAMES.put("keystrokes", "Кейстроксы");
        RU_MODULE_NAMES.put("kill_shockwave", "Ударная волна при убийстве");
        RU_MODULE_NAMES.put("kinetic_impact", "Кинетический импульс");
        RU_MODULE_NAMES.put("landing_fx", "Эффект приземления");
        RU_MODULE_NAMES.put("lava_ember_physics", "Искры лавы");
        RU_MODULE_NAMES.put("low_fire", "Низкий огонь");
        RU_MODULE_NAMES.put("lunacy_bloom", "Неоновый блум");
        RU_MODULE_NAMES.put("media_player", "Медиаплеер");
        RU_MODULE_NAMES.put("meteor_shower", "Метеорный поток");
        RU_MODULE_NAMES.put("minimal_compass", "Минималистичный компас");
        RU_MODULE_NAMES.put("motion_trails", "Шлейфы движений");
        RU_MODULE_NAMES.put("nametags", "Неймтаги");
        RU_MODULE_NAMES.put("nether_sky_fx", "Эффекты неба Незера");
        RU_MODULE_NAMES.put("orbital_halo", "Орбитальный нимб");
        RU_MODULE_NAMES.put("orbiting_cosmetics", "Орбитальные сферы");
        RU_MODULE_NAMES.put("particle_trail_3d", "3D Шлейф частиц");
        RU_MODULE_NAMES.put("pearl_prediction", "Траектория жемчуга");
        RU_MODULE_NAMES.put("photo_mode", "Фото-режим");
        RU_MODULE_NAMES.put("portal_warp", "Искажение портала");
        RU_MODULE_NAMES.put("potion_hud", "Эффекты зелий");
        RU_MODULE_NAMES.put("projectile_prediction", "Траектория снарядов");
        RU_MODULE_NAMES.put("radar_3d", "Мини-радар 3D");
        RU_MODULE_NAMES.put("radial_menu", "Круговое меню");
        RU_MODULE_NAMES.put("reflective_puddles", "Отражающие лужи");
        RU_MODULE_NAMES.put("shield_pulse_barrier", "Барьер щита");
        RU_MODULE_NAMES.put("shield_status", "Статус щита");
        RU_MODULE_NAMES.put("smart_hud", "Умный HUD");
        RU_MODULE_NAMES.put("sound_visualizer", "Визуализатор звука");
        RU_MODULE_NAMES.put("sparks_ricochet", "Искры рикошета");
        RU_MODULE_NAMES.put("speed_lines", "Линии гиперскорости");
        RU_MODULE_NAMES.put("spring_transitions", "Пружинные анимации");
        RU_MODULE_NAMES.put("target_lock_reticle", "Захват цели");
        RU_MODULE_NAMES.put("totem_counter", "Счетчик тотемов");
        RU_MODULE_NAMES.put("totem_pop", "Срабатывание тотема");
        RU_MODULE_NAMES.put("underwater_refraction", "Преломление под водой");
        RU_MODULE_NAMES.put("verlet_cape", "Физический плащ Верле");
        RU_MODULE_NAMES.put("view_model", "Позиция рук");
        RU_MODULE_NAMES.put("visual_studio", "Студия визуалов");
        RU_MODULE_NAMES.put("void_abyss_glow", "Свечение бездны");
        RU_MODULE_NAMES.put("volumetric_nameplates", "Объемные неймтаги");
        RU_MODULE_NAMES.put("voxel_death", "Воксельная гибель");
        RU_MODULE_NAMES.put("watermark", "Вотермарка клиента");
        RU_MODULE_NAMES.put("weapon_trails", "Шлейфы оружия");
        RU_MODULE_DESCS.put("adaptive_effects", "Динамическая подстройка эффектов под производительность");
        RU_MODULE_DESCS.put("ambience", "Настройка цвета неба, тумана и времени суток");
        RU_MODULE_DESCS.put("animated_hotbar", "Плавная анимация переключения слотов хотбара");
        RU_MODULE_DESCS.put("armor_hud", "Отображение слотов брони и прочности в HUD");
        RU_MODULE_DESCS.put("armor_status_dura", "Отображение экипированной брони и ее оставшейся прочности");
        RU_MODULE_DESCS.put("beacon_waypoints", "Световые столбы и дистанция до маяков и ключевых точек");
        RU_MODULE_DESCS.put("bioluminescent_foliage", "Ночное свечение листвы, травы и грибов");
        RU_MODULE_DESCS.put("biome_atmosphere", "Уникальная цветовая гамма и частицы для каждого биома");
        RU_MODULE_DESCS.put("block_place_ripple", "Круговая импульсная волна при размещении блоков");
        RU_MODULE_DESCS.put("blood_splatter", "Эффект брызг крови и капель при получении урона");
        RU_MODULE_DESCS.put("body_aura", "Энергетическая аура вокруг модели персонажа");
        RU_MODULE_DESCS.put("boss_bar_eclipsed", "Кастомный стилизованный интерфейс полосы здоровья боссов");
        RU_MODULE_DESCS.put("bow_laser", "Отображает лазерную линию прицеливания при натяжении лука");
        RU_MODULE_DESCS.put("cape_wings", "Анимированные крылья, синхронизированные с плащом");
        RU_MODULE_DESCS.put("celestial_bodies", "Высокодетализированные текстуры луны, солнца и созвездий");
        RU_MODULE_DESCS.put("chat_mention_glow", "Выделение и звуковое оповещение при упоминании вашего ника");
        RU_MODULE_DESCS.put("china_hat", "Традиционная азиатская коническая шляпа со свечением");
        RU_MODULE_DESCS.put("chromatic_aberration", "Оптический эффект расщепления цветовых каналов RGB по краям");
        RU_MODULE_DESCS.put("cinematic_glide", "Плавное кинематографичное сглаживание движений камеры");
        RU_MODULE_DESCS.put("combo_counter", "Отображение серии непрерывных ударов по противникам");
        RU_MODULE_DESCS.put("companion_spirit", "Магический светлячок, следующий за персонажем");
        RU_MODULE_DESCS.put("inventory_hud", "Отображение содержимого инвентаря на экране");
        RU_MODULE_DESCS.put("container_preview", "Отображение содержимого инвентаря на экране");
        RU_MODULE_DESCS.put("notifications", "Всплывающие капсулы при переключении модулей");
        RU_MODULE_DESCS.put("cosmetic_preview", "Окно примерки и настройки кастомной косметики");
        RU_MODULE_DESCS.put("crosshair_physics", "Плавное покачивание и реакция прицела на отдачу и движение");
        RU_MODULE_DESCS.put("custom_chat", "Полупрозрачный чат с плавной анимацией появления строк");
        RU_MODULE_DESCS.put("custom_crosshair", "Индивидуальный прицел с настройкой размера, цвета и формы");
        RU_MODULE_DESCS.put("custom_hitboxes", "Цветная и стилизованная подсветка хитбоксов сущностей");
        RU_MODULE_DESCS.put("cyber_aurora", "Мерцающие переливы северного сияния на ночном небе");
        RU_MODULE_DESCS.put("cyber_crt", "Стилизация экрана под старый ЭЛТ-монитор со строками развертки");
        RU_MODULE_DESCS.put("damage_indicators", "Всплывающие цифры нанесенного урона над целями");
        RU_MODULE_DESCS.put("death_effects", "Зрелищные эффекты при гибели игроков и мобов");
        RU_MODULE_DESCS.put("depth_fog", "Мягкий объемный туман с градиентом высоты");
        RU_MODULE_DESCS.put("dynamic_fov", "Плавное изменение угла обзора при ускорении и спринте");
        RU_MODULE_DESCS.put("elytra_banking", "Реалистичный наклон и крен камеры во время полета на элитрах");
        RU_MODULE_DESCS.put("emissive_eyes", "Светящиеся зрачки и взгляд персонажа в темноте");
        RU_MODULE_DESCS.put("energy_shield", "Гексагональное силовое поле вокруг модели персонажа");
        RU_MODULE_DESCS.put("enhanced_tooltips", "Информативные подсказки предметов с иконками и прочностью");
        RU_MODULE_DESCS.put("fluid_waves", "Анимированные объемные волны на поверхности воды и лавы");
        RU_MODULE_DESCS.put("footprint_decals", "Отображение отпечатков шагов на земле и снегу");
        RU_MODULE_DESCS.put("fractal_lightning", "Ветвящиеся детализированные вспышки молний во время грозы");
        RU_MODULE_DESCS.put("freelook", "Вращение камеры вокруг персонажа без изменения взгляда");
        RU_MODULE_DESCS.put("frostbite_vignette", "Эффект замерзания и ледяной рамки экрана в рыхлом снегу");
        RU_MODULE_DESCS.put("fullbright", "Максимальная видимость в пещерах и ночью");
        RU_MODULE_DESCS.put("ghost_afterimages", "Остаточные силуэты игрока при быстром перемещении");
        RU_MODULE_DESCS.put("glow_block_outline", "Неоновый контур вокруг выбранного блока");
        RU_MODULE_DESCS.put("god_rays", "Световые лучи сквозь кроны деревьев и облака");
        RU_MODULE_DESCS.put("ground_aura", "Светящееся магическое кольцо под ногами персонажа");
        RU_MODULE_DESCS.put("heartbeat_vignette", "Пульсирующая красная виньетка при критическом здоровье");
        RU_MODULE_DESCS.put("hit_color", "Кастомный цвет вспышки мобов и игроков при получении урона");
        RU_MODULE_DESCS.put("holographic_drone", "Парящий дрон-компаньон со световым лучом");
        RU_MODULE_DESCS.put("holographic_wings", "Светящиеся неоновые крылья за спиной игрока");
        RU_MODULE_DESCS.put("item_glint_overlay", "Кастомный цвет и скорость перелива зачарованных предметов");
        RU_MODULE_DESCS.put("item_inspect", "Анимация детального осмотра предмета в руках от первого лица");
        RU_MODULE_DESCS.put("item_physics_3d", "Предметы на земле лежат горизонтально с реальной физикой");
        RU_MODULE_DESCS.put("item_rarity_beams", "Световые лучи над выброшенными предметами по их редкости");
        RU_MODULE_DESCS.put("item_scroller", "Быстрое перемещение предметов колесиком мыши");
        RU_MODULE_DESCS.put("jump_hit_particles", "Усиленные эффекты частиц при критических ударах");
        RU_MODULE_DESCS.put("jump_ripples", "Волновые круги и импульс под ногами при прыжке и приземлении");
        RU_MODULE_DESCS.put("keystrokes", "Отображение нажатий клавиш WASD, пробела и мыши на экране");
        RU_MODULE_DESCS.put("kill_shockwave", "Эффект взрывной волны и частиц при уничтожении цели");
        RU_MODULE_DESCS.put("kinetic_impact", "Кинетическая геодезическая деформация пространства при ударах");
        RU_MODULE_DESCS.put("landing_fx", "Пылевой вихрь и микро-сотрясение при падении с высоты");
        RU_MODULE_DESCS.put("lava_ember_physics", "Физические горящие угольки и дым над лавой");
        RU_MODULE_DESCS.put("low_fire", "Уменьшение высоты пламени на экране при горении");
        RU_MODULE_DESCS.put("lunacy_bloom", "Мягкое кинематографичное свечение ярких объектов и интерфейса");
        RU_MODULE_DESCS.put("media_player", "Интерактивный плеер музыки Windows с эквалайзером и таймлайном");
        RU_MODULE_DESCS.put("meteor_shower", "Падающие звезды и кометы в ясном ночном небе");
        RU_MODULE_DESCS.put("minimal_compass", "Компактная лента компаса со сторонами света вверху экрана");
        RU_MODULE_DESCS.put("motion_trails", "Шлейф за руками и телом при резких движениях");
        RU_MODULE_DESCS.put("nametags", "Кастомные плашки ников игроков с броней и здоровьем");
        RU_MODULE_DESCS.put("nether_sky_fx", "Зловещие пепельные бури и расколотое небо в Аду");
        RU_MODULE_DESCS.put("orbital_halo", "Вращающийся энергетический ореол над головой персонажа");
        RU_MODULE_DESCS.put("orbiting_cosmetics", "Вращающиеся магические сферы вокруг персонажа");
        RU_MODULE_DESCS.put("particle_trail_3d", "Объемный шлейф светящихся частиц за игроком при движении");
        RU_MODULE_DESCS.put("pearl_prediction", "Предсказание траектории и места приземления эндер-жемчуга");
        RU_MODULE_DESCS.put("photo_mode", "Инструменты для создания красивых скриншотов с эффектами");
        RU_MODULE_DESCS.put("portal_warp", "Волновое пространственное искажение экрана возле порталов");
        RU_MODULE_DESCS.put("potion_hud", "Информативный список активных зелий с таймерами");
        RU_MODULE_DESCS.put("projectile_prediction", "Линия полета стрел, трезубцев и снежков");
        RU_MODULE_DESCS.put("radar_3d", "Радар окружения с отслеживанием ближайших сущностей и игроков");
        RU_MODULE_DESCS.put("radial_menu", "Радиальное меню быстрого доступа к модулям и функциям");
        RU_MODULE_DESCS.put("reflective_puddles", "Зеркальные лужи на поверхности земли во время дождя");
        RU_MODULE_DESCS.put("shield_pulse_barrier", "Анимированный голографический барьер при блокировании щитом");
        RU_MODULE_DESCS.put("shield_status", "Индикатор кулдауна и готовности щита в интерфейсе");
        RU_MODULE_DESCS.put("smart_hud", "Адаптивный информационный HUD с автоскрытием");
        RU_MODULE_DESCS.put("sound_visualizer", "Отображение направлений звуков шагов и мобов");
        RU_MODULE_DESCS.put("sparks_ricochet", "Разлетающиеся физические искры при ударах и парировании");
        RU_MODULE_DESCS.put("speed_lines", "Аниме-линии скорости при быстром беге и падении");
        RU_MODULE_DESCS.put("spring_transitions", "Пружинная физика для элементов интерфейса и всплывающих окон");
        RU_MODULE_DESCS.put("target_lock_reticle", "Футуристический прицел захвата на ближайшем противнике");
        RU_MODULE_DESCS.put("totem_counter", "Количество оставшихся тотемов в инвентаре");
        RU_MODULE_DESCS.put("totem_pop", "Кастомная анимация и счетчик сбитых тотемов");
        RU_MODULE_DESCS.put("underwater_refraction", "Реалистичные водные искажения и каустика под водой");
        RU_MODULE_DESCS.put("verlet_cape", "Реалистичная физика ткани плаща с симуляцией Верле");
        RU_MODULE_DESCS.put("view_model", "Настройка расположения и масштаба рук и предметов");
        RU_MODULE_DESCS.put("visual_studio", "Глобальная панель тонкой настройки шейдеров и графики");
        RU_MODULE_DESCS.put("void_abyss_glow", "Мистическое свечение и частицы тумана на дне мира");
        RU_MODULE_DESCS.put("volumetric_nameplates", "Стильные трехмерные плашки с именами и полосой здоровья");
        RU_MODULE_DESCS.put("voxel_death", "Распад персонажа и мобов на воксельные кубы при смерти");
        RU_MODULE_DESCS.put("watermark", "Стильный логотип клиента с FPS, пингом и временем");
        RU_MODULE_DESCS.put("weapon_trails", "Световой след при взмахах мечом, топором и трезубцем");
        EN_MODULE_NAMES.put("frame_time_hud", "Frame Time");
        EN_MODULE_DESCS.put("frame_time_hud", "Recent frame times in milliseconds");
        EN_MODULE_NAMES.put("adaptive_effects", "Adaptive Effects");
        EN_MODULE_NAMES.put("ambience", "Ambience / Custom World");
        EN_MODULE_NAMES.put("animated_hotbar", "Animated Hotbar");
        EN_MODULE_NAMES.put("armor_hud", "ArmorHUD");
        EN_MODULE_NAMES.put("armor_status_dura", "Armor Status Dura");
        EN_MODULE_NAMES.put("beacon_waypoints", "Beacon Waypoints");
        EN_MODULE_NAMES.put("bioluminescent_foliage", "Bioluminescent Foliage");
        EN_MODULE_NAMES.put("biome_atmosphere", "Biome Atmosphere");
        EN_MODULE_NAMES.put("block_place_ripple", "Block Place Ripple");
        EN_MODULE_NAMES.put("blood_splatter", "Blood Splatter");
        EN_MODULE_NAMES.put("body_aura", "Body Aura");
        EN_MODULE_NAMES.put("boss_bar_eclipsed", "Boss Bar Eclipsed");
        EN_MODULE_NAMES.put("bow_laser", "Bow Laser Sight");
        EN_MODULE_NAMES.put("cape_wings", "Back Cosmetics");
        EN_MODULE_NAMES.put("celestial_bodies", "Custom Celestial Bodies");
        EN_MODULE_NAMES.put("chat_mention_glow", "Chat Mention Glow");
        EN_MODULE_NAMES.put("china_hat", "China Hat & Head Cosmetics");
        EN_MODULE_NAMES.put("chromatic_aberration", "Chromatic Aberration");
        EN_MODULE_NAMES.put("cinematic_glide", "Cinematic Camera Glide");
        EN_MODULE_NAMES.put("combo_counter", "Combo Counter");
        EN_MODULE_NAMES.put("companion_spirit", "Companion Spirit");
        EN_MODULE_NAMES.put("inventory_hud", "Inventory HUD");
        EN_MODULE_NAMES.put("container_preview", "Inventory HUD");
        EN_MODULE_NAMES.put("notifications", "Notifications");
        EN_MODULE_NAMES.put("cosmetic_preview", "Cosmetic Preview");
        EN_MODULE_NAMES.put("crosshair_physics", "Crosshair Physics");
        EN_MODULE_NAMES.put("custom_chat", "Custom Chat HUD");
        EN_MODULE_NAMES.put("custom_crosshair", "Custom Crosshair");
        EN_MODULE_NAMES.put("custom_hitboxes", "Custom Hitboxes");
        EN_MODULE_NAMES.put("cyber_aurora", "Cyber Aurora");
        EN_MODULE_NAMES.put("cyber_crt", "Retro Cyber CRT");
        EN_MODULE_NAMES.put("damage_indicators", "Damage Indicators");
        EN_MODULE_NAMES.put("death_effects", "Death Effects");
        EN_MODULE_NAMES.put("depth_fog", "Atmospheric Depth Fog");
        EN_MODULE_NAMES.put("dynamic_fov", "Dynamic FOV");
        EN_MODULE_NAMES.put("elytra_banking", "Elytra Banking");
        EN_MODULE_NAMES.put("emissive_eyes", "Emissive Eyes");
        EN_MODULE_NAMES.put("energy_shield", "Energy Shield");
        EN_MODULE_NAMES.put("enhanced_tooltips", "Enhanced Tooltips");
        EN_MODULE_NAMES.put("fluid_waves", "Fluid Waves");
        EN_MODULE_NAMES.put("footprint_decals", "Footprint Decals");
        EN_MODULE_NAMES.put("fractal_lightning", "Fractal Lightning");
        EN_MODULE_NAMES.put("freelook", "Freelook");
        EN_MODULE_NAMES.put("frostbite_vignette", "Frostbite Vignette");
        EN_MODULE_NAMES.put("fullbright", "Fullbright / NightVision");
        EN_MODULE_NAMES.put("ghost_afterimages", "Ghost Afterimages");
        EN_MODULE_NAMES.put("glow_block_outline", "Glow Block Outline");
        EN_MODULE_NAMES.put("god_rays", "Volumetric God Rays");
        EN_MODULE_NAMES.put("ground_aura", "Ground Aura");
        EN_MODULE_NAMES.put("heartbeat_vignette", "Heartbeat Vignette");
        EN_MODULE_NAMES.put("hit_color", "Damage Color");
        EN_MODULE_NAMES.put("holographic_drone", "Holographic Drone");
        EN_MODULE_NAMES.put("holographic_wings", "Holographic Wings");
        EN_MODULE_NAMES.put("item_glint_overlay", "Block & Chunk Outlines");
        EN_MODULE_NAMES.put("item_inspect", "Item Inspect");
        EN_MODULE_NAMES.put("item_physics_3d", "Item Physics 3D");
        EN_MODULE_NAMES.put("item_rarity_beams", "Item Rarity Beams");
        EN_MODULE_NAMES.put("item_scroller", "Item Scroller");
        EN_MODULE_NAMES.put("jump_hit_particles", "Jump & Hit Particles");
        EN_MODULE_NAMES.put("jump_ripples", "Kinetic Jump Rings");
        EN_MODULE_NAMES.put("keystrokes", "Keystrokes");
        EN_MODULE_NAMES.put("kill_shockwave", "Kill Shockwave");
        EN_MODULE_NAMES.put("kinetic_impact", "Kinetic Impact Distortion");
        EN_MODULE_NAMES.put("landing_fx", "Landing FX");
        EN_MODULE_NAMES.put("lava_ember_physics", "Lava Ember Physics");
        EN_MODULE_NAMES.put("low_fire", "Low & Colored Fire");
        EN_MODULE_NAMES.put("lunacy_bloom", "AeroVisuals Bloom");
        EN_MODULE_NAMES.put("media_player", "Media Player HUD");
        EN_MODULE_NAMES.put("meteor_shower", "Meteor Shower");
        EN_MODULE_NAMES.put("minimal_compass", "Minimal Compass");
        EN_MODULE_NAMES.put("motion_trails", "MotionTrails");
        EN_MODULE_NAMES.put("nametags", "Custom Nametags");
        EN_MODULE_NAMES.put("nether_sky_fx", "Nether Sky FX");
        EN_MODULE_NAMES.put("orbital_halo", "Orbital Halo");
        EN_MODULE_NAMES.put("orbiting_cosmetics", "Orbiting Cosmetics");
        EN_MODULE_NAMES.put("particle_trail_3d", "3D Particle Trail");
        EN_MODULE_NAMES.put("pearl_prediction", "Pearl Trajectory");
        EN_MODULE_NAMES.put("photo_mode", "Photo Mode");
        EN_MODULE_NAMES.put("portal_warp", "Portal Warp");
        EN_MODULE_NAMES.put("potion_hud", "Potion & Cooldown HUD");
        EN_MODULE_NAMES.put("projectile_prediction", "Trajectory Prediction");
        EN_MODULE_NAMES.put("radar_3d", "Mini Radar");
        EN_MODULE_NAMES.put("radial_menu", "Radial Menu");
        EN_MODULE_NAMES.put("reflective_puddles", "Reflective Rain Puddles");
        EN_MODULE_NAMES.put("shield_pulse_barrier", "Shield Pulse Barrier");
        EN_MODULE_NAMES.put("shield_status", "Shield Status");
        EN_MODULE_NAMES.put("smart_hud", "Smart HUD");
        EN_MODULE_NAMES.put("sound_visualizer", "Sound Visualizer");
        EN_MODULE_NAMES.put("sparks_ricochet", "Sparks Ricochet");
        EN_MODULE_NAMES.put("speed_lines", "Hyperspace Speed Lines");
        EN_MODULE_NAMES.put("spring_transitions", "Spring Transitions");
        EN_MODULE_NAMES.put("target_lock_reticle", "Target Lock Reticle");
        EN_MODULE_NAMES.put("totem_counter", "Totem Counter");
        EN_MODULE_NAMES.put("totem_pop", "Minimal Totem Pop");
        EN_MODULE_NAMES.put("underwater_refraction", "Underwater Refraction");
        EN_MODULE_NAMES.put("verlet_cape", "Verlet Physics Cape");
        EN_MODULE_NAMES.put("view_model", "Hands & ViewModel");
        EN_MODULE_NAMES.put("visual_studio", "Visual Studio");
        EN_MODULE_NAMES.put("void_abyss_glow", "Void Abyss Glow");
        EN_MODULE_NAMES.put("volumetric_nameplates", "Volumetric Nameplates");
        EN_MODULE_NAMES.put("voxel_death", "Voxel Death");
        EN_MODULE_NAMES.put("watermark", "Watermark");
        EN_MODULE_NAMES.put("weapon_trails", "Weapon Trails");
        EN_MODULE_DESCS.put("adaptive_effects", "Dynamically throttles visual effects to maintain FPS");
        EN_MODULE_DESCS.put("ambience", "Customizable ambient lighting, sky color, and fog");
        EN_MODULE_DESCS.put("animated_hotbar", "Smooth animated selector slider for hotbar slots");
        EN_MODULE_DESCS.put("armor_hud", "Armor slots and durability status on HUD");
        EN_MODULE_DESCS.put("armor_status_dura", "Equipped armor durability and icons on HUD");
        EN_MODULE_DESCS.put("beacon_waypoints", "High-visibility marker pillars towards active beacons");
        EN_MODULE_DESCS.put("bioluminescent_foliage", "Subtle neon emission on leaves, grass, and mushrooms");
        EN_MODULE_DESCS.put("biome_atmosphere", "Custom ambient tinting and drifting particles per biome");
        EN_MODULE_DESCS.put("block_place_ripple", "Expanding holographic ground ring when placing blocks");
        EN_MODULE_DESCS.put("blood_splatter", "Cyberpunk energy blood and plasma splatter droplets in combat");
        EN_MODULE_DESCS.put("body_aura", "Pulsing energy aura aura surrounding player avatar");
        EN_MODULE_DESCS.put("boss_bar_eclipsed", "Minimalist stylized boss health bar with glowing gradient");
        EN_MODULE_DESCS.put("bow_laser", "Holographic targeting line while drawing bows or crossbows");
        EN_MODULE_DESCS.put("cape_wings", "Animated wings and cape cosmetics");
        EN_MODULE_DESCS.put("celestial_bodies", "High-res stylized moon phases, sun, and constellations");
        EN_MODULE_DESCS.put("chat_mention_glow", "Highlights and chimes when your name is typed in chat");
        EN_MODULE_DESCS.put("china_hat", "Conical hat and glowing head accessories");
        EN_MODULE_DESCS.put("chromatic_aberration", "RGB channel fringing at screen edges during fast motion");
        EN_MODULE_DESCS.put("cinematic_glide", "Ultra-smooth cinematic camera dampening");
        EN_MODULE_DESCS.put("combo_counter", "Real-time attack combo multiplier and streak display");
        EN_MODULE_DESCS.put("companion_spirit", "Floating celestial wisp orbiting player");
        EN_MODULE_DESCS.put("inventory_hud", "Displays player inventory on HUD");
        EN_MODULE_DESCS.put("container_preview", "Displays player inventory on HUD");
        EN_MODULE_DESCS.put("notifications", "Capsule toast notifications on module toggles");
        EN_MODULE_DESCS.put("cosmetic_preview", "Interactive 3D preview window for cosmetics");
        EN_MODULE_DESCS.put("crosshair_physics", "Sway and recoil inertia applied to crosshair");
        EN_MODULE_DESCS.put("custom_chat", "Modern frosted glass chat panel with smooth slide-in");
        EN_MODULE_DESCS.put("custom_crosshair", "Configurable custom crosshairs with dynamic recoil");
        EN_MODULE_DESCS.put("custom_hitboxes", "Custom stylized hitboxes with gradient outlines");
        EN_MODULE_DESCS.put("cyber_aurora", "Shimmering neon auroras lighting the night sky");
        EN_MODULE_DESCS.put("cyber_crt", "Scanline distortion and phosphor aesthetic overlay");
        EN_MODULE_DESCS.put("damage_indicators", "Floating animated damage numbers over hit entities");
        EN_MODULE_DESCS.put("death_effects", "Spectacular particle bursts and animations on death");
        EN_MODULE_DESCS.put("depth_fog", "Volumetric height fog enhancing atmospheric depth");
        EN_MODULE_DESCS.put("dynamic_fov", "Smooth FOV shifts responding to velocity and sprinting");
        EN_MODULE_DESCS.put("elytra_banking", "Dynamic camera tilt and banking while gliding on elytras");
        EN_MODULE_DESCS.put("emissive_eyes", "Glowing luminous eyes piercing through darkness");
        EN_MODULE_DESCS.put("energy_shield", "Hexagonal energy field enveloping player model");
        EN_MODULE_DESCS.put("enhanced_tooltips", "Refined item tooltips with durability bars and icons");
        EN_MODULE_DESCS.put("fluid_waves", "Surface wave displacement simulation on liquids");
        EN_MODULE_DESCS.put("footprint_decals", "Directional glowing tread marks left on terrain");
        EN_MODULE_DESCS.put("fractal_lightning", "Branching fractal lightning bolts and thunder flash");
        EN_MODULE_DESCS.put("freelook", "Freely look around character in 3rd person mode");
        EN_MODULE_DESCS.put("frostbite_vignette", "Crystalline frost overlay when in powder snow");
        EN_MODULE_DESCS.put("fullbright", "Clear night vision without darkness in caves");
        EN_MODULE_DESCS.put("ghost_afterimages", "Neon silhouette clones left behind while sprinting");
        EN_MODULE_DESCS.put("glow_block_outline", "Pulsing neon outline around targeted block");
        EN_MODULE_DESCS.put("god_rays", "Volumetric sunbeams piercing through clouds and canopy");
        EN_MODULE_DESCS.put("ground_aura", "Luminescent runic circle beneath player feet");
        EN_MODULE_DESCS.put("heartbeat_vignette", "Pulsing red screen vignette and audio at low health");
        EN_MODULE_DESCS.put("hit_color", "Customizable flash tint on damaged entities");
        EN_MODULE_DESCS.put("holographic_drone", "Miniature companion drone hovering nearby with scanner beam");
        EN_MODULE_DESCS.put("holographic_wings", "Floating luminous angel or cyber wings behind player");
        EN_MODULE_DESCS.put("item_glint_overlay", "Customizable enchantment glint color and outlines");
        EN_MODULE_DESCS.put("item_inspect", "First-person inspect weapon/item animation");
        EN_MODULE_DESCS.put("item_physics_3d", "Dropped items lay flat and roll with realistic physics");
        EN_MODULE_DESCS.put("item_rarity_beams", "Vertical colored loot beams above dropped items");
        EN_MODULE_DESCS.put("item_scroller", "Fast inventory item scrolling and moving");
        EN_MODULE_DESCS.put("jump_hit_particles", "Enhanced particle bursts on critical attacks");
        EN_MODULE_DESCS.put("jump_ripples", "Expanding kinetic wave dispersion rings on jumps and landings");
        EN_MODULE_DESCS.put("keystrokes", "On-screen real-time keypresses HUD display");
        EN_MODULE_DESCS.put("kill_shockwave", "Expanding holographic shockwave and sound upon eliminating targets");
        EN_MODULE_DESCS.put("kinetic_impact", "Geodesic spatial deformation lattice on combat impacts");
        EN_MODULE_DESCS.put("landing_fx", "Ground dust shockwave and micro-camera shake upon landing");
        EN_MODULE_DESCS.put("lava_ember_physics", "Physical rising embers and smoke over lava bodies");
        EN_MODULE_DESCS.put("low_fire", "Lower screen fire overlay with custom color tint");
        EN_MODULE_DESCS.put("lunacy_bloom", "Luminescent cinematic glow around neon elements");
        EN_MODULE_DESCS.put("media_player", "Interactive Windows media player HUD with audio visualizer and controls");
        EN_MODULE_DESCS.put("meteor_shower", "Occasional shooting stars and meteor streaks in night sky");
        EN_MODULE_DESCS.put("minimal_compass", "Horizontal directional tape at top of HUD");
        EN_MODULE_DESCS.put("motion_trails", "Glowing motion trails behind player limb movements");
        EN_MODULE_DESCS.put("nametags", "Enhanced player nameplates with health, armor and ping");
        EN_MODULE_DESCS.put("nether_sky_fx", "Ash storms and volcanic auroras in the Nether");
        EN_MODULE_DESCS.put("orbital_halo", "Geometric floating halo rotating above player's head");
        EN_MODULE_DESCS.put("orbiting_cosmetics", "Orbiting luminous orbs encircling player body");
        EN_MODULE_DESCS.put("particle_trail_3d", "Volumetric particle ribbon following player movements");
        EN_MODULE_DESCS.put("pearl_prediction", "Accurate physical trajectory and landing point for ender pearls");
        EN_MODULE_DESCS.put("photo_mode", "Cinematic photo mode with depth of field and filters");
        EN_MODULE_DESCS.put("portal_warp", "Spatial distortion ripple near active portals");
        EN_MODULE_DESCS.put("potion_hud", "Clean potion effect status display with countdowns");
        EN_MODULE_DESCS.put("projectile_prediction", "Accurate flight arcs for arrows, tridents, and snowballs");
        EN_MODULE_DESCS.put("radar_3d", "Circular HUD radar showing nearby entities and players");
        EN_MODULE_DESCS.put("radial_menu", "Pie menu for rapid module toggling and commands");
        EN_MODULE_DESCS.put("reflective_puddles", "Dynamic reflective water puddles forming in rain");
        EN_MODULE_DESCS.put("shield_pulse_barrier", "Holographic hexagonal energy barrier when blocking with shields");
        EN_MODULE_DESCS.put("shield_status", "Shield cooldown timer and ready indicator on HUD");
        EN_MODULE_DESCS.put("smart_hud", "Contextual HUD elements that fade in and out");
        EN_MODULE_DESCS.put("sound_visualizer", "Visual direction arcs for footsteps and entity noises");
        EN_MODULE_DESCS.put("sparks_ricochet", "Directional physical sparks bouncing off blocks and shields");
        EN_MODULE_DESCS.put("speed_lines", "Anime speed streak lines during high velocity");
        EN_MODULE_DESCS.put("spring_transitions", "Bouncy spring physics for UI overlays and popups");
        EN_MODULE_DESCS.put("target_lock_reticle", "Futuristic tracking brackets locking onto the targeted entity");
        EN_MODULE_DESCS.put("totem_counter", "Real-time count of totems in inventory");
        EN_MODULE_DESCS.put("totem_pop", "Customized totem pop animation and pop tracker");
        EN_MODULE_DESCS.put("underwater_refraction", "Wavy optical refraction effect while submerged");
        EN_MODULE_DESCS.put("verlet_cape", "Smooth physics cape with multi-segment Verlet integration");
        EN_MODULE_DESCS.put("view_model", "Custom hands and held item positioning and scale");
        EN_MODULE_DESCS.put("visual_studio", "Master control dashboard for visuals and shaders");
        EN_MODULE_DESCS.put("void_abyss_glow", "Mystical glowing fog and void particles near the abyss");
        EN_MODULE_DESCS.put("volumetric_nameplates", "Floating 3D nameplates with smooth health bar and glow");
        EN_MODULE_DESCS.put("voxel_death", "Entities disintegrate into floating voxel cubes on death");
        EN_MODULE_DESCS.put("watermark", "Sleek AeroVisuals client watermark with FPS, ping and clock");
        EN_MODULE_DESCS.put("weapon_trails", "Neon curved arc trails following melee attacks");

        UA_MODULE_NAMES.put("frame_time_hud", "Графік плавності");
        UA_MODULE_DESCS.put("frame_time_hud", "Час останніх кадрів у мілісекундах");
        UA_MODULE_NAMES.put("adaptive_effects", "Адаптивні ефекти");
        UA_MODULE_NAMES.put("ambience", "Освітлення та оточення");
        UA_MODULE_NAMES.put("animated_hotbar", "Анімований хотбар");
        UA_MODULE_NAMES.put("armor_hud", "Броня на екрані");
        UA_MODULE_NAMES.put("armor_status_dura", "Міцність броні");
        UA_MODULE_NAMES.put("beacon_waypoints", "Мітки маяків");
        UA_MODULE_NAMES.put("bioluminescent_foliage", "Біолюмінесцентне листя");
        UA_MODULE_NAMES.put("biome_atmosphere", "Атмосфера біомів");
        UA_MODULE_NAMES.put("block_place_ripple", "Хвиля встановлення блоку");
        UA_MODULE_NAMES.put("blood_splatter", "Бризки крові");
        UA_MODULE_NAMES.put("body_aura", "Аура тіла");
        UA_MODULE_NAMES.put("boss_bar_eclipsed", "Затьмарений босбар");
        UA_MODULE_NAMES.put("bow_laser", "Лазерний приціл лука");
        UA_MODULE_NAMES.put("cape_wings", "Крила на плащі");
        UA_MODULE_NAMES.put("celestial_bodies", "Кастомні світила");
        UA_MODULE_NAMES.put("chat_mention_glow", "Підсвічування в чаті");
        UA_MODULE_NAMES.put("china_hat", "Конічний капелюх");
        UA_MODULE_NAMES.put("chromatic_aberration", "Хроматична аберація");
        UA_MODULE_NAMES.put("cinematic_glide", "Кінематографічна камера");
        UA_MODULE_NAMES.put("combo_counter", "Лічильник комбо");
        UA_MODULE_NAMES.put("companion_spirit", "Дух-супутник");
        UA_MODULE_NAMES.put("inventory_hud", "Інвентар");
        UA_MODULE_NAMES.put("container_preview", "Інвентар");
        UA_MODULE_NAMES.put("notifications", "Сповіщення");
        UA_MODULE_NAMES.put("cosmetic_preview", "Прев'ю косметики");
        UA_MODULE_NAMES.put("crosshair_physics", "Фізика прицілу");
        UA_MODULE_NAMES.put("custom_chat", "Кастомний чат");
        UA_MODULE_NAMES.put("custom_crosshair", "Кастомний приціл");
        UA_MODULE_NAMES.put("custom_hitboxes", "Кастомні хітбокси");
        UA_MODULE_NAMES.put("cyber_aurora", "Кібер-північне сяйво");
        UA_MODULE_NAMES.put("cyber_crt", "Ретро Cyber CRT");
        UA_MODULE_NAMES.put("damage_indicators", "Індикатори шкоди");
        UA_MODULE_NAMES.put("death_effects", "Ефекти смерті");
        UA_MODULE_NAMES.put("depth_fog", "Атмосферний туман глибини");
        UA_MODULE_NAMES.put("dynamic_fov", "Динамічний FOV");
        UA_MODULE_NAMES.put("elytra_banking", "Крен елітр");
        UA_MODULE_NAMES.put("emissive_eyes", "Сяючі очі");
        UA_MODULE_NAMES.put("energy_shield", "Енергетичний щит");
        UA_MODULE_NAMES.put("enhanced_tooltips", "Покращені підказки");
        UA_MODULE_NAMES.put("fluid_waves", "Хвилі рідин");
        UA_MODULE_NAMES.put("footprint_decals", "Сліди на землі");
        UA_MODULE_NAMES.put("fractal_lightning", "Фрактальні блискавки");
        UA_MODULE_NAMES.put("freelook", "Вільний огляд");
        UA_MODULE_NAMES.put("frostbite_vignette", "Він'єтка обмороження");
        UA_MODULE_NAMES.put("fullbright", "Яскравість (FullBright)");
        UA_MODULE_NAMES.put("ghost_afterimages", "Примарні фантоми");
        UA_MODULE_NAMES.put("glow_block_outline", "Сяючий контур блоків");
        UA_MODULE_NAMES.put("god_rays", "Об'ємні промені сонця");
        UA_MODULE_NAMES.put("ground_aura", "Коло під ногами");
        UA_MODULE_NAMES.put("heartbeat_vignette", "Пульсація при низькому HP");
        UA_MODULE_NAMES.put("hit_color", "Колір удару");
        UA_MODULE_NAMES.put("holographic_drone", "Голографічний дрон");
        UA_MODULE_NAMES.put("holographic_wings", "Голографічні крила");
        UA_MODULE_NAMES.put("item_glint_overlay", "Блиск чар");
        UA_MODULE_NAMES.put("item_inspect", "Огляд предмета");
        UA_MODULE_NAMES.put("item_physics_3d", "3D Фізика предметів");
        UA_MODULE_NAMES.put("item_rarity_beams", "Промені рідкісності предметів");
        UA_MODULE_NAMES.put("item_scroller", "Швидка прокрутка");
        UA_MODULE_NAMES.put("jump_hit_particles", "Частинки крит-ударів");
        UA_MODULE_NAMES.put("jump_ripples", "Кінетичні кільця");
        UA_MODULE_NAMES.put("keystrokes", "Кейстрокси");
        UA_MODULE_NAMES.put("kill_shockwave", "Ударна хвиля при вбивстві");
        UA_MODULE_NAMES.put("kinetic_impact", "Кінетичний імпульс");
        UA_MODULE_NAMES.put("landing_fx", "Ефект приземлення");
        UA_MODULE_NAMES.put("lava_ember_physics", "Іскри лави");
        UA_MODULE_NAMES.put("low_fire", "Низький вогонь");
        UA_MODULE_NAMES.put("lunacy_bloom", "Неоновий блум");
        UA_MODULE_NAMES.put("media_player", "Медіаплеєр");
        UA_MODULE_NAMES.put("meteor_shower", "Метеорний потік");
        UA_MODULE_NAMES.put("minimal_compass", "Мінімалістичний компас");
        UA_MODULE_NAMES.put("motion_trails", "Шлейфи рухів");
        UA_MODULE_NAMES.put("nametags", "Неймтаги");
        UA_MODULE_NAMES.put("nether_sky_fx", "Ефекти неба Незера");
        UA_MODULE_NAMES.put("orbital_halo", "Орбітальний німб");
        UA_MODULE_NAMES.put("orbiting_cosmetics", "Орбітальні сфери");
        UA_MODULE_NAMES.put("particle_trail_3d", "3D Шлейф частинок");
        UA_MODULE_NAMES.put("pearl_prediction", "Траєкторія перлини");
        UA_MODULE_NAMES.put("photo_mode", "Фото-режим");
        UA_MODULE_NAMES.put("portal_warp", "Викривлення порталу");
        UA_MODULE_NAMES.put("potion_hud", "Ефекти зілля");
        UA_MODULE_NAMES.put("projectile_prediction", "Траєкторія снарядів");
        UA_MODULE_NAMES.put("radar_3d", "Міні-радар 3D");
        UA_MODULE_NAMES.put("radial_menu", "Кругове меню");
        UA_MODULE_NAMES.put("reflective_puddles", "Відбиваючі калюжі");
        UA_MODULE_NAMES.put("shield_pulse_barrier", "Бар'єр щита");
        UA_MODULE_NAMES.put("shield_status", "Статус щита");
        UA_MODULE_NAMES.put("smart_hud", "Розумний HUD");
        UA_MODULE_NAMES.put("sound_visualizer", "Візуалізатор звуку");
        UA_MODULE_NAMES.put("sparks_ricochet", "Іскри рикошету");
        UA_MODULE_NAMES.put("speed_lines", "Лінії гіпершвидкості");
        UA_MODULE_NAMES.put("spring_transitions", "Пружні анімації");
        UA_MODULE_NAMES.put("target_lock_reticle", "Захоплення цілі");
        UA_MODULE_NAMES.put("totem_counter", "Лічильник тотемів");
        UA_MODULE_NAMES.put("totem_pop", "Спрацьовування тотема");
        UA_MODULE_NAMES.put("underwater_refraction", "Заломлення під водою");
        UA_MODULE_NAMES.put("verlet_cape", "Фізичний плащ Верле");
        UA_MODULE_NAMES.put("view_model", "Позиція рук");
        UA_MODULE_NAMES.put("visual_studio", "Студія візуалів");
        UA_MODULE_NAMES.put("void_abyss_glow", "Світіння безодні");
        UA_MODULE_NAMES.put("volumetric_nameplates", "Об'ємні неймтаги");
        UA_MODULE_NAMES.put("voxel_death", "Воксельна загибель");
        UA_MODULE_NAMES.put("watermark", "Вотермарка клієнта");
        UA_MODULE_NAMES.put("weapon_trails", "Шлейфи зброї");
        UA_MODULE_DESCS.put("adaptive_effects", "Динамічне підлаштування ефектів під продуктивність");
        UA_MODULE_DESCS.put("ambience", "Налаштування кольору неба, туману та часу доби");
        UA_MODULE_DESCS.put("animated_hotbar", "Плавна анімація перемикання слотів хотбара");
        UA_MODULE_DESCS.put("armor_hud", "Відображення слотів броні та міцності в HUD");
        UA_MODULE_DESCS.put("armor_status_dura", "Відображення екіпірованої броні та її залишкової міцності");
        UA_MODULE_DESCS.put("beacon_waypoints", "Світлові стовпи та дистанція до маяків і ключових точок");
        UA_MODULE_DESCS.put("bioluminescent_foliage", "Нічне світіння листя, трави та грибів");
        UA_MODULE_DESCS.put("biome_atmosphere", "Унікальна колірна гама та частинки для кожного біома");
        UA_MODULE_DESCS.put("block_place_ripple", "Кругова імпульсна хвиля при встановленні блоків");
        UA_MODULE_DESCS.put("blood_splatter", "Ефект бризок крові та крапель при отриманні шкоди");
        UA_MODULE_DESCS.put("body_aura", "Енергетична аура навколо моделі персонажа");
        UA_MODULE_DESCS.put("boss_bar_eclipsed", "Кастомний стилізований інтерфейс смуги здоров'я босів");
        UA_MODULE_DESCS.put("bow_laser", "Відображає лазерну лінію прицілювання при натягуванні лука");
        UA_MODULE_DESCS.put("cape_wings", "Анімовані крила, синхронізовані з плащем");
        UA_MODULE_DESCS.put("celestial_bodies", "Високодеталізовані текстури місяця, сонця та сузір'їв");
        UA_MODULE_DESCS.put("chat_mention_glow", "Виділення та звукове сповіщення при згадці вашого ніка");
        UA_MODULE_DESCS.put("china_hat", "Традиційний азійський конічний капелюх зі світінням");
        UA_MODULE_DESCS.put("chromatic_aberration", "Оптичний ефект розщеплення колірних каналів RGB по краях");
        UA_MODULE_DESCS.put("cinematic_glide", "Плавне кінематографічне згладжування рухів камери");
        UA_MODULE_DESCS.put("combo_counter", "Відображення серії безперервних ударів по ворогах");
        UA_MODULE_DESCS.put("companion_spirit", "Магічний світлячок, що слідує за персонажем");
        UA_MODULE_DESCS.put("inventory_hud", "Відображення вмісту інвентаря на екрані");
        UA_MODULE_DESCS.put("container_preview", "Відображення вмісту інвентаря на екрані");
        UA_MODULE_DESCS.put("notifications", "Спливаючі капсули при перемиканні модулів");
        UA_MODULE_DESCS.put("cosmetic_preview", "Вікно примірки та налаштування кастомної косметики");
        UA_MODULE_DESCS.put("crosshair_physics", "Плавне погойдування та реакція прицілу на віддачу і рух");
        UA_MODULE_DESCS.put("custom_chat", "Напівпрозорий чат із плавною анімацією появи рядків");
        UA_MODULE_DESCS.put("custom_crosshair", "Індивідуальний приціл із налаштуванням розміру, кольору та форми");
        UA_MODULE_DESCS.put("custom_hitboxes", "Кольорове та стилізоване підсвічування хітбоксів сутностей");
        UA_MODULE_DESCS.put("cyber_aurora", "Мерехтливі переливи північного сяйва на нічному небі");
        UA_MODULE_DESCS.put("cyber_crt", "Стилізація екрана під старий ЕПТ-монітор із рядками розгортки");
        UA_MODULE_DESCS.put("damage_indicators", "Спливаючі цифри завданої шкоди над цілями");
        UA_MODULE_DESCS.put("death_effects", "Видовищні ефекти при загибелі гравців та мобів");
        UA_MODULE_DESCS.put("depth_fog", "М'який об'ємний туман із градієнтом висоти");
        UA_MODULE_DESCS.put("dynamic_fov", "Плавна зміна кута огляду при прискоренні та спринті");
        UA_MODULE_DESCS.put("elytra_banking", "Реалістичний нахил і крен камери під час польоту на елітрах");
        UA_MODULE_DESCS.put("emissive_eyes", "Сяючі зіниці та погляд персонажа в темряві");
        UA_MODULE_DESCS.put("energy_shield", "Гексагональне силове поле навколо моделі персонажа");
        UA_MODULE_DESCS.put("enhanced_tooltips", "Інформативні підказки предметів з іконками та міцністю");
        UA_MODULE_DESCS.put("fluid_waves", "Анімовані об'ємні хвилі на поверхні води та лави");
        UA_MODULE_DESCS.put("footprint_decals", "Відображення слідів кроків на землі та снігу");
        UA_MODULE_DESCS.put("fractal_lightning", "Деталізовані спалахи блискавок, що гілкуються під час грози");
        UA_MODULE_DESCS.put("freelook", "Обертання камери навколо персонажа без зміни погляду");
        UA_MODULE_DESCS.put("frostbite_vignette", "Ефект замерзання та крижаної рамки екрана в пухкому снігу");
        UA_MODULE_DESCS.put("fullbright", "Максимальна видимість у печерах і вночі");
        UA_MODULE_DESCS.put("ghost_afterimages", "Залишкові силуети гравця при швидкому переміщенні");
        UA_MODULE_DESCS.put("glow_block_outline", "Неоновий контур навколо вибраного блоку");
        UA_MODULE_DESCS.put("god_rays", "Світлові промені крізь крони дерев і хмари");
        UA_MODULE_DESCS.put("ground_aura", "Сяюче магічне коло під ногами персонажа");
        UA_MODULE_DESCS.put("heartbeat_vignette", "Пульсуюча червона він'єтка при критичному здоров'ї");
        UA_MODULE_DESCS.put("hit_color", "Кастомний колір спалаху мобів та гравців при отриманні шкоди");
        UA_MODULE_DESCS.put("holographic_drone", "Ширяючий дрон-компаньйон зі світловим променем");
        UA_MODULE_DESCS.put("holographic_wings", "Сяючі неонові крила за спиною гравця");
        UA_MODULE_DESCS.put("item_glint_overlay", "Кастомний колір і швидкість переливу зачарованих предметів");
        UA_MODULE_DESCS.put("item_inspect", "Анімація детального огляду предмета в руках від першої особи");
        UA_MODULE_DESCS.put("item_physics_3d", "Предмети на землі лежать горизонтально з реальною фізикою");
        UA_MODULE_DESCS.put("item_rarity_beams", "Світлові промені над викинутими предметами за їхньою рідкістю");
        UA_MODULE_DESCS.put("item_scroller", "Швидке переміщення предметів коліщатком миші");
        UA_MODULE_DESCS.put("jump_hit_particles", "Посилені ефекти частинок при критичних ударах");
        UA_MODULE_DESCS.put("jump_ripples", "Хвильові кола та імпульс під ногами при стрибку і приземленні");
        UA_MODULE_DESCS.put("keystrokes", "Відображення натискань клавіш WASD, пробілу та миші на екрані");
        UA_MODULE_DESCS.put("kill_shockwave", "Ефект вибухової хвилі та частинок при знищенні цілі");
        UA_MODULE_DESCS.put("kinetic_impact", "Кінетична геодезична деформація простору при ударах");
        UA_MODULE_DESCS.put("landing_fx", "Ефект пилу та мікрострус камери при приземленні");
        UA_MODULE_DESCS.put("lava_ember_physics", "Фізичні іскри та дим над поверхнею лави");
        UA_MODULE_DESCS.put("low_fire", "Знижений екранний вогонь із кастомним відтінком");
        UA_MODULE_DESCS.put("lunacy_bloom", "Люмінесцентне кінематографічне світіння навколо неонових елементів");
        UA_MODULE_DESCS.put("media_player", "Інтерактивний HUD медіаплеєра Windows із візуалізатором і керуванням");
        UA_MODULE_DESCS.put("meteor_shower", "Падаючі зірки та метеорні смуги в нічному небі");
        UA_MODULE_DESCS.put("minimal_compass", "Горизонтальна стрічка компаса у верхній частині HUD");
        UA_MODULE_DESCS.put("motion_trails", "Сяючі шлейфи рухів за кінцівками гравця");
        UA_MODULE_DESCS.put("nametags", "Покращені таблички імен гравців зі здоров'ям, бронею та пінгом");
        UA_MODULE_DESCS.put("nether_sky_fx", "Попільні бурі та вулканічні сяйва в Незері");
        UA_MODULE_DESCS.put("orbital_halo", "Геометричний німб, що обертається над головою гравця");
        UA_MODULE_DESCS.put("orbiting_cosmetics", "Сяючі сфери, що кружляють навколо тіла гравця");
        UA_MODULE_DESCS.put("particle_trail_3d", "Об'ємна стрічка частинок, що слідує за рухами гравця");
        UA_MODULE_DESCS.put("pearl_prediction", "Точна фізична траєкторія та точка приземлення перлин ендера");
        UA_MODULE_DESCS.put("photo_mode", "Кінематографічний фоторежим із глибиною різкості та фільтрами");
        UA_MODULE_DESCS.put("portal_warp", "Хвильове просторове викривлення біля активних порталів");
        UA_MODULE_DESCS.put("potion_hud", "Відображення активних ефектів зілля з таймерами");
        UA_MODULE_DESCS.put("projectile_prediction", "Точні дуги польоту стріл, тризубів та сніжок");
        UA_MODULE_DESCS.put("radar_3d", "Круговий радар HUD, що показує найближчих сутностей і гравців");
        UA_MODULE_DESCS.put("radial_menu", "Кругове меню для швидкого перемикання модулів і команд");
        UA_MODULE_DESCS.put("reflective_puddles", "Динамічні калюжі з віддзеркаленням, що утворюються під час дощу");
        UA_MODULE_DESCS.put("shield_pulse_barrier", "Голографічний гексагональний силовий бар'єр при блокуванні щитом");
        UA_MODULE_DESCS.put("shield_status", "Таймер перезарядки щита та індикатор готовності на HUD");
        UA_MODULE_DESCS.put("smart_hud", "Контекстні елементи HUD, які плавно з'являються та зникають");
        UA_MODULE_DESCS.put("sound_visualizer", "Візуальні дуги напрямку кроків і звуків сутностей");
        UA_MODULE_DESCS.put("sparks_ricochet", "Фізичні іскри, що відскакують від блоків і щитів");
        UA_MODULE_DESCS.put("speed_lines", "Аніме-лінії швидкості при високій швидкості пересування");
        UA_MODULE_DESCS.put("spring_transitions", "Пружні плавні анімації для оверлеїв і спливаючих вікон");
        UA_MODULE_DESCS.put("target_lock_reticle", "Футуристичні рамки захоплення цілі на противнику");
        UA_MODULE_DESCS.put("totem_counter", "Кількість тотемів в інвентарі в реальному часі");
        UA_MODULE_DESCS.put("totem_pop", "Кастомна анімація спрацьовування тотема та лічильник попів");
        UA_MODULE_DESCS.put("underwater_refraction", "Хвильовий оптичний ефект заломлення під водою");
        UA_MODULE_DESCS.put("verlet_cape", "Плавний плащ із фізикою багатосегментного інтегрування Верле");
        UA_MODULE_DESCS.put("view_model", "Кастомне положення та масштаб рук і предметів");
        UA_MODULE_DESCS.put("visual_studio", "Центральна панель керування візуалами та шейдерами");
        UA_MODULE_DESCS.put("void_abyss_glow", "Містичний сяючий туман і частинки порожнечі біля безодні");
        UA_MODULE_DESCS.put("volumetric_nameplates", "Ширяючі 3D таблички імен із плавною смугою здоров'я");
        UA_MODULE_DESCS.put("voxel_death", "Розпад сутностей на ширяючі воксельні куби при смерті");
        UA_MODULE_DESCS.put("watermark", "Стильна вотермарка клієнта AeroVisuals з FPS, пінгом та годинником");
        UA_MODULE_DESCS.put("weapon_trails", "Неонові дугові шлейфи після атак ближнього бою");

        loadLanguageMap("/assets/aerovisuals/lang/ru_ru.json", RU_MAP);
        loadLanguageMap("/assets/aerovisuals/lang/en_us.json", EN_MAP);
        loadLanguageMap("/assets/aerovisuals/lang/uk_ua.json", UA_MAP);
    }

    private static void loadLanguageMap(String path, Map<String, String> target) {
        try (InputStream in = AeroI18n.class.getResourceAsStream(path)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Map<String, String> parsed = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                    if (parsed != null) {
                        target.putAll(parsed);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private AeroI18n() {}

    public static boolean isRussian() {
        return languageMode == 1;
    }

    public static boolean isUkrainian() {
        return languageMode == 2;
    }

    public static boolean isEnglish() {
        return languageMode == 0;
    }

    public static void setLanguage(int mode) {
        languageMode = (mode == 1 || mode == 2) ? mode : 0;
    }

    public static String tr(String path, Object... args) {
        return trOr(path, path, args);
    }

    public static String trOr(String path, String fallback, Object... args) {
        String fullKey = path.startsWith(PREFIX) ? path : (PREFIX + path);
        String shortKey = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : path;

        Map<String, String> activeMap = isRussian() ? RU_MAP : (isUkrainian() ? UA_MAP : EN_MAP);
        if (activeMap.containsKey(fullKey)) {
            return format(activeMap.get(fullKey), args);
        }
        if (activeMap.containsKey(shortKey)) {
            return format(activeMap.get(shortKey), args);
        }

        // Check Minecraft's built-in I18n if available
        if (hasMcTranslation(fullKey)) {
            return getMcTranslation(fullKey, args);
        }
        if (hasMcTranslation(shortKey)) {
            return getMcTranslation(shortKey, args);
        }

        // Check embedded UI fallbacks
        String embedded = getEmbeddedFallback(shortKey);
        if (embedded != null) {
            return format(embedded, args);
        }

        return fallback;
    }

    private static boolean hasMcTranslation(String key) {
        try {
            return net.minecraft.locale.Language.getInstance().has(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String getMcTranslation(String key, Object... args) {
        try {
            return I18n.get(key, args);
        } catch (Throwable ignored) {
            return key;
        }
    }

    private static String format(String pattern, Object... args) {
        if (args == null || args.length == 0 || pattern == null) return pattern;
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (Throwable ignored) {
            return pattern;
        }
    }

    public static String moduleName(Module module) {
        if (module == null) return "";
        String id = module.id();
        if (isRussian()) {
            if (RU_MAP.containsKey(PREFIX + "module." + id + ".name")) return RU_MAP.get(PREFIX + "module." + id + ".name");
            if (RU_MAP.containsKey("module." + id + ".name")) return RU_MAP.get("module." + id + ".name");
            if (RU_MODULE_NAMES.containsKey(id)) return RU_MODULE_NAMES.get(id);
        } else if (isUkrainian()) {
            if (UA_MAP.containsKey(PREFIX + "module." + id + ".name")) return UA_MAP.get(PREFIX + "module." + id + ".name");
            if (UA_MAP.containsKey("module." + id + ".name")) return UA_MAP.get("module." + id + ".name");
            if (UA_MODULE_NAMES.containsKey(id)) return UA_MODULE_NAMES.get(id);
        } else {
            if (EN_MAP.containsKey(PREFIX + "module." + id + ".name")) return EN_MAP.get(PREFIX + "module." + id + ".name");
            if (EN_MAP.containsKey("module." + id + ".name")) return EN_MAP.get("module." + id + ".name");
            if (EN_MODULE_NAMES.containsKey(id)) return EN_MODULE_NAMES.get(id);
        }
        return module.name();
    }

    public static String moduleDescription(Module module) {
        if (module == null) return "";
        String id = module.id();
        if (isRussian()) {
            if (RU_MAP.containsKey(PREFIX + "module." + id + ".description")) return RU_MAP.get(PREFIX + "module." + id + ".description");
            if (RU_MAP.containsKey("module." + id + ".description")) return RU_MAP.get("module." + id + ".description");
            if (RU_MODULE_DESCS.containsKey(id)) return RU_MODULE_DESCS.get(id);
        } else if (isUkrainian()) {
            if (UA_MAP.containsKey(PREFIX + "module." + id + ".description")) return UA_MAP.get(PREFIX + "module." + id + ".description");
            if (UA_MAP.containsKey("module." + id + ".description")) return UA_MAP.get("module." + id + ".description");
            if (UA_MODULE_DESCS.containsKey(id)) return UA_MODULE_DESCS.get(id);
        } else {
            if (EN_MAP.containsKey(PREFIX + "module." + id + ".description")) return EN_MAP.get(PREFIX + "module." + id + ".description");
            if (EN_MAP.containsKey("module." + id + ".description")) return EN_MAP.get("module." + id + ".description");
            if (EN_MODULE_DESCS.containsKey(id)) return EN_MODULE_DESCS.get(id);
        }
        return module.description();
    }

    public static String category(ModuleCategory category) { return category == null ? "" : LocalText.get(category.displayName()); }

    public static String settingName(Setting<?> setting) {
        if (setting == null) return "";
        String key = setting.key();
        String fullKey = PREFIX + "setting." + key + ".name";
        String shortKey = "setting." + key + ".name";
        Map<String, String> activeMap = isRussian() ? RU_MAP : (isUkrainian() ? UA_MAP : EN_MAP);
        if (activeMap.containsKey(fullKey)) return activeMap.get(fullKey);
        if (activeMap.containsKey(shortKey)) return activeMap.get(shortKey);

        if (isRussian()) {
            return getCommonSettingRu(key, setting.name());
        } else if (isUkrainian()) {
            return getCommonSettingUa(key, setting.name());
        }
        return setting.name();
    }

    public static String settingDescription(Setting<?> setting) {
        if (setting == null) return "";
        String key = setting.key();
        String fullKey = PREFIX + "setting." + key + ".description";
        String shortKey = "setting." + key + ".description";
        Map<String, String> activeMap = isRussian() ? RU_MAP : (isUkrainian() ? UA_MAP : EN_MAP);
        if (activeMap.containsKey(fullKey)) return activeMap.get(fullKey);
        if (activeMap.containsKey(shortKey)) return activeMap.get(shortKey);

        return setting.description();
    }

    private static String getCommonSettingUa(String key, String fallback) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "scale" -> "Масштаб";
            case "size" -> "Розмір";
            case "speed" -> "Швидкість";
            case "color" -> "Колір";
            case "opacity", "alpha" -> "Прозорість";
            case "blur" -> "Розмиття";
            case "rainbow" -> "Веселка";
            case "rainbow_speed" -> "Швидкість веселки";
            case "glow" -> "Світіння";
            case "particles" -> "Частинки";
            case "radius" -> "Радіус";
            case "range" -> "Дистанція";
            case "thickness" -> "Товщина";
            case "volume" -> "Гучність";
            case "intensity" -> "Інтенсивність";
            case "fps" -> "Показувати FPS";
            case "ping" -> "Показувати пінг";
            case "time" -> "Показувати час";
            case "background" -> "Фон";
            case "shadow" -> "Тінь";
            case "border" -> "Обведення";
            case "rounded" -> "Скруглення";
            case "font" -> "Шрифт";
            case "smooth" -> "Плавність";
            case "fov" -> "Кут огляду";
            case "shake" -> "Трясіння екрана";
            case "mode" -> "Режим";
            case "density" -> "Щільність";
            case "enabled" -> "Увімкнено";
            case "disabled" -> "Вимкнено";
            default -> fallback;
        };
    }

    private static String getCommonSettingRu(String key, String fallback) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "scale" -> "Масштаб";
            case "size" -> "Размер";
            case "speed" -> "Скорость";
            case "color" -> "Цвет";
            case "opacity", "alpha" -> "Прозрачность";
            case "blur" -> "Размытие";
            case "rainbow" -> "Радуга";
            case "rainbow_speed" -> "Скорость радуги";
            case "glow" -> "Свечение";
            case "particles" -> "Частицы";
            case "radius" -> "Радиус";
            case "range" -> "Дистанция";
            case "thickness" -> "Толщина";
            case "volume" -> "Громкость";
            case "intensity" -> "Интенсивность";
            case "fps" -> "Показывать FPS";
            case "ping" -> "Показывать пинг";
            case "time" -> "Показывать время";
            case "background" -> "Фон";
            case "shadow" -> "Тень";
            case "border" -> "Обводка";
            case "rounded" -> "Скругление";
            case "font" -> "Шрифт";
            case "smooth" -> "Плавность";
            case "fov" -> "Угол обзора";
            case "shake" -> "Тряска экрана";
            case "mode" -> "Режим";
            case "density" -> "Плотность";
            case "enabled" -> "Включено";
            case "disabled" -> "Выключено";
            default -> fallback;
        };
    }

    private static String getEmbeddedFallback(String key) {
        boolean ru = isRussian();
        boolean ua = isUkrainian();
        return switch (key) {
            case "clickgui.user_default" -> ru ? "Пользователь AeroVisuals" : (ua ? "Користувач AeroVisuals" : "AeroVisuals User");
            case "clickgui.premium" -> ru ? "Премиум" : (ua ? "Преміум" : "Premium");
            case "clickgui.client_settings" -> ru ? "Настройки клиента" : (ua ? "Налаштування клієнта" : "Client Settings");
            case "clickgui.hud_editor" -> ru ? "Редактор HUD" : (ua ? "Редактор HUD" : "HUD Editor");
            case "clickgui.search" -> ru ? "Поиск..." : (ua ? "Пошук..." : "Search...");
            case "clickgui.interface" -> ru ? "Интерфейс" : (ua ? "Інтерфейс" : "Interface");
            case "clickgui.interface_desc" -> ru ? "Масштабирование GUI, темы и цвета" : (ua ? "Масштабування GUI, теми та кольори" : "GUI Scaling, neon themes & accent colors");
            case "clickgui.ui_scale" -> ru ? "Масштаб интерфейса" : (ua ? "Масштаб інтерфейсу" : "UI Scale");
            case "clickgui.accent_color" -> ru ? "Основной акцент" : (ua ? "Основний акцент" : "Accent Color");
            case "clickgui.secondary_cyan" -> ru ? "Вторичный бирюзовый" : (ua ? "Вторинний бірюзовий" : "Secondary Cyan");
            case "clickgui.controls" -> ru ? "Управление" : (ua ? "Керування" : "Controls");
            case "clickgui.menu_keybind" -> ru ? "Клавиша открытия ClickGUI" : (ua ? "Клавіша відкриття ClickGUI" : "ClickGUI open keybind");
            case "clickgui.gui_key" -> ru ? "Клавиша меню" : (ua ? "Клавіша меню" : "GUI Key");
            case "clickgui.audio" -> ru ? "Звук" : (ua ? "Звук" : "Audio");
            case "clickgui.audio_desc" -> ru ? "Тактильные звуковые эффекты меню" : (ua ? "Тактильні звукові ефекти меню" : "Tactile UI sound effects");
            case "clickgui.ui_sounds" -> ru ? "Звуки интерфейса" : (ua ? "Звуки інтерфейсу" : "UI Sounds");
            case "clickgui.volume" -> ru ? "Громкость" : (ua ? "Гучність" : "Volume");
            case "clickgui.color_palette" -> ru ? "Палитра цветов" : (ua ? "Палітра кольорів" : "Color Palette");
            case "clickgui.neon_accent" -> ru ? "Неоновый акцент" : (ua ? "Неоновий акцент" : "Neon Accent");
            case "clickgui.setting_color" -> ru ? "Цвет настройки" : (ua ? "Колір налаштування" : "Setting Color");
            case "clickgui.hud_editor_hint" -> ru ? "Редактор HUD — Перетащите для перемещения" : (ua ? "Редактор HUD — Перетягніть для переміщення" : "HUD Editor - Drag to move");
            case "clickgui.done_editing" -> ru ? "Готово" : (ua ? "Готово" : "Done Editing");
            case "clickgui.language" -> ru ? "Язык" : (ua ? "Мова" : "Language");
            case "clickgui.language_desc" -> ru ? "Язык интерфейса клиента" : (ua ? "Мова інтерфейсу клієнта" : "Client interface language");
            default -> null;
        };
    }

    public static String value(Object value) {
        if (value == null) return "";
        String raw = value instanceof Enum<?> enumeration ? enumeration.name() : String.valueOf(value);
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? raw : trOr("value." + normalized, raw.replace('_', ' '));
    }
}
