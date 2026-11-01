package kuro.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import java.util.ArrayList;
import java.util.List;
import kuro.Config;

/** Modul acma/kapama bildirimleri: sagdan kayarak gelir, ilerleme cizgisi ile kapanir. */
public final class Toasts {
   private static final float LIFE = 2.4F;
   private static final float W = 196.0F;
   private static final float H = 40.0F;
   private static final List<Toast> LIST = new ArrayList<>();

   private static final class Toast {
      final String title;
      final String sub;
      final boolean positive;
      float age;
      final Anim in = new Anim(0.0F);

      Toast(String title, String sub, boolean positive) {
         this.title = title;
         this.sub = sub;
         this.positive = positive;
      }
   }

   private Toasts() {
   }

   public static void push(String title, boolean positive, String sub) {
      // ayni modul icin eskisini degistir
      LIST.removeIf(t -> t.title.equals(title));
      if (LIST.size() >= 5) {
         LIST.remove(0);
      }

      LIST.add(new Toast(title, sub, positive));
   }

   static boolean active() {
      return Config.toasts && !LIST.isEmpty();
   }

   static void render(float lw, float lh, float dt) {
      if (!Config.toasts || LIST.isEmpty()) {
         return;
      }

      int accent = Theme.accent();
      float baseY = lh - 76.0F;
      int slot = 0;
      for (int i = LIST.size() - 1; i >= 0; i--) {
         Toast t = LIST.get(i);
         t.age += dt;
         t.in.to(t.age < LIFE ? 1.0F : 0.0F).tick(dt, 14.0F);
         if (t.age >= LIFE && t.in.v <= 0.01F) {
            LIST.remove(i);
            continue;
         }

         float e = Anim.easeOutCubic(t.in.v);
         float x = lw - 16.0F - W + (1.0F - e) * (W + 24.0F);
         float y = baseY - slot * (H + 8.0F);
         slot++;

         nvgSave(Nvg.ctx);
         nvgGlobalAlpha(Nvg.ctx, e);
         int c = t.positive ? Theme.GREEN : Theme.RED;
         Nvg.glow(x, y + 2.0F, W, H, 6.0F, 14.0F, 0x66000000);
         Nvg.rect(x, y, W, H, 6.0F, Theme.HUD_BG);
         Nvg.border(x, y, W, H, 6.0F, 1.0F, 0x14FFFFFF);
         // sol renkli serit
         Nvg.rect(x, y + 6.0F, 3.0F, H - 12.0F, 1.5F, c);
         // durum noktasi
         Nvg.circle(x + 20.0F, y + H * 0.5F, 4.0F, c);
         Nvg.glow(x + 16.0F, y + H * 0.5F - 4.0F, 8.0F, 8.0F, 4.0F, 8.0F, Theme.a(c, 0.5F));
         Nvg.textL(t.title, x + 34.0F, y + 14.0F, 13.0F, true, Theme.TEXT);
         Nvg.textL(t.sub, x + 34.0F, y + 28.0F, 11.0F, false, Theme.TEXT_DIM);
         // alt ilerleme cizgisi
         float p = Anim.clamp(1.0F - t.age / LIFE, 0.0F, 1.0F);
         Nvg.rectGradH(x + 8.0F, y + H - 4.0F, (W - 16.0F) * p, 2.0F, 1.0F, accent, Theme.accent2());
         nvgRestore(Nvg.ctx);
      }
   }
}
