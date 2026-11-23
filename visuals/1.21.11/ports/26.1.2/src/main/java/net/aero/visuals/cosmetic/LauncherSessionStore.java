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

    LauncherSessionStore(URI base){
        this.base=base;
        String explicit=System.getProperty("aero.sessionFile",System.getenv("AERO_SESSION_FILE"));
        if(explicit==null||explicit.isBlank())explicit=System.getProperty("lunacy.sessionFile",System.getenv("LUNACY_SESSION_FILE"));
        if(explicit!=null&&!explicit.isBlank())try{selected=Path.of(explicit);}catch(InvalidPathException ignored){}
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

    Session read(){
        if(selected==null)for(Path path:candidates)if(Files.isRegularFile(path)){selected=path;break;}
        if(selected==null)return new Session(false,"","Файл авторизации Aero Launcher не найден. Скачайте лаунчер и войдите в аккаунт.");
        // Once a launcher owns this session, deleting it is logout, not permission to reuse old website tokens.
        try{
            if(!Files.isRegularFile(selected))return new Session(true,"","Файл авторизации лаунчера отсутствует. Войдите в Aero Launcher.");
            if(Files.size(selected)>32768)return new Session(true,"","Файл авторизации лаунчера повреждён. Повторите вход в лаунчере.");
            var root=JsonParser.parseString(Files.readString(selected)).getAsJsonObject();
            if(root.has("apiBaseUrl")&&!URI.create(root.get("apiBaseUrl").getAsString().replaceAll("/+$","")+"/").equals(base))return new Session(true,"","Сессия лаунчера относится к другому сайту.");
            String token=root.has("token")?root.get("token").getAsString():"";
            String hwid=root.has("hwid")?root.get("hwid").getAsString().strip().toUpperCase(Locale.ROOT):"";
            if(hwid.length()>256)return new Session(true,"","Файл авторизации содержит некорректный HWID.");
            return token.matches("[a-f0-9]{64}")?new Session(true,token,"",hwid):new Session(true,"","В файле лаунчера нет действительной авторизации. Войдите в Aero Launcher.");
        }catch(Exception ignored){return new Session(true,"","Не удалось прочитать авторизацию лаунчера. Повторите вход в Aero Launcher.");}
    }

    boolean managed(){return selected!=null;}
    void rescan(){selected=explicitPath;}
}
