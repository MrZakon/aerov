package client.gui.screen;

import client.api.Theme;
import client.api.UiMetrics;
import client.audio.SoundEngine;
import client.concurrent.ConfigManager;
import client.concurrent.ModuleRegistry;
import client.data.ClientAccess;
import client.data.ConfigData;
import client.data.GuiState;
import client.data.Tween;
import client.enums.GuiTab;
import client.enums.SoundEvent;
import client.gui.widget.ButtonWidget;
import client.gui.widget.HeaderPainter;
import client.gui.widget.NavBar;
import client.gui.widget.QuickMenu;
import client.gui.widget.ScissorStack;
import client.gui.widget.SearchBar;
import client.gui.widget.SideBar;
import client.gui.widget.UiContext;
import client.gui.widget.WidgetState;
import client.module.Category;
import client.module.Module;
import client.util.InterfaceSizing;
import client.render.BlurEffect;
import client.render.ItemIconCache;
import client.render.MatrixUtil;
import client.render.ShapeShader;
import client.util.EasingPresets;
import client.util.Easings;
import client.util.ThemeState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class ClickGuiScreen extends Screen implements Theme, UiMetrics {
   public static volatile boolean isClientGuiOpen = false;
   private static float value235 = 1.0F;
   private static boolean flag;
   private SideBar sideBar;
   private final net.aero.visuals.gui.CosmeticPreviewScreen cosmeticPanel=new net.aero.visuals.gui.CosmeticPreviewScreen(this);
   private final net.aero.visuals.gui.FriendsPanelScreen friendsPanel=new net.aero.visuals.gui.FriendsPanelScreen();
   private final net.aero.visuals.gui.ConfigsPanelScreen configsPanel=new net.aero.visuals.gui.ConfigsPanelScreen();
   public boolean configsOpen() { return navBar != null && navBar.isCategory(Category.CONFIGS); }
   private boolean friendsOpen() { return navBar != null && navBar.isCategory(Category.FRIENDS); }
   @Override
   public void tick() {
      super.tick();
      if (cosmeticsOpen()) cosmeticPanel.tickEmbedded();
   }

   private boolean cosmeticsOpen() { return navBar != null && navBar.isCategory(Category.COSMETICS); }
   private NavBar navBar;
   private SearchBar searchBar;
   private float value236;
   private float value237;
   private float value238 = 1.0F;
   private boolean flag2;
   private boolean flag3;
   private boolean flag4;
   private float value239;
   private float value240;
   private boolean flag5;
   private boolean flag6;
   private double value241;
   private double value242;
   private static final float value243 = 3.0F;
   private float value244 = 224.0F;
   private final Map<String, Float> map = new HashMap<>();
   private Category category;
   private Category category2;
   private static final float value245 = 0.5F;
   private static final float value246 = 0.42F;
   private static final float value247 = 18.0F;
   private static final float value248 = 0.93F;
   private static final float value249 = 18.0F;
   private static final float value250 = 0.35F;
   private static final float value251 = 0.5F;
   private static final float value252 = 0.24F;
   private static final float value253 = 0.35F;
   private static final float value254 = 22.0F;
   private static final float value255 = 14.0F;
   private static final float value256 = 18.0F;
   private final Tween tween = new Tween(0.0F, 0.5F).getTweenByFunction(Easings::getFloatByFloat9);
   private final Tween tween2 = EasingPresets.getTweenByFloatFloat(0.0F, 0.32F);
   private final Tween tween3 = EasingPresets.getTweenByFloatFloat(1.0F, 0.32F);
   private final Tween tween4 = new Tween(ThemeState.check() ? 1.0F : 0.0F, 0.35F).getTweenByFunction(Easings::getFloatByFloat3);
   private final Tween tween5 = new Tween(0.0F, 0.5F).getTweenByFunction(Easings::getFloatByFloat6);
   private final net.aero.visuals.animation.SpringValue springAnim = new net.aero.visuals.animation.SpringValue(0.0, 180.0, 16.0);

   private long moduleVisibilityVersion;

   private void refreshModuleVisibility() {
      long version = net.aero.visuals.regulator.ServerRestrictions.visibilityVersion();
      if (this.moduleVisibilityVersion == version) return;
      this.moduleVisibilityVersion = version;
      if (WidgetState.getWidget() != this.searchBar) WidgetState.setWidget2(null);
      var content = this.navBar.getContentArea();
      float scroll = content.getValue249();
      boolean favorites = this.navBar.getGuiTab() == GuiTab.FAVORITES;
      content.addList(favorites ? ClientAccess.getList() : ClientAccess.getListByCategory(this.navBar.getCategory()));
      content.onBoolean(favorites);
      content.setFloat2(scroll);
      if (this.searchBar != null) this.searchBar.refreshModuleVisibility();
   }

   public ClickGuiScreen() {
      super(Component.empty());
   }

   private void update() {
      if (this.searchBar != null) {
         this.searchBar.update5();
      }
   }

   private void update2() {
      if (this.navBar != null) {
         GuiTab guitab = this.navBar.getGuiTab();
         if (guitab != GuiTab.MODULES && guitab != GuiTab.FAVORITES) {
            GuiState.addNavBar(this.navBar);
         } else {
            if (guitab == GuiTab.MODULES) {
               Category categoryx = this.navBar.getCategory();
               this.map.put(GuiState.getStringByCategory(categoryx), this.navBar.getContentArea().getValue249());
            }

            GuiState.addNavBar(this.navBar);
         }
      }
   }

   private void render(GuiGraphicsExtractor drawContext, float value) {
      if (WidgetState.getWidget() instanceof ButtonWidget buttonwidget) {
         float f = buttonwidget.getFloat4();
         if (!(f <= 0.001F)) {
            float f1 = (float)Math.pow(f, 0.4);
            float f2 = buttonwidget.getFloat5();
            float f3 = buttonwidget.getValue237() * f2;
            float f4 = buttonwidget.getValue238() * f2;
            float f5 = buttonwidget.getValue235() + buttonwidget.getValue237() * 0.5F - f3 * 0.5F;
            float f6 = buttonwidget.getValue236() + buttonwidget.getValue238() * 0.5F + buttonwidget.getFloat3() - f4 * 0.5F;
            float f12 = f1 * value;
            float f13 = buttonwidget.getFloat() * f2;
            float f11 = this.getFloat();
            float f10 = f13;
            float f9 = 0.0F;
            float f8 = 18.0F;
            float f7 = f12;
            BlurEffect.onFloatFloatFloatFloatFloatFloatFloatFloatFloat(f3, f6, f10, f7, f11, f8, f4, f5, f9);
         }
      }
   }

   private float getValue238(double value) {
      return (float)(value / this.value238);
   }

   private boolean check() {
      ConfigData configdata = GuiState.getConfigData();
      if (configdata == null) return false;
      GuiState.onConfigData(configdata);
      this.tween4.setFloat(ThemeState.check() ? 1.0F : 0.0F);
      this.flag3 = configdata.isFlag();
      this.value239 = configdata.getValue();
      this.value240 = configdata.getValue2();
      GuiState.onConfigDataSideBarTween(configdata, this.sideBar, this.tween2);
      GuiState.onMapConfigData(this.map, configdata);
      GuiState.onSearchBarConfigData(this.searchBar, configdata);
      Category category = Category.getCategoryByString(configdata.getText());
      this.sideBar.onCategory(category);
      this.navBar.onCategoryList(category, ClientAccess.getListByCategory(category));
      this.navBar.getContentArea().setFloat2(this.getFloatByCategory(category));
      this.category = category;
      return true;
   }

   private float getFloat() {
      return this.value238 * (float)this.minecraft.getWindow().getGuiScale();
   }

   private static float getFloatByFloat(float value) {
      float f = Math.clamp(value, 0.0F, 1.0F);
      float f1 = (float)Math.pow(f, 0.65);
      float f2 = (float)Math.sin(f1 * Math.PI);
      return f2 * f2;
   }

   private void update3() {
      this.onRunnable(this.navBar::update12);
   }

   private void onCategory(Category category) {
      if (this.navBar.getGuiTab() == GuiTab.MODULES) {
         this.navBar.getContentArea().setFloat(0.0F);
         this.map.put(GuiState.getStringByCategory(category), 0.0F);
      }
   }

   public static float getValue235() {
      return value235;
   }

   private void onCategory2(Category category) {
      this.sideBar.onCategory(category);
      if (this.navBar.isCategory(category)) {
         this.navBar.onCategoryList(category, ClientAccess.getListByCategory(category));
         GuiState.onNavBar(this.navBar);
      } else {
         this.onCategory3(category);
      }
   }

   private void update4() {
      this.onRunnable(this.navBar::update7);
   }

   private void update5() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(null);
      } else {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc != null) mc.gui.setScreen(null);
      }
   }

   private void update6() {
   }

   private void update7() {
      if (this.navBar.getGuiTab() == GuiTab.FAVORITES) {
         Category categoryx = this.category2 != null ? this.category2 : (this.category != null ? this.category : Category.HUD);
         this.onRunnable(() -> {
            this.sideBar.onCategory(categoryx);
            this.navBar.onCategoryListFloat2(categoryx, ClientAccess.getListByCategory(categoryx), this.getFloatByCategory(categoryx));
            this.category = categoryx;
            this.category2 = null;
         });
      } else {
         Category category1 = this.navBar.getGuiTab() == GuiTab.MODULES ? this.navBar.getCategory() : null;
         this.onRunnable(() -> {
            this.category2 = category1;
            this.navBar.onList(ClientAccess.getList());
         });
      }
   }

   private boolean isDoubleDouble(double value, double value2) {
      float f = this.value236 + this.value244 + 1.0F;
      float f1 = 1188.0F - this.value244 - 1.0F;
      return value >= f && value <= f + f1 && value2 >= this.value237 && value2 <= this.value237 + 48.0F;
   }

   private float getValue2382(double value) {
      return (float)(value / this.value238);
   }

   public static boolean isFlag() {
      return flag;
   }

   public static void setValue235(float value) {
      value235 = value;
   }

   private ConfigData getConfigData() {
      float f1 = this.value240;
      float f = this.value239;
      boolean flagx = this.flag3;
      Map mapx = this.map;
      SearchBar searchbar = this.searchBar;
      NavBar navbar = this.navBar;
      return GuiState.getConfigDataByFloatNavBarMapSearchBarFloatBoolean(f, navbar, mapx, searchbar, f1, flagx);
   }

   private float getFloatByCategory(Category category) {
      Float f = this.map.get(GuiState.getStringByCategory(category));
      return f != null ? f : 0.0F;
   }

   private void onCategoryModule(Category category2, Module module2) {
      this.sideBar.onCategory(category2);
      if (!this.navBar.isCategory(category2)) {
         this.update2();
         this.navBar.onCategoryList(category2, ClientAccess.getListByCategory(category2));
         GuiState.onNavBar(this.navBar);
      }

      this.navBar.onModule(module2);
      this.category = category2;
   }

   private void setBoolean(boolean flag) {
      this.flag3 = flag;
      this.tween2.setFloat2(flag ? 1.0F : 0.0F);
   }

   private float getFloat2() {
      if (GLFW.glfwGetMouseButton(this.minecraft.getWindow().handle(), 0) == 1) {
         this.tween3.setFloat(this.tween3.getValue3());
         return this.tween3.getValue3();
      } else {
         this.tween3.setFloat2(InterfaceSizing.getGuiScale());
         return this.tween3.getFloat();
      }
   }

   private void onFloatFloatDrawContextFloatFloatFloatFloat(float value, float value2, GuiGraphicsExtractor drawContext, float value3, float value4, float value5, float value6) {
      float f = 1.0F - this.navBar.getFloat3();
      if (!(f <= 0.001F)) {
         float f1 = this.value236 + value2 + 1.0F;
         float f2 = this.navBar.getFloat();
         float f3 = this.navBar.getFloat2();
         float f4 = f1 + value3 * 0.5F;
         float f5 = f2 + f3 * 0.5F;
         float f6 = value3 * value;
         float f7 = f3 * value;
         float f8 = value4 + (f4 - value4) * value - f6 * 0.5F;
         float f9 = value5 + (f5 - value5) * value - f7 * 0.5F + value6;
         float f13 = this.getFloat();
         float f12 = 0.0F;
         float f11 = 0.0F;
         float f10 = 14.0F;
         BlurEffect.onFloatFloatFloatFloatFloatFloatFloatFloatFloat(f6, f9, f12, f, f13, f10, f7, f8, f11);
      }
   }

   private void render(GuiGraphicsExtractor drawContext) {
      this.tween4.setFloat2(ThemeState.check() ? 1.0F : 0.0F);
      float f = this.tween4.getFloat() * this.tween5.getFloat();
      if (!(f <= 0.001F)) {
         float f2 = 0.35F * f;
         float f1 = 18.0F;
         BlurEffect.onFloatFloatFloat(f2, f, f1);
      }
   }

   private float getFloatByFloat2(float value) {
      return Math.round(value * this.value238) / this.value238;
   }

   private void onRunnable(Runnable runnable) {
      this.update2();
      runnable.run();
      SoundEngine.getInstance().onSoundEvent(SoundEvent.CATEGORY_SWITCH);
   }

   private void onFloatDrawContextFloatFloatFloatFloat(float value, GuiGraphicsExtractor drawContext, float value2, float value3, float value4, float value5) {
      float f = getFloatByFloat(value5);
      if (!(f <= 0.001F)) {
         float f1 = this.getFloat();
         float f2 = 22.0F / f1;
         float f3 = 1188.0F * value4 + f2 * 2.0F;
         float f4 = 700.0F * value4 + f2 * 2.0F;
         float f5 = value3 - f3 * 0.5F;
         float f6 = value - f4 * 0.5F + value2;
         float f9 = 12.0F * value4 + f2;
         float f8 = 0.0F;
         float f7 = 22.0F;
         BlurEffect.onFloatFloatFloatFloatFloatFloatFloatFloatFloat(f3, f6, f9, f, f1, f7, f4, f5, f8);
      }
   }

   private void render2(GuiGraphicsExtractor drawContext, float value) {
      if (this.searchBar != null) {
         float f = this.searchBar.getFloat2();
         if (!(f <= 0.001F)) {
            int i = Math.clamp((long)((int)(128.0F * f * value)), 0, 255);
            int j = (int)Math.ceil(this.width / this.value238);
            int k = (int)Math.ceil(this.height / this.value238);
            drawContext.fill(0, 0, j, k, i << 24);
         }
      }
   }

   private void update8() {
      this.onRunnable(this.navBar::update8);
   }

   private void onCategory3(Category category2) {
      this.onRunnable(() -> {
         this.navBar.onCategoryListFloat2(category2, ClientAccess.getListByCategory(category2), this.getFloatByCategory(category2));
         this.category = category2;
      });
   }

   private void update9() {
      float requested = Math.min(this.width / 1920.0F, this.height / 1080.0F) * this.getFloat2();
      float fit = Math.min(Math.max(1,this.width-24)/1188.0F,Math.max(1,this.height-24)/700.0F);
      this.value238 = Math.max(.01f,Math.min(requested,fit));
      value235 = this.value238;
      float f = this.width / this.value238;
      float f1 = this.height / this.value238;
      float f2 = Math.max(0.0F, (f - 1188.0F) / 2.0F);
      float f3 = Math.max(0.0F, (f1 - 700.0F) / 2.0F);
      this.value239 = Math.clamp(this.value239, -f2, f2);
      this.value240 = Math.clamp(this.value240, -f3, f3);
      flag = this.flag5;
      this.value236 = (f - 1188.0F) / 2.0F + this.value239;
      this.value237 = (f1 - 700.0F) / 2.0F + this.value240;
   }

   public void extractRenderState(GuiGraphicsExtractor drawContext, int count, int count2, float value) {
      isClientGuiOpen = true;
      net.minecraft.client.Minecraft mcInst = net.minecraft.client.Minecraft.getInstance();
      if (mcInst != null && mcInst.isWindowActive() && mcInst.getFramerateLimitTracker() != null) {
         mcInst.getFramerateLimitTracker().onInputReceived();
      }
      if (this.navBar == null || this.sideBar == null) {
         this.init();
         if (this.navBar == null || this.sideBar == null) {
            return;
         }
      }

      this.refreshModuleVisibility();
      UiContext.update();

      this.update9();
      float f = this.getValue2382(count);
      float f1 = this.getValue238(count2);
      float f2 = this.tween.getFloat();
      if (this.flag4 && f2 <= 0.05F) {
         this.update5();
         return;
      } else {
         ItemIconCache.setDrawContext(drawContext);
         float f3 = EasingPresets.getFloatByFloat3(f2);
         float f4 = this.tween2.getFloat();
         float f12 = 48.0F;
         float f11 = 224.0F;
         float f5 = EasingPresets.getFloatByFloatFloatFloat(f12, f4, f11);
         this.value244 = f5;
         float f6 = 1188.0F - f5 - 1.0F;
         drawContext.pose().pushMatrix();
         drawContext.pose().scale(this.value238, this.value238);
         Matrix4f matrix4f = MatrixUtil.toMatrix4f(drawContext.pose());
         float f7 = this.value236 + 594.0F;
         float f8 = this.value237 + 350.0F;
         float f9 = 0.93F + 0.06999999F * f2;
         float f10 = (1.0F - f2) * 18.0F;
         Matrix4f matrix4f1 = new Matrix4f(matrix4f).translate(f7, f8, 0.0F).scale(f9, f9, 1.0F).translate(-f7, -f8, 0.0F).translate(0.0F, f10, 0.0F);
         ScissorStack.onFloatFloatFloatFloat2(f7, f10, f9, f8);


         try {
            this.render(drawContext);
            float f23 = this.value236;
            float f24 = this.value237;
            int k = Theme.background();
            int l = Theme.border();
            float f18 = 1.0F;
            int j = l;
            int i = k;
            float f17 = 12.0F;
            float f16 = 700.0F;
            float f15 = 1188.0F;
            float f14 = f24;
            float f13 = f23;
            ShapeShader.onFloatFloatFloatMatrix4fFloatIntFloatFloatIntFloat(f18, f15, f3, matrix4f1, f14, i, f17, f13, j, f16);
            this.sideBar.setFloat2(f4);
            float f20 = this.value237;
            float f19 = this.value236;
            this.sideBar.onFloatFloat2(f20, f19);
            this.sideBar.onFloatFloatFloatMatrix4f(f3, f1, f, matrix4f1);
            this.navBar.onFloatFloatBoolean(f6, f4, this.flag3);
            f23 = this.value236 + f5 + 1.0F;
            float f22 = this.value237;
            float f21 = f23;
            this.navBar.onFloatFloat2(f22, f21);
            this.navBar.onFloatFloatFloatMatrix4f(f3, f1, f, matrix4f1);
            this.onFloatFloatDrawContextFloatFloatFloatFloat(f9, f5, drawContext, f6, f7, f8, f10);
            this.onFloatDrawContextFloatFloatFloatFloat(f8, drawContext, f10, f7, f9, f2);
            HeaderPainter.onMatrix4fFloat(matrix4f1, f3);
            this.renderEmbeddedPanel(drawContext, count, count2, value);
            if (WidgetState.isWidgetAsBoolean()) {
               drawContext.nextStratum();
               this.render2(drawContext, f3);
               this.render(drawContext, f3);
               WidgetState.onMatrix4fFloatFloatFloat(matrix4f1, f, f1, f3);
            }
         } finally {
            ScissorStack.update2();

            drawContext.pose().popMatrix();
            ItemIconCache.setDrawContext(null);
         }
      }
   }

   private record PanelBounds(int x, int y, int width, int height) {
      boolean contains(double mouseX, double mouseY) {
         return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
      }
   }

   private PanelBounds embeddedPanelBounds() {
      int left = (int)((value236 + value244 + 1) * value238);
      int top = (int)((value237 + 49) * value238);
      int right = (int)((value236 + 1188) * value238);
      int bottom = (int)((value237 + 700) * value238);
      return new PanelBounds(left, top, right - left, bottom - top);
   }

   private void renderEmbeddedPanel(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float delta) {
      Category active = navBar.getCategory();
      if (navBar.getGuiTab() != GuiTab.MODULES || active == null || !active.isEmbeddedPanel()) return;
      PanelBounds bounds = embeddedPanelBounds();
      if (bounds.width() <= 0 || bounds.height() <= 0) return;
      int x = bounds.x(), y = bounds.y(), width = bounds.width(), height = bounds.height();
      if (WidgetState.isWidgetAsBoolean() || !bounds.contains(mouseX, mouseY)) {
         mouseX = mouseY = -1_000;
      }
      drawContext.nextStratum();
      var matrices = drawContext.pose();
      matrices.pushMatrix();
      try {
         // The embedded screens lay out their content in Minecraft GUI coordinates.
         matrices.scale(1.0F / value238, 1.0F / value238);
         drawContext.enableScissor(x, y, x + width, y + height);
         try {
            float progress = navBar.getFloat3();
            switch (active) {
               case COSMETICS -> cosmeticPanel.renderEmbedded(drawContext, x, y, width, height, mouseX, mouseY, delta, true, progress);
               case FRIENDS -> friendsPanel.renderEmbedded(drawContext, x, y, width, height, mouseX, mouseY, delta, progress);
               case CONFIGS -> configsPanel.renderEmbedded(drawContext, x, y, width, height, mouseX, mouseY, delta, progress);
               default -> { }
            }
         } finally {
            drawContext.disableScissor();
         }
      } finally {
         matrices.popMatrix();
      }
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return true;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void removed() {
      WidgetState.setWidget2(null);
      super.removed();
      isClientGuiOpen = false;
   }

   public boolean keyPressed(int count, int count2, int count3) {
      if (count == 256 && WidgetState.getWidget() instanceof QuickMenu menu) {
         menu.update4();
         return true;
      }
      if (count == 256 || net.aero.visuals.gui.ClickGuiScreen.matchesMenuKey(count, count3)) {
         this.onClose();
         return true;
      }
      if (this.flag4) {
         this.update5();
         return true;
      } else if ((count3 & 2) != 0
         && count >= 49
         && count <= 57
         && this.searchBar != null
         && WidgetState.getWidget() == this.searchBar
         && this.searchBar.isInt(count - 49)) {
         return true;
      } else if (WidgetState.isIntIntInt2(count3, count, count2)) {
         return true;
      } else if (this.navBar != null && this.navBar.isIntIntInt2(count2, count3, count)) {
         return true;
      } else if (count == 70 && (count3 & 2) != 0) {
         this.update();
         return true;
      } else if (count == 258) {
         this.update();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void onClose() {
      isClientGuiOpen = false;
      this.flag4 = true;
      this.springAnim.setTarget(0.0);
      try {
         this.update2();
         ConfigManager configmanager = ClientAccess.getConfigManager();
         if (configmanager != null) {
            configmanager.setConfigData(this.getConfigData());
         }
         SoundEngine.getInstance().onSoundEvent(SoundEvent.GUI_CLOSE);
      } catch (Throwable ignored) {
      }
      this.update5();
   }

   protected void init() {
      isClientGuiOpen = true;
      super.init();
      net.minecraft.client.Minecraft mcInst = net.minecraft.client.Minecraft.getInstance();
      if (mcInst != null && mcInst.isWindowActive() && mcInst.getFramerateLimitTracker() != null) {
         mcInst.getFramerateLimitTracker().onInputReceived();
      }
      this.springAnim.snap(0.0);
      this.springAnim.setTarget(1.0);
      this.tween3.setFloat(InterfaceSizing.getGuiScale());
      this.update9();
      this.flag4 = false;
      this.tween.setValue5(0.5F);
      this.tween.getTweenByFunction(Easings::getFloatByFloat9);
      this.tween.setFloat(0.0F);
      this.tween.setFloat2(1.0F);
      this.tween5.setValue5(0.5F);
      this.tween5.getTweenByFunction(Easings::getFloatByFloat9);
      this.tween5.setFloat(0.0F);
      this.tween5.setFloat2(1.0F);
      if (this.flag2) {
         this.navBar.update9();
      } else {
         this.sideBar = new SideBar();
         this.navBar = new NavBar();
         this.searchBar = new SearchBar();
         this.sideBar.setConsumer(this::onCategory3);
         this.sideBar.setConsumer2(this::onCategory);
         this.sideBar.setRunnable(this::update8);
         this.sideBar.setRunnable2(this::update3);
         this.sideBar.setRunnable3(this::update4);
         this.sideBar.setRunnable4(this::update);
         this.sideBar.onRunnable(this::update6);
         this.searchBar.setConsumer(this::onCategory2);
         this.searchBar.setBiConsumer(this::onCategoryModule);
         this.navBar.onConsumer(this::setBoolean);
         this.navBar.onRunnable(this::update7);
         ConfigManager configmanager = ClientAccess.getConfigManager();
         if (configmanager != null) {
            configmanager.setSupplier(this::getConfigData);
            configmanager.setRunnable(this::check);
         }

         if (!this.check()) {
            Category categoryx = this.sideBar.getCategory();
            this.navBar.onCategoryList(categoryx, ClientAccess.getListByCategory(categoryx));
            this.category = categoryx;
         }

         this.flag2 = true;
      }
   }

   public boolean keyReleased(int count, int count2, int count3) {
      if (this.flag4) {
         return true;
      } else if (WidgetState.isIntIntInt(count, count3, count2)) {
         return true;
      } else {
         return this.navBar != null && this.navBar.isIntIntInt(count, count3, count2);
      }
   }

   public boolean mouseReleased(double value, double value2, int count) {
      if (count == 0) {
         this.flag6 = false;
         if (this.flag5) {
            this.flag5 = false;
            return true;
         }
      }

      double d0 = this.getValue2382(value);
      double d1 = this.getValue238(value2);
      if (WidgetState.isDoubleDoubleInt(d0, d1, count)) {
         return true;
      } else {
         return this.navBar != null && this.navBar.isDoubleDoubleInt(d0, d1, count);
      }
   }

   public boolean charTyped(char symbol, int count) {
      if (WidgetState.isCharInt(symbol, count)) {
         return true;
      } else {
         return this.navBar != null && this.navBar.isIntChar(count, symbol);
      }
   }

   public boolean mouseClicked(double value, double value2, int count) {
      if (this.flag4) {
         return true;
      } else {
         double d0 = this.getValue2382(value);
         double d1 = this.getValue238(value2);
         if (WidgetState.isIntDoubleDouble(count, d0, d1)) {
            return true;
         } else if (this.sideBar != null && this.sideBar.isIntDoubleDouble(count, d0, d1)) {
            return true;
         } else if (this.isDoubleDouble(d0, d1)) {
            if (this.navBar != null && this.navBar.isDoubleIntDouble(d1, count, d0)) {
               return true;
            } else {
               if (count == 0) {
                  this.flag6 = true;
                  this.value241 = d0;
                  this.value242 = d1;
               }

               return true;
            }
         } else {
            return this.navBar != null && this.navBar.isIntDoubleDouble(count, d0, d1);
         }
      }
   }

   public boolean mouseScrolled(double value, double value2, double value3, double value4) {
      double d0 = this.getValue2382(value);
      double d1 = this.getValue238(value2);
      if (WidgetState.isDoubleDoubleDouble(d0, d1, value4)) return true;
      if (embeddedPanelBounds().contains(value, value2)) {
         if (configsOpen()) return configsPanel.scrollEmbedded(value, value2, value3, value4);
         if (friendsOpen()) return friendsPanel.scrollEmbedded(value, value2, value3, value4);
         if (cosmeticsOpen()) return cosmeticPanel.scrollEmbedded(value, value2, value3, value4);
      }
      return this.navBar != null && this.navBar.isDoubleDoubleDouble(value4, d0, d1);
   }

   public boolean mouseDragged(double value, double value2, int count, double value3, double value4) {
      double d0 = this.getValue2382(value);
      double d1 = this.getValue238(value2);
      double d2 = value3 / this.value238;
      double d3 = value4 / this.value238;
      if (this.flag6 && count == 0) {
         double d4 = d0 - this.value241;
         double d5 = d1 - this.value242;
         if (d4 * d4 + d5 * d5 >= 9.0) {
            this.flag6 = false;
            this.flag5 = true;
         }
      }

      if (this.flag5 && count == 0) {
         this.value239 += (float)d2;
         this.value240 += (float)d3;
         return true;
      } else if (WidgetState.isDoubleIntDoubleDoubleDouble(d1, count, d2, d3, d0)) {
         return true;
      } else {
         return this.navBar != null && this.navBar.isDoubleDoubleIntDoubleDouble(d1, d0, count, d2, d3);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (net.aero.visuals.gui.ClickGuiScreen.matchesMenuKey(click.button(), click.modifiers())) { this.onClose(); return true; }
      if (!WidgetState.isWidgetAsBoolean() && embeddedPanelBounds().contains(click.x(), click.y())) {
         if (configsOpen()) return configsPanel.clickEmbedded(click, doubled);
         if (friendsOpen()) return friendsPanel.clickEmbedded(click, doubled);
         if (cosmeticsOpen()) return cosmeticPanel.clickEmbedded(click, doubled);
      }
      return this.mouseClicked(click.x(), click.y(), click.button());
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (!WidgetState.isWidgetAsBoolean()) {
         if (configsOpen() && configsPanel.releaseEmbedded(click)) return true;
         if (friendsOpen() && friendsPanel.releaseEmbedded(click)) return true;
         if (cosmeticsOpen() && cosmeticPanel.releaseEmbedded(click)) return true;
      }
      return this.mouseReleased(click.x(), click.y(), click.button());
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
      if (!WidgetState.isWidgetAsBoolean() && embeddedPanelBounds().contains(click.x(), click.y())) {
         if (configsOpen()) return configsPanel.dragEmbedded(click, deltaX, deltaY);
         if (friendsOpen()) return friendsPanel.dragEmbedded(click, deltaX, deltaY);
         if (cosmeticsOpen()) return cosmeticPanel.dragEmbedded(click, deltaX, deltaY);
      }
      return this.mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (!WidgetState.isWidgetAsBoolean() && input.key()!=GLFW.GLFW_KEY_ESCAPE) {
         if (configsOpen() && configsPanel.keyPressed(input)) return true;
         if (friendsOpen() && friendsPanel.keyPressed(input)) return true;
         if (cosmeticsOpen() && cosmeticPanel.keyPressed(input)) return true;
      }
      return this.keyPressed(input.key(), input.scancode(), input.modifiers());
   }

   @Override
   public boolean keyReleased(KeyEvent input) {
      return this.keyReleased(input.key(), input.scancode(), input.modifiers());
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      if (!WidgetState.isWidgetAsBoolean()) {
         if (configsOpen()) return configsPanel.charTyped(input);
         if (friendsOpen()) return friendsPanel.charTyped(input);
         if (cosmeticsOpen()) return cosmeticPanel.charTyped(input);
      }
      boolean handled = false;
      for (char part : Character.toChars(input.codepoint())) {
         handled |= this.charTyped(part, 0);
      }
      return handled;
   }
}

