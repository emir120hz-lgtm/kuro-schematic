package kuro.ui;

import kuro.Config;

/** CS2 hile menusu tarzi: neredeyse siyah yuzeyler, ince cizgiler, tek bir vurgu rengi. */
public final class Theme {
   public static final int[] ACCENTS = new int[]{
      0x8B5CF6, // mor
      0x3B82F6, // mavi
      0x22D3EE, // turkuaz
      0x34D399, // yesil
      0xF43F5E, // kirmizi
      0xFB923C, // turuncu
      0xEC4899, // pembe
      0xE5E7EB  // beyaz
   };
   public static final String[] ACCENT_NAMES = new String[]{"Mor", "Mavi", "Turkuaz", "Yesil", "Kirmizi", "Turuncu", "Pembe", "Beyaz"};

   public static final int BG = 0xF50B0B0F;
   public static final int BG_SIDE = 0xFF08080B;
   public static final int HUD_BG = 0xD60B0B10;
   public static final int TEXT = 0xFFE9E9EF;
   public static final int TEXT_DIM = 0xFF8C8C99;
   public static final int TEXT_MUTED = 0xFF5B5B68;
   public static final int GREEN = 0xFF34D399;
   public static final int RED = 0xFFF43F5E;

   private static final Anim R = new Anim(0x8B);
   private static final Anim G = new Anim(0x5C);
   private static final Anim B = new Anim(0xF6);

   private Theme() {
   }

   /** Vurgu rengini hedefe dogru yumusakca kaydirir. */
   public static void update(float dt) {
      int c = ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, Config.accent))];
      R.to((c >> 16) & 0xFF).tick(dt, 10.0F);
      G.to((c >> 8) & 0xFF).tick(dt, 10.0F);
      B.to(c & 0xFF).tick(dt, 10.0F);
   }

   public static int accent() {
      return 0xFF000000 | ((int)R.v << 16) | ((int)G.v << 8) | (int)B.v;
   }

   /** Gradyanin ikinci ucu: vurgunun biraz aciklastirilmis hali. */
   public static int accent2() {
      return mix(accent(), 0xFFFFFFFF, 0.38F);
   }

   public static int a(int argb, float mul) {
      int al = (int)(((argb >>> 24) & 0xFF) * Anim.clamp(mul, 0.0F, 1.0F));
      return (al << 24) | (argb & 0xFFFFFF);
   }

   public static int alpha(int rgb, float a) {
      return ((int)(Anim.clamp(a, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
   }

   public static int mix(int c1, int c2, float t) {
      t = Anim.clamp(t, 0.0F, 1.0F);
      int a = (int)Anim.lerp((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, t);
      int r = (int)Anim.lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
      int g = (int)Anim.lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
      int b = (int)Anim.lerp(c1 & 0xFF, c2 & 0xFF, t);
      return (a << 24) | (r << 16) | (g << 8) | b;
   }

   /** Mantiksal arayuz olcegi (cozunurluge gore; GUI olcegi ayarindan bagimsiz). */
   public static float scale(int framebufferHeight) {
      return Anim.clamp(framebufferHeight / 900.0F, 1.0F, 3.0F);
   }

   /**
    * Yari saydam "cam" (glassmorphism) kutu: NanoVG gercek arka plan bulaniklastirmasi (backdrop-blur)
    * yapamadigi icin bu, katmanli yari saydam dolgu + yumusak dis parlama + ust ic isik seridi + ince
    * parlak kenarlik ile o hissi taklit eder. Orijinal bir kompozisyon; disaridan kopyalanmis bir
    * texture/shader icermez.
    */
   public static void glassBox(float x, float y, float w, float h, float r, int accent) {
      Nvg.glow(x, y + 2.0F, w, h, r, 16.0F, 0x40000000);
      Nvg.rect(x, y, w, h, r, 0x8C101014);
      float hiH = Math.min(h * 0.55F, 14.0F);
      Nvg.rectGradV(x + 1.0F, y + 1.0F, w - 2.0F, hiH, Math.max(0.0F, r - 1.0F), 0x24FFFFFF, 0x00FFFFFF);
      Nvg.border(x, y, w, h, r, 1.0F, 0x2EFFFFFF);
      Nvg.rectGradV(x + w - 3.0F, y + 3.0F, 2.0F, h - 6.0F, 1.0F, accent, mix(accent, 0xFFFFFFFF, 0.4F));
   }
}
