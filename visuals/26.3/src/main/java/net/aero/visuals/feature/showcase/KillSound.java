package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.*;
/** Three bundled Rival sounds plus an actual local WAV path, decoded off the game thread. */
public final class KillSound extends VisualExpansion.Feature implements AutoCloseable {
    public enum Sound { KILL_1, KILL_2, KILL_3, CUSTOM_WAV }
    private final EnumSetting<Sound> sound=add(new EnumSetting<>("sound","Звук","Три встроенных звука или config/aerovisuals/sounds/kill.wav.",Sound.KILL_1,Sound.class));
    private final DoubleSetting volume=number("volume","Громкость",.7,.05,1,.05),pitch=number("pitch","Высота звука",1,.5,2,.1);
    private final BooleanSetting preview=flag("preview","Прослушать звук",false);
    private final ExecutorService audio=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Aero custom kill audio");t.setDaemon(true);return t;});
    private final AtomicBoolean opening=new AtomicBoolean();private volatile Clip clip;private volatile int generation;private long last;
    public KillSound(){super("kill_sound","KillSound","Звук при смерти последней атакованной цели: встроенные эффекты или свой WAV, громкость и высота тона.", ModuleCategory.PVP);}
    @Subscribe private void tick(TickEvent.Client e){if(preview.get()){preview.set(false);play();}}
    public void play(){if(!isEnabled())return;long now=System.nanoTime();if(now-last<100_000_000L)return;last=now;if(sound.get()!=Sound.CUSTOM_WAV){var id=Identifier.fromNamespaceAndPath("aerovisuals","showcase.kill_"+(sound.get().ordinal()+1));Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id),pitch.get().floatValue(),volume.get().floatValue()));return;}if(!opening.compareAndSet(false,true))return;int ticket=generation;float gain=volume.get().floatValue(),tone=pitch.get().floatValue();audio.execute(()->{
        Clip opened=null;try{Path file=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("aerovisuals/sounds/kill.wav");Files.createDirectories(file.getParent());if(!Files.isRegularFile(file)||Files.size(file)>4_194_304)throw new java.io.IOException("Добавьте WAV до 4 МБ: config/aerovisuals/sounds/kill.wav");
            byte[] bytes;AudioFormat format;try(AudioInputStream input=AudioSystem.getAudioInputStream(file.toFile())){var source=input.getFormat();format=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,source.getSampleRate(),16,source.getChannels(),source.getChannels()*2,source.getSampleRate(),false);try(var decoded=AudioSystem.getAudioInputStream(format,input)){bytes=decoded.readNBytes((int)Math.min(4_194_304,format.getFrameRate()*format.getFrameSize()*10));}}
            if(ticket!=generation||!isEnabled())return;var shifted=new AudioFormat(format.getSampleRate()*tone,format.getSampleSizeInBits(),format.getChannels(),true,false);opened=AudioSystem.getClip();opened.open(shifted,bytes,0,bytes.length);if(opened.isControlSupported(FloatControl.Type.MASTER_GAIN)){var control=(FloatControl)opened.getControl(FloatControl.Type.MASTER_GAIN);control.setValue(Math.clamp((float)(20*Math.log10(Math.max(.001,gain))),control.getMinimum(),control.getMaximum()));}var running=opened;opened.addLineListener(event->{if(event.getType()==LineEvent.Type.STOP){running.close();if(clip==running)clip=null;opening.set(false);}});if(ticket!=generation||!isEnabled()){opened.close();return;}clip=opened;opened.start();opened=null;
        }catch(Exception e){String message=e.getMessage();Minecraft.getInstance().execute(()->net.aero.visuals.notification.NotificationManager.post(message==null?"Не удалось открыть WAV":message,false));}finally{if(opened!=null)opened.close();if(clip==null)opening.set(false);}
    });}
    @Override protected void onDisable(){generation++;preview.set(false);var active=clip;clip=null;if(active!=null){active.stop();active.close();}opening.set(false);}
    @Override public void close(){onDisable();audio.shutdownNow();}
}
