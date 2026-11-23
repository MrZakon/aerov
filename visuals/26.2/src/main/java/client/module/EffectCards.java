package client.module;

import java.util.List;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleManager;

enum EffectCards {
    JUMPS("Эффекты прыжка", "Jump Effects", "Ефекти стрибка",
            "jump_circles", "wave_effect"),
    TRAILS("Следы игрока", "Player Trails", "Сліди гравця",
            "player_trail", "dash_trail"),
    PARTICLES("Частицы окружения", "Ambient Particles", "Частинки довкілля",
            "world_particles", "particular", "weather_effects"),
    ITEM_PARTICLES("Частицы предметов", "Item Particles", "Частинки предметів",
            "pick_blowup", "block_particles"),
    MATERIALS("Шейдеры моделей", "Model Shaders", "Шейдери моделей",
            "shader_player", "shader_crystals"),
    SOUND("Настройки звука", "Sound Effects", "Налаштування звуку",
            "sound_controller", "sound_reducer");

    private final String russian;
    private final String english;
    private final String ukrainian;
    private final List<String> ids;

    EffectCards(String russian, String english, String ukrainian, String... ids) {
        this.russian = russian;
        this.english = english;
        this.ukrainian = ukrainian;
        this.ids = List.of(ids);
    }

    String title() {
        return LocalText.pick(russian, english, ukrainian);
    }

    boolean isPrimary(String id) {
        return ids.getFirst().equals(id);
    }

    List<Module> modules(ModuleManager manager) {
        return ids.stream().flatMap(id -> manager.find(id).stream()).toList();
    }

    static EffectCards find(String id) {
        for (EffectCards card : values()) {
            if (card.ids.contains(id)) return card;
        }
        return null;
    }
}