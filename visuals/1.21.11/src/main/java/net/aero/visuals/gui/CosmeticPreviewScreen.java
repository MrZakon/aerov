package net.aero.visuals.gui;
import net.aero.visuals.feature.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.setting.DoubleSetting;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;
import client.api.Theme;
import net.aero.visuals.localization.LocalText;
/** Local dressing room; both GUI and world render the same model-attached feature. */
public final class CosmeticPreviewScreen extends Screen {
    private java.util.List<Integer> filtered=java.util.List.of();private double scroll,targetScroll;
    private List<RowLabel> rowLabels = List.of();private int uiLanguage=-1;
    private boolean embedded;private int originX,originY;private double visibility;private long transitionTime;private float panelScale=1;
    private final Screen parent;private String type="cape",query="";private int page;private float yaw=180;private boolean auto=true;private long frame;private TextFieldWidget search;private final CosmeticPreviewState state=new CosmeticPreviewState();
    public CosmeticPreviewScreen(Screen parent){super(Text.literal("Косметика"));this.parent=parent;}
    private int split(){return Math.max(180,width/2);}
    private void button(String label, int x, int y, int width, Runnable action) {
        addDrawableChild(new ClickableWidget(x, y, width, 20, Text.literal(label.startsWith("• ") ? "• " + LocalText.get(label.substring(2)) : LocalText.get(label))) {
            private net.minecraft.client.font.TextRenderer layoutFont;
            private net.minecraft.util.Language layoutLanguage;
            private int layoutWidth;
            private List<net.minecraft.text.OrderedText> lines = List.of();
            private final int[] offsets = new int[2];

            @Override
            public void onClick(Click event, boolean twice) {
                action.run();
            }

            @Override
            public boolean keyPressed(net.minecraft.client.input.KeyInput event) {
                int key = event.key();
                if (!isFocused() || (key != 257 && key != 32)) return false;
                action.run();
                return true;
            }

            @Override
            protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder narration) {
                appendDefaultNarrations(narration);
            }

            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                net.aero.visuals.render.UiRenderer.roundedRect(context, getX(), getY(), getWidth(),
                        getHeight(), 5, isHovered() ? Theme.elevated() : Theme.surface());
                var font = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
                var language = net.minecraft.util.Language.getInstance();
                if (font != layoutFont || language != layoutLanguage || getWidth() != layoutWidth) {
                    layoutFont = font;
                    layoutLanguage = language;
                    layoutWidth = getWidth();
                    var wrapped = font.wrapLines(getMessage(), layoutWidth - 10);
                    lines = List.copyOf(wrapped.subList(0, Math.min(2, wrapped.size())));
                    for (int i = 0; i < lines.size(); i++) {
                        offsets[i] = (layoutWidth - font.getWidth(lines.get(i))) / 2;
                    }
                }
                for (int i = 0; i < lines.size(); i++) {
                    context.drawText(font, lines.get(i), getX() + offsets[i],
                            getY() + (lines.size() > 1 ? 1 + i * 9 : 6), Theme.foreground(), false);
                }
            }
        });
    }
    private ImportedFeatures.Cosmetics cosmetics(){return ImportedFeatures.get(ImportedFeatures.Cosmetics.class);}
    private ImportedFeatures.Pet pet(){return ImportedFeatures.get(ImportedFeatures.Pet.class);}
    private DoubleSetting selection(){return switch(type){case "cape"->cosmetics().variant;case "wings"->cosmetics().wingsVariant;case "hat"->cosmetics().hatVariant;case "bodywear"->cosmetics().bodywearVariant;case "graffiti"->cosmetics().graffitiVariant;default->pet().variant;};}
    private boolean enabled(){return type.equals("pet")?pet().isEnabled():cosmetics().isEnabled()&&switch(type){case "cape"->cosmetics().cape.get();case "wings"->cosmetics().wings.get();case "bodywear"->cosmetics().bodywear.get();case "graffiti"->cosmetics().graffiti.get();default->cosmetics().hat.get();};}
    private void toggle(){boolean value=!enabled();if(type.equals("pet")){pet().setEnabled(value);if(!value)ClientRuntime.get().cosmeticSelection().clear("pet");}else{switch(type){case "cape"->cosmetics().cape.set(value);case "wings"->cosmetics().wings.set(value);case "hat"->cosmetics().hat.set(value);case "bodywear"->cosmetics().bodywear.set(value);case "graffiti"->cosmetics().graffiti.set(value);}if(value)cosmetics().setEnabled(true);else ClientRuntime.get().cosmeticSelection().clear(type);}rebuild();}
    private void choose(int index){if(selection().get().intValue()==index&&enabled()){toggle();return;}selection().set((double)index);if(type.equals("pet"))pet().setEnabled(true);else{cosmetics().setEnabled(true);switch(type){case "cape"->cosmetics().cape.set(true);case "wings"->cosmetics().wings.set(true);case "hat"->cosmetics().hat.set(true);case "bodywear"->cosmetics().bodywear.set(true);case "graffiti"->cosmetics().graffiti.set(true);}}rebuild();}
    private boolean rebuilding;
    private void rebuild() {
        boolean focused = search != null && getFocused() == search;
        query = search == null ? query : search.getText();
        setFocused(null);
        clearChildren();
        rebuilding = true;
        try { init(); } finally { rebuilding = false; }
        if (focused && search != null) setFocused(search);
    }
    @Override protected void init(){if(cosmetics()==null||pet()==null)return;int leftWidth=split()-24;String[] types={"cape","wings","bodywear","hat","pet","graffiti"},names={"Плащи","Крылья","Рюкзаки","Украшения","Питомцы","Граффити"};int cell=Math.max(35,(leftWidth-10)/6);for(int i=0;i<6;i++){String next=types[i];button((next.equals(type)?"• ":"")+names[i],12+i*(cell+2),35,cell,()->{type=next;page=0;scroll=targetScroll=0;rebuild();});}
        if (rebuilding && search != null) {
            addDrawableChild(search);
            search.setPlaceholder(Text.literal(LocalText.get("Найти модель…")));
        } else {
            search=addDrawableChild(new TextFieldWidget(textRenderer,12,61,leftWidth,20,Text.literal("Поиск косметики")));search.setMaxLength(48);search.setPlaceholder(Text.literal(LocalText.get("Найти модель…")));search.setText(query);search.setChangedListener(value->{query=value;page=0;refreshLater=true;});
        }
        button(enabled()?"Выключить":"Включить",12,87,leftWidth,()->toggle());

        rebuildRows();
        button(LocalText.pick("Наборы","Sets","Набори"),split()-100,7,76,()->client.setScreen(new CosmeticPresetsScreen(client.currentScreen)));
        int toolsX = split() + 8;
        int toolsWidth = Math.max(1, width - toolsX - 8);
        boolean showPair = !net.aero.visuals.regulator.ServerRestrictions.hidesModule("paired_emotes");
        boolean showPet = type.equals("pet") && !net.aero.visuals.regulator.ServerRestrictions.hidesModule("pet");
        int pairWidth = showPair && showPet ? Math.max(1, (toolsWidth - 4) / 2) : Math.min(130, toolsWidth);
        if (showPair) button("Парные эмоции", toolsX, 1, pairWidth,
                () -> client.setScreen(new ClientToolsScreen(client.currentScreen, ClientToolsScreen.Mode.PAIR)));
        int petOffset = showPair ? pairWidth + 4 : 0;
        if (showPet) button("Настройки", toolsX + petOffset, 1, Math.max(1, toolsWidth - petOffset),
                () -> client.setScreen(new ClientToolsScreen(client.currentScreen, ClientToolsScreen.Mode.PET)));
        int right=split()+12,rw=width-right-12;button(auto?"Вращение: вкл":"Вращение: выкл",right,height-65,rw,()->{auto=!auto;rebuild();});if(!embedded)button("Назад",12,height-30,100,this::close);else button("Снять всё",12,height-30,100,()->{cosmetics().cape.set(false);cosmetics().wings.set(false);cosmetics().hat.set(false);cosmetics().bodywear.set(false);cosmetics().graffiti.set(false);cosmetics().setEnabled(false);pet().setEnabled(false);for(String t:net.aero.visuals.cosmetic.CosmeticLoadout.TYPES)ClientRuntime.get().cosmeticSelection().clear(t);rebuild();});button("Спереди",right,height-38,Math.max(35,(rw-4)/2),()->{yaw=180;auto=false;rebuild();});button("Сзади",right+(rw+4)/2,height-38,Math.max(35,(rw-4)/2),()->{yaw=0;auto=false;rebuild();});var sync=ClientRuntime.get().cosmeticSync();if(sync!=null)button(sync.status(),120,height-30,Math.max(80,leftWidth-108),sync::connect);}
    private long regulatorVisibility = -1;private boolean refreshLater;private String websiteStatus="";
    private java.util.Map<String,Integer> displayedLoadout=java.util.Map.of();
    public void tickEmbedded() {
        if (search != null) tick();
    }

    @Override public void tick(){long policyVisibility=net.aero.visuals.regulator.ServerRestrictions.visibilityVersion();if(regulatorVisibility!=policyVisibility){regulatorVisibility=policyVisibility;refreshLater=true;}var equipped=net.aero.visuals.cosmetic.CosmeticLoadout.capture();if(!equipped.equals(displayedLoadout)){displayedLoadout=equipped;refreshLater=true;}var sync=ClientRuntime.get().cosmeticSync();String next=sync==null?"":sync.status();if(!websiteStatus.equals(next)){websiteStatus=next;refreshLater=true;}if(uiLanguage!=net.aero.visuals.localization.AeroI18n.languageMode){uiLanguage=net.aero.visuals.localization.AeroI18n.languageMode;refreshLater=true;}if(refreshLater){refreshLater=false;rebuild();}}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){net.aero.visuals.render.UiRenderer.roundedRect(context,0,0,width,height,8,Theme.background());net.aero.visuals.render.UiRenderer.roundedRect(context,split(),28,width-split()-5,height-103,8,Theme.surface());super.render(context,mouseX,mouseY,delta);context.drawText(textRenderer,LocalText.get("Косметика"),12,13,Theme.foreground(),false);
        long now=System.nanoTime();double dt=frame==0?0:Math.min(.1,(now-frame)/1e9);frame=now;scroll+=(targetScroll-scroll)*(1-Math.exp(-18*dt));drawList(context);if(auto)yaw=(yaw+(float)(dt*28))%360;
        if(client!=null&&client.player!=null){try{PlayerEntityRenderer renderer=(PlayerEntityRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);renderer.updateRenderState(client.player,state,1);state.bodyYaw=yaw;state.relativeHeadYaw=0;state.pitch=0;state.pose=net.minecraft.entity.EntityPose.STANDING;state.height=1.8f;state.baseScale=1;state.invisible=false;state.light=15728880;int x1=split()+5,y1=49,x2=width-10,y2=height-85;float scale=Math.max(20,Math.min((y2-y1)/2.7f,(x2-x1)/2.8f));context.addEntity(state,scale*panelScale,new Vector3f(0,.95f,0),new Quaternionf().rotateZ((float)Math.PI),null,(int)(x1*panelScale)+originX,(int)(y1*panelScale)+originY,(int)(x2*panelScale)+originX,(int)(y2*panelScale)+originY);}catch(RuntimeException e){context.drawCenteredTextWithShadow(textRenderer,LocalText.get("Не удалось отрисовать игрока"),(width+split())/2,70,0xFFFFAAAA);}}
        else context.drawCenteredTextWithShadow(textRenderer,LocalText.get("Зайди в мир для предпросмотра"),(width+split())/2,70,Theme.mutedFg());
        context.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(LocalText.get("Колесо — список, мышь — поворот"),width-split()-16),(width+split())/2,23,0xFFBFC5D9);
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy){if(click.button()==0&&click.x()>split()&&click.y()>35&&click.y()<height-75){auto=false;yaw=(yaw+(float)dx*.8f)%360;return true;}return super.mouseDragged(click,dx,dy);}
    public boolean isAnimating(){return false;}
    public void renderEmbedded(DrawContext d,int x,int y,int w,int h,int mx,int my,float delta,boolean open,float guiProgress){
        embedded = true;
        visibility = open ? 1 : 0;
        if (!open || w <= 0 || h <= 0) return;
        panelScale = Math.min(1f, Math.min(w / 560f, h / 300f));
        w = Math.round(w / panelScale);
        h = Math.round(h / panelScale);
        originX = x;
        originY = y + Math.round((1 - guiProgress) * 10);
        if(width!=w||height!=h)init(w,h);d.getMatrices().pushMatrix();d.getMatrices().translate(originX,originY);d.getMatrices().scale(panelScale,panelScale);try{render(d,(int)((mx-originX)/panelScale),(int)((my-originY)/panelScale),delta);}finally{d.getMatrices().popMatrix();}}
    private double maxScroll(){return Math.max(0,filtered.size()*32-Math.max(1,height-215));}
    private record RowLabel(String singleLine, List<net.minecraft.text.OrderedText> lines) {}

    private void rebuildRows() {
        var entries = ImportedModels.catalog(type);
        String filter = query.toLowerCase(java.util.Locale.ROOT);
        var indices = new java.util.ArrayList<Integer>();
        var labels = new java.util.ArrayList<RowLabel>();
        int textWidth = split() - 53;
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (!entry.name().toLowerCase(java.util.Locale.ROOT).contains(filter)) continue;
            indices.add(index);
            String label = entry.name().isBlank() ? type + " #" + entry.id() : entry.name();
            var lines = textRenderer.wrapLines(Text.literal(label), textWidth);
            if (lines.size() <= 1) {
                labels.add(new RowLabel(textRenderer.trimToWidth(label, textWidth), List.of()));
            } else {
                labels.add(new RowLabel(null, List.of(lines.get(0), lines.get(1))));
            }
        }
        filtered = indices;
        rowLabels = labels;
        scroll = Math.clamp(scroll, 0, maxScroll());
        targetScroll = Math.clamp(targetScroll, 0, maxScroll());
    }

    private void drawList(DrawContext context) {
        int bottom = height - 102;
        if (bottom <= 113) return;
        int divider = split();
        int offset = (int) scroll;
        int first = Math.max(0, (offset + 1) / 32);
        int end = Math.min(filtered.size(), (bottom - 113 + offset) / 32 + 1);
        int selected = enabled() ? selection().get().intValue() : -1;
        context.enableScissor(10, 113, divider - 10, bottom);
        try {
            for (int row = first; row < end; row++) {
                int y = 113 + row * 32 - offset;
                boolean active = filtered.get(row) == selected;
                net.aero.visuals.render.UiRenderer.roundedRect(context, 12, y, divider - 30, 29, 6,
                        active ? Theme.elevated() : Theme.surface());
                if (active) net.aero.visuals.render.UiRenderer.rect(context, 12, y + 6, 2, 17, Theme.primary());
                RowLabel label = rowLabels.get(row);
                if (label.singleLine() != null) {
                    context.drawText(textRenderer, label.singleLine(), 21, y + 10, Theme.foreground(), false);
                } else {
                    context.drawText(textRenderer, label.lines().get(0), 21, y + 4, Theme.foreground(), false);
                    context.drawText(textRenderer, label.lines().get(1), 21, y + 15, Theme.mutedFg(), false);
                }
            }
        } finally {
            context.disableScissor();
        }
        double range = maxScroll();
        if (range > 0) {
            double track = bottom - 113;
            double handle = Math.max(16, track * track / (filtered.size() * 32));
            net.aero.visuals.render.UiRenderer.roundedRect(context, divider - 15,
                    113 + (track - handle) * scroll / range, 3, handle, 1.5, Theme.primary());
        }
    }
    public boolean scrollEmbedded(double x, double y, double horizontal, double vertical) {
        double localX = (x - originX) / panelScale;
        double localY = (y - originY) / panelScale;
        if (localX < 12 || localX >= split() - 10 || localY < 113 || localY >= height - 102) return false;
        targetScroll = Math.clamp(targetScroll - vertical * 32, 0, maxScroll());
        return true;
    }
    private Click local(Click c){return new Click((c.x()-originX)/panelScale,(c.y()-originY)/panelScale,c.buttonInfo());}
    public boolean clickEmbedded(Click c,boolean twice){var local=local(c);if(local.button()==0&&local.x()>12&&local.x()<split()-18&&local.y()>=113&&local.y()<height-102){int row=(int)((local.y()-113+scroll)/32);if(row>=0&&row<filtered.size()){choose(filtered.get(row));return true;}}return visibility>.98&&super.mouseClicked(local,twice);}
    public boolean dragEmbedded(Click c,double dx,double dy){return visibility>.98&&mouseDragged(local(c),dx/panelScale,dy/panelScale);}
    public boolean releaseEmbedded(Click c){return super.mouseReleased(local(c));}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
