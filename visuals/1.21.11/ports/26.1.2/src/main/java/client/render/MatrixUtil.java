package client.render;

import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

public final class MatrixUtil {
   private MatrixUtil() {
   }

   public static Matrix4f toMatrix4f(Matrix3x2fStack matrixStack) {
      return matrixStack != null ? new Matrix4f().mul(matrixStack) : new Matrix4f();
   }

   public static Matrix4f getMatrix4fByFloatFloatFloatFloatMatrixStack(float value, float value2, float value3, float value4, Matrix3x2fStack matrixStack) {
      float f = 0.0F;
      return getMatrix4fByFloatFloatFloatFloatMatrixStackFloatFloat(value4, f, value, value3, matrixStack, value2, value);
   }

   public static Matrix4f getMatrix4fByFloatFloatFloatFloatMatrixStackFloat(float value, float value2, float value3, float value4, Matrix3x2fStack matrixStack, float value5) {
      float f = 0.0F;
      return getMatrix4fByFloatFloatFloatFloatMatrixStackFloatFloat(value3, f, value5, value4, matrixStack, value, value2);
   }

   public static Matrix4f getMatrix4fByFloatFloatFloatFloatMatrixStackFloatFloat(
      float value, float value2, float value3, float value4, Matrix3x2fStack matrixStack, float value5, float value6
   ) {
      if (matrixStack != null) {
         matrixStack.pushMatrix();
         matrixStack.translate(value5 + value2, value + value4);
         matrixStack.scale(value3, value6);
         matrixStack.translate(-value5, -value);
         return new Matrix4f().mul(matrixStack);
      }
      return new Matrix4f();
   }
}
