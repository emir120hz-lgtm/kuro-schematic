package kuro.ui;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;

/**
 * NanoVG kendi OpenGL durumunu degistirir. Minecraft ise bazi durumlari
 * (GlStateManager) onbellekte tutar; bu yuzden NanoVG'den once okunan durum
 * birebir geri yazilir ve Minecraft hicbir sey fark etmez.
 */
final class GlState {
   private static int program;
   private static int vao;
   private static int arrayBuffer;
   private static int uniformBuffer;
   private static int uniformBuffer0;
   private static int activeTexture;
   private static int texture0;
   private static int unpackAlignment;
   private static int unpackRowLength;
   private static int unpackSkipPixels;
   private static int unpackSkipRows;
   private static boolean blend;
   private static boolean cull;
   private static boolean depthTest;
   private static boolean scissorTest;
   private static boolean stencilTest;
   private static int blendSrcRgb;
   private static int blendDstRgb;
   private static int blendSrcA;
   private static int blendDstA;
   private static int blendEqRgb;
   private static int blendEqA;
   private static int cullMode;
   private static int frontFace;
   private static int stencilMask;
   private static int stencilFunc;
   private static int stencilRef;
   private static int stencilValueMask;
   private static int stencilFail;
   private static int stencilZFail;
   private static int stencilZPass;
   private static final boolean[] colorMask = new boolean[4];

   private GlState() {
   }

   static void save() {
      program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
      vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
      arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
      uniformBuffer = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
      uniformBuffer0 = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, 0);

      activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

      unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
      unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
      unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
      unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);

      blend = GL11.glIsEnabled(GL11.GL_BLEND);
      cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
      depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);

      blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
      blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
      blendSrcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
      blendDstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
      blendEqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
      blendEqA = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

      cullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
      frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);

      stencilMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
      stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
      stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
      stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
      stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
      stencilZFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
      stencilZPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);

      try (MemoryStack stack = MemoryStack.stackPush()) {
         ByteBuffer b = stack.malloc(4);
         GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, b);
         for (int i = 0; i < 4; i++) {
            colorMask[i] = b.get(i) != 0;
         }
      }
   }

   static void restore() {
      GL20.glUseProgram(program);
      GL30.glBindVertexArray(vao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
      GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, uniformBuffer0);
      GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uniformBuffer);

      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
      GL13.glActiveTexture(activeTexture);

      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackRowLength);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackSkipRows);

      set(GL11.GL_BLEND, blend);
      set(GL11.GL_CULL_FACE, cull);
      set(GL11.GL_DEPTH_TEST, depthTest);
      set(GL11.GL_SCISSOR_TEST, scissorTest);
      set(GL11.GL_STENCIL_TEST, stencilTest);

      GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcA, blendDstA);
      GL20.glBlendEquationSeparate(blendEqRgb, blendEqA);
      GL11.glCullFace(cullMode);
      GL11.glFrontFace(frontFace);

      GL11.glStencilMask(stencilMask);
      GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
      GL11.glStencilOp(stencilFail, stencilZFail, stencilZPass);
      GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
   }

   private static void set(int cap, boolean enabled) {
      if (enabled) {
         GL11.glEnable(cap);
      } else {
         GL11.glDisable(cap);
      }
   }
}
