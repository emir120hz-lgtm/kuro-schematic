package kuro.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import kuro.Kuro;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

/** NanoVG icin ince sarmalayici: baslatma, font yukleme, cizim yardimcilari. */
public final class Nvg {
   public static long ctx;
   public static int fontReg = -1;
   public static int fontBold = -1;

   private static boolean tried;
   // yazi tipi verisi NanoVG kullandigi surece bellekte kalmali
   private static ByteBuffer regData;
   private static ByteBuffer boldData;

   private static final NVGColor C1 = NVGColor.create();
   private static final NVGColor C2 = NVGColor.create();
   private static final NVGPaint P = NVGPaint.create();
   private static final float[] BOUNDS = new float[4];
   @SuppressWarnings("unchecked")
   private static final HashMap<String, Float>[] WIDTHS = new HashMap[128];

   private Nvg() {
   }

   /** NanoVG baglamini (ve fontlari) ilk kullanimda olusturur. Basarisizsa false. */
   public static boolean ensure() {
      if (ctx != 0L) {
         return true;
      }

      if (tried) {
         return false;
      }

      tried = true;
      GlState.save();
      try {
         ctx = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS);
         if (ctx == 0L) {
            Kuro.LOGGER.error("NanoVG baglami olusturulamadi (OpenGL 3.2+ gerekli).");
            return false;
         }

         regData = loadResource("/assets/kuro/fonts/Inter-Regular.ttf");
         boldData = loadResource("/assets/kuro/fonts/Inter-SemiBold.ttf");
         fontReg = nvgCreateFontMem(ctx, "inter", regData, false);
         fontBold = nvgCreateFontMem(ctx, "inter-semibold", boldData, false);
         if (fontReg < 0 || fontBold < 0) {
            Kuro.LOGGER.error("NanoVG yazi tipleri yuklenemedi.");
            ctx = 0L;
            return false;
         }

         Kuro.LOGGER.info("NanoVG hazir.");
         return true;
      } catch (Throwable t) {
         Kuro.LOGGER.error("NanoVG baslatilamadi", t);
         ctx = 0L;
         return false;
      } finally {
         GlState.restore();
      }
   }

   private static ByteBuffer loadResource(String path) throws IOException {
      try (InputStream in = Nvg.class.getResourceAsStream(path)) {
         if (in == null) {
            throw new IOException("Kaynak bulunamadi: " + path);
         }

         byte[] bytes = in.readAllBytes();
         ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
         buf.put(bytes).flip();
         return buf;
      }
   }

   /** Cizim karesini baslatir; Minecraft'in GL durumunu saklar. */
   public static void begin(float w, float h, float ratio) {
      GlState.save();
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      nvgBeginFrame(ctx, w, h, ratio);
   }

   /** Cizimi bitirir ve Minecraft'in GL durumunu geri yazar. */
   public static void end() {
      nvgEndFrame(ctx);
      GlState.restore();
   }

   // ---------------------------------------------------------------- renk

   private static NVGColor col(NVGColor c, int argb) {
      return nvgRGBA((byte)(argb >> 16), (byte)(argb >> 8), (byte)argb, (byte)(argb >>> 24), c);
   }

   public static void fill(int argb) {
      nvgFillColor(ctx, col(C1, argb));
   }

   // ---------------------------------------------------------------- sekiller

   public static void rect(float x, float y, float w, float h, float r, int argb) {
      nvgBeginPath(ctx);
      nvgRoundedRect(ctx, x, y, w, h, r);
      fill(argb);
      nvgFill(ctx);
   }

   /** Koseleri ayri ayri yuvarlatilmis dikdortgen (ust-sol, ust-sag, alt-sag, alt-sol). */
   public static void rectVar(float x, float y, float w, float h, float tl, float tr, float br, float bl, int argb) {
      nvgBeginPath(ctx);
      nvgRoundedRectVarying(ctx, x, y, w, h, tl, tr, br, bl);
      fill(argb);
      nvgFill(ctx);
   }

   /** Yatay gradyan dolgu. */
   public static void rectGradH(float x, float y, float w, float h, float r, int left, int right) {
      nvgBeginPath(ctx);
      nvgRoundedRect(ctx, x, y, w, h, r);
      nvgFillPaint(ctx, nvgLinearGradient(ctx, x, y, x + w, y, col(C1, left), col(C2, right), P));
      nvgFill(ctx);
   }

   /** Dikey gradyan dolgu. */
   public static void rectGradV(float x, float y, float w, float h, float r, int top, int bottom) {
      nvgBeginPath(ctx);
      nvgRoundedRect(ctx, x, y, w, h, r);
      nvgFillPaint(ctx, nvgLinearGradient(ctx, x, y, x, y + h, col(C1, top), col(C2, bottom), P));
      nvgFill(ctx);
   }

   public static void border(float x, float y, float w, float h, float r, float width, int argb) {
      nvgBeginPath(ctx);
      nvgRoundedRect(ctx, x + width * 0.5F, y + width * 0.5F, w - width, h - width, Math.max(0.0F, r - width * 0.5F));
      nvgStrokeWidth(ctx, width);
      nvgStrokeColor(ctx, col(C1, argb));
      nvgStroke(ctx);
   }

   public static void circle(float cx, float cy, float r, int argb) {
      nvgBeginPath(ctx);
      nvgCircle(ctx, cx, cy, r);
      fill(argb);
      nvgFill(ctx);
   }

   public static void circleStroke(float cx, float cy, float r, float width, int argb) {
      nvgBeginPath(ctx);
      nvgCircle(ctx, cx, cy, r);
      nvgStrokeWidth(ctx, width);
      nvgStrokeColor(ctx, col(C1, argb));
      nvgStroke(ctx);
   }

   public static void line(float x1, float y1, float x2, float y2, float width, int argb) {
      nvgBeginPath(ctx);
      nvgMoveTo(ctx, x1, y1);
      nvgLineTo(ctx, x2, y2);
      nvgStrokeWidth(ctx, width);
      nvgStrokeColor(ctx, col(C1, argb));
      nvgStroke(ctx);
   }

   /** Yumusak golge / parlama (box gradient). */
   public static void glow(float x, float y, float w, float h, float r, float blur, int argb) {
      nvgBeginPath(ctx);
      nvgRect(ctx, x - blur, y - blur, w + blur * 2.0F, h + blur * 2.0F);
      nvgFillPaint(ctx, nvgBoxGradient(ctx, x, y, w, h, r, blur, col(C1, argb), col(C2, argb & 0xFFFFFF), P));
      nvgFill(ctx);
   }

   public static void stroke(float width, int argb) {
      nvgStrokeWidth(ctx, width);
      nvgStrokeColor(ctx, col(C1, argb));
      nvgStroke(ctx);
   }

   // ---------------------------------------------------------------- yazi

   public static float text(String s, float x, float y, float size, boolean bold, int align, int argb) {
      nvgFontFaceId(ctx, bold ? fontBold : fontReg);
      nvgFontSize(ctx, size);
      nvgTextAlign(ctx, align);
      fill(argb);
      return nvgText(ctx, x, y, s);
   }

   /** Olculen genislikler onbellekte tutulur (nvgTextBounds kare basina onlarca kez cagrilmasin). */
   public static float textWidth(String s, float size, boolean bold) {
      int idx = (((int)(size * 2.0F)) & 63) * 2 + (bold ? 1 : 0);
      HashMap<String, Float> m = WIDTHS[idx];
      if (m == null) {
         m = new HashMap<>();
         WIDTHS[idx] = m;
      }

      Float cached = m.get(s);
      if (cached != null) {
         return cached;
      }

      nvgFontFaceId(ctx, bold ? fontBold : fontReg);
      nvgFontSize(ctx, size);
      float w = nvgTextBounds(ctx, 0.0F, 0.0F, s, BOUNDS);
      if (m.size() > 400) {
         m.clear();
      }

      m.put(s, w);
      return w;
   }

   /** Sol-orta hizali yazi. */
   public static float textL(String s, float x, float y, float size, boolean bold, int argb) {
      return text(s, x, y, size, bold, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE, argb);
   }

   /** Sag-orta hizali yazi. */
   public static void textR(String s, float x, float y, float size, boolean bold, int argb) {
      text(s, x, y, size, bold, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE, argb);
   }

   /** Orta-orta hizali yazi. */
   public static void textC(String s, float x, float y, float size, boolean bold, int argb) {
      text(s, x, y, size, bold, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE, argb);
   }
}
