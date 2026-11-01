package kuro.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import kuro.BlockSel;
import kuro.ClearRender;
import kuro.XraySel;
import kuro.Config;
import kuro.Kuro;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** Siyah, modern, animasyonlu NanoVG menusu (CS2 hile menusu tarzi). */
public final class MenuScreen extends Screen {
   // ---------------------------------------------------------------- yerlesim
   private static final float W = 720.0F;
   private static final float H = 470.0F;
   private static final float SIDE = 176.0F;
   private static final float HEAD = 74.0F;
   private static final float PAD = 18.0F;
   private static final float CARD_H = 54.0F;
   private static final float SLIDER_H = 38.0F;

   private static final String[] TABS = new String[]{"Savaş", "Hareket", "Görsel", "Kamera", "Diğer", "Donut", "Ayarlar"};
   private static final String[] TAB_SUB = new String[]{
      "Savaş, macro ve legit ayarları",
      "Uçuş, hız ve hareket yardımcıları",
      "ESP ve görsel yardımcılar",
      "Freecam ve Freelook",
      "Yardımcı araçlar",
      "Base ve yapı bulucu ESP / tarayıcılar",
      "Tema, HUD ve görünüm"
   };
   private static final int[][] TAB_MODS = new int[][]{
      {Kuro.TOTEM, Kuro.CRYSTAL, Kuro.CMACRO, Kuro.AMACRO, Kuro.SAFEANCHOR, Kuro.KILLAURA, Kuro.TRIGGERBOT, Kuro.VELOCITY,
         Kuro.COBWEB, Kuro.CRYSTALOPT, Kuro.MISPLACE, Kuro.SHIELDBREAKER, Kuro.REACH, Kuro.HITBOXES, Kuro.JUMPRESET,
         Kuro.AUTOCLICKER, Kuro.AUTOARMOR, Kuro.AUTOLOG, Kuro.CRYSTALBASE, Kuro.ANTITRAP,
         Kuro.AIMASSIST, Kuro.KEYPEARL, Kuro.HOVERTOTEM, Kuro.DOUBLEHAND},
      {Kuro.FLY, Kuro.SPEED, Kuro.SPRINT, Kuro.NOFALL, Kuro.ELYTRASWAP, Kuro.STEP},
      {Kuro.STORAGE, Kuro.BLOCK, Kuro.PLAYER, Kuro.FULLBRIGHT, Kuro.VIEWMODEL, Kuro.SWINGANIM, Kuro.NOHURTCAM, Kuro.WORLDTIME, Kuro.ZOOM, Kuro.ITEMESP, Kuro.TARGETHUD, Kuro.NOBOB, Kuro.NAMETAGS, Kuro.XRAY, Kuro.CLEARRENDER},
      {Kuro.FREECAM, Kuro.FREELOOK},
      {Kuro.BASES, Kuro.FASTPLACE, Kuro.AUTOTOOL, Kuro.CHESTSTEALER, Kuro.AUTOEAT, Kuro.AUTORESPAWN, Kuro.FAKEPLAYER, Kuro.MONEYFMT, Kuro.FAKEBAN, Kuro.FAKESCOREBOARD, Kuro.SCHEMBUILDER},
      {Kuro.PILLAGERESP, Kuro.VILLAGERESP, Kuro.WANDERESP, Kuro.DROWNEDESP, Kuro.INVISESP, Kuro.AMETHYSTESP, Kuro.KELPESP, Kuro.DEEPSLATEESP,
         Kuro.ROTDEEPESP, Kuro.DRIPESP, Kuro.VINEESP, Kuro.BERRYESP, Kuro.BEEESP, Kuro.PISTONESP, Kuro.VOIDESP, Kuro.TUNNELESP, Kuro.HOLE1ESP, Kuro.SPAWNERESP,
         Kuro.STASHESP, Kuro.CHUNKESP, Kuro.LIGHTESP, Kuro.COLLECTESP, Kuro.SKELETONESP},
      {}
   };
   private static final int SETTINGS_TAB = 6;
   private static final String[] SETTING_NAMES = new String[]{"Filigran", "Modül listesi", "Kısayol paneli", "Bilgi çubuğu", "Bildirimler", "Sütunlu menü stili", "Modül listesi cam görünümü"};
   private static final String[] SETTING_DESCS = new String[]{
      "Sol üstte kuro / oyuncu / fps / ping / saat",
      "Açık modülleri sağ üstte listeler",
      "Açık modüllerin tuşlarını sol tarafta gösterir",
      "Sağ altta koordinat, yön ve hız",
      "Modül açıp kapatınca sağ altta bildirim çıkar",
      "Menüyü dört sütunlu panel görünümüne geçirir",
      "Modül listesi kutularını yarı saydam \"cam\" (glassmorphism) stiliyle çizer"
   };

   // ---------------------------------------------------------------- ayar sürgüleri
   record Slider(String name, float min, float max, float step, DoubleSupplier get, DoubleConsumer set) {
   }

   static final Slider[][] SLIDERS = new Slider[Kuro.NAMES.length][];
   private static final float TOGGLE_H = 30.0F;

   /** Donut modulleri icin ortak sürgüler + modüle özel ek sürgüler. */
   private static Slider[] dn(boolean blockScan, Slider... extra) {
      List<Slider> l = new ArrayList<>();
      l.add(new Slider("Tracer çiz", 0.0F, 1.0F, 1.0F, () -> Config.dnTracer, v -> Config.dnTracer = (float)v));
      l.add(new Slider("Sohbette bildir", 0.0F, 1.0F, 1.0F, () -> Config.dnChat, v -> Config.dnChat = (float)v));
      l.add(new Slider("Çizgi kalınlığı", 1.0F, 6.0F, 0.5F, () -> Config.dnThick, v -> Config.dnThick = (float)v));
      l.add(new Slider("Opaklık (%)", 10.0F, 100.0F, 5.0F, () -> Config.dnAlpha, v -> Config.dnAlpha = (float)v));
      l.add(new Slider("Discord webhook (kuro_webhook.txt)", 0.0F, 1.0F, 1.0F, () -> Config.dnWebhook, v -> Config.dnWebhook = (float)v));
      if (blockScan) {
         l.add(new Slider("Tarama yarıçapı (chunk)", 2.0F, 12.0F, 1.0F, () -> Config.dnRadius, v -> Config.dnRadius = (float)v));
      } else {
         l.add(new Slider("Menzil", 32.0F, 512.0F, 16.0F, () -> Config.dnRange, v -> Config.dnRange = (float)v));
      }

      for (Slider s : extra) {
         l.add(s);
      }

      return l.toArray(new Slider[0]);
   }

   private static boolean isToggle(Slider s) {
      return s.min == 0.0F && s.max == 1.0F && s.step == 1.0F;
   }

   private static float rowH(Slider s) {
      return isToggle(s) ? TOGGLE_H : SLIDER_H;
   }

   private static float rowsH(Slider[] sl) {
      float h = 0.0F;
      for (Slider s : sl) {
         h += rowH(s);
      }

      return h;
   }

   static {
      SLIDERS[Kuro.FLY] = new Slider[]{
         new Slider("Yatay hız", 0.1F, 2.0F, 0.05F, () -> Config.flyH, v -> Config.flyH = (float)v),
         new Slider("Dikey hız", 0.1F, 1.5F, 0.05F, () -> Config.flyV, v -> Config.flyV = (float)v)
      };
      SLIDERS[Kuro.TOTEM] = new Slider[]{
         new Slider("Min gecikme (tick)", 0.0F, 20.0F, 1.0F, () -> Config.totemMin, v -> Config.totemMin = (float)v),
         new Slider("Max gecikme (tick)", 0.0F, 20.0F, 1.0F, () -> Config.totemMax, v -> Config.totemMax = (float)v),
         new Slider("Can eşiği (20 = hep)", 1.0F, 20.0F, 1.0F, () -> Config.totemHealth, v -> Config.totemHealth = (float)v),
         new Slider("Legit mod (0 kapalı / 1 açık)", 0.0F, 1.0F, 1.0F, () -> Config.totemLegit, v -> Config.totemLegit = (float)v)
      };
      SLIDERS[Kuro.CRYSTAL] = new Slider[]{
         new Slider("Legit mod (0 hızlı / 1 legit)", 0.0F, 1.0F, 1.0F, () -> Config.acLegit, v -> Config.acLegit = (float)v),
         new Slider("Sağ tık basılıyken çalış (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.acHold, v -> Config.acHold = (float)v),
         new Slider("Tepki min (tick)", 0.0F, 20.0F, 1.0F, () -> Config.acReactMin, v -> Config.acReactMin = (float)v),
         new Slider("Tepki max (tick)", 0.0F, 20.0F, 1.0F, () -> Config.acReactMax, v -> Config.acReactMax = (float)v),
         new Slider("Koyma min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.acPlaceMin, v -> Config.acPlaceMin = (float)v),
         new Slider("Koyma max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.acPlaceMax, v -> Config.acPlaceMax = (float)v),
         new Slider("Kırma min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.acBreakMin, v -> Config.acBreakMin = (float)v),
         new Slider("Kırma max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.acBreakMax, v -> Config.acBreakMax = (float)v),
         new Slider("Nişan hızı (°/tick)", 1.0F, 90.0F, 1.0F, () -> Config.acAimSpeed, v -> Config.acAimSpeed = (float)v),
         new Slider("Nişan sapması (°)", 0.0F, 5.0F, 0.1F, () -> Config.acAimNoise, v -> Config.acAimNoise = (float)v),
         new Slider("Koyma menzili", 1.0F, 4.5F, 0.1F, () -> Config.acPlaceRange, v -> Config.acPlaceRange = (float)v),
         new Slider("Hedef menzili", 3.0F, 16.0F, 1.0F, () -> Config.acTargetRange, v -> Config.acTargetRange = (float)v),
         new Slider("Min hedef hasarı", 0.0F, 36.0F, 0.5F, () -> Config.acMinDmg, v -> Config.acMinDmg = (float)v),
         new Slider("Max kendi hasarım", 0.0F, 20.0F, 0.5F, () -> Config.acMaxSelf, v -> Config.acMaxSelf = (float)v),
         new Slider("Hata payı (%)", 0.0F, 50.0F, 1.0F, () -> Config.acMiss, v -> Config.acMiss = (float)v),
         new Slider("Hotbar'dan kristal seç (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.acSwap, v -> Config.acSwap = (float)v)
      };
      SLIDERS[Kuro.SAFEANCHOR] = new Slider[]{
         new Slider("Menzil", 2.0F, 4.5F, 0.1F, () -> Config.saRange, v -> Config.saRange = (float)v),
         new Slider("Sadece dolu anchor (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.saOnlyCharged, v -> Config.saOnlyCharged = (float)v),
         new Slider("Tepki min (tick)", 0.0F, 20.0F, 1.0F, () -> Config.saReactMin, v -> Config.saReactMin = (float)v),
         new Slider("Tepki max (tick)", 0.0F, 20.0F, 1.0F, () -> Config.saReactMax, v -> Config.saReactMax = (float)v),
         new Slider("Koyma min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.saDelayMin, v -> Config.saDelayMin = (float)v),
         new Slider("Koyma max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.saDelayMax, v -> Config.saDelayMax = (float)v),
         new Slider("Nişan hızı (°/tick)", 1.0F, 90.0F, 1.0F, () -> Config.saAimSpeed, v -> Config.saAimSpeed = (float)v)
      };
      SLIDERS[Kuro.KILLAURA] = new Slider[]{
         new Slider("Menzil", 2.5F, 6.0F, 0.1F, () -> Config.auraRange, v -> Config.auraRange = (float)v),
         new Slider("Dönüş hızı (0 = dönme)", 0.0F, 180.0F, 5.0F, () -> Config.auraTurn, v -> Config.auraTurn = (float)v)
      };
      SLIDERS[Kuro.SPEED] = new Slider[]{
         new Slider("Hız", 0.1F, 1.0F, 0.05F, () -> Config.speedValue, v -> Config.speedValue = (float)v)
      };
      SLIDERS[Kuro.VELOCITY] = new Slider[]{
         new Slider("Kalan itme (%)", 0.0F, 100.0F, 5.0F, () -> Config.velocityKeep, v -> Config.velocityKeep = (float)v)
      };
      SLIDERS[Kuro.CMACRO] = new Slider[]{
         new Slider("Koyma gecikmesi (tick)", 0.0F, 6.0F, 1.0F, () -> Config.crystalPlace, v -> Config.crystalPlace = (float)v),
         new Slider("Kırma gecikmesi (tick)", 0.0F, 6.0F, 1.0F, () -> Config.crystalBreak, v -> Config.crystalBreak = (float)v),
         new Slider("Rastgele ek gecikme (tick)", 0.0F, 6.0F, 1.0F, () -> Config.macroJitter, v -> Config.macroJitter = (float)v),
         new Slider("Hata payı (%)", 0.0F, 50.0F, 1.0F, () -> Config.macroMiss, v -> Config.macroMiss = (float)v)
      };
      SLIDERS[Kuro.AMACRO] = new Slider[]{
         new Slider("Adım gecikmesi (tick)", 1.0F, 8.0F, 1.0F, () -> Config.anchorDelay, v -> Config.anchorDelay = (float)v),
         new Slider("Rastgele ek gecikme (tick)", 0.0F, 6.0F, 1.0F, () -> Config.macroJitter, v -> Config.macroJitter = (float)v),
         new Slider("Hata payı (%)", 0.0F, 50.0F, 1.0F, () -> Config.macroMiss, v -> Config.macroMiss = (float)v)
      };
      SLIDERS[Kuro.PLAYER] = new Slider[]{
         new Slider("Kutu çiz", 0.0F, 1.0F, 1.0F, () -> Config.peBox, v -> Config.peBox = (float)v),
         new Slider("Tracer çiz", 0.0F, 1.0F, 1.0F, () -> Config.peTracer, v -> Config.peTracer = (float)v),
         new Slider("Cana göre renk (yeşil → kırmızı)", 0.0F, 1.0F, 1.0F, () -> Config.peHealth, v -> Config.peHealth = (float)v),
         new Slider("Çizgi kalınlığı", 1.0F, 6.0F, 0.5F, () -> Config.peThick, v -> Config.peThick = (float)v),
         new Slider("Kutu opaklığı (%)", 10.0F, 100.0F, 5.0F, () -> Config.peAlpha, v -> Config.peAlpha = (float)v),
         new Slider("Tracer opaklığı (%)", 10.0F, 100.0F, 5.0F, () -> Config.peTracerAlpha, v -> Config.peTracerAlpha = (float)v),
         new Slider("Renk tonu", 0.0F, 360.0F, 5.0F, () -> Config.peHue, v -> Config.peHue = (float)v),
         new Slider("Renk doygunluğu (0 = beyaz) (%)", 0.0F, 100.0F, 5.0F, () -> Config.peSat, v -> Config.peSat = (float)v),
         new Slider("Menzil", 20.0F, 256.0F, 10.0F, () -> Config.peRange, v -> Config.peRange = (float)v)
      };
      Slider[] bs = new Slider[3 + BlockSel.ALL.length];
      bs[0] = new Slider("Çizgi kalınlığı", 1.0F, 6.0F, 0.5F, () -> Config.beThick, v -> Config.beThick = (float)v);
      bs[1] = new Slider("Opaklık (%)", 10.0F, 100.0F, 5.0F, () -> Config.beAlpha, v -> Config.beAlpha = (float)v);
      bs[2] = new Slider("Menzil", 16.0F, 192.0F, 8.0F, () -> Config.beRange, v -> Config.beRange = (float)v);
      for (int i = 0; i < BlockSel.ALL.length; i++) {
         BlockSel.Entry en = BlockSel.ALL[i];
         bs[3 + i] = new Slider(en.label(), 0.0F, 1.0F, 1.0F, () -> BlockSel.has(en.key()) ? 1.0 : 0.0, v -> BlockSel.set(en.key(), v >= 0.5));
      }

      SLIDERS[Kuro.BLOCK] = bs;
      Slider[] xs = new Slider[1 + BlockSel.ALL.length];
      xs[0] = new Slider("Otomatik aydınlık (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.xrLight, v -> Config.xrLight = (float)v);
      for (int i = 0; i < BlockSel.ALL.length; i++) {
         BlockSel.Entry en = BlockSel.ALL[i];
         xs[1 + i] = new Slider(en.label(), 0.0F, 1.0F, 1.0F, () -> XraySel.has(en.key()) ? 1.0 : 0.0, v -> XraySel.set(en.key(), v >= 0.5));
      }

      SLIDERS[Kuro.XRAY] = xs;
      SLIDERS[Kuro.COBWEB] = new Slider[]{
         new Slider("Menzil", 2.0F, 5.0F, 0.1F, () -> Config.webRange, v -> Config.webRange = (float)v),
         new Slider("Nişan hızı (°/tick)", 1.0F, 90.0F, 1.0F, () -> Config.webAim, v -> Config.webAim = (float)v),
         new Slider("Tepki min (tick)", 0.0F, 20.0F, 1.0F, () -> Config.webMin, v -> Config.webMin = (float)v),
         new Slider("Tepki max (tick)", 0.0F, 20.0F, 1.0F, () -> Config.webMax, v -> Config.webMax = (float)v),
         new Slider("Hata payı (%)", 0.0F, 50.0F, 1.0F, () -> Config.macroMiss, v -> Config.macroMiss = (float)v)
      };
      SLIDERS[Kuro.ELYTRASWAP] = new Slider[]{
         new Slider("Tıklama arası min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.esMin, v -> Config.esMin = (float)v),
         new Slider("Tıklama arası max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.esMax, v -> Config.esMax = (float)v)
      };
      SLIDERS[Kuro.HITBOXES] = new Slider[]{
         new Slider("Genişletme (blok)", 0.0F, 0.4F, 0.01F, () -> Config.hbExpand, v -> Config.hbExpand = (float)v)
      };
      SLIDERS[Kuro.JUMPRESET] = new Slider[]{
         new Slider("Şans (%)", 10.0F, 100.0F, 5.0F, () -> Config.jrChance, v -> Config.jrChance = (float)v),
         new Slider("Gecikme min (tick)", 0.0F, 5.0F, 1.0F, () -> Config.jrMin, v -> Config.jrMin = (float)v),
         new Slider("Gecikme max (tick)", 0.0F, 5.0F, 1.0F, () -> Config.jrMax, v -> Config.jrMax = (float)v)
      };
      SLIDERS[Kuro.MISPLACE] = new Slider[]{
         new Slider("Menzil", 2.0F, 4.5F, 0.1F, () -> Config.mpRange, v -> Config.mpRange = (float)v),
         new Slider("Koyma gecikmesi (tick)", 0.0F, 10.0F, 1.0F, () -> Config.mpDelay, v -> Config.mpDelay = (float)v),
         new Slider("Hata payı (%)", 0.0F, 50.0F, 1.0F, () -> Config.macroMiss, v -> Config.macroMiss = (float)v)
      };
      SLIDERS[Kuro.REACH] = new Slider[]{
         new Slider("Menzil (blok)", 3.0F, 3.6F, 0.05F, () -> Config.reachValue, v -> Config.reachValue = (float)v)
      };
      SLIDERS[Kuro.SHIELDBREAKER] = new Slider[]{
         new Slider("Otomatik vur (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.sbAuto, v -> Config.sbAuto = (float)v),
         new Slider("Tam şarjı bekle (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.sbCharge, v -> Config.sbCharge = (float)v),
         new Slider("Tepki min (tick)", 0.0F, 20.0F, 1.0F, () -> Config.sbMin, v -> Config.sbMin = (float)v),
         new Slider("Tepki max (tick)", 0.0F, 20.0F, 1.0F, () -> Config.sbMax, v -> Config.sbMax = (float)v)
      };
      SLIDERS[Kuro.VIEWMODEL] = new Slider[]{
         new Slider("Yatay (X)", -1.0F, 1.0F, 0.05F, () -> Config.vmX, v -> Config.vmX = (float)v),
         new Slider("Dikey (Y)", -1.0F, 1.0F, 0.05F, () -> Config.vmY, v -> Config.vmY = (float)v),
         new Slider("Derinlik (Z)", -1.0F, 1.0F, 0.05F, () -> Config.vmZ, v -> Config.vmZ = (float)v),
         new Slider("Boyut", 0.3F, 1.5F, 0.05F, () -> Config.vmScale, v -> Config.vmScale = (float)v)
      };
      SLIDERS[Kuro.SWINGANIM] = new Slider[]{
         new Slider("Stil (0 vanilla, 1 slash, 2 punch, 3 sallan)", 0.0F, 3.0F, 1.0F, () -> Config.saStyle, v -> Config.saStyle = (float)v),
         new Slider("Vanilla swing (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.saVanilla, v -> Config.saVanilla = (float)v),
         new Slider("Swing süresi (x)", 0.5F, 4.0F, 0.25F, () -> Config.saSlow, v -> Config.saSlow = (float)v),
         new Slider("Eşya değiştirme animasyonu (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.saEquip, v -> Config.saEquip = (float)v)
      };
      SLIDERS[Kuro.WORLDTIME] = new Slider[]{
         new Slider("Saat (0-24000)", 0.0F, 24000.0F, 500.0F, () -> Config.wtTime, v -> Config.wtTime = (float)v),
         new Slider("Yağmuru kapat (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.wtNoRain, v -> Config.wtNoRain = (float)v)
      };
      SLIDERS[Kuro.AUTOCLICKER] = new Slider[]{
         new Slider("CPS min", 1.0F, 20.0F, 1.0F, () -> Config.ckMin, v -> Config.ckMin = (float)v),
         new Slider("CPS max", 1.0F, 20.0F, 1.0F, () -> Config.ckMax, v -> Config.ckMax = (float)v)
      };
      SLIDERS[Kuro.AUTOTOOL] = new Slider[]{
         new Slider("Gecikme min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.atMin, v -> Config.atMin = (float)v),
         new Slider("Gecikme max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.atMax, v -> Config.atMax = (float)v)
      };
      SLIDERS[Kuro.AUTOEAT] = new Slider[]{
         new Slider("Açlık eşiği", 1.0F, 19.0F, 1.0F, () -> Config.aeHunger, v -> Config.aeHunger = (float)v)
      };
      SLIDERS[Kuro.CHESTSTEALER] = new Slider[]{
         new Slider("Gecikme min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.csMin, v -> Config.csMin = (float)v),
         new Slider("Gecikme max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.csMax, v -> Config.csMax = (float)v),
         new Slider("Bitince kapat (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.csClose, v -> Config.csClose = (float)v)
      };
      SLIDERS[Kuro.AUTOARMOR] = new Slider[]{
         new Slider("Gecikme min (tick)", 0.0F, 20.0F, 1.0F, () -> Config.aaMin, v -> Config.aaMin = (float)v),
         new Slider("Gecikme max (tick)", 0.0F, 20.0F, 1.0F, () -> Config.aaMax, v -> Config.aaMax = (float)v)
      };
      SLIDERS[Kuro.AUTORESPAWN] = new Slider[]{
         new Slider("Gecikme min (tick)", 0.0F, 60.0F, 1.0F, () -> Config.arMin, v -> Config.arMin = (float)v),
         new Slider("Gecikme max (tick)", 0.0F, 60.0F, 1.0F, () -> Config.arMax, v -> Config.arMax = (float)v)
      };
      SLIDERS[Kuro.AUTOLOG] = new Slider[]{
         new Slider("Can eşiği", 1.0F, 19.0F, 1.0F, () -> Config.alHealth, v -> Config.alHealth = (float)v)
      };
      SLIDERS[Kuro.STEP] = new Slider[]{
         new Slider("Yükseklik (blok)", 0.6F, 2.5F, 0.1F, () -> Config.stepHeight, v -> Config.stepHeight = (float)v)
      };
      SLIDERS[Kuro.ZOOM] = new Slider[]{
         new Slider("FOV", 30.0F, 110.0F, 5.0F, () -> Config.zoomFov, v -> Config.zoomFov = (float)v)
      };
      SLIDERS[Kuro.ITEMESP] = new Slider[]{
         new Slider("Menzil", 16.0F, 128.0F, 8.0F, () -> Config.ieRange, v -> Config.ieRange = (float)v),
         new Slider("Çizgi kalınlığı", 1.0F, 6.0F, 0.5F, () -> Config.ieThick, v -> Config.ieThick = (float)v)
      };
      SLIDERS[Kuro.CRYSTALBASE] = new Slider[]{
         new Slider("Sağ tık basılıyken çalış (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.baHold, v -> Config.baHold = (float)v),
         new Slider("Legit mod (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.baLegit, v -> Config.baLegit = (float)v),
         new Slider("Obsidyene basınca tetikle (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.baStep, v -> Config.baStep = (float)v),
         new Slider("Yöntem (0 kristal, 1 anchor, 2 oto)", 0.0F, 2.0F, 1.0F, () -> Config.baMethod, v -> Config.baMethod = (float)v),
         new Slider("Tepki süresi min (tick)", 0.0F, 20.0F, 1.0F, () -> Config.baReactMin, v -> Config.baReactMin = (float)v),
         new Slider("Tepki süresi max (tick)", 0.0F, 20.0F, 1.0F, () -> Config.baReactMax, v -> Config.baReactMax = (float)v),
         new Slider("Obsidyen koy (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.baObsidian, v -> Config.baObsidian = (float)v),
         new Slider("Duvar arkası / nişansız (legit kapalıyken) (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.baWalls, v -> Config.baWalls = (float)v),
         new Slider("Min hedef hasarı", 0.0F, 36.0F, 0.5F, () -> Config.baMinDmg, v -> Config.baMinDmg = (float)v),
         new Slider("Max kendi hasarım", 0.0F, 36.0F, 0.5F, () -> Config.baMaxSelf, v -> Config.baMaxSelf = (float)v),
         new Slider("Patlama koruman (%)", 0.0F, 90.0F, 5.0F, () -> Config.baProt, v -> Config.baProt = (float)v),
         new Slider("Durum mesajı (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.baStatus, v -> Config.baStatus = (float)v),
         new Slider("Hedef menzili", 3.0F, 16.0F, 1.0F, () -> Config.baTargetRange, v -> Config.baTargetRange = (float)v),
         new Slider("Koyma menzili", 1.0F, 4.5F, 0.1F, () -> Config.baPlaceRange, v -> Config.baPlaceRange = (float)v),
         new Slider("Kırma menzili", 1.0F, 4.5F, 0.1F, () -> Config.baBreakRange, v -> Config.baBreakRange = (float)v),
         new Slider("Obsidyen gecikmesi min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.baObsMin, v -> Config.baObsMin = (float)v),
         new Slider("Obsidyen gecikmesi max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.baObsMax, v -> Config.baObsMax = (float)v),
         new Slider("Kristal koyma min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.baPlaceMin, v -> Config.baPlaceMin = (float)v),
         new Slider("Kristal koyma max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.baPlaceMax, v -> Config.baPlaceMax = (float)v),
         new Slider("Kırma min (tick)", 0.0F, 10.0F, 1.0F, () -> Config.baBreakMin, v -> Config.baBreakMin = (float)v),
         new Slider("Kırma max (tick)", 0.0F, 10.0F, 1.0F, () -> Config.baBreakMax, v -> Config.baBreakMax = (float)v),
         new Slider("Nişan hızı (°/tick)", 1.0F, 90.0F, 1.0F, () -> Config.baAim, v -> Config.baAim = (float)v),
         new Slider("Nişan sapması (°)", 0.0F, 5.0F, 0.1F, () -> Config.baNoise, v -> Config.baNoise = (float)v),
         new Slider("Hata payı (%)", 0.0F, 50.0F, 1.0F, () -> Config.baMiss, v -> Config.baMiss = (float)v)
      };
      SLIDERS[Kuro.NAMETAGS] = new Slider[]{
         new Slider("Boyut", 0.5F, 2.0F, 0.1F, () -> Config.ntScale, v -> Config.ntScale = (float)v),
         new Slider("Can göster (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.ntHealth, v -> Config.ntHealth = (float)v),
         new Slider("Zırh yüzdesi (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.ntArmor, v -> Config.ntArmor = (float)v),
         new Slider("Ping göster (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.ntPing, v -> Config.ntPing = (float)v),
         new Slider("Mesafe göster (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.ntDist, v -> Config.ntDist = (float)v),
         new Slider("Menzil", 16.0F, 256.0F, 8.0F, () -> Config.ntRange, v -> Config.ntRange = (float)v)
      };
      Slider[] cr = new Slider[ClearRender.ALL.length];
      for (int i = 0; i < cr.length; i++) {
         String[] en = ClearRender.ALL[i];
         cr[i] = new Slider(en[1] + " gizle", 0.0F, 1.0F, 1.0F, () -> ClearRender.has(en[0]) ? 1.0 : 0.0, v -> ClearRender.set(en[0], v >= 0.5));
      }

      SLIDERS[Kuro.CLEARRENDER] = cr;
      SLIDERS[Kuro.SCHEMBUILDER] = new Slider[]{
         new Slider("Dosya no (schematics klasörü)", 1.0F, 30.0F, 1.0F, () -> Config.schemIndex, v -> Config.schemIndex = (float)v),
         new Slider("Dönüş (x90°)", 0.0F, 3.0F, 1.0F, () -> Config.schemRot, v -> Config.schemRot = (float)v),
         new Slider("Blok / tick", 1.0F, 8.0F, 1.0F, () -> Config.schemPerTick, v -> Config.schemPerTick = (float)v),
         new Slider("Bekleme (tick)", 0.0F, 10.0F, 1.0F, () -> Config.schemDelay, v -> Config.schemDelay = (float)v),
         new Slider("Menzil", 2.0F, 4.5F, 0.1F, () -> Config.schemReach, v -> Config.schemReach = (float)v),
         new Slider("Otomatik yürü (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.schemWalk, v -> Config.schemWalk = (float)v),
         new Slider("Yanlış blokları kır (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.schemBreak, v -> Config.schemBreak = (float)v)
      };
      SLIDERS[Kuro.ANTITRAP] = new Slider[]{
         new Slider("Menzil", 2.0F, 6.0F, 0.1F, () -> Config.atRange, v -> Config.atRange = (float)v),
         new Slider("Vuruş aralığı (tick)", 1.0F, 10.0F, 1.0F, () -> Config.atDelay, v -> Config.atDelay = (float)v),
         new Slider("Armor stand kır", 0.0F, 1.0F, 1.0F, () -> Config.atArmor, v -> Config.atArmor = (float)v),
         new Slider("Sandıklı vagon kır", 0.0F, 1.0F, 1.0F, () -> Config.atCart, v -> Config.atCart = (float)v),
         new Slider("Hedefe dön (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.atAim, v -> Config.atAim = (float)v),
         new Slider("Bekleme süresini bekle (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.atWait, v -> Config.atWait = (float)v)
      };
      SLIDERS[Kuro.PILLAGERESP] = dn(false);
      SLIDERS[Kuro.VILLAGERESP] = dn(false);
      SLIDERS[Kuro.WANDERESP] = dn(false);
      SLIDERS[Kuro.DROWNEDESP] = dn(false);
      SLIDERS[Kuro.INVISESP] = dn(false,
         new Slider("Oyuncuları göster", 0.0F, 1.0F, 1.0F, () -> Config.ivPlayers, v -> Config.ivPlayers = (float)v),
         new Slider("Mobları göster", 0.0F, 1.0F, 1.0F, () -> Config.ivMobs, v -> Config.ivMobs = (float)v));
      SLIDERS[Kuro.AMETHYSTESP] = dn(true,
         new Slider("Chunk başına min ametist", 1.0F, 80.0F, 1.0F, () -> Config.amMin, v -> Config.amMin = (float)v),
         new Slider("Tüm chunk'ı kutula", 0.0F, 1.0F, 1.0F, () -> Config.amChunk, v -> Config.amChunk = (float)v));
      SLIDERS[Kuro.KELPESP] = dn(true,
         new Slider("Chunk başına min kelp ucu", 1.0F, 30.0F, 1.0F, () -> Config.klMin, v -> Config.klMin = (float)v),
         new Slider("Kelp ucu Y seviyesi", 30.0F, 100.0F, 1.0F, () -> Config.klY, v -> Config.klY = (float)v));
      SLIDERS[Kuro.DEEPSLATEESP] = dn(true,
         new Slider("Min Y", -64.0F, 320.0F, 1.0F, () -> Config.dsMinY, v -> Config.dsMinY = (float)v),
         new Slider("Max Y", -64.0F, 320.0F, 1.0F, () -> Config.dsMaxY, v -> Config.dsMaxY = (float)v));
      SLIDERS[Kuro.ROTDEEPESP] = dn(true,
         new Slider("Min Y", -64.0F, 320.0F, 1.0F, () -> Config.rdMinY, v -> Config.rdMinY = (float)v),
         new Slider("Max Y", -64.0F, 320.0F, 1.0F, () -> Config.rdMaxY, v -> Config.rdMaxY = (float)v));
      SLIDERS[Kuro.DRIPESP] = dn(true,
         new Slider("Min uzunluk", 2.0F, 20.0F, 1.0F, () -> Config.dpLen, v -> Config.dpLen = (float)v));
      SLIDERS[Kuro.VINEESP] = dn(true,
         new Slider("Min uzunluk", 2.0F, 30.0F, 1.0F, () -> Config.vnLen, v -> Config.vnLen = (float)v));
      SLIDERS[Kuro.BERRYESP] = dn(true);
      SLIDERS[Kuro.BEEESP] = dn(true);
      SLIDERS[Kuro.PISTONESP] = dn(true);
      SLIDERS[Kuro.VOIDESP] = dn(true,
         new Slider("Min boşluk boyutu (blok)", 1.0F, 100.0F, 1.0F, () -> Config.vdMin, v -> Config.vdMin = (float)v));
      SLIDERS[Kuro.TUNNELESP] = dn(true,
         new Slider("Mod (0 hepsi / 1 kuyu / 2 tünel)", 0.0F, 2.0F, 1.0F, () -> Config.tnMode, v -> Config.tnMode = (float)v),
         new Slider("Min kuyu derinliği", 3.0F, 40.0F, 1.0F, () -> Config.thDepth, v -> Config.thDepth = (float)v),
         new Slider("Min tünel uzunluğu", 4.0F, 16.0F, 1.0F, () -> Config.tunLen, v -> Config.tunLen = (float)v),
         new Slider("Max tünel yüksekliği", 1.0F, 4.0F, 1.0F, () -> Config.tunH, v -> Config.tunH = (float)v),
         new Slider("Tarama min Y", -64.0F, 300.0F, 1.0F, () -> Config.tnMinY, v -> Config.tnMinY = (float)v),
         new Slider("Tarama max Y", -64.0F, 300.0F, 1.0F, () -> Config.tnMaxY, v -> Config.tnMaxY = (float)v));
      SLIDERS[Kuro.HOLE1ESP] = dn(true,
         new Slider("Tarama min Y", -64.0F, 300.0F, 1.0F, () -> Config.tnMinY, v -> Config.tnMinY = (float)v),
         new Slider("Tarama max Y", -64.0F, 300.0F, 1.0F, () -> Config.tnMaxY, v -> Config.tnMaxY = (float)v));
      SLIDERS[Kuro.SPAWNERESP] = dn(true);
      SLIDERS[Kuro.STASHESP] = dn(true,
         new Slider("Chunk başına min depo", 1.0F, 60.0F, 1.0F, () -> Config.stMin, v -> Config.stMin = (float)v),
         new Slider("Spawner varsa da işaretle", 0.0F, 1.0F, 1.0F, () -> Config.stSpawner, v -> Config.stSpawner = (float)v));
      SLIDERS[Kuro.CHUNKESP] = dn(true,
         new Slider("Chunk başına min blok", 1.0F, 200.0F, 1.0F, () -> Config.cfMin, v -> Config.cfMin = (float)v),
         new Slider("Deepslate min Y (doğal sınırın üstü)", -64.0F, 320.0F, 1.0F, () -> Config.cfMinY, v -> Config.cfMinY = (float)v));
      SLIDERS[Kuro.LIGHTESP] = dn(true,
         new Slider("Min ışık seviyesi", 1.0F, 15.0F, 1.0F, () -> Config.lgMin, v -> Config.lgMin = (float)v),
         new Slider("Min Y", -64.0F, 320.0F, 1.0F, () -> Config.lgMinY, v -> Config.lgMinY = (float)v),
         new Slider("Max Y", -64.0F, 320.0F, 1.0F, () -> Config.lgMaxY, v -> Config.lgMaxY = (float)v));
      SLIDERS[Kuro.COLLECTESP] = dn(true);
      SLIDERS[Kuro.SKELETONESP] = dn(false);
      SLIDERS[Kuro.AIMASSIST] = new Slider[]{
         new Slider("Menzil", 2.0F, 6.0F, 0.1F, () -> Config.aaRange, v -> Config.aaRange = (float)v),
         new Slider("Görüş açısı (°)", 10.0F, 180.0F, 5.0F, () -> Config.aaFov, v -> Config.aaFov = (float)v),
         new Slider("Hız (°/tick)", 1.0F, 30.0F, 1.0F, () -> Config.aaSpeed, v -> Config.aaSpeed = (float)v),
         new Slider("Sapma (°)", 0.0F, 3.0F, 0.1F, () -> Config.aaNoise, v -> Config.aaNoise = (float)v)
      };
      SLIDERS[Kuro.HOVERTOTEM] = new Slider[]{
         new Slider("Min gecikme (tick)", 0.0F, 10.0F, 1.0F, () -> Config.htMin, v -> Config.htMin = (float)v),
         new Slider("Max gecikme (tick)", 0.0F, 10.0F, 1.0F, () -> Config.htMax, v -> Config.htMax = (float)v)
      };
      SLIDERS[Kuro.DOUBLEHAND] = new Slider[]{
         new Slider("Can eşiği (yarım kalp)", 2.0F, 20.0F, 1.0F, () -> Config.ddHealth, v -> Config.ddHealth = (float)v)
      };
      SLIDERS[Kuro.FAKEPLAYER] = new Slider[]{
         new Slider("Totem tut (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.fpTotem, v -> Config.fpTotem = (float)v),
         new Slider("Sonsuz totem (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.fpInfinite, v -> Config.fpInfinite = (float)v),
         new Slider("Hasar al (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.fpDmg, v -> Config.fpDmg = (float)v),
         new Slider("Zırh (0-20)", 0.0F, 20.0F, 1.0F, () -> Config.fpArmor, v -> Config.fpArmor = (float)v),
         new Slider("Can", 1.0F, 40.0F, 1.0F, () -> Config.fpHealth, v -> Config.fpHealth = (float)v),
         new Slider("Ölünce yeniden doğ (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.fpRespawn, v -> Config.fpRespawn = (float)v)
      };
      SLIDERS[Kuro.FREELOOK] = new Slider[]{
         new Slider("Basılı tut (0 aç/kapa / 1 basılı tut)", 0.0F, 1.0F, 1.0F, () -> Config.flHold, v -> Config.flHold = (float)v),
         new Slider("3. şahıs kamerası (0/1)", 0.0F, 1.0F, 1.0F, () -> Config.flThird, v -> Config.flThird = (float)v),
         new Slider("Hassasiyet", 0.2F, 2.0F, 0.05F, () -> Config.flSens, v -> Config.flSens = (float)v)
      };
      SLIDERS[Kuro.FREECAM] = new Slider[]{
         new Slider("Kamera hızı", 0.1F, 3.0F, 0.05F, () -> Config.freecamSpeed, v -> Config.freecamSpeed = (float)v)
      };
   }

   // ---------------------------------------------------------------- tıklama alanları
   private static final int T_TAB = 0;
   private static final int T_CARD = 1;
   private static final int T_CHIP = 2;
   private static final int T_ARROW = 3;
   private static final int T_SLIDER = 4;
   private static final int T_ACTION = 5;
   private static final int T_SWATCH = 6;
   private static final int T_SETTING = 7;
   private static final int T_INPUT = 8;
   private static final int T_ADD = 9;
   private static final int T_CUSTOM = 10;
   private String input = "";
   private boolean inputFocus;
   private String inputMsg = "";
   private static final float CUSTOM_H = 30.0F;
   private int inputFor = Kuro.BLOCK;

   private record Hit(int type, int a, int b, float x, float y, float w, float h) {
      boolean contains(float px, float py) {
         return px >= x && px <= x + w && py >= y && py <= y + h;
      }
   }

   private final List<Hit> hits = new ArrayList<>();
   private Hit drag;

   // ---------------------------------------------------------------- durum
   private int tab = 0;
   private int listening = -1;
   private float prog = 0.0F;
   private boolean closing;
   private long last = System.nanoTime();
   private float uiScale = 1.0F;
   private float winX;
   private float winY;
   private float time;

   private final Anim tabInd = new Anim(0.0F);
   private final Anim tabFade = new Anim(0.0F);
   private final Anim[] tabHover = new Anim[TABS.length];
   private final Anim[] modHover = new Anim[Kuro.NAMES.length];
   private final Anim[] modOn = new Anim[Kuro.NAMES.length];
   private final Anim[] modExp = new Anim[Kuro.NAMES.length];
   private final Anim[] setOn = new Anim[SETTING_NAMES.length];
   private final Anim[] setHover = new Anim[SETTING_NAMES.length];
   private final Anim scroll = new Anim(0.0F);
   private float scrollTarget;
   private float contentH;

   public MenuScreen() {
      super(Text.literal("Kuro"));
      for (int i = 0; i < TABS.length; i++) {
         tabHover[i] = new Anim(0.0F);
      }

      for (int i = 0; i < Kuro.NAMES.length; i++) {
         modHover[i] = new Anim(0.0F);
         modOn[i] = new Anim(Kuro.on[i] ? 1.0F : 0.0F);
         modExp[i] = new Anim(0.0F);
      }

      for (int i = 0; i < SETTING_NAMES.length; i++) {
         setOn[i] = new Anim(getSetting(i) ? 1.0F : 0.0F);
         setHover[i] = new Anim(0.0F);
      }
   }

   // ---------------------------------------------------------------- Screen

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
      // vanilla arka plan / bulanıklık yok; NanoVG kendi karartmasını çizer
   }

   @Override
   public void close() {
      closing = true;
      listening = -1;
      drag = null;
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      if (!Nvg.ensure()) {
         context.drawCenteredTextWithShadow(this.textRenderer, "NanoVG yüklenemedi (log dosyasına bak). Esc ile kapat.", this.width / 2, this.height / 2, 0xFFFFFF);
         if (closing) {
            Kuro.mc.setScreen(null);
         }

         return;
      }

      context.draw();

      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - last) / 1.0E9F);
      last = now;
      time += dt;

      prog = Anim.clamp(prog + (closing ? -dt : dt) / 0.24F, 0.0F, 1.0F);

      Window win = Kuro.mc.getWindow();
      int fw = win.getFramebufferWidth();
      int fh = win.getFramebufferHeight();
      if (fw <= 0 || fh <= 0) {
         return;
      }

      // pencere her zaman sığsın
      uiScale = Math.min(Theme.scale(fh), Math.min(fh / (H + 24.0F), fw / (W + 24.0F)));
      float lw = fw / uiScale;
      float lh = fh / uiScale;
      winX = (lw - W) * 0.5F;
      winY = (lh - H) * 0.5F;
      Theme.update(dt);

      Nvg.begin(lw, lh, uiScale);
      try {
         draw(lw, lh, dt);
      } finally {
         Nvg.end();
      }

      if (closing && prog <= 0.0F) {
         Kuro.mc.setScreen(null);
      }
   }

   // ---------------------------------------------------------------- girdi

   private float localX() {
      Window w = Kuro.mc.getWindow();
      double sx = (double)w.getFramebufferWidth() / (double)w.getWidth();
      return (float)(Kuro.mc.mouse.getX() * sx / uiScale) - winX;
   }

   private float localY() {
      Window w = Kuro.mc.getWindow();
      double sy = (double)w.getFramebufferHeight() / (double)w.getHeight();
      return (float)(Kuro.mc.mouse.getY() * sy / uiScale) - winY;
   }

   private Hit pick(float x, float y) {
      for (int i = hits.size() - 1; i >= 0; i--) {
         Hit h = hits.get(i);
         if (!h.contains(x, y)) {
            continue;
         }

         if (h.type != T_TAB && (y < HEAD || y > H || x < SIDE || x > W)) {
            continue; // kaydırılan içerik görünür alanın dışında
         }

         return h;
      }

      return null;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (closing) {
         return true;
      }

      listening = -1;
      inputFocus = false;

      float x = localX();
      float y = localY();
      Hit h = pick(x, y);
      if (h == null) {
         return true;
      }

      switch (h.type) {
         case T_TAB -> setTab(h.a);
         case T_CARD -> {
            if (button == 1 && SLIDERS[h.a] != null) {
               modExp[h.a].to(modExp[h.a].t > 0.5F ? 0.0F : 1.0F);
            } else if (button == 0) {
               Kuro.toggle(h.a);
            }
         }
         case T_ACTION -> Kuro.toggle(h.a);
         case T_ARROW -> modExp[h.a].to(modExp[h.a].t > 0.5F ? 0.0F : 1.0F);
         case T_CHIP -> {
            if (button == 1) {
               Kuro.binds[h.a] = -1;
               Kuro.saveBinds();
            } else {
               listening = h.a;
            }
         }
         case T_SLIDER -> {
            Slider sd = SLIDERS[h.a][h.b];
            if (isToggle(sd)) {
               sd.set.accept(sd.get.getAsDouble() >= 0.5 ? 0.0 : 1.0);
               Config.save();
            } else {
               drag = h;
               applySlider(h, x);
            }
         }
         case T_SWATCH -> {
            Config.accent = h.a;
            Config.save();
         }
         case T_INPUT -> {
            inputFocus = true;
            inputFor = h.a;
         }
         case T_ADD -> {
            inputFor = h.a;
            addInput();
         }
         case T_CUSTOM -> {
            if (h.b == Kuro.XRAY) {
               XraySel.removeCustom(h.a);
            } else {
               BlockSel.removeCustom(h.a);
            }

            Config.save();
         }
         case T_SETTING -> {
            setSetting(h.a, !getSetting(h.a));
            Config.save();
            if (h.a == 5 && Config.columnMenu) {
               Kuro.mc.setScreen(new ClickGuiScreen());
            }
         }
         default -> {
         }
      }

      return true;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (drag != null) {
         Config.save();
         drag = null;
      }

      return true;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (drag != null) {
         applySlider(drag, localX());
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      float max = Math.max(0.0F, contentH - (H - HEAD - PAD));
      scrollTarget = Anim.clamp(scrollTarget - (float)verticalAmount * 44.0F, 0.0F, max);
      return true;
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (inputFocus && input.length() < 48 && (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':' || chr == '.' || chr == '/' || chr == '-')) {
         input += chr;
         inputMsg = "";
         return true;
      }

      return super.charTyped(chr, modifiers);
   }

   private void addInput() {
      String err = inputFor == Kuro.XRAY ? XraySel.addCustom(input) : BlockSel.addCustom(input);
      if (err == null) {
         input = "";
         inputMsg = "Eklendi";
         Config.save();
      } else {
         inputMsg = err;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (inputFocus) {
         if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            inputFocus = false;
         } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            addInput();
         } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) {
               input = input.substring(0, input.length() - 1);
            }
         } else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            String cb = client.keyboard.getClipboard();
            if (cb != null) {
               input = (input + cb.trim()).substring(0, Math.min(48, (input + cb.trim()).length()));
            }
         }

         return true;
      }

      if (listening >= 0) {
         if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            listening = -1;
         } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
            Kuro.binds[listening] = -1;
            Kuro.saveBinds();
            listening = -1;
         } else {
            Kuro.binds[listening] = keyCode;
            Kuro.saveBinds();
            listening = -1;
         }

         return true;
      }

      if (Kuro.menuKey != null && Kuro.menuKey.matchesKey(keyCode, scanCode)) {
         close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   private void setTab(int t) {
      if (t == tab) {
         return;
      }

      tab = t;
      scrollTarget = 0.0F;
      scroll.snap(0.0F);
      tabFade.snap(0.0F);
   }

   private void applySlider(Hit h, float mx) {
      Slider s = SLIDERS[h.a][h.b];
      float t = Anim.clamp((mx - h.x) / h.w, 0.0F, 1.0F);
      float v = s.min + t * (s.max - s.min);
      v = Math.round(v / s.step) * s.step;
      s.set.accept(Anim.clamp(v, s.min, s.max));
   }

   private static boolean getSetting(int k) {
      return switch (k) {
         case 0 -> Config.watermark;
         case 1 -> Config.moduleList;
         case 2 -> Config.keybinds;
         case 3 -> Config.info;
         case 4 -> Config.toasts;
         case 5 -> Config.columnMenu;
         default -> Config.hudGlass;
      };
   }

   private static void setSetting(int k, boolean v) {
      switch (k) {
         case 0 -> Config.watermark = v;
         case 1 -> Config.moduleList = v;
         case 2 -> Config.keybinds = v;
         case 3 -> Config.info = v;
         case 4 -> Config.toasts = v;
         case 5 -> Config.columnMenu = v;
         default -> Config.hudGlass = v;
      }
   }

   // ---------------------------------------------------------------- çizim

   private void draw(float lw, float lh, float dt) {
      hits.clear();
      float e = Anim.easeOutCubic(prog);
      int accent = Theme.accent();
      int accent2 = Theme.accent2();

      // arka plan karartması
      Nvg.rect(0.0F, 0.0F, lw, lh, 0.0F, Theme.alpha(0x000000, 0.52F * e));

      nvgSave(Nvg.ctx);
      nvgTranslate(Nvg.ctx, winX + W * 0.5F, winY + H * 0.5F + (1.0F - e) * 14.0F);
      float sc = 0.955F + 0.045F * e;
      nvgScale(Nvg.ctx, sc, sc);
      nvgTranslate(Nvg.ctx, -W * 0.5F, -H * 0.5F);
      nvgGlobalAlpha(Nvg.ctx, e);

      // pencere
      Nvg.glow(0.0F, 8.0F, W, H, 12.0F, 46.0F, 0xB0000000);
      Nvg.rect(0.0F, 0.0F, W, H, 12.0F, Theme.BG);
      Nvg.rectVar(0.0F, 0.0F, SIDE, H, 12.0F, 0.0F, 0.0F, 12.0F, Theme.BG_SIDE);
      Nvg.line(SIDE, 0.0F, SIDE, H, 1.0F, 0x14FFFFFF);
      // ince vurgu çizgisi (üst kenar)
      Nvg.rectGradH(14.0F, 0.0F, W - 28.0F, 2.0F, 1.0F, Theme.a(accent, 0.0F), accent);
      Nvg.rectGradH(W * 0.5F, 0.0F, W * 0.5F - 14.0F, 2.0F, 1.0F, accent, Theme.a(accent2, 0.0F));
      Nvg.border(0.0F, 0.0F, W, H, 12.0F, 1.0F, 0x1AFFFFFF);

      drawSidebar(dt);
      drawHeader();
      drawContent(dt);

      nvgRestore(Nvg.ctx);
   }

   private void drawSidebar(float dt) {
      int accent = Theme.accent();
      float mx = localX();
      float my = localY();

      // logo
      Nvg.textL("kuro", 22.0F, 36.0F, 26.0F, true, accent);
      Nvg.textL("v1.3.0  ·  1.21.4", 23.0F, 56.0F, 10.5F, false, Theme.TEXT_MUTED);

      // aktif sekme göstergesi (kayar)
      float y0 = 86.0F;
      float rowH = 40.0F;
      tabInd.to(tab).tick(dt, 14.0F);
      float iy = y0 + tabInd.v * rowH;
      Nvg.rect(12.0F, iy, SIDE - 24.0F, 34.0F, 8.0F, Theme.a(accent, 0.13F));
      Nvg.border(12.0F, iy, SIDE - 24.0F, 34.0F, 8.0F, 1.0F, Theme.a(accent, 0.30F));
      Nvg.rect(12.0F, iy + 8.0F, 3.0F, 18.0F, 1.5F, accent);

      for (int i = 0; i < TABS.length; i++) {
         float ry = y0 + i * rowH;
         boolean hov = mx >= 12.0F && mx <= SIDE - 12.0F && my >= ry && my <= ry + 34.0F;
         tabHover[i].to(hov ? 1.0F : 0.0F).tick(dt, 16.0F);
         float sel = Anim.clamp(1.0F - Math.abs(tabInd.v - i), 0.0F, 1.0F);
         int txt = Theme.mix(Theme.mix(Theme.TEXT_DIM, 0xFFCFCFD8, tabHover[i].v), Theme.TEXT, sel);
         int ic = Theme.mix(txt, accent, sel);
         icon(i, 34.0F, ry + 17.0F, ic);
         Nvg.textL(TABS[i], 54.0F, ry + 17.5F, 13.5F, sel > 0.5F, txt);
         hits.add(new Hit(T_TAB, i, 0, 12.0F, ry, SIDE - 24.0F, 34.0F));
      }

      // alt: oyuncu + menü tuşu
      float by = H - 56.0F;
      Nvg.line(16.0F, by - 8.0F, SIDE - 16.0F, by - 8.0F, 1.0F, 0x12FFFFFF);
      String user = Kuro.mc.getSession().getUsername();
      Nvg.circle(32.0F, by + 18.0F, 13.0F, Theme.a(accent, 0.2F));
      Nvg.circleStroke(32.0F, by + 18.0F, 13.0F, 1.0F, Theme.a(accent, 0.6F));
      Nvg.textC(user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase(), 32.0F, by + 18.5F, 12.5F, true, accent);
      Nvg.textL(user, 52.0F, by + 12.0F, 12.5F, true, Theme.TEXT);
      String mk = Kuro.menuKey == null ? "-" : Keys.name(menuKeyCode());
      Nvg.textL("Menü  [" + mk + "]", 52.0F, by + 27.0F, 10.5F, false, Theme.TEXT_MUTED);
   }

   private static int menuKeyCode() {
      String key = Kuro.menuKey.getBoundKeyTranslationKey();
      return net.minecraft.client.util.InputUtil.fromTranslationKey(key).getCode();
   }

   private void drawHeader() {
      Nvg.textL(TABS[tab], SIDE + PAD, 30.0F, 21.0F, true, Theme.TEXT);
      Nvg.textL(TAB_SUB[tab], SIDE + PAD, 53.0F, 11.5F, false, Theme.TEXT_DIM);
      Nvg.textR("Sağ tık: ayar aç  ·  Esc: kapat", W - PAD, 30.0F, 10.5F, false, Theme.TEXT_MUTED);
      Nvg.line(SIDE + PAD, HEAD - 2.0F, W - PAD, HEAD - 2.0F, 1.0F, 0x12FFFFFF);
   }

   private void drawContent(float dt) {
      float ca = Anim.easeOutCubic(prog);
      tabFade.to(1.0F).tick(dt, 14.0F);
      float fade = Anim.easeOutCubic(tabFade.v);

      float viewH = H - HEAD - PAD;
      float max = Math.max(0.0F, contentH - viewH);
      scrollTarget = Anim.clamp(scrollTarget, 0.0F, max);
      scroll.to(scrollTarget).tick(dt, 18.0F);

      nvgSave(Nvg.ctx);
      nvgIntersectScissor(Nvg.ctx, SIDE, HEAD, W - SIDE, H - HEAD - 4.0F);
      nvgGlobalAlpha(Nvg.ctx, ca * fade);

      float x = SIDE + PAD;
      float w = W - SIDE - PAD * 2.0F;
      float y = HEAD + 8.0F - scroll.v + (1.0F - fade) * 10.0F;
      float startY = y;

      if (tab == SETTINGS_TAB) {
         y = drawSettings(x, y, w, dt, ca * fade);
      } else {
         for (int id : TAB_MODS[tab]) {
            y += drawModuleCard(id, x, y, w, dt, ca * fade) + 10.0F;
         }
      }

      contentH = y - startY + 8.0F;

      // kaydırma çubuğu
      if (max > 0.0F) {
         float trackH = H - HEAD - 16.0F;
         float thumbH = Math.max(28.0F, trackH * viewH / contentH);
         float ty = HEAD + 8.0F + (trackH - thumbH) * (scroll.v / max);
         Nvg.rect(W - 8.0F, ty, 3.0F, thumbH, 1.5F, 0x30FFFFFF);
      }

      nvgRestore(Nvg.ctx);
   }

   // ---------------------------------------------------------------- modül kartı

   private float drawModuleCard(int id, float x, float y, float w, float dt, float alpha) {
      float mx = localX();
      float my = localY();
      boolean action = Kuro.isAction(id);
      Slider[] sl = SLIDERS[id];
      int accent = Theme.accent();

      modOn[id].to(Kuro.on[id] ? 1.0F : 0.0F).tick(dt, 14.0F);
      modExp[id].tick(dt, 14.0F);
      float on = action ? 0.0F : modOn[id].v;
      float exp = modExp[id].v;
      float extra = sl == null ? 0.0F : exp * (rowsH(sl) + (hasCustom(id) ? customH(id) : 0.0F) + 6.0F);
      float total = CARD_H + extra;

      boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + CARD_H && my >= HEAD && my <= H;
      modHover[id].to(hov ? 1.0F : 0.0F).tick(dt, 16.0F);
      float hv = modHover[id].v;

      // kart
      Nvg.rect(x, y, w, total, 9.0F, Theme.alpha(0xFFFFFF, 0.035F + 0.028F * hv));
      Nvg.border(x, y, w, total, 9.0F, 1.0F, Theme.mix(0x0DFFFFFF, Theme.a(accent, 0.38F), on));
      if (on > 0.01F) {
         float bh = 26.0F * on;
         Nvg.glow(x, y + (CARD_H - bh) * 0.5F, 3.0F, bh, 1.5F, 10.0F, Theme.a(accent, 0.45F * on));
         Nvg.rect(x, y + (CARD_H - bh) * 0.5F, 3.0F, bh, 1.5F, accent);
      }

      hits.add(new Hit(T_CARD, id, 0, x, y, w, CARD_H));

      // sağdan sola: anahtar / buton, tuş rozeti, ok
      float rx = x + w - 16.0F;
      if (action) {
         float bw = 92.0F;
         float bx = rx - bw;
         Nvg.rectGradH(bx, y + 14.0F, bw, 26.0F, 6.0F, accent, Theme.accent2());
         Nvg.textC("Çalıştır", bx + bw * 0.5F, y + 27.5F, 12.0F, true, 0xFF0B0B0F);
         hits.add(new Hit(T_ACTION, id, 0, bx, y + 14.0F, bw, 26.0F));
         rx = bx - 10.0F;
      } else {
         switchControl(rx - 34.0F, y + (CARD_H - 18.0F) * 0.5F, on);
         rx -= 34.0F + 12.0F;
      }

      // tuş rozeti
      boolean lis = listening == id;
      String label = lis ? "..." : Keys.name(Kuro.binds[id]);
      float cw = Math.max(36.0F, Nvg.textWidth(label, 11.5F, true) + 18.0F);
      float cx = rx - cw;
      float cy = y + (CARD_H - 22.0F) * 0.5F;
      float pulse = 0.5F + 0.5F * (float)Math.sin(time * 7.0F);
      Nvg.rect(cx, cy, cw, 22.0F, 6.0F, lis ? Theme.a(accent, 0.16F) : 0x14FFFFFF);
      Nvg.border(cx, cy, cw, 22.0F, 6.0F, 1.0F, lis ? Theme.a(accent, 0.45F + 0.4F * pulse) : 0x14FFFFFF);
      Nvg.textC(label, cx + cw * 0.5F, cy + 11.5F, 11.5F, true, lis ? accent : Theme.TEXT_DIM);
      hits.add(new Hit(T_CHIP, id, 0, cx, cy, cw, 22.0F));
      rx = cx - 10.0F;

      // genişletme oku
      if (sl != null) {
         float ax = rx - 20.0F;
         float ay = y + CARD_H * 0.5F;
         nvgSave(Nvg.ctx);
         nvgTranslate(Nvg.ctx, ax + 10.0F, ay);
         nvgRotate(Nvg.ctx, (float)Math.toRadians(-90.0F + 90.0F * exp));
         nvgBeginPath(Nvg.ctx);
         nvgMoveTo(Nvg.ctx, -3.5F, -2.0F);
         nvgLineTo(Nvg.ctx, 0.0F, 2.0F);
         nvgLineTo(Nvg.ctx, 3.5F, -2.0F);
         nvgLineCap(Nvg.ctx, NVG_ROUND);
         nvgLineJoin(Nvg.ctx, NVG_ROUND);
         Nvg.stroke(1.6F, Theme.mix(Theme.TEXT_MUTED, Theme.TEXT, Math.max(hv, exp)));
         nvgRestore(Nvg.ctx);
         hits.add(new Hit(T_ARROW, id, 0, ax - 4.0F, y + 8.0F, 28.0F, CARD_H - 16.0F));
         rx = ax - 6.0F;
      }

      // ad + açıklama (sağdaki kontrollerin soluna sığdır)
      nvgSave(Nvg.ctx);
      nvgIntersectScissor(Nvg.ctx, x + 14.0F, y, Math.max(0.0F, rx - x - 18.0F), CARD_H);
      Nvg.textL(Kuro.NAMES[id], x + 18.0F, y + 21.0F, 14.0F, true, Theme.mix(0xFFB4B4C0, Theme.TEXT, Math.max(on, hv * 0.6F)));
      Nvg.textL(Kuro.DESCS[id], x + 18.0F, y + 38.5F, 11.0F, false, Theme.TEXT_MUTED);
      nvgRestore(Nvg.ctx);

      // sürgüler
      if (sl != null && exp > 0.01F) {
         nvgSave(Nvg.ctx);
         nvgIntersectScissor(Nvg.ctx, x, y + CARD_H, w, extra);
         nvgGlobalAlpha(Nvg.ctx, alpha * exp);
         Nvg.line(x + 16.0F, y + CARD_H - 1.0F, x + w - 16.0F, y + CARD_H - 1.0F, 1.0F, 0x10FFFFFF);
         float ry = y + CARD_H;
         for (int k = 0; k < sl.length; k++) {
            sliderRow(id, k, sl[k], x, ry, w);
            ry += rowH(sl[k]);
         }

         if (hasCustom(id)) {
            customSection(id, x, ry, w);
         }

         nvgRestore(Nvg.ctx);
      }

      return total;
   }

   private static boolean hasCustom(int id) {
      return id == Kuro.BLOCK || id == Kuro.XRAY;
   }

   private static java.util.List<String> customList(int id) {
      return id == Kuro.XRAY ? XraySel.custom() : BlockSel.custom();
   }

   private static float customH(int id) {
      return CUSTOM_H + customList(id).size() * CUSTOM_H;
   }

   /** Menuden blok ekleme: yazi kutusu + Ekle butonu + eklenen blok listesi. */
   private void customSection(int id, float x, float y, float w) {
      int accent = Theme.accent();
      boolean foc = inputFocus && inputFor == id;
      float bw = 56.0F;
      float fx = x + 18.0F;
      float fw = w - 36.0F - bw - 8.0F;
      float fy = y + 3.0F;
      float fh = CUSTOM_H - 6.0F;
      Nvg.rect(fx, fy, fw, fh, 6.0F, foc ? Theme.a(accent, 0.12F) : 0x14FFFFFF);
      Nvg.border(fx, fy, fw, fh, 6.0F, 1.0F, foc ? Theme.a(accent, 0.6F) : 0x1AFFFFFF);
      boolean caret = foc && ((int)(time * 2.0F) & 1) == 0;
      String txt = inputFor == id ? input : "";
      String shown = txt.isEmpty() && !foc ? (inputMsg.isEmpty() || inputFor != id ? "Blok ekle (örn: bookshelf)" : inputMsg) : txt + (caret ? "|" : "");
      nvgSave(Nvg.ctx);
      nvgIntersectScissor(Nvg.ctx, fx + 4.0F, fy, fw - 8.0F, fh);
      Nvg.textL(shown, fx + 10.0F, fy + fh * 0.5F, 12.0F, false, txt.isEmpty() && !foc ? Theme.TEXT_MUTED : Theme.TEXT);
      nvgRestore(Nvg.ctx);
      hits.add(new Hit(T_INPUT, id, 0, fx, fy, fw, fh));

      float bx = fx + fw + 8.0F;
      Nvg.rectGradH(bx, fy, bw, fh, 6.0F, accent, Theme.accent2());
      Nvg.textC("Ekle", bx + bw * 0.5F, fy + fh * 0.5F, 12.0F, true, 0xFF0B0B0F);
      hits.add(new Hit(T_ADD, id, 0, bx, fy, bw, fh));

      float ry = y + CUSTOM_H;
      java.util.List<String> l = customList(id);
      for (int i = 0; i < l.size(); i++) {
         Nvg.textL(l.get(i), x + 18.0F, ry + CUSTOM_H * 0.5F, 12.0F, false, Theme.TEXT);
         Nvg.textR("Kaldır", x + w - 18.0F, ry + CUSTOM_H * 0.5F, 11.0F, true, accent);
         hits.add(new Hit(T_CUSTOM, i, id, x + w - 78.0F, ry, 70.0F, CUSTOM_H));
         ry += CUSTOM_H;
      }
   }

   private void sliderRow(int id, int k, Slider s, float x, float y, float w) {
      int accent = Theme.accent();
      float v = (float)s.get.getAsDouble();
      if (isToggle(s)) {
         boolean on = v >= 0.5F;
         Nvg.textL(s.name, x + 18.0F, y + TOGGLE_H * 0.5F, 12.0F, false, on ? Theme.TEXT : Theme.TEXT_DIM);
         switchControl(x + w - 18.0F - 34.0F, y + (TOGGLE_H - 18.0F) * 0.5F, on ? 1.0F : 0.0F);
         hits.add(new Hit(T_SLIDER, id, k, x + 8.0F, y, w - 16.0F, TOGGLE_H));
         return;
      }

      float t = Anim.clamp((v - s.min) / (s.max - s.min), 0.0F, 1.0F);
      float tx = x + 18.0F;
      float tw = w - 36.0F;
      float ty = y + 26.0F;
      boolean dragging = drag != null && drag.type == T_SLIDER && drag.a == id && drag.b == k;
      boolean hov = dragging || (localX() >= tx - 6.0F && localX() <= tx + tw + 6.0F && localY() >= ty - 9.0F && localY() <= ty + 9.0F);

      Nvg.textL(s.name, tx, y + 12.0F, 12.0F, false, Theme.TEXT_DIM);
      if (s.name.startsWith("Renk tonu")) {
         Nvg.circle(tx + tw - 46.0F, y + 12.0F, 5.5F, 0xFF000000 | Config.hsb(v, Config.peSat / 100.0F, 1.0F));
      }

      Nvg.textR(s.step >= 1.0F ? Integer.toString(Math.round(v)) : Float.toString(Math.round(v * 100.0F) / 100.0F), tx + tw, y + 12.0F, 12.0F, true, Theme.TEXT);
      Nvg.rect(tx, ty - 2.0F, tw, 4.0F, 2.0F, 0x1AFFFFFF);
      if (t > 0.0F) {
         Nvg.rectGradH(tx, ty - 2.0F, Math.max(4.0F, tw * t), 4.0F, 2.0F, accent, Theme.accent2());
      }

      float kx = tx + tw * t;
      if (hov) {
         Nvg.glow(kx - 6.0F, ty - 6.0F, 12.0F, 12.0F, 6.0F, 10.0F, Theme.a(accent, 0.55F));
      }

      Nvg.circle(kx, ty, hov ? 6.5F : 5.5F, 0xFFFFFFFF);
      hits.add(new Hit(T_SLIDER, id, k, tx, ty - 10.0F, tw, 20.0F));
   }

   // ---------------------------------------------------------------- ayarlar sekmesi

   private float drawSettings(float x, float y, float w, float dt, float alpha) {
      float mx = localX();
      float my = localY();
      int accent = Theme.accent();

      Nvg.textL("VURGU RENGİ", x + 2.0F, y + 8.0F, 10.5F, true, Theme.TEXT_MUTED);
      y += 22.0F;

      float ch = 64.0F;
      Nvg.rect(x, y, w, ch, 9.0F, 0x09FFFFFF);
      Nvg.border(x, y, w, ch, 9.0F, 1.0F, 0x0DFFFFFF);
      for (int i = 0; i < Theme.ACCENTS.length; i++) {
         float cx = x + 30.0F + i * 40.0F;
         float cy = y + ch * 0.5F;
         int c = 0xFF000000 | Theme.ACCENTS[i];
         boolean hov = Math.hypot(mx - cx, my - cy) <= 14.0F && my >= HEAD;
         boolean sel = Config.accent == i;
         if (sel) {
            Nvg.glow(cx - 11.0F, cy - 11.0F, 22.0F, 22.0F, 11.0F, 12.0F, Theme.a(c, 0.5F));
            Nvg.circleStroke(cx, cy, 16.0F, 2.0F, c);
         }

         Nvg.circle(cx, cy, hov || sel ? 11.5F : 10.5F, c);
         hits.add(new Hit(T_SWATCH, i, 0, cx - 15.0F, cy - 15.0F, 30.0F, 30.0F));
      }

      Nvg.textR(Theme.ACCENT_NAMES[Config.accent], x + w - 16.0F, y + ch * 0.5F + 0.5F, 12.5F, true, accent);
      y += ch + 18.0F;

      Nvg.textL("HUD", x + 2.0F, y + 8.0F, 10.5F, true, Theme.TEXT_MUTED);
      y += 22.0F;

      for (int i = 0; i < SETTING_NAMES.length; i++) {
         float rh = 50.0F;
         boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + rh && my >= HEAD;
         setHover[i].to(hov ? 1.0F : 0.0F).tick(dt, 16.0F);
         setOn[i].to(getSetting(i) ? 1.0F : 0.0F).tick(dt, 14.0F);

         Nvg.rect(x, y, w, rh, 9.0F, Theme.alpha(0xFFFFFF, 0.035F + 0.028F * setHover[i].v));
         Nvg.border(x, y, w, rh, 9.0F, 1.0F, Theme.mix(0x0DFFFFFF, Theme.a(accent, 0.38F), setOn[i].v));
         Nvg.textL(SETTING_NAMES[i], x + 18.0F, y + 19.0F, 13.5F, true, Theme.mix(0xFFB4B4C0, Theme.TEXT, Math.max(setOn[i].v, setHover[i].v * 0.6F)));
         Nvg.textL(SETTING_DESCS[i], x + 18.0F, y + 35.5F, 11.0F, false, Theme.TEXT_MUTED);
         switchControl(x + w - 16.0F - 34.0F, y + (rh - 18.0F) * 0.5F, setOn[i].v);
         hits.add(new Hit(T_SETTING, i, 0, x, y, w, rh));
         y += rh + 8.0F;
      }

      return y;
   }

   // ---------------------------------------------------------------- bileşenler

   private static void switchControl(float x, float y, float t) {
      int accent = Theme.accent();
      float e = Anim.easeOutCubic(t);
      int track = Theme.mix(0xFF26262F, accent, e);
      if (e > 0.01F) {
         Nvg.glow(x, y, 34.0F, 18.0F, 9.0F, 10.0F, Theme.a(accent, 0.35F * e));
      }

      Nvg.rect(x, y, 34.0F, 18.0F, 9.0F, track);
      float kx = x + 9.0F + 16.0F * t;
      Nvg.circle(kx, y + 9.0F, 6.5F, Theme.mix(0xFFB9B9C6, 0xFFFFFFFF, e));
   }

   /** Sekme simgeleri (vektör). */
   private static void icon(int i, float cx, float cy, int color) {
      long c = Nvg.ctx;
      nvgSave(c);
      nvgTranslate(c, cx, cy);
      nvgLineCap(c, NVG_ROUND);
      nvgLineJoin(c, NVG_ROUND);
      nvgBeginPath(c);
      switch (i) {
         case 0 -> { // nişangah
            nvgCircle(c, 0.0F, 0.0F, 5.5F);
            nvgMoveTo(c, 0.0F, -9.0F);
            nvgLineTo(c, 0.0F, -3.5F);
            nvgMoveTo(c, 0.0F, 9.0F);
            nvgLineTo(c, 0.0F, 3.5F);
            nvgMoveTo(c, -9.0F, 0.0F);
            nvgLineTo(c, -3.5F, 0.0F);
            nvgMoveTo(c, 9.0F, 0.0F);
            nvgLineTo(c, 3.5F, 0.0F);
            Nvg.stroke(1.6F, color);
         }
         case 1 -> { // ok
            nvgMoveTo(c, -7.0F, 5.5F);
            nvgLineTo(c, 8.0F, 0.0F);
            nvgLineTo(c, -7.0F, -5.5F);
            nvgLineTo(c, -4.0F, 0.0F);
            nvgClosePath(c);
            Nvg.stroke(1.6F, color);
         }
         case 2 -> { // göz
            nvgMoveTo(c, -9.0F, 0.0F);
            nvgBezierTo(c, -4.5F, -7.0F, 4.5F, -7.0F, 9.0F, 0.0F);
            nvgBezierTo(c, 4.5F, 7.0F, -4.5F, 7.0F, -9.0F, 0.0F);
            Nvg.stroke(1.6F, color);
            nvgBeginPath(c);
            nvgCircle(c, 0.0F, 0.0F, 2.6F);
            Nvg.stroke(1.6F, color);
         }
         case 3 -> { // kamera
            nvgRoundedRect(c, -8.5F, -4.5F, 17.0F, 11.5F, 2.5F);
            nvgMoveTo(c, -3.0F, -4.5F);
            nvgLineTo(c, -1.8F, -7.0F);
            nvgLineTo(c, 1.8F, -7.0F);
            nvgLineTo(c, 3.0F, -4.5F);
            Nvg.stroke(1.6F, color);
            nvgBeginPath(c);
            nvgCircle(c, 0.0F, 1.2F, 3.0F);
            Nvg.stroke(1.6F, color);
         }
         case 4 -> { // ızgara
            nvgRoundedRect(c, -7.5F, -7.5F, 6.0F, 6.0F, 1.6F);
            nvgRoundedRect(c, 1.5F, -7.5F, 6.0F, 6.0F, 1.6F);
            nvgRoundedRect(c, -7.5F, 1.5F, 6.0F, 6.0F, 1.6F);
            nvgRoundedRect(c, 1.5F, 1.5F, 6.0F, 6.0F, 1.6F);
            Nvg.stroke(1.6F, color);
         }
         case 5 -> { // elmas (Donut)
            nvgMoveTo(c, 0.0F, -8.0F);
            nvgLineTo(c, 8.0F, -1.0F);
            nvgLineTo(c, 0.0F, 8.0F);
            nvgLineTo(c, -8.0F, -1.0F);
            nvgClosePath(c);
            nvgMoveTo(c, -8.0F, -1.0F);
            nvgLineTo(c, 8.0F, -1.0F);
            Nvg.stroke(1.6F, color);
         }
         default -> { // sürgüler
            nvgMoveTo(c, -8.0F, -5.0F);
            nvgLineTo(c, 8.0F, -5.0F);
            nvgMoveTo(c, -8.0F, 0.0F);
            nvgLineTo(c, 8.0F, 0.0F);
            nvgMoveTo(c, -8.0F, 5.0F);
            nvgLineTo(c, 8.0F, 5.0F);
            Nvg.stroke(1.6F, color);
            Nvg.circle(-3.0F, -5.0F, 2.4F, 0xFF0B0B0F);
            Nvg.circle(4.0F, 0.0F, 2.4F, 0xFF0B0B0F);
            Nvg.circle(-1.0F, 5.0F, 2.4F, 0xFF0B0B0F);
            Nvg.circleStroke(-3.0F, -5.0F, 2.4F, 1.5F, color);
            Nvg.circleStroke(4.0F, 0.0F, 2.4F, 1.5F, color);
            Nvg.circleStroke(-1.0F, 5.0F, 2.4F, 1.5F, color);
         }
      }

      nvgRestore(c);
   }
}
