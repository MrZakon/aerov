package net.aero.visuals.cosmetic;

import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Read-only handoff from AeroAuth::SaveSession and GameLauncher::Launch. */
final class LauncherSessionStore {
    record Session(boolean managed,String token,String message,String hwid){
        Session(boolean managed,String token,String message){this(managed,token,message,"");}
    }
    private final URI base;
    private final List<Path> candidates=new ArrayList<>();
    private volatile Path selected;
    private final Path explicitPath;
    private final boolean explicitRequested;

    LauncherSessionStore(URI base){
        this.base=base;
        String explicit=System.getProperty("aero.sessionFile",System.getenv("AERO_SESSION_FILE"));
        if(explicit==null||explicit.isBlank())explicit=System.getProperty("lunacy.sessionFile",System.getenv("LUNACY_SESSION_FILE"));
        explicitRequested = explicit != null && !explicit.isBlank();
        if (explicitRequested) {
            try { selected = Path.of(explicit); }
            catch (InvalidPathException ignored) { }
        }
        explicitPath=selected;
        String appdata=System.getenv("APPDATA"),home=System.getProperty("user.home"),xdg=System.getenv("XDG_CONFIG_HOME");
        if(appdata!=null&&!appdata.isBlank())for(String name:List.of("aero","Aero Launcher",".aero","Lunacy Launcher",".lunacy"))
            candidates.add(Path.of(appdata,name,name.toLowerCase(Locale.ROOT).contains("lunacy")?"lunacy-session.json":"aero-session.json"));
        if(home!=null&&!home.isBlank()){
            candidates.add(Path.of(home,"Library","Application Support","Aero","aero-session.json"));
            candidates.add(xdg!=null&&!xdg.isBlank()?Path.of(xdg,"aero","aero-session.json"):Path.of(home,".config","aero","aero-session.json"));
            candidates.add(Path.of(home,".aero","aero-session.json"));
        }
    }

    Session read(Set<String> rejected, String localHwid) {
        if (explicitRequested) {
            return explicitPath != null ? readFile(explicitPath)
                    : new Session(true, "", "Некорректный путь к файлу авторизации Aero Launcher.");
        }
        if (selected != null && !Files.isRegularFile(selected)) return readFile(selected);

        Session error = null;
        List<Path> available = candidates.stream().filter(Files::isRegularFile)
                .sorted(Comparator.comparingLong(LauncherSessionStore::modifiedTime).reversed()).toList();
        for (Path candidate : available) {
            Session session = readFile(candidate);
            boolean wrongDevice = !session.hwid().isBlank() && !localHwid.isBlank() && !session.hwid().equalsIgnoreCase(localHwid);
            if (!wrongDevice && !session.token().isBlank() && !rejected.contains(session.token())) {
                selected = candidate;
                return session;
            }
            if (error == null) {
                if (session.token().isBlank()) error = session;
                else if (wrongDevice) error = new Session(true, "", "HWID файла лаунчера не совпадает с ПК. Повторите вход в Aero Launcher.");
                else error = new Session(true, "", "Сессия лаунчера истекла. Повторите вход в Aero Launcher.");
            }
        }
        return error != null ? error : new Session(false, "", "Файл авторизации Aero Launcher не найден. Скачайте лаунчер и войдите в аккаунт.");
    }

    private static long modifiedTime(Path file) {
        try { return Files.getLastModifiedTime(file).toMillis(); }
        catch (java.io.IOException ignored) { return 0; }
    }

    private Session readFile(Path file) {
        try{
            if(!Files.isRegularFile(file))return new Session(true,"","Файл авторизации лаунчера отсутствует. Войдите в Aero Launcher.");
            if(Files.size(file)>32768)return new Session(true,"","Файл авторизации лаунчера повреждён. Повторите вход в лаунчере.");
            var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(root.has("apiBaseUrl")&&!URI.create(root.get("apiBaseUrl").getAsString().replaceAll("/+$","")+"/").equals(base))return new Session(true,"","Сессия лаунчера относится к другому сайту.");
            String token=root.has("token")?root.get("token").getAsString():"";
            String hwid=root.has("hwid")?root.get("hwid").getAsString().strip().toUpperCase(Locale.ROOT):"";
            if(hwid.isBlank()||hwid.length()>256)return new Session(true,"","В файле лаунчера отсутствует или повреждён HWID. Повторите вход в Aero Launcher.");
            return token.matches("[a-f0-9]{64}")?new Session(true,token,"",hwid):new Session(true,"","В файле лаунчера нет действительной авторизации. Войдите в Aero Launcher.");
        }catch(Exception ignored){return new Session(true,"","Не удалось прочитать авторизацию лаунчера. Повторите вход в Aero Launcher.");}
    }

    boolean managed(){return explicitRequested || selected != null || candidates.stream().anyMatch(Files::isRegularFile);}
}
