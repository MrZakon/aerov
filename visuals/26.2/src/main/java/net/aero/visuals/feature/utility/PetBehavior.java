package net.aero.visuals.feature.utility;

/** Small animation state for an existing follower. It never changes collision or movement. */
public final class PetBehavior {
    public enum Mood { NORMAL, SLEEP, WET, RAIN, DANCE, GREETING }
    private enum Character { CALM, PLAYFUL, FLOATING }
    private static final Character[] CHARACTERS = Character.values();
    private int age, idle, greeting;
    private boolean wasAway;
    private Mood mood = Mood.NORMAL;
    private float bob, previousBob, pitch, previousPitch, roll, previousRoll, height = 1, previousHeight = 1;

    public void tick(int model, boolean moving, boolean water, boolean rain, boolean music, boolean homeSet, boolean home) {
        var character = switch (model) {
            case 132 -> Character.CALM;
            case 45, 46, 66, 84 -> Character.FLOATING;
            case 47, 135 -> Character.PLAYFUL;
            default -> CHARACTERS[Math.floorMod(model, CHARACTERS.length)];
        };
        age++;
        idle = moving ? 0 : Math.min(240, idle + 1);
        if (!homeSet) { wasAway = false; greeting = 0; }
        else if (!home) { wasAway = true; greeting = 0; }
        else if (wasAway) { wasAway = false; greeting = 34; }
        if (greeting > 0) greeting--;
        mood = water ? Mood.WET : greeting > 0 ? Mood.GREETING : music ? Mood.DANCE : rain ? Mood.RAIN
                : idle >= (character == Character.CALM ? 90 : 160) ? Mood.SLEEP : Mood.NORMAL;

        float bounce = 0, tilt = 0, sway = 0, stretch = 1;
        switch (mood) {
            case SLEEP -> {
                stretch = Math.min(.95f, (character == Character.FLOATING ? .87f : .72f) + sine(.13) * .025f);
                tilt = -9;
            }
            case WET -> {
                bounce = character == Character.FLOATING ? .16f : Math.abs(sine(.64)) * .15f;
                sway = sine(.95) * (character == Character.CALM ? 18 : 9);
                tilt = -13;
            }
            case RAIN -> {
                stretch = character == Character.FLOATING ? .98f : .9f;
                sway = sine(.65) * 5;
                tilt = 7;
            }
            case DANCE -> {
                double tempo = character == Character.CALM ? .25 : .43;
                bounce = Math.abs(sine(tempo)) * .11f;
                sway = sine(tempo) * (character == Character.CALM ? 11 : 22);
            }
            case GREETING -> {
                bounce = Math.abs(sine(.39)) * (character == Character.PLAYFUL ? .28f : .17f);
                tilt = sine(.39) * 13;
            }
            case NORMAL -> {
                if (character == Character.FLOATING) bounce = .07f + sine(.15) * .043f;
                else if (moving) bounce = Math.abs(sine(.44)) * (character == Character.PLAYFUL ? .077f : .035f);
            }
        }
        previousBob = bob; previousPitch = pitch; previousRoll = roll; previousHeight = height;
        bob += (bounce - bob) * .52f;
        pitch += (tilt - pitch) * .4f;
        roll += (sway - roll) * .48f;
        height += (stretch - height) * .36f;
    }

    public void reset() {
        age = idle = greeting = 0; wasAway = false; mood = Mood.NORMAL;
        bob = previousBob = pitch = previousPitch = roll = previousRoll = 0;
        height = previousHeight = 1;
    }
    public Mood mood() { return mood; }
    public float bob(float delta) { return lerp(previousBob, bob, delta); }
    public float pitch(float delta) { return lerp(previousPitch, pitch, delta); }
    public float roll(float delta) { return lerp(previousRoll, roll, delta); }
    public float height(float delta) { return lerp(previousHeight, height, delta); }
    private float sine(double speed) { return (float) Math.sin(age * speed); }
    private static float lerp(float before, float after, float delta) { return before + (after - before) * delta; }
}
