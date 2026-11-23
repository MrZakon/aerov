package client.gui.widget;

import client.data.CharTiming;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;

public final class TextPainter {
   private static final Map<EditBox, TextPainter> map = new WeakHashMap<>();
   private final Map<Integer, Long> map2 = new HashMap<>();
   private final Map<Integer, CharTiming> map3 = new HashMap<>();
   private String text = "";

   private TextPainter() {
   }

   private static TextPainter getTextPainterByTextFieldWidget(EditBox textFieldWidget) {
      return new TextPainter();
   }

   public void setText(String text2) {
      this.text = text2;
   }

   public static TextPainter getTextPainterByTextFieldWidget2(EditBox textFieldWidget) {
      synchronized (map) {
         return map.computeIfAbsent(textFieldWidget, TextPainter::getTextPainterByTextFieldWidget);
      }
   }

   public void onTextRendererBooleanDrawLayerDrawContextIntMatrixStackFloatBiFunctionInt(
      Font textRenderer, boolean flag, DrawLayer drawLayer, GuiGraphicsExtractor drawContext, int count, PoseStack matrixStack, float value, BiFunction biFunction, int count2
   ) {
      long i = System.currentTimeMillis();
      byte b0 = 9;
      Iterator iterator = this.map3.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry entry = (Entry)iterator.next();
         CharTiming chartiming = (CharTiming)entry.getValue();
         float f = (float)(i - chartiming.getTimestamp()) / value;
         if (f >= 1.0F) {
            iterator.remove();
         } else {
            float f1 = Math.max(1.0F - drawLayer.getFloatByFloat(f), 0.003921569F);
            if (f1 <= 0.01F) {
               iterator.remove();
            } else {
               FormattedCharSequence orderedtext = (FormattedCharSequence)biFunction.apply(String.valueOf(chartiming.getCh()), (Integer)entry.getKey());
               matrixStack.pushPose();
               if (drawLayer.isFlag()) {
                  int l = chartiming.getX();
                  float f2 = 1.0F;
                  int j = l;
                  drawLayer.onIntMatrixStackIntFloatInt(b0, matrixStack, count, f2, j);
                  drawContext.text(textRenderer, orderedtext, chartiming.getX(), count, LayerStack.getIntByFloatInt(f1, count2), flag);
               } else {
                  int k = chartiming.getX();
                  drawLayer.onIntMatrixStackIntFloatInt(b0, matrixStack, count, f1, k);
                  drawContext.text(textRenderer, orderedtext, chartiming.getX(), count, count2, flag);
               }

               matrixStack.popPose();
            }
         }
      }
   }

   public int getIntByIntBooleanIntBiFunctionMatrixStackIntTextRendererIntStringFloatIntDrawLayerDrawContext(
      int count,
      boolean flag,
      int count2,
      BiFunction biFunction,
      PoseStack matrixStack,
      int count3,
      Font textRenderer,
      int count4,
      String text2,
      float value,
      int count5,
      DrawLayer drawLayer,
      GuiGraphicsExtractor drawContext
   ) {
      int i = count3;
      int j = count3;
      long k = System.currentTimeMillis();
      byte b0 = 9;

      for (int l = 0; l < text2.length(); l++) {
         FormattedCharSequence orderedtext = (FormattedCharSequence)biFunction.apply(String.valueOf(text2.charAt(l)), count + l);
         long i1 = this.map2.getOrDefault(l, k);
         float f = Math.min((float)(k - i1) / value, 1.0F);
         if (!(f < 1.0E-4F)) {
            int j1 = count4;
            float f1 = 1.0F;
            if (drawLayer.isFlag()) {
               float f2 = drawLayer.getFloatByFloat(f);
               j1 = LayerStack.getIntByFloatInt(f2, count4);
            } else {
               f1 = drawLayer.getFloatByFloatLongLong(value, i1, k);
            }

            matrixStack.pushPose();
            drawLayer.onIntFloatIntMatrixStackInt(i, f1, b0, matrixStack, count5);
            drawContext.text(textRenderer, orderedtext, i, count5, j1, flag);
            matrixStack.popPose();
            int k1 = textRenderer.width(orderedtext);
            if (l < count2) {
               j += k1;
            }

            i += k1;
         }
      }

      return j;
   }

   public void onTextRendererStringInt(Font textRenderer, String text2, int count) {
      long i = System.currentTimeMillis();
      int j = Math.max(text2.length(), this.text.length());

      for (int k = 0; k < j; k++) {
         boolean flag = k < text2.length();
         boolean flag1 = k < this.text.length();
         char c0 = flag ? text2.charAt(k) : 0;
         char c1 = flag1 ? this.text.charAt(k) : 0;
         if (flag && (!flag1 || c0 != c1)) {
            this.map2.put(k, i);
         }

         if (flag1 && (!flag || c0 != c1)) {
            int l = textRenderer.width(this.text.substring(0, k));
            this.map3.put(k, new CharTiming(c1, i, count + l));
         }
      }
   }
}
