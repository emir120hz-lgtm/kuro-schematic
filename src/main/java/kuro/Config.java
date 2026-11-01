package kuro;

import java.io.IOException;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Kalici ayarlar (kuro_settings.txt). Tuslar eskisi gibi kuro_keys.txt icinde. */
public final class Config {
   public static int accent = 0;
   public static boolean watermark = true;
   public static boolean moduleList = true;
   public static boolean keybinds = true;
   public static boolean info = true;
   public static boolean toasts = true;
   public static boolean columnMenu = false;
   public static boolean hudGlass = true; // modul listesi kutularinin "cam" (glassmorphism) gorunumu

   public static float flyH = 0.8F;
   public static float flyV = 0.5F;
   public static float freecamSpeed = 0.6F;
   // Freelook: basili tut / 3. sahis / hassasiyet
   public static float flHold = 1.0F;
   public static float flThird = 1.0F;
   public static float flSens = 1.0F;
   public static float crystalPlace = 1.0F;
   public static float crystalBreak = 0.0F;
   public static float anchorDelay = 2.0F;
   // AutoTotem (legit ayarlari)
   public static float totemMin = 3.0F;
   public static float totemMax = 7.0F;
   public static float totemHealth = 20.0F;
   public static float totemLegit = 1.0F;
   // yeni moduller
   public static float auraRange = 4.2F;
   public static float auraTurn = 45.0F;
   public static float speedValue = 0.35F;
   public static float velocityKeep = 0.0F;
   // AutoCrystal (legit)
   public static float acLegit = 1.0F;
   public static float acHold = 0.0F;
   public static float acReactMin = 3.0F;
   public static float acReactMax = 7.0F;
   public static float acPlaceMin = 2.0F;
   public static float acPlaceMax = 4.0F;
   public static float acBreakMin = 1.0F;
   public static float acBreakMax = 3.0F;
   public static float acAimSpeed = 22.0F;
   public static float acAimNoise = 0.6F;
   public static float acPlaceRange = 4.0F;
   public static float acTargetRange = 10.0F;
   public static float acMinDmg = 5.0F;
   public static float acMaxSelf = 6.0F;
   public static float acMiss = 0.0F;
   public static float acSwap = 0.0F;
   // SafeAnchor
   public static float saRange = 4.5F;
   public static float saOnlyCharged = 0.0F;
   public static float saReactMin = 3.0F;
   public static float saReactMax = 7.0F;
   public static float saDelayMin = 2.0F;
   public static float saDelayMax = 4.0F;
   public static float saAimSpeed = 22.0F;
   // ClearRender
   public static String clearBlocks = ClearRender.DEFAULT;
   // AntiTrap
   // SchematicBuilder
   public static float schemIndex = 1.0F;
   public static float schemRot = 0.0F;
   public static float schemPerTick = 2.0F;
   public static float schemDelay = 0.0F;
   public static float schemReach = 4.0F;
   public static float schemWalk = 1.0F;
   public static float schemBreak = 1.0F;
   public static float atRange = 4.0F;
   public static float atDelay = 1.0F;
   public static float atWait = 0.0F;
   public static float atAim = 0.0F;
   public static float atArmor = 1.0F;
   public static float atCart = 1.0F;
   // Donut sekmesi
   public static float dnTracer = 0.0F;
   public static float dnChat = 1.0F;
   public static float dnThick = 2.0F;
   public static float dnAlpha = 100.0F;
   public static float dnRadius = 5.0F;
   public static float dnRange = 256.0F;
   public static float amMin = 8.0F;
   public static float amChunk = 1.0F;
   public static float klMin = 3.0F;
   public static float klY = 62.0F;
   public static float dsMinY = 9.0F;
   public static float dsMaxY = 256.0F;
   public static float rdMinY = -64.0F;
   public static float rdMaxY = 320.0F;
   public static float dpLen = 5.0F;
   public static float vnLen = 8.0F;
   public static float vdMin = 4.0F;
   public static float tnMode = 0.0F;
   public static float thDepth = 6.0F;
   public static float tunLen = 8.0F;
   public static float tunH = 3.0F;
   public static float tnMinY = -60.0F;
   public static float tnMaxY = 40.0F;
   public static float ivPlayers = 1.0F;
   public static float ivMobs = 0.0F;
   public static float dnWebhook = 0.0F;
   public static float stMin = 6.0F;
   public static float stSpawner = 1.0F;
   public static float cfMin = 10.0F;
   public static float cfMinY = 9.0F;
   public static float lgMin = 12.0F;
   public static float lgMinY = -64.0F;
   public static float lgMaxY = 40.0F;
   public static float aaRange = 4.5F;
   public static float aaFov = 60.0F;
   public static float aaSpeed = 8.0F;
   public static float aaNoise = 0.3F;
   public static float htMin = 2.0F;
   public static float htMax = 5.0F;
   public static float ddHealth = 8.0F;
   // FakeScoreboard: gercek scoreboard'daki yazilari yalnizca yerelde degistiren "bul→yerine" kurallari (bkz. ScoreboardEdit)
   public static String fsbRules = "";
   // FakeBan: sunucudan cikip ana menude gosterilen sahte kopma ekrani (metinler asagida).
   // Hepsi serbestce duzenlenebilir; bos birakilirsa otomatik deger kullanilir (tarih = bugun, Ban ID = rastgele).
   public static String fbanTitle = "Failed to connect to the server";
   public static String fbanReason = "You are banned.";
   public static String fbanDate = ""; // bos -> bugunun tarihi otomatik yazilir
   public static String fbanId = ""; // bos -> rastgele bir ID otomatik uretilir
   public static String fbanAppeal = ""; // istersen "itiraz icin ... " gibi bir satir ekle, bos birakirsan gosterilmez
   // makrolar (legit)
   public static float macroJitter = 1.0F;
   public static float macroMiss = 0.0F;
   // yeni savas modulleri
   public static float webRange = 4.5F;
   public static float webAim = 22.0F;
   public static float webMin = 2.0F;
   public static float webMax = 5.0F;
   public static float esMin = 2.0F;
   public static float esMax = 5.0F;
   public static float hbExpand = 0.1F;
   public static float jrChance = 100.0F;
   public static float jrMin = 0.0F;
   public static float jrMax = 1.0F;
   public static float mpRange = 4.5F;
   public static float mpDelay = 2.0F;
   public static float reachValue = 3.2F;
   public static float sbAuto = 1.0F;
   public static float sbCharge = 1.0F;
   public static float sbMin = 2.0F;
   public static float sbMax = 5.0F;
   // gorsel moduller
   public static float vmX = 0.0F;
   public static float vmY = 0.0F;
   public static float vmZ = 0.0F;
   public static float vmScale = 1.0F;
   public static float saStyle = 0.0F;
   public static float saVanilla = 1.0F;
   public static float saSlow = 1.0F;
   public static float saEquip = 1.0F;
   public static float wtTime = 6000.0F;
   public static float wtNoRain = 0.0F;
   // yardimci hileler / yeni gorseller
   public static float ckMin = 8.0F;
   public static float ckMax = 12.0F;
   public static float atMin = 1.0F;
   public static float atMax = 3.0F;
   public static float aeHunger = 14.0F;
   public static float csMin = 1.0F;
   public static float csMax = 3.0F;
   public static float csClose = 1.0F;
   public static float aaMin = 2.0F;
   public static float aaMax = 5.0F;
   public static float arMin = 10.0F;
   public static float arMax = 30.0F;
   public static float alHealth = 6.0F;
   public static float stepHeight = 1.0F;
   public static float zoomFov = 30.0F;
   public static float ieRange = 64.0F;
   public static float ieThick = 2.0F;
   // BaseAura + Nametags
   public static float baHold = 1.0F;
   public static float baLegit = 1.0F;
   public static float baStep = 1.0F;
   public static float baMethod = 2.0F;
   public static float baReactMin = 3.0F;
   public static float baReactMax = 8.0F;
   public static float baObsidian = 1.0F;
   public static float baWalls = 0.0F;
   public static float baMinDmg = 5.0F;
   public static float baMaxSelf = 12.0F;
   public static float baProt = 0.0F;
   public static float baStatus = 1.0F;
   public static float baTargetRange = 10.0F;
   public static float baPlaceRange = 4.0F;
   public static float baBreakRange = 3.0F;
   public static float baPlaceMin = 2.0F;
   public static float baPlaceMax = 4.0F;
   public static float baBreakMin = 1.0F;
   public static float baBreakMax = 3.0F;
   public static float baObsMin = 2.0F;
   public static float baObsMax = 5.0F;
   public static float baAim = 22.0F;
   public static float baNoise = 0.6F;
   public static float baMiss = 0.0F;
   public static float ntScale = 1.0F;
   public static float ntHealth = 1.0F;
   public static float ntDist = 1.0F;
   public static float ntPing = 0.0F;
   public static float ntArmor = 1.0F;
   public static float ntRange = 128.0F;
   // FakePlayer
   public static float fpTotem = 1.0F;
   public static float fpInfinite = 0.0F;
   public static float fpDmg = 1.0F;
   public static float fpArmor = 0.0F;
   public static float fpHealth = 20.0F;
   public static float fpRespawn = 1.0F;
   // Oyuncu ESP
   public static float peBox = 1.0F;
   public static float peTracer = 1.0F;
   public static float peThick = 2.0F;
   public static float peAlpha = 100.0F;
   public static float peHue = 0.0F;
   public static float peSat = 80.0F;
   public static float peHealth = 0.0F;
   public static float peRange = 256.0F;
   public static float peTracerAlpha = 70.0F;
   // Blok ESP
   public static float beThick = 2.0F;
   public static float beAlpha = 100.0F;
   public static float beRange = 128.0F;
   public static String blocks = BlockSel.DEFAULT;
   public static String customBlocks = "";
   // XRay
   public static float xrLight = 1.0F;
   public static String xrayBlocks = XraySel.DEFAULT;
   public static String xrayCustom = "";

   private Config() {
   }

   static Path file() {
      return FabricLoader.getInstance().getGameDir().resolve("kuro_settings.txt");
   }

   private static boolean saved(Field f) {
      int m = f.getModifiers();
      if (!Modifier.isStatic(m) || Modifier.isFinal(m) || !Modifier.isPublic(m)) {
         return false;
      }

      Class<?> t = f.getType();
      return t == float.class || t == int.class || t == boolean.class || t == String.class;
   }

   public static void load() {
      Path f = file();
      if (!Files.exists(f)) {
         BlockSel.load();
         XraySel.load();
         return;
      }

      try {
         for (String line : Files.readAllLines(f)) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
               continue;
            }

            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            try {
               Field fl = Config.class.getDeclaredField(k);
               if (!saved(fl)) {
                  continue;
               }

               Class<?> t = fl.getType();
               if (t == float.class) {
                  float x = Float.parseFloat(v);
                  if (Float.isFinite(x)) {
                     fl.setFloat(null, x);
                  }
               } else if (t == int.class) {
                  fl.setInt(null, Integer.parseInt(v));
               } else if (t == boolean.class) {
                  fl.setBoolean(null, Boolean.parseBoolean(v));
               } else {
                  fl.set(null, v);
               }
            } catch (NoSuchFieldException | IllegalAccessException | NumberFormatException ignored) {
            }
         }
      } catch (IOException ignored) {
      }

      accent = Math.max(0, Math.min(7, accent));
      BlockSel.load();
      XraySel.load();
   }

   public static void save() {
      StringBuilder sb = new StringBuilder();
      for (Field f : Config.class.getDeclaredFields()) {
         if (!saved(f)) {
            continue;
         }

         try {
            sb.append(f.getName()).append('=').append(f.get(null)).append('\n');
         } catch (IllegalAccessException ignored) {
         }
      }

      try {
         Files.writeString(file(), sb.toString());
      } catch (IOException ignored) {
      }
   }

   static float clamp(float v, float a, float b) {
      return Math.max(a, Math.min(b, v));
   }

   /** HSB -> 0xRRGGBB (h: 0..360, s/v: 0..1). */
   public static int hsb(float h, float s, float v) {
      float hh = (((h % 360.0F) + 360.0F) % 360.0F) / 60.0F;
      int i = (int)hh;
      float f = hh - i;
      float p = v * (1.0F - s);
      float q = v * (1.0F - s * f);
      float t = v * (1.0F - s * (1.0F - f));
      float r;
      float g;
      float b;
      switch (i) {
         case 0 -> { r = v; g = t; b = p; }
         case 1 -> { r = q; g = v; b = p; }
         case 2 -> { r = p; g = v; b = t; }
         case 3 -> { r = p; g = q; b = v; }
         case 4 -> { r = t; g = p; b = v; }
         default -> { r = v; g = p; b = q; }
      }

      return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
   }
}
