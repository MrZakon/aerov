package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.impl.misc.SmartEffectsModule;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.performance.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.Locale;

public final class FpsInspectorModule extends HudWidgetModule {
    private static FpsInspectorModule active;
    private final FrameStatistics history = new FrameStatistics(2048);
    private final FrameComparison comparison = new FrameComparison();
    private long lastSummary;
    private String summary = "Нет замеров", detail = "Открой мир и закрой меню", budget = "Бюджет эффектов выключен";
    private String stageLine = "", beforeLine = "", afterLine = "";
    private List<String> costLines = List.of();

    public FpsInspectorModule() {
        super("fps_inspector", "FPS Inspector", "Время кадра, p99 и CPU-затраты модулей Aero. Сравнение: кнопки в чате или .fps before / .fps after. Замеры идут только в активной игре.", new HudPosition(HudAnchor.TOP_LEFT, 8, 190, 1));
        scale.set(.9);
    }
    public static FpsInspectorModule active() { return active != null && active.isEnabled() ? active : null; }
    @Override protected void onEnable() { resetMeasurements(); active = this; }
    @Override protected void onDisable() { if (active == this) active = null; resetMeasurements(); }
    public void sample(long nanos) { history.add(nanos); comparison.sample(nanos); }
    public FrameComparison comparison() { return comparison; }
    public void resetMeasurements() {
        history.reset(); comparison.reset(); lastSummary = 0; PerformanceProbe.reset();
        stageLine = beforeLine = afterLine = "";
        summary = "Нет замеров"; detail = "Открой мир и закрой меню"; costLines = List.of();
    }
    public void before() { comparison.startBefore(); }
    public boolean after() { return comparison.startAfter(); }

    @Override protected void refresh(Minecraft client, float delta) {
        width = 274; height = 220;
        long now = System.nanoTime();
        if (now - lastSummary < 500_000_000L) return;
        lastSummary = now;
        var stats = history.snapshot();
        if (stats.frames() > 0) {
            summary = String.format(Locale.ROOT, "%.0f FPS  ·  %.2f ms  ·  p99 %.2f ms", stats.fps(), stats.meanMs(), stats.p99Ms());
            detail = String.format(Locale.ROOT, "Пик %.1f ms  ·  кадров >50 ms: %d", stats.peakMs(), stats.stalls());
        }
        costLines = PerformanceProbe.costs().stream().map(cost -> String.format(Locale.ROOT,
                "%s: %.3f ms/кадр", cost.label(), cost.milliseconds())).toList();
        var adaptive = SmartEffectsModule.active();
        budget = adaptive == null ? "Бюджет эффектов выключен" : String.format(Locale.ROOT,
                "Бюджет %.0f%%  ·  цель %.0f FPS", adaptive.factor() * 100, adaptive.effectiveTarget());
        stageLine = stageText();
        var before = comparison.before(); var after = comparison.after();
        beforeLine = before == null ? "" : String.format(Locale.ROOT, "До: %.0f FPS · p99 %.1f ms", before.fps(), before.p99Ms());
        afterLine = after == null ? "" : String.format(Locale.ROOT, "После: %.0f FPS · %+.1f%% · p99 %.1f ms", after.fps(),
                (after.fps() / before.fps() - 1) * 100, after.p99Ms());
    }
    @Override protected void renderHud(GuiGraphicsExtractor draw, Minecraft client) {
        title(draw, "FPS Inspector");
        text(draw, summary, 8, 28, 10, 0xFFF1F4FF);
        text(draw, detail, 8, 43, 9, 0xFFACB6C4);
        text(draw, budget, 8, 57, 9, 0xFF80DAB8);
        text(draw, client.screen == null ? "Время вызовов Aero · CPU-side, не GPU-таймер"
                : "Замеры на паузе · закрой меню или чат", 8, 76, 8, 0xFFACB6C4);
        if (costLines.isEmpty()) text(draw, "Собираю CPU-замеры…", 8, 89, 9, 0xFFACB6C4);
        for (int i = 0; i < costLines.size(); i++) text(draw, fit(costLines.get(i), width - 16, 9), 8, 89 + i * 12, 9, 0xFFE2E9EE);
        text(draw, fit(stageLine, width - 16, 9), 8, 142, 9, 0xFFF1F4FF);
        text(draw, beforeLine, 8, 156, 9, 0xFFACB6C4);
        text(draw, fit(afterLine, width - 16, 9), 8, 169, 9, 0xFF80DAB8);
        button(draw, 8, "Замер ДО"); button(draw, 96, "Замер ПОСЛЕ"); button(draw, 184, "Сброс");
        text(draw, "Кнопки — в чате. Сравнивай одну и ту же сцену.", 8, 207, 8, 0xFFACB6C4);
    }
    private String stageText() {
        return switch (comparison.stage()) {
            case IDLE -> "Сравнение: 2 сек. прогрев + 10 сек. замер";
            case READY -> "Измени эффекты и нажми «Замер ПОСЛЕ»";
            case COMPLETE -> "Сравнение завершено";
            default -> String.format(Locale.ROOT, "%s · осталось %.1f сек. активной игры",
                    comparison.stage() == FrameComparison.Stage.BEFORE ? "Замер ДО"
                            : comparison.stage() == FrameComparison.Stage.AFTER ? "Замер ПОСЛЕ" : "Прогрев", comparison.remainingSeconds());
        };
    }
    private void button(GuiGraphicsExtractor draw, int x, String label) {
        UiRenderer.roundedRect(draw, x, 187, 82, 16, 4, 0xFF2C3746);
        text(draw, label, x + 5, 191, 9, 0xFFF1F4FF);
    }
    @Override protected boolean handleClick(double x, double y) {
        if (y < 187 || y > 203) return false;
        if (x >= 8 && x <= 90) { before(); return true; }
        if (x >= 96 && x <= 178) { after(); return true; }
        if (x >= 184 && x <= 266) { resetMeasurements(); return true; }
        return false;
    }
}
