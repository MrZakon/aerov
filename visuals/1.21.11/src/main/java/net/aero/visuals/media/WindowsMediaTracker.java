package net.aero.visuals.media;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.W32APIOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance background service tracking media playback on Windows (Spotify, Yandex Music, YouTube, VK, browsers).
 * Uses a dual-engine architecture:
 * 1. Primary: Windows System Media Transport Controls (GSMTC) + Audio Endpoint Meter bridge for exact track,
 *    album artwork, position/duration, and real-time audio peak waveform.
 * 2. Fallback: Direct Win32 API window title enumeration via JNA with simulated audio wave harmonics.
 */
public final class WindowsMediaTracker {
    public static final MediaTrack EMPTY = new MediaTrack("", "", "NONE");

    private static final PlaybackSnapshot STOPPED = new PlaybackSnapshot(EMPTY, false, 0, 0, 0, "");
    private static final WindowsMediaTracker INSTANCE = new WindowsMediaTracker();

    private volatile PlaybackSnapshot playback = STOPPED;

    public record PlaybackSnapshot(MediaTrack track, boolean playing, int positionMs, int durationMs,
                                   long sampledAtNanos, String artworkPath) {
        public int interpolatedPositionMs() {
            long elapsedMs = playing && sampledAtNanos != 0
                    ? Math.max(0L, System.nanoTime() - sampledAtNanos) / 1_000_000L : 0;
            long position = Math.max(0, positionMs) + elapsedMs;
            return (int) Math.min(position, durationMs > 0 ? durationMs : Integer.MAX_VALUE);
        }
    }

    private final float[] waveLevels = new float[16];
    private int waveHead = 0;
    private volatile float audioPeak = 0.0f;

    private Thread workerThread;
    private Thread bridgeThread;
    private volatile Process bridgeProcess;
    private volatile Path commandFile;
    private long commandSequence;
    private volatile boolean running;
    private volatile long generation;
    private static boolean jnaAvailable = true;

    public interface User32 extends Library {
        User32 INSTANCE = load();

        private static User32 load() {
            try {
                return Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);
            } catch (Throwable t) {
                jnaAvailable = false;
                return null;
            }
        }

        interface WNDENUMPROC extends Callback {
            boolean callback(Pointer hWnd, Pointer arg);
        }

        boolean EnumWindows(WNDENUMPROC lpEnumFunc, Pointer arg);
        int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);
        int GetWindowThreadProcessId(Pointer hWnd, com.sun.jna.ptr.IntByReference processId);
        boolean IsWindowVisible(Pointer hWnd);
        void keybd_event(byte bVk, byte bScan, int dwFlags, Pointer dwExtraInfo);
    }

    private WindowsMediaTracker() {}

    public static WindowsMediaTracker get() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (running) return;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.startsWith("windows")) return;
        running = true;
        long session = ++generation;

        // 1. Try launching GSMTC native bridge
        bridgeThread = new Thread(() -> initBridge(session), "AeroVisuals-MediaBridge");
        bridgeThread.setDaemon(true);
        bridgeThread.setPriority(Thread.MIN_PRIORITY);
        bridgeThread.start();

        // 2. Start fallback polling loop
        workerThread = new Thread(() -> pollLoop(session), "AeroVisuals-MediaTracker");
        workerThread.setDaemon(true);
        workerThread.setPriority(Thread.MIN_PRIORITY);
        workerThread.start();
    }

    public synchronized void stop() {
        running = false;
        generation++;
        if (bridgeProcess != null) {
            try {
                bridgeProcess.destroyForcibly();
            } catch (Throwable ignored) {}
            bridgeProcess = null;
        }
        if (bridgeThread != null) {
            bridgeThread.interrupt();
            bridgeThread = null;
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        clearMediaState();
        audioPeak = 0.0f;
        synchronized (waveLevels) { java.util.Arrays.fill(waveLevels, 0); waveHead = 0; }
    }

    public PlaybackSnapshot playback() {
        return playback;
    }

    public MediaTrack getCurrentTrack() {
        return playback.track();
    }

    public boolean isPlaying() {
        return playback.playing();
    }

    public float getAudioPeak() {
        return audioPeak;
    }

    public float getWaveSample(int index) {
        if (index < 0 || index >= waveLevels.length) return 0.0f;
        synchronized (waveLevels) {
            return waveLevels[(waveHead - 1 - index + waveLevels.length) % waveLevels.length];
        }
    }

    public int getPositionMs() {
        return playback.interpolatedPositionMs();
    }

    public int getDurationMs() {
        return playback.durationMs();
    }

    public boolean isBridgeAlive() {
        Process process=bridgeProcess;
        return process != null && process.isAlive();
    }

    public static void playPause() {
        if (INSTANCE.isBridgeAlive()) {
            INSTANCE.sendCommand("TOGGLE");
        }
    }

    public static void nextTrack() {
        if (INSTANCE.isBridgeAlive()) {
            INSTANCE.sendCommand("NEXT");
        }
    }

    public static void previousTrack() {
        if (INSTANCE.isBridgeAlive()) {
            INSTANCE.sendCommand("PREV");
        }
    }

    private synchronized void sendCommand(String cmd) {
        Path directory=commandFile;
        if(!running||directory==null)return;
        Path temporary=null;
        try {
            temporary=Files.createTempFile(directory,"pending-",".tmp");
            Files.writeString(temporary,cmd,StandardCharsets.UTF_8);
            Path ready=directory.resolve(String.format(java.util.Locale.ROOT,"%020d.cmd",++commandSequence));
            try{Files.move(temporary,ready,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,ready);}
        } catch (IOException failure) {
            net.aero.visuals.AeroVisuals.LOGGER.debug("Could not queue music command",failure);
        } finally {
            if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}
        }
    }

    private static void sendKey(byte vkCode) {
        if (!jnaAvailable || User32.INSTANCE == null) return;
        try {
            User32.INSTANCE.keybd_event(vkCode, (byte) 0, 0, Pointer.NULL);
            User32.INSTANCE.keybd_event(vkCode, (byte) 0, 2, Pointer.NULL);
        } catch (Throwable ignored) {}
    }

    private boolean active(long session) { return running && generation == session; }

    private void initBridge(long session) {
        Process process = null;
        Path commands = null;
        try {
            String appData = System.getenv("LOCALAPPDATA");
            Path dir = appData != null ? Paths.get(appData, "AeroVisuals", "media")
                    : Paths.get(System.getProperty("java.io.tmpdir"), "lunacy_media");
            Files.createDirectories(dir);
            Path exe = prepareBridge(dir, session);
            if (exe == null || !active(session)) return;
            commands = Files.createTempDirectory(dir, "commands-" + ProcessHandle.current().pid() + "-");
            Path artDir = commands.resolve("art");
            Files.createDirectories(artDir);
            ProcessBuilder builder = new ProcessBuilder(exe.toString(), commands.toString(), artDir.toString(),
                    String.valueOf(ProcessHandle.current().pid())).redirectErrorStream(true);
            synchronized (this) {
                if (!active(session)) return;
                process = builder.start();
                bridgeProcess = process;
                commandFile = commands;
                commandSequence = 0;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (active(session) && (line = reader.readLine()) != null) {
                    synchronized (this) {
                        if (!active(session)) break;
                        if (line.startsWith("P\t")) {
                            try { pushPeak(Float.parseFloat(line.substring(2))); }
                            catch (NumberFormatException ignored) {}
                        } else if (line.startsWith("M\t")) handleMediaLine(line.substring(2));
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            if (active(session)) net.aero.visuals.AeroVisuals.LOGGER.warn("Music session bridge could not start", error);
        } finally {
            if (process != null) process.destroyForcibly();
            synchronized (this) {
                if (bridgeProcess == process) bridgeProcess = null;
                if (commandFile == commands) commandFile = null;
            }
            cleanupCommandDirectory(commands);
        }
    }

    private void cleanupCommandDirectory(Path directory) {
        if (directory == null) return;
        for (Path folder : List.of(directory.resolve("art"), directory)) {
            try (var files = Files.list(folder)) {
                for (Path file : files.toList()) if (Files.isRegularFile(file)) Files.deleteIfExists(file);
            } catch (IOException ignored) {}
            try { Files.deleteIfExists(folder); } catch (IOException ignored) {}
        }
    }

    /** Only a successfully compiled, checksum-matched executable may be reused. */
    private Path prepareBridge(Path directory, long session) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        String version = java.util.HexFormat.of().formatHex(digest.digest(BRIDGE_SOURCE.getBytes(StandardCharsets.UTF_8)));
        Path executable = directory.resolve("MediaBridge-v2-" + version + ".exe");
        Path receipt = directory.resolve("MediaBridge-v2-" + version + ".sha256");
        Path lockFile = directory.resolve("MediaBridge-v2-" + version + ".lock");
        // The lock is held only by this background worker, including across JVMs.
        try (var channel = java.nio.channels.FileChannel.open(lockFile,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            java.nio.channels.FileLock lock = null;
            long deadline = System.nanoTime() + 45_000_000_000L;
            while (active(session) && System.nanoTime() - deadline < 0 && lock == null) {
                try { lock = channel.tryLock(); }
                catch (java.nio.channels.OverlappingFileLockException busy) { /* previous worker is retiring */ }
                if (lock == null) Thread.sleep(50);
            }
            if (lock == null) return null;
            try {
                if (!active(session)) return null;
                if (validBridge(executable, receipt)) return executable;
                Path staging = Files.createTempDirectory(directory, "bridge-build-");
                Path source = staging.resolve("MediaBridge.cs");
                Path output = staging.resolve("MediaBridge.exe");
                Path checksum = staging.resolve("MediaBridge.sha256");
                try {
                    Files.writeString(source, BRIDGE_SOURCE, StandardCharsets.UTF_8);
                    if (!compileBridge(source, output) || !active(session)) return null;
                    Files.writeString(checksum, bridgeChecksum(output), StandardCharsets.UTF_8);
                    replaceBridgeFile(output, executable);
                    // Publishing the checksum last commits the complete cache entry.
                    replaceBridgeFile(checksum, receipt);
                    return executable;
                } finally {
                    for (Path file : List.of(source, output, checksum, staging)) {
                        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
                    }
                }
            } finally { lock.release(); }
        }
    }

    private static void replaceBridgeFile(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static boolean validBridge(Path executable, Path receipt) {
        try {
            return Files.isRegularFile(executable) && Files.size(executable) > 0 && Files.size(executable) <= 8_388_608L
                    && Files.isRegularFile(receipt) && Files.size(receipt) <= 128
                    && bridgeChecksum(executable).equals(Files.readString(receipt, StandardCharsets.UTF_8).strip());
        } catch (Exception failure) { return false; }
    }

    private static String bridgeChecksum(Path executable) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(executable)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }


    private boolean compileBridge(Path source, Path exe) {
        try {
            Path csc = Paths.get("C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319", "csc.exe");
            Path metadata = Paths.get("C:\\Windows\\System32\\WinMetadata");
            if (!Files.isRegularFile(csc) || !Files.isDirectory(metadata)) return false;

            List<String> cmd = new ArrayList<>(List.of(
                    csc.toString(), "-nologo", "-codepage:65001", "-optimize+", "-target:exe",
                    "-out:" + exe.toAbsolutePath()
            ));
            for (String winmd : new String[]{"Windows.Foundation.winmd", "Windows.Media.winmd", "Windows.Storage.winmd"}) {
                Path p = metadata.resolve(winmd);
                if (Files.isReadable(p)) cmd.add("-reference:" + p);
            }
            cmd.add("-reference:C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\System.Runtime.dll");
            cmd.add(source.toAbsolutePath().toString());

            // No output is consumed by the caller; discard it so a full pipe cannot stall shutdown.
            Process compiler = new ProcessBuilder(cmd).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try {
                return compiler.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                        && compiler.exitValue() == 0 && Files.isRegularFile(exe);
            } finally {
                if (compiler.isAlive()) compiler.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception failure) {
            return false;
        }
    }

    private void handleMediaLine(String payload) {
        String[] parts = payload.split("\t", -1);
        if (parts.length < 6) {
            clearMediaState();
            return;
        }
        String title = parts[0].trim();
        String artist = parts[1].trim();
        String status = parts[2].trim();
        int pos = 0;
        int dur = 0;
        try { pos = Integer.parseInt(parts[3]); } catch (Exception ignored) {}
        try { dur = Integer.parseInt(parts[4]); } catch (Exception ignored) {}
        String source = parts[5].trim();
        String art = parts.length > 6 ? parts[6].trim() : "";

        if (!"NONE".equalsIgnoreCase(status) && (!title.isEmpty() || !artist.isEmpty())) {
            var track = new MediaTrack(title, artist, source.isEmpty() ? "Media" : source);
            playback = new PlaybackSnapshot(track, "Playing".equalsIgnoreCase(status),
                    Math.max(0, pos), Math.max(0, dur), System.nanoTime(), art);
        } else {
            clearMediaState();
        }
    }

    private void clearMediaState() {
        playback = STOPPED;
    }

    private void pushPeak(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        audioPeak = clamped;
        synchronized (waveLevels) {
            waveLevels[waveHead] = clamped;
            waveHead = (waveHead + 1) % waveLevels.length;
        }
    }

    private void pollLoop(long session) {
        char[] buffer = new char[512];
        final MediaTrack[] foundTrack = new MediaTrack[1];

        User32.WNDENUMPROC callback = (hWnd, arg) -> {
            try {
                if (!User32.INSTANCE.IsWindowVisible(hWnd)) return true;
                int len = User32.INSTANCE.GetWindowTextW(hWnd, buffer, 512);
                if (len > 0) {
                    String title = new String(buffer, 0, len).trim();
                    MediaTrack t = parseTitle(title);
                    if(t==null){var pid=new com.sun.jna.ptr.IntByReference();User32.INSTANCE.GetWindowThreadProcessId(hWnd,pid);String command=ProcessHandle.of(Integer.toUnsignedLong(pid.getValue())).flatMap(process->process.info().command()).orElse("").toLowerCase(java.util.Locale.ROOT);if(command.contains("yandex")&&command.contains("music")&&!title.equalsIgnoreCase("Яндекс Музыка")&&!title.equalsIgnoreCase("Yandex Music"))t=parseSongAndArtist(title,"Yandex Music");}
                    if (t != null) {
                        foundTrack[0] = t;
                        return false;
                    }
                }
            } catch (Throwable ignored) {}
            return true;
        };

        long tick = 0;
        while (active(session)) {
            // Only use window enumeration fallback if bridge process is not feeding data
            if (!isBridgeAlive()) {
                try {
                    foundTrack[0] = null;
                    if (jnaAvailable && User32.INSTANCE != null) {
                        User32.INSTANCE.EnumWindows(callback, Pointer.NULL);
                    }
                    synchronized (this) {
                    if (!active(session)) break;
                    if (isBridgeAlive()) continue;
                    if (foundTrack[0] != null) {
                        var nextTrack = foundTrack[0];
                        var previous = playback;
                        if (!previous.playing() || previous.sampledAtNanos() != 0 || !nextTrack.equals(previous.track())) {
                            playback = new PlaybackSnapshot(nextTrack, true, 0, 0, 0, "");
                        }
                        // Synthesize wave harmonics when playing under fallback
                        float syntheticPeak = (float) (0.2 + 0.6 * Math.abs(Math.sin(tick * 0.25) * Math.cos(tick * 0.13)));
                        pushPeak(syntheticPeak);
                    } else {
                        clearMediaState();
                        pushPeak(0.0f);
                    }
                    }
                } catch (Throwable ignored) {
                    synchronized (this) {
                        if (!active(session)) break;
                        if (isBridgeAlive()) continue;
                        clearMediaState();
                    }
                }
            }
            tick++;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static MediaTrack parseTitle(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        String lower = t.toLowerCase();

        if (lower.contains("собираем музыку") || lower.equals("моя волна") || lower.equals("главное")
                || lower.equals("радио") || lower.equals("плейлист дня") || lower.equals("чарт")
                || lower.equals("коллекция") || lower.equals("настройки") || lower.equals("яндекс музыка")
                || lower.equals("яндекс.музыка") || lower.equals("yandex music")
                || lower.equals("spotify") || lower.equals("spotify free") || lower.equals("spotify premium")
                || lower.equals("default") || lower.startsWith("lunacy") || lower.startsWith("minecraft")
                || lower.startsWith("aerovisuals") || lower.startsWith("aero launcher")
                || lower.contains("visual studio") || lower.contains("intellij") || lower.contains("discord")
                || lower.contains("telegram") || lower.contains("explorer") || lower.contains("settings")
                || lower.contains("task manager") || lower.contains("command prompt") || lower.contains("powershell")) {
            return null;
        }

        if (t.startsWith("Яндекс Музыка — ") || t.startsWith("Яндекс Музыка - ")) {
            return parseSongAndArtist(t.substring(16).trim(), "Yandex Music");
        }
        if (t.contains(" - Яндекс Музыка") || t.contains(" — Яндекс Музыка")) {
            return parseSongAndArtist(t.replace(" - Яндекс Музыка", "").replace(" — Яндекс Музыка", "").trim(), "Yandex Music");
        }
        if (t.contains(" - YouTube Music")) {
            return parseSongAndArtist(t.replace(" - YouTube Music", "").trim(), "YouTube Music");
        }
        if (t.contains(" - Spotify")) {
            return parseSongAndArtist(t.replace(" - Spotify", "").trim(), "Spotify");
        }
        return null;
    }

    private static MediaTrack parseSongAndArtist(String text, String source) {
        String sep = text.contains(" — ") ? " — " : (text.contains(" - ") ? " - " : null);
        if (sep != null) {
            String[] parts = text.split(sep, 2);
            if (parts.length == 2) {
                String p1 = parts[0].trim();
                String p2 = parts[1].trim();
                if ("Spotify".equalsIgnoreCase(source)) {
                    return new MediaTrack(p2, p1, source);
                }
                return new MediaTrack(p1, p2, source);
            }
        }
        return new MediaTrack(text, "", source);
    }

    public record MediaTrack(String title, String artist, String source) {
        public boolean isPlaying() {
            return title != null && !title.isBlank() && !"NONE".equalsIgnoreCase(title);
        }
        public boolean isSpotify() { return "Spotify".equalsIgnoreCase(source); }
        public boolean isYandex() { return source != null && source.toLowerCase().contains("yandex"); }
        public String app() { return source != null ? source : "Media"; }
        public String shortTitle() { return isPlaying() ? title : ""; }
        public String fullTitle() {
            if (!isPlaying()) return "";
            if (artist != null && !artist.isBlank() && !artist.equalsIgnoreCase(title)) {
                return title + " - " + artist;
            }
            return title;
        }
    }

    private static final String BRIDGE_SOURCE = """
using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using Windows.Foundation;
using Windows.Media.Control;
using Windows.Storage.Streams;

[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
internal class MMDeviceEnumeratorComObject { }

[ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IMMDeviceEnumerator {
    int NotImpl1();
    int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
}

[ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IMMDevice {
    int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
}

[ComImport, Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IAudioMeterInformation {
    int GetPeakValue(out float peak);
}

internal static class AudioPeak {
    private static IAudioMeterInformation meter;
    public static float Read() {
        try {
            if (meter == null) {
                var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumeratorComObject());
                IMMDevice device;
                enumerator.GetDefaultAudioEndpoint(0, 1, out device);
                Guid iid = typeof(IAudioMeterInformation).GUID;
                object instance;
                device.Activate(ref iid, 1, IntPtr.Zero, out instance);
                meter = (IAudioMeterInformation)instance;
            }
            float val;
            meter.GetPeakValue(out val);
            return val;
        } catch {
            meter = null;
            return 0f;
        }
    }
}

public static class MediaBridge {
    const string TAB = "\\t";
    static T Await<T>(IAsyncOperation<T> op) {
        var ev = new ManualResetEventSlim(false);
        op.Completed = (o, s) => ev.Set();
        ev.Wait();
        return op.GetResults();
    }

    static string Clean(string s) {
        if (string.IsNullOrEmpty(s)) return "";
        return s.Replace('\\t', ' ').Replace('\\r', ' ').Replace('\\n', ' ');
    }

    static string SaveThumbnail(IRandomAccessStreamReference refStream, string artDir, int counter) {
        try {
            if (refStream == null) return "";
            byte[] bytes;
            using (var stream = Await(refStream.OpenReadAsync())) {
                if (stream == null || stream.Size == 0 || stream.Size > 16 * 1024 * 1024) return "";
                uint size = (uint)stream.Size;
                using (var reader = new DataReader(stream.GetInputStreamAt(0))) {
                    if (Await(reader.LoadAsync(size)) != size) return "";
                    bytes = new byte[size];
                    reader.ReadBytes(bytes);
                }
            }
            string path = Path.Combine(artDir, "art_" + counter + ".png");
            File.WriteAllBytes(path, bytes);
            string old = Path.Combine(artDir, "art_" + (counter - 2) + ".png");
            if (File.Exists(old)) { try { File.Delete(old); } catch {} }
            return path;
        } catch { return ""; }
    }

    static bool IsMusic(GlobalSystemMediaTransportControlsSession session) {
        try {
            var properties = Await(session.TryGetMediaPropertiesAsync());
            if (string.IsNullOrWhiteSpace(properties.Title)) return false;
            string type = properties.PlaybackType.ToString();

            string app = (session.SourceAppUserModelId ?? "").ToLowerInvariant();
            bool yandexMusic = (app.Contains("yandex") && app.Contains("music")) || (app.Contains("яндекс") && app.Contains("музык"));
            if (yandexMusic) return true;
            if (type == "Video" || type == "Image") return false;
            string[] musicApps = {"spotify", "yandexmusic", "yandex.music", "yandex-music", "яндекс музыка", "deezer", "tidal", "qobuz", "applemusic", "apple.music", "itunes", "aimp", "foobar2000", "musicbee", "winamp", "ytmdesktop", "youtube-music", "youtubemusic", "vkmusic", "vk.music", "boom"};
            foreach (string name in musicApps) if (app.Contains(name)) return true;
            // Unknown sources (including ordinary browsers) require an explicit music type
            // and track metadata. Never infer music merely from a window title or audio peak.
            return type == "Music" && !string.IsNullOrWhiteSpace(properties.Artist);
        } catch { return false; }
    }
    static GlobalSystemMediaTransportControlsSession FindMusic(GlobalSystemMediaTransportControlsSessionManager manager) {
        GlobalSystemMediaTransportControlsSession paused = null;
        try { foreach (var candidate in manager.GetSessions()) {
            if (!IsMusic(candidate)) continue;
            try { if (candidate.GetPlaybackInfo().PlaybackStatus.ToString() == "Playing") return candidate; } catch {}
            if (paused == null) paused = candidate;
        }} catch {}
        return paused;
    }
    public static int Main(string[] args) {
        Console.OutputEncoding = System.Text.Encoding.UTF8;
        string cmdFile = args.Length > 0 ? args[0] : null;
        string artDir = args.Length > 1 ? args[1] : Path.GetTempPath();
        Process parent = null;
        if (args.Length > 2) {
            try { parent = Process.GetProcessById(int.Parse(args[2], CultureInfo.InvariantCulture)); } catch { return 0; }
        }

        GlobalSystemMediaTransportControlsSessionManager mgr;
        try { mgr = Await(GlobalSystemMediaTransportControlsSessionManager.RequestAsync()); } catch { return 1; }

        string lastSource = null;
        string lastTitle = null;
        string lastArtist = null;
        string artFile = "";
        int artCounter = 0;
        var artClock = Stopwatch.StartNew();
        long nextArtAttempt = 0;
        int tick = 0;

        GlobalSystemMediaTransportControlsSession sess = null;
        while (true) {
            if (parent != null && tick % 20 == 0) {
                try { if (parent.HasExited) return 0; } catch {}
            }
            if (tick % 10 == 0) sess = FindMusic(mgr);

            if (cmdFile != null && Directory.Exists(cmdFile) && sess != null) {
                string[] commands = Directory.GetFiles(cmdFile, "*.cmd");
                Array.Sort(commands, StringComparer.Ordinal);
                foreach (string command in commands) {
                    try {
                        string c = File.ReadAllText(command).Trim();
                        File.Delete(command);
                        if (c == "TOGGLE") Await(sess.TryTogglePlayPauseAsync());
                        else if (c == "NEXT") Await(sess.TrySkipNextAsync());
                        else if (c == "PREV") Await(sess.TrySkipPreviousAsync());
                    } catch {}
                }
            }

            // The endpoint meter includes game/video sounds. Do not expose the mixed system signal.
            Console.WriteLine("P" + TAB + "0.0000");

            if (tick % 10 == 0) {
                if (sess == null) {
                    lastSource = lastTitle = lastArtist = null;
                    artFile = "";
                    nextArtAttempt = 0;
                    Console.WriteLine("M" + TAB + TAB + TAB + "NONE" + TAB + "0" + TAB + "0" + TAB + TAB);
                } else {
                    try {
                        var props = Await(sess.TryGetMediaPropertiesAsync());
                        var tl = sess.GetTimelineProperties();
                        var pb = sess.GetPlaybackInfo();
                        string title = Clean(props.Title);
                        string artist = Clean(props.Artist);
                        string status = pb.PlaybackStatus.ToString();
                        string source = Clean(sess.SourceAppUserModelId);
                        long pos = (long)tl.Position.TotalMilliseconds;
                        long dur = (long)(tl.EndTime - tl.StartTime).TotalMilliseconds;
                        if (status == "Playing") {
                            double age = (DateTimeOffset.Now - tl.LastUpdatedTime).TotalMilliseconds;
                            if (age > 0 && age < 60000) pos += (long)age;
                        }
                        if (dur <= 0 || dur > 86400000L) dur = 0;
                        if (pos < 0) pos = 0;
                        if (dur > 0 && pos > dur) pos = dur;
                        if (source != lastSource || title != lastTitle || artist != lastArtist) {
                            lastSource = source;
                            lastTitle = title;
                            lastArtist = artist;
                            artFile = "";
                            nextArtAttempt = 0;
                        }
                        // Some players publish the thumbnail after the track metadata.
                        if (artFile.Length == 0 && artClock.ElapsedMilliseconds >= nextArtAttempt) {
                            var thumbnail = props.Thumbnail;
                            if (thumbnail != null) {
                                artFile = SaveThumbnail(thumbnail, artDir, artCounter + 1);
                                if (artFile.Length != 0) artCounter++;
                            }
                            nextArtAttempt = artClock.ElapsedMilliseconds + 2000;
                        }
                        Console.WriteLine("M" + TAB + title + TAB + artist + TAB + status + TAB + pos + TAB + dur + TAB + source + TAB + artFile);
                    } catch {
                        Console.WriteLine("M" + TAB + TAB + TAB + "NONE" + TAB + "0" + TAB + "0" + TAB + TAB);
                    }
                }
            }
            tick++;
            Thread.Sleep(50);
        }
    }
}
""";
}
