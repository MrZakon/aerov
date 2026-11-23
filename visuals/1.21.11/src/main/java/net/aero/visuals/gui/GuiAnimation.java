package net.aero.visuals.gui;

public final class GuiAnimation {
    private float value;
    private float startValue;
    private float targetValue;
    private long startTime;
    private long duration;
    private int easing; // 0 = expo, 1 = cubic

    public GuiAnimation(float initialValue) {
        this.value = initialValue;
        this.startValue = initialValue;
        this.targetValue = initialValue;
    }

    public static float easeOutExpo(float x) {
        return x >= 0.999f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * x);
    }

    public static float easeOutCubic(float x) {
        return 1.0f - (float) Math.pow(1.0 - x, 3.0);
    }

    public void animateTo(float target, long durationMs) {
        if (Math.abs(this.targetValue - target) > 0.0001f) {
            this.startValue = getValue();
            this.targetValue = target;
            this.duration = Math.max(1L, durationMs);
            this.startTime = System.currentTimeMillis();
        }
    }

    public void force(float value) {
        this.value = value;
        this.startValue = value;
        this.targetValue = value;
        this.startTime = 0L;
    }

    public void setEasing(int easing) {
        this.easing = easing;
    }

    public float getValue() {
        if (this.startTime == 0L) {
            return this.value;
        }
        long elapsed = System.currentTimeMillis() - this.startTime;
        if (elapsed >= this.duration) {
            this.value = this.targetValue;
            this.startTime = 0L;
            return this.value;
        }
        float progress = Math.clamp((float) elapsed / (float) this.duration, 0.0f, 1.0f);
        float eased = (this.easing == 1) ? easeOutCubic(progress) : easeOutExpo(progress);
        this.value = this.startValue + (this.targetValue - this.startValue) * eased;
        return this.value;
    }

    public float getTarget() {
        return this.targetValue;
    }
}
