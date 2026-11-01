package kuro.ui;

/** Kare hizindan bagimsiz, ustel yumusatmali basit animasyon degeri. */
public final class Anim {
   public float v;
   public float t;

   public Anim(float init) {
      this.v = init;
      this.t = init;
   }

   public Anim to(float target) {
      this.t = target;
      return this;
   }

   public void snap(float x) {
      this.v = x;
      this.t = x;
   }

   /** @param speed buyudukce daha hizli (ornek: 12 ~ 0.2sn) */
   public float tick(float dt, float speed) {
      v += (t - v) * (1.0F - (float)Math.exp(-speed * dt));
      if (Math.abs(t - v) < 0.0008F) {
         v = t;
      }

      return v;
   }

   public static float clamp(float v, float a, float b) {
      return v < a ? a : Math.min(v, b);
   }

   public static float easeOutCubic(float x) {
      float i = 1.0F - clamp(x, 0.0F, 1.0F);
      return 1.0F - i * i * i;
   }

   public static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }
}
