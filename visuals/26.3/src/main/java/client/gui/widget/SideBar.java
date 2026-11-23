package client.gui.widget;

import client.api.Theme;
import client.module.Category;
import client.module.CategoryType;
import client.render.ShapeShader;
import client.util.EasingPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.joml.Matrix4f;

public class SideBar extends Widget {
   private float openness;
   private final IconSlider logo = new IconSlider(CategoryType.LOGO, "AeroVisuals");
   private final AvatarSlider heading = new AvatarSlider("Categories");
   private final List<CategoryTab> categories = new ArrayList<>();
   private final IconTab settings = new IconTab(CategoryType.SETTINGS, "Настройки", this::openSettings);
   private final IconTab search = new IconTab(CategoryType.SEARCH, "Поиск", () -> { if (this.searchAction != null) this.searchAction.run(); });
   private final ServerLabel serverLabel = new ServerLabel();
   private final QuickMenu quickMenu = new QuickMenu();
   private Consumer<Category> consumer;
   private Consumer<Category> consumer2;
   private Runnable searchAction;
   private Category selected = Category.HUD;

   public SideBar() {
      this.value237 = 224;
      this.value238 = 700;
      for (Category category : Category.values()) categories.add(new CategoryTab(category));
      onCategory(Category.HUD);
   }

   public void setRunnable(Runnable action) {}
   public void setRunnable2(Runnable action) {}
   public void setRunnable3(Runnable action) {}
   public void setRunnable4(Runnable action) { searchAction = action; }
   public void setConsumer(Consumer<Category> action) { consumer = action; }
   public void setConsumer2(Consumer<Category> action) { consumer2 = action; }
   public void onRunnable(Runnable action) { quickMenu.setRunnable(action); }
   public void update4() { onCategory(Category.HUD); }
   public void update7() { onCategory(Category.HUD); }
   public void update12() { onCategory(Category.HUD); }

   public void setFloat(float value) {
      openness = Math.clamp(value, 0, 1);
      this.value237 = EasingPresets.getFloatByFloatFloatFloat(48, openness, 224);
   }

   public void setFloat2(float value) { setFloat(value); }

   public void onCategory(Category category) {
      selected = category == null ? Category.HUD : category;
      for (CategoryTab tab : categories) tab.setFlag4(tab.getCategory() == selected);
   }

   public Category getCategory() { return selected; }

   @Override
   public boolean isIntDoubleDouble(int button, double x, double y) {
      for (CategoryTab tab : categories) {
         if (!tab.isIntDoubleDouble(button, x, y)) continue;
         boolean alreadySelected = selected == tab.getCategory();
         onCategory(tab.getCategory());
         if (alreadySelected && consumer2 != null) consumer2.accept(selected);
         else if (consumer != null) consumer.accept(selected);
         return true;
      }
      return settings.isIntDoubleDouble(button, x, y) || search.isIntDoubleDouble(button, x, y);
   }

   @Override
   public void onFloatFloatFloatMatrix4f(float alpha, float mouseY, float mouseX, Matrix4f matrix) {
      ShapeShader.onFloatFloatMatrix4fFloatFloatIntFloatFloat(this.value238, this.value235, matrix,
              this.value237, 12, Theme.surface(), this.value236, alpha);
      ShapeShader.onFloatFloatFloatFloatFloatMatrix4fInt(alpha, 1, this.value235,
              this.value236 + 48, this.value237 + 1, matrix, Theme.border());
      ShapeShader.onFloatFloatFloatFloatFloatMatrix4fInt(alpha, this.value238,
              this.value235 + this.value237, this.value236, 1, matrix, Theme.border());
      layout(logo, this.value235, this.value236, this.value237, alpha, mouseY, mouseX, matrix);
      float x = this.value235 + 8;
      float rowWidth = EasingPresets.getFloatByFloatFloatFloat(32, openness, 208);
      layout(heading, x, this.value236 + 58, rowWidth, alpha, mouseY, mouseX, matrix);
      float y = this.value236 + 94;
      for (CategoryTab tab : categories) {
         if (tab.getCategory() == Category.COSMETICS) {
            ShapeShader.onFloatFloatFloatFloatFloatMatrix4fInt(alpha, 1, x, y - 1, rowWidth, matrix, Theme.border());
            y += 12;
         }
         layout(tab, x, y, rowWidth, alpha, mouseY, mouseX, matrix);
         y += 36;
      }
      float bottom = this.value236 + this.value238 - 50;
      layout(settings, x, bottom - 86, rowWidth, alpha, mouseY, mouseX, matrix);
      layout(search, x, bottom - 50, rowWidth, alpha, mouseY, mouseX, matrix);
      layout(serverLabel, x, bottom, rowWidth, alpha, mouseY, mouseX, matrix);
   }

   private void layout(SliderWidget widget, float x, float y, float width,
                       float alpha, float mouseY, float mouseX, Matrix4f matrix) {
      widget.setValue239(openness);
      widget.setValue237(width);
      widget.onFloatFloat2(y, x);
      widget.onFloatFloatFloatMatrix4f(alpha, mouseY, mouseX, matrix);
   }

   private void openSettings() {
      quickMenu.openAt(settings, this);
   }
}
