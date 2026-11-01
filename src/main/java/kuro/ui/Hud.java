package kuro.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kuro.Config;
import kuro.Freecam;
import kuro.Freelook;
import kuro.Kuro;
import net.minecraft.client.util.Window;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Oyun ici HUD (NanoVG): filigran, modul listesi, kisayol paneli, bilgi cubugu,
 * kamera modu gostergesi ve bildirimler.
 */
public final class Hud {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
   private static final String[] DIRS = new String[]{"Güney +Z", "Batı -X", "Kuzey -Z", "Doğu +X"}; // yaw: 0=Güney 90=Batı 180=Kuzey 270=Doğu

   private static long last = System.nanoTime();
   private static final Anim[] LIST_A = new Anim[Kuro.NAMES.length];
   private static final Anim[] KB_A = new Anim[Kuro.NAMES.length];
   private static final Anim WM_IN = new Anim(0.0F);
   private static final Anim CAM_A = new Anim(0.0F);
   private static float camLabelAge;

   private static double lastX;
   private static double lastZ;
   private static boolean haveLast;
   private static float speed;
   private static int slowTick;
   private static String fpsStr = "0 fps";
   private static String pingStr = "";
   private static String timeStr = "";
   private static String xyzStr = "";
   private static String dirStr = "";
   private static String speedStr = "";
   private static final List<String> SEGS = new ArrayList<>();
   private static final Integer[] ORDER = new Integer[Kuro.NAMES.length];
   private static final float[] WIDTHS = new float[Kuro.NAMES.length];

   static {
      for (int i = 0; i < LIST_A.length; i++) {
         LIST_A[i] = new Anim(0.0F);
         KB_A[i] = new Anim(0.0F);
      }
   }

   private Hud() {
   }

   /** Her client tick'inde: yatay hizi olc (blok/sn). */
   public static void tick() {
      MinecraftClient mc = Kuro.mc;
      if (mc.player == null) {
         haveLast = false;
         return;
      }

      double x = mc.player.getX();
      double z = mc.player.getZ();
      if (haveLast) {
         float v = (float)(Math.hypot(x - lastX, z - lastZ) * 20.0);
         speed += (v - speed) * 0.25F;
      }

      lastX = x;
      lastZ = z;
      haveLast = true;

      // metinleri kare basina degil tick basina uret (string/format allocation'i azalir)
      double py = mc.player.getY();
      int dir = ((int)Math.floor(mc.player.getYaw() * 4.0F / 360.0F + 0.5F)) & 3;
      xyzStr = (int)Math.floor(x) + "  " + (int)Math.floor(py) + "  " + (int)Math.floor(z);
      dirStr = DIRS[dir];
      speedStr = ((int)(speed * 10.0F) / 10.0F) + " b/s";
      if (slowTick++ % 10 == 0) {
         fpsStr = mc.getCurrentFps() + " fps";
         int ping = ping(mc);
         pingStr = ping > 0 ? ping + " ms" : "";
         timeStr = LocalTime.now().format(TIME);
      }
   }

   public static void render(DrawContext dc, RenderTickCounter tickCounter) {
      MinecraftClient mc = Kuro.mc;
      if (mc.player == null || mc.world == null || mc.options.hudHidden) {
         return;
      }

      if (!Nvg.ensure()) {
         return;
      }

      Window win = mc.getWindow();
      int fw = win.getFramebufferWidth();
      int fh = win.getFramebufferHeight();
      if (fw <= 0 || fh <= 0) {
         return;
      }

      if (!needsDraw()) {
         last = System.nanoTime();
         return;
      }

      // Minecraft'in bekleyen cizimlerini bitir, sonra NanoVG ile ustune ciz
      dc.draw();

      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - last) / 1.0E9F);
      last = now;

      float s = Theme.scale(fh);
      float lw = fw / s;
      float lh = fh / s;
      Theme.update(dt);

      Nvg.begin(lw, lh, s);
      try {
         if (Config.watermark) {
            watermark(mc, lw, dt);
         } else {
            WM_IN.snap(0.0F);
         }

         if (Config.keybinds) {
            keybinds(dt);
         }

         if (Config.moduleList) {
            moduleList(lw, dt);
         }

         cameraBadge(lw, dt);

         if (Config.info) {
            infoBar(mc, lw, lh);
         }

         if (Kuro.on[Kuro.TARGETHUD]) {
            targetHud(mc, lw, lh, dt);
         }

         Toasts.render(lw, lh, dt);
      } finally {
         Nvg.end();
      }
   }

   // ------------------------------------------------------------------ TargetHUD
   private static net.minecraft.entity.player.PlayerEntity thTarget;
   private static float thTime;
   private static final Anim TH_IN = new Anim(0.0F);
   private static final Anim TH_HP = new Anim(1.0F);

   private static void targetHud(MinecraftClient mc, float lw, float lh, float dt) {
      net.minecraft.entity.player.PlayerEntity t = null;
      if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr
         && ehr.getEntity() instanceof net.minecraft.entity.player.PlayerEntity pe
         && pe.isAlive()) {
         t = pe;
         thTarget = pe;
         thTime = 3.0F;
      } else if (thTarget != null && thTime > 0.0F) {
         thTime -= dt;
         if (thTarget.isAlive() && thTarget.squaredDistanceTo(mc.player) < 400.0) {
            t = thTarget;
         }
      }

      TH_IN.to(t != null ? 1.0F : 0.0F).tick(dt, 12.0F);
      if (TH_IN.v <= 0.01F || thTarget == null) {
         return;
      }

      net.minecraft.entity.player.PlayerEntity d = thTarget;
      float hp = d.getHealth() + d.getAbsorptionAmount();
      float ratio = Anim.clamp(hp / Math.max(1.0F, d.getMaxHealth()), 0.0F, 1.0F);
      TH_HP.to(ratio).tick(dt, 10.0F);
      float w = 160.0F;
      float h = 44.0F;
      float x = lw * 0.5F + 30.0F;
      float y = lh * 0.5F + 30.0F;
      float e = Anim.easeOutCubic(TH_IN.v);

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      nvgTranslate(Nvg.ctx, 0.0F, (1.0F - e) * 8.0F);
      Nvg.glow(x, y + 2.0F, w, h, 5.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 5.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 5.0F, 1.0F, 0x12FFFFFF);
      Nvg.rectGradH(x + 4.0F, y, w - 8.0F, 2.0F, 1.0F, Theme.accent(), Theme.accent2());
      Nvg.textL(d.getName().getString(), x + 10.0F, y + 15.0F, 13.0F, true, Theme.TEXT);
      Nvg.textR(Float.toString(Math.round(hp * 10.0F) / 10.0F), x + w - 10.0F, y + 15.0F, 12.0F, false, Theme.TEXT_DIM);
      Nvg.rect(x + 10.0F, y + 28.0F, w - 20.0F, 6.0F, 3.0F, 0xFF26262B);
      Nvg.rect(x + 10.0F, y + 28.0F, Math.max(4.0F, (w - 20.0F) * TH_HP.v), 6.0F, 3.0F, Theme.mix(Theme.RED, Theme.GREEN, TH_HP.v));
      nvgRestore(Nvg.ctx);
   }

   /** Cizilecek hicbir sey yoksa NanoVG karesi (GL durumu kaydet/geri yaz) hic acilmaz. */
   private static boolean needsDraw() {
      if (Config.watermark || Config.info || Toasts.active()) {
         return true;
      }

      for (int i = 0; i < Kuro.NAMES.length; i++) {
         if ((Kuro.on[i] && !Kuro.isAction(i)) || LIST_A[i].v > 0.0F || KB_A[i].v > 0.0F) {
            return true;
         }
      }

      return false;
   }

   // ------------------------------------------------------------------ filigran

   private static void watermark(MinecraftClient mc, float lw, float dt) {
      WM_IN.to(1.0F).tick(dt, 6.0F);
      float e = Anim.easeOutCubic(WM_IN.v);

      List<String> segs = SEGS;
      segs.clear();
      segs.add(mc.getSession().getUsername());
      segs.add(fpsStr);
      if (!pingStr.isEmpty()) {
         segs.add(pingStr);
      }

      segs.add(timeStr);

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      nvgTranslate(Nvg.ctx, 0.0F, -10.0F * (1.0F - e));
      bar(lw, 12.0F, 12.0F, false, "kuro", segs);
      nvgRestore(Nvg.ctx);
   }

   private static int ping(MinecraftClient mc) {
      if (mc.getNetworkHandler() == null) {
         return 0;
      }

      UUID id = mc.player.getUuid();
      PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(id);
      return entry == null ? 0 : entry.getLatency();
   }

   /** Ust vurgu cizgili, ince ayiricili yatay bilgi cubugu. */
   private static void bar(float lw, float x, float y, boolean right, String brand, List<String> segs) {
      float h = 26.0F;
      float pad = 10.0F;
      float brandW = Nvg.textWidth(brand, 14.0F, true);
      float w = pad + brandW;
      for (String s : segs) {
         w += 20.0F + Nvg.textWidth(s, 12.5F, false);
      }

      w += pad;
      if (right) {
         x = lw - x - w;
      }

      int a1 = Theme.accent();
      int a2 = Theme.accent2();
      Nvg.glow(x, y + 2.0F, w, h, 5.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 5.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 5.0F, 1.0F, 0x12FFFFFF);
      Nvg.rectGradH(x + 4.0F, y, w - 8.0F, 2.0F, 1.0F, a1, a2);

      float cx = x + pad;
      float cy = y + h * 0.5F + 1.0F;
      Nvg.textL(brand, cx, cy, 14.0F, true, a1);
      cx += brandW;
      for (String s : segs) {
         Nvg.line(cx + 10.0F, y + 8.0F, cx + 10.0F, y + h - 6.0F, 1.0F, 0x1FFFFFFF);
         cx += 20.0F;
         cx += Nvg.textL(s, cx, cy, 12.5F, false, Theme.TEXT);
      }
   }

   // ------------------------------------------------------------------ kisayol paneli

   private static void keybinds(float dt) {
      int n = Kuro.NAMES.length;
      float total = 0.0F;
      float maxA = 0.0F;
      float w = 158.0F;
      for (int i = 0; i < n; i++) {
         boolean show = Kuro.on[i] && !Kuro.isAction(i);
         KB_A[i].to(show ? 1.0F : 0.0F).tick(dt, 14.0F);
         total += 22.0F * KB_A[i].v;
         maxA = Math.max(maxA, KB_A[i].v);
         if (KB_A[i].v > 0.0F) {
            w = Math.max(w, 40.0F + Nvg.textWidth(Kuro.NAMES[i], 12.5F, false) + 40.0F);
         }
      }

      if (maxA <= 0.0F) {
         return;
      }

      float x = 12.0F;
      float y = 50.0F;
      float headH = 26.0F;
      float h = headH + total + 6.0F * maxA;
      float e = Anim.easeOutCubic(maxA);

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      Nvg.glow(x, y + 2.0F, w, h, 5.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 5.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 5.0F, 1.0F, 0x12FFFFFF);
      Nvg.rectGradH(x + 4.0F, y, w - 8.0F, 2.0F, 1.0F, Theme.accent(), Theme.accent2());
      Nvg.textL("Kısayollar", x + 10.0F, y + headH * 0.5F + 1.0F, 12.5F, true, Theme.TEXT);

      float ry = y + headH;
      for (int i = 0; i < n; i++) {
         float a = KB_A[i].v;
         if (a <= 0.0F) {
            continue;
         }

         float rh = 22.0F * a;
         nvgSave(Nvg.ctx);
         nvgGlobalAlpha(Nvg.ctx, e * a);
         float cy = ry + rh * 0.5F;
         Nvg.circle(x + 14.0F, cy, 2.5F, Theme.accent());
         Nvg.textL(Kuro.NAMES[i], x + 24.0F, cy, 12.5F, false, Theme.TEXT);
         String key = Keys.name(Kuro.binds[i]);
         Nvg.textR("[" + key + "]", x + w - 10.0F, cy, 11.5F, false, Theme.TEXT_DIM);
         nvgRestore(Nvg.ctx);
         ry += rh;
      }

      nvgRestore(Nvg.ctx);
   }

   // ------------------------------------------------------------------ modul listesi

   private static void moduleList(float lw, float dt) {
      int n = Kuro.NAMES.length;
      Integer[] order = ORDER;
      float[] widths = WIDTHS;
      for (int i = 0; i < n; i++) {
         order[i] = i;
         boolean show = Kuro.on[i] && !Kuro.isAction(i);
         LIST_A[i].to(show ? 1.0F : 0.0F).tick(dt, 13.0F);
         widths[i] = Nvg.textWidth(Kuro.NAMES[i], 13.0F, true);
      }

      java.util.Arrays.sort(order, (a, b) -> Float.compare(WIDTHS[b], WIDTHS[a]));

      float y = 12.0F;
      for (int k = 0; k < n; k++) {
         int i = order[k];
         float a = LIST_A[i].v;
         if (a <= 0.0F) {
            continue;
         }

         float e = Anim.easeOutCubic(a);
         float rh = 22.0F;
         float w = widths[i] + 18.0F;
         float x = lw - 12.0F - w + (1.0F - e) * (w + 16.0F);

         nvgSave(Nvg.ctx);
         nvgGlobalAlpha(Nvg.ctx, e);
         if (Config.hudGlass) {
            Theme.glassBox(x, y, w, rh - 2.0F, 6.0F, Theme.accent());
         } else {
            Nvg.rect(x, y, w, rh - 2.0F, 4.0F, Theme.HUD_BG);
            Nvg.rectGradV(x + w - 3.0F, y + 3.0F, 2.0F, rh - 8.0F, 1.0F, Theme.accent(), Theme.accent2());
         }

         Nvg.textL(Kuro.NAMES[i], x + 8.0F, y + (rh - 2.0F) * 0.5F + 0.5F, 13.0F, true, Theme.TEXT);
         nvgRestore(Nvg.ctx);

         y += rh * a;
      }
   }

   // ------------------------------------------------------------------ kamera gostergesi

   private static void cameraBadge(float lw, float dt) {
      boolean fc = Freecam.active;
      boolean fl = Freelook.active;
      CAM_A.to(fc || fl ? 1.0F : 0.0F).tick(dt, 12.0F);
      if (CAM_A.v <= 0.0F) {
         return;
      }

      String label = fc ? "FREECAM" : "FREELOOK";
      String extra = fc ? "hız " + (Math.round(Config.freecamSpeed * 10.0F) / 10.0F) : null;
      float e = Anim.easeOutCubic(CAM_A.v);
      float tw = Nvg.textWidth(label, 12.5F, true);
      float ew = extra == null ? 0.0F : Nvg.textWidth(extra, 11.5F, false) + 14.0F;
      float w = tw + ew + 26.0F;
      float h = 24.0F;
      float x = (lw - w) * 0.5F;
      float y = 14.0F + (1.0F - e) * -12.0F;

      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e);
      Nvg.glow(x, y + 2.0F, w, h, 12.0F, 14.0F, 0x59000000);
      Nvg.rect(x, y, w, h, 12.0F, Theme.HUD_BG);
      Nvg.border(x, y, w, h, 12.0F, 1.0F, Theme.a(Theme.accent(), 0.55F));
      Nvg.circle(x + 12.0F, y + h * 0.5F, 3.0F, Theme.accent());
      Nvg.textL(label, x + 22.0F, y + h * 0.5F + 0.5F, 12.5F, true, Theme.TEXT);
      if (extra != null) {
         Nvg.textR(extra, x + w - 10.0F, y + h * 0.5F + 0.5F, 11.5F, false, Theme.TEXT_DIM);
      }

      nvgRestore(Nvg.ctx);
   }

   // ------------------------------------------------------------------ bilgi cubugu

   private static void infoBar(MinecraftClient mc, float lw, float lh) {
      List<String> segs = SEGS;
      segs.clear();
      segs.add(xyzStr);
      segs.add(dirStr);
      segs.add(speedStr);
      bar(lw, 12.0F, lh - 12.0F - 26.0F, true, "XYZ", segs);
   }
}
