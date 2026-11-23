package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.*;
/** GPU section transforms for the current Minecraft terrain renderer, not the obsolete renderLayer hook. */
public final class ChunkAnimator extends VisualExpansion.Feature {
    public enum Direction { BELOW, ABOVE, SIDE }
    private static final class Seen {final long born;Seen(long now){born=now;}}
    private final EnumSetting<Direction> direction=add(new EnumSetting<>("direction","Направление","Откуда появляются новые секции чанков.",Direction.BELOW,Direction.class));
    private final DoubleSetting duration=number("duration","Длительность (мс)",700,150,2000,50),distance=number("near","Не анимировать рядом",48,16,96,8),height=number("height","Смещение",12,2,24,1);
    private final LinkedHashMap<Long,Seen> sections=new LinkedHashMap<>(256,.75f,true);private java.lang.ref.WeakReference<Object> world = new java.lang.ref.WeakReference<>(null);
    public ChunkAnimator(){super("chunk_animator","ChunkAnimator","Плавное появление секций чанков снизу, сверху или сбоку. Использует штатный рендер Minecraft.", ModuleCategory.RENDER);
        if (!net.aero.visuals.mixin.AeroMixinPlugin.supportsChunkAnimation()) return;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register((level,chunk)->{
            if(world.get()==level){var pos=chunk.getPos();sections.keySet().removeIf(key->(BlockPos.getX(key)>>4)==pos.x()&&(BlockPos.getZ(key)>>4)==pos.z());}
        });
    }
    @Override public String displayValue() {
        return net.aero.visuals.mixin.AeroMixinPlugin.supportsChunkAnimation() ? "" : "Недоступно с Sodium";
    }
    @Override protected void onDisable(){sections.clear();world.clear();}
    public Matrix4fc transform(Matrix4fc view,int x,int y,int z){var mc=Minecraft.getInstance();if(!isEnabled()||mc.player==null||mc.level==null)return view;if(world.get()!=mc.level){sections.clear();world = new java.lang.ref.WeakReference<>(mc.level);}long now=System.nanoTime();
        long key=BlockPos.asLong(x,y,z);var entry=sections.get(key);if(entry==null){entry=new Seen(now);sections.put(key,entry);}long born=entry.born;while(sections.size()>32768)sections.remove(sections.keySet().iterator().next());
        double dx=x+8-mc.player.getX(),dz=z+8-mc.player.getZ();if(dx*dx+dz*dz<distance.get()*distance.get())return view;double t=Math.clamp((now-born)/1e6/duration.get(),0,1);if(t>=1)return view;double offset=height.get()*Math.pow(1-t,3);return switch(direction.get()){case BELOW->new Matrix4f(view).translate(0,(float)-offset,0);case ABOVE->new Matrix4f(view).translate(0,(float)offset,0);case SIDE->{double length=Math.max(1,Math.sqrt(dx*dx+dz*dz));yield new Matrix4f(view).translate((float)(dx/length*offset),0,(float)(dz/length*offset));}};
    }
}
