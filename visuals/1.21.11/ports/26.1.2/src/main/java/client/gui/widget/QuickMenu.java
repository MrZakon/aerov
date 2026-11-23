package client.gui.widget;

import client.api.ColorSupplier;
import client.api.Theme;
import client.concurrent.Translations;
import client.data.ThemeConfig;
import client.enums.Language;
import client.render.ShapeShader;
import client.util.EasingPresets;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

public class QuickMenu extends ButtonWidget {
   private final List<TabbedPanel> entries = new ArrayList<>();
   private Runnable runnable;
   private final java.util.List<SliderRow> sizeRows=new java.util.ArrayList<>();
   private final java.util.List<String> sizeLabels=java.util.List.of("Размер окна","Размер текста");

   public QuickMenu() {
      this.value237 = 260;
      entries.add(new TabbedPanel(()->net.aero.visuals.localization.LocalText.get("Сменить тему"), Theme::foreground, this::changeTheme));
      entries.add(new TabbedPanel(()->net.aero.visuals.localization.LocalText.get("Закрыть"), Theme::danger, this::update4));
      entries.add(new TabbedPanel(()->"Language / Язык / Мова: "+switch(net.aero.visuals.localization.AeroI18n.languageMode){case 0->"English";case 2->"Українська";default->"Русский";},Theme::foreground,()->{var l=net.aero.visuals.ClientRuntime.get().settings().language();var v=l.get().ordinal();l.set(net.aero.visuals.config.ClientSettings.InterfaceLanguage.values()[(v+1)%3]);}));
      var config=net.aero.visuals.ClientRuntime.get().settings();
      sizeRows.add(sizeSlider(config.clickGuiScale()));sizeRows.add(sizeSlider(config.clickGuiTextScale()));
      this.value237=300;this.value238 = 12 + entries.size() * 32 + Math.max(0, entries.size() - 1) * 9+sizeRows.size()*48;
   }

   private SliderRow sizeSlider(net.aero.visuals.module.setting.DoubleSetting setting){var slider=new client.setting.SliderSetting(setting.name(),"",setting.get()*100,setting.minimum()*100,setting.maximum()*100,setting.step()*100,"%",0);slider.setOnChange(()->setting.set(slider.getValue()/100));setting.onChanged(value->slider.setDouble(value*100));return new SliderRow(slider);}
   @Override
   protected void onFloatFloatFloatMatrix4f3(float mouseY, float alpha, float mouseX, Matrix4f source) {
      float y = this.value236 + EasingPresets.getFloatByFloat(alpha);
      Matrix4f matrix = EasingPresets.getMatrix4fByMatrix4fFloatFloatFloat(source, alpha,
              this.value235 + this.value237 / 2, y + this.value238 / 2);
      ShapeShader.onFloatFloatFloatMatrix4fFloatIntFloatFloatIntFloat(1, this.value237, alpha, matrix,
              y, Theme.background(), 8, this.value235, Theme.border(), this.value238);
      float nextY = y + 4;
      for (int index = 0; index < entries.size(); index++) {
         var entry = entries.get(index);
         entry.setValue237(this.value237 - 8);
         entry.onFloatFloat2(nextY, this.value235 + 4);
         entry.onFloatFloatFloatMatrix4f(alpha, mouseY, mouseX, matrix);
         nextY += 32;
         if (index < entries.size() - 1) {
            nextY += 4;
            ShapeShader.onFloatFloatFloatFloatFloatMatrix4fInt(alpha, 1, this.value235 + 4,
                    nextY, this.value237 - 8, matrix, Theme.border());
            nextY += 5;
         }
      }
      for(int i=0;i<sizeRows.size();i++){var row=sizeRows.get(i);row.setFloat(this.value237-24);row.onFloatFloat2(nextY+6,this.value235+12);row.onFloatFloatFloatMatrix4f(alpha,mouseY,mouseX,matrix);nextY+=48;}
   }

   @Override
   protected boolean isIntDoubleDouble2(int button, double x, double y) {
      for(var row:sizeRows)if(row.isIntDoubleDouble(button,x,y))return true;
      for (var entry : entries) if (entry.isIntDoubleDouble(button, x, y)) return true;
      return true;
   }

   @Override public boolean isDoubleDoubleIntDoubleDouble(double y,double x,int button,double dx,double dy){boolean handled=false;for(var row:sizeRows)handled|=row.isDoubleDoubleIntDoubleDouble(y,x,button,dx,dy);return handled;}
   @Override public boolean isDoubleDoubleInt(double x,double y,int button){boolean handled=false;for(var row:sizeRows)handled|=row.isDoubleDoubleInt(x,y,button);return handled;}
   @Override protected void update7(){for(var row:sizeRows)row.isDoubleDoubleInt(0,0,0);}
   public void setRunnable(Runnable action) { runnable = action; }

   @Override
   public void setColorSupplier2(ColorSupplier anchor) {
      super.setColorSupplier2(() -> {
         float[] position = anchor.get();
         return new float[]{position[0], position[1] - this.value238 / 2};
      });
   }

   private void changeTheme() {
      ThemeConfig.update();
      update4();
   }


}
