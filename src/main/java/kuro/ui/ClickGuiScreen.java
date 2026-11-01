package kuro.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import java.util.ArrayList;
import java.util.List;
import kuro.Config;
import kuro.Kuro;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Dort sutunlu panel gorunumlu menu (kategori basliklari, alt sekmeler, yuvarlak dugmeler). */
public class ClickGuiScreen extends Screen {
   private static final float PW = 168.0F;
   private static final float GAP = 8.0F;
   private static final float TOP = 26.0F;
   private static final float HEAD = 30.0F;
   private static final float TABS_H = 24.0F;
   private static final float BTN_H = 22.0F;
   private static final float BTN_STEP = 25.0F;
   private static final float TOG_H = 24.0F;
   private static final float SLD_H = 30.0F;

   private static final int PANEL_BG = 0xF2101013;
   private static final int PANEL_BORDER = 0x2EFFFFFF;
   private static final int BTN = 0xFF1B1B1F;
   private static final int BTN_HOVER = 0xFF26262B;
   private static final int BTN_ON = 0xFF3E3E46;
   private static final int ROW_BG = 0xFF151518;

   private record Entry(String name, int mod, MenuScreen.Slider set) {
   }

   private record Group(String name, Entry[] items) {
   }

   private record Panel(String title, Group[] groups) {
   }

   private static Entry m(int id) {
      return new Entry(Kuro.NAMES[id], id, null);
   }

   private static Entry t(String name, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
      return new Entry(name, -1, new MenuScreen.Slider(name, 0.0F, 1.0F, 1.0F, () -> get.getAsBoolean() ? 1.0 : 0.0, v -> set.accept(v >= 0.5)));
   }

   private static final Panel[] PANELS = new Panel[]{
      new Panel("Savaş", new Group[]{
         new Group("Saldırı", new Entry[]{m(Kuro.KILLAURA), m(Kuro.TRIGGERBOT), m(Kuro.REACH), m(Kuro.HITBOXES), m(Kuro.SHIELDBREAKER), m(Kuro.COBWEB), m(Kuro.AUTOCLICKER)}),
         new Group("Kristal", new Entry[]{m(Kuro.CRYSTAL), m(Kuro.CRYSTALBASE), m(Kuro.CRYSTALOPT), m(Kuro.MISPLACE), m(Kuro.CMACRO), m(Kuro.AMACRO)}),
         new Group("Koruma", new Entry[]{m(Kuro.TOTEM), m(Kuro.SAFEANCHOR), m(Kuro.VELOCITY), m(Kuro.JUMPRESET), m(Kuro.AUTOARMOR), m(Kuro.AUTOLOG), m(Kuro.ANTITRAP), m(Kuro.AIMASSIST), m(Kuro.KEYPEARL), m(Kuro.HOVERTOTEM), m(Kuro.DOUBLEHAND)})
      }),
      new Panel("Hareket", new Group[]{
         new Group("Temel", new Entry[]{m(Kuro.FLY), m(Kuro.SPEED), m(Kuro.SPRINT), m(Kuro.NOFALL), m(Kuro.ELYTRASWAP), m(Kuro.STEP)})
      }),
      new Panel("Görsel", new Group[]{
         new Group("ESP", new Entry[]{m(Kuro.PLAYER), m(Kuro.STORAGE), m(Kuro.BLOCK), m(Kuro.XRAY), m(Kuro.CLEARRENDER), m(Kuro.ITEMESP), m(Kuro.NAMETAGS)}),
         new Group("Animasyon", new Entry[]{m(Kuro.VIEWMODEL), m(Kuro.SWINGANIM), m(Kuro.NOHURTCAM)}),
         new Group("Kozmetik", new Entry[]{m(Kuro.FULLBRIGHT), m(Kuro.WORLDTIME), m(Kuro.ZOOM), m(Kuro.TARGETHUD), m(Kuro.NOBOB)})
      }),
      new Panel("Donut", new Group[]{
         new Group("Varlık ESP", new Entry[]{m(Kuro.PILLAGERESP), m(Kuro.VILLAGERESP), m(Kuro.WANDERESP), m(Kuro.DROWNEDESP), m(Kuro.INVISESP)}),
         new Group("Yapı bulucu", new Entry[]{m(Kuro.TUNNELESP), m(Kuro.HOLE1ESP), m(Kuro.VOIDESP), m(Kuro.AMETHYSTESP), m(Kuro.KELPESP), m(Kuro.SPAWNERESP), m(Kuro.STASHESP), m(Kuro.CHUNKESP)}),
         new Group("Blok ESP", new Entry[]{m(Kuro.DEEPSLATEESP), m(Kuro.ROTDEEPESP), m(Kuro.DRIPESP), m(Kuro.VINEESP), m(Kuro.BERRYESP), m(Kuro.BEEESP), m(Kuro.PISTONESP), m(Kuro.LIGHTESP), m(Kuro.COLLECTESP), m(Kuro.SKELETONESP)})
      }),
      new Panel("Araçlar", new Group[]{
         new Group("Dünya", new Entry[]{m(Kuro.FREECAM), m(Kuro.FREELOOK), m(Kuro.BASES), m(Kuro.FASTPLACE), m(Kuro.AUTOTOOL), m(Kuro.CHESTSTEALER), m(Kuro.AUTOEAT), m(Kuro.AUTORESPAWN)}),
         new Group("Test", new Entry[]{m(Kuro.FAKEPLAYER), m(Kuro.MONEYFMT), m(Kuro.FAKEBAN), m(Kuro.FAKESCOREBOARD), m(Kuro.SCHEMBUILDER)}),
         new Group("Ayarlar", new Entry[]{
            t("Filigran", () -> Config.watermark, v -> Config.watermark = v),
            t("Modül listesi", () -> Config.moduleList, v -> Config.moduleList = v),
            t("Kısayol paneli", () -> Config.keybinds, v -> Config.keybinds = v),
            t("Bilgi çubuğu", () -> Config.info, v -> Config.info = v),
            t("Bildirimler", () -> Config.toasts, v -> Config.toasts = v),
            t("Sütunlu menü", () -> Config.columnMenu, v -> Config.columnMenu = v),
            new Entry("Tema rengi", -1, new MenuScreen.Slider("Tema rengi", 0.0F, 7.0F, 1.0F, () -> Config.accent, v -> Config.accent = (int)v))
         })
      })
   };

   private static final int T_TAB = 0;
   private static final int T_MOD = 1;
   private static final int T_SLIDER = 2;

   private record Hit(int type, int a, int b, MenuScreen.Slider s, float x, float y, float w, float h) {
      boolean contains(float px, float py) {
         return px >= x && px <= x + w && py >= y && py <= y + h;
      }
   }

   private final List<Hit> hits = new ArrayList<>();
   private Hit drag;
   private final int[] tab = new int[PANELS.length];
   private final Anim[] scroll = new Anim[PANELS.length];
   private final float[] scrollTarget = new float[PANELS.length];
   private final float[] contentH = new float[PANELS.length];
   private final float[] viewH = new float[PANELS.length];
   private final float[] panelX = new float[PANELS.length];
   private final float[] panelY = new float[PANELS.length];
   private final Anim[] hover = new Anim[Kuro.NAMES.length];
   private final Anim[] on = new Anim[Kuro.NAMES.length];
   private final Anim[] exp = new Anim[Kuro.NAMES.length];
   private final Anim[] setHover = new Anim[64];
   private final Anim[] setOn = new Anim[64];
   private int listening = -1;
   private float prog;
   private boolean closing;
   private long last = System.nanoTime();
   private float uiScale = 1.0F;
   private float totalW;

   public ClickGuiScreen() {
      super(Text.literal("Kuro"));
      for (int i = 0; i < PANELS.length; i++) {
         scroll[i] = new Anim(0.0F);
      }

      for (int i = 0; i < Kuro.NAMES.length; i++) {
         hover[i] = new Anim(0.0F);
         on[i] = new Anim(Kuro.on[i] ? 1.0F : 0.0F);
         exp[i] = new Anim(0.0F);
      }

      for (int i = 0; i < setHover.length; i++) {
         setHover[i] = new Anim(0.0F);
         setOn[i] = new Anim(0.0F);
      }

      totalW = PANELS.length * PW + (PANELS.length - 1) * GAP;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void close() {
      closing = true;
      listening = -1;
      drag = null;
   }

   // ---------------------------------------------------------------- yardimcilar

   /** Metin verilen genislige sigmazsa sonuna "…" koyup keser (taşmayı önler). */
   /** Sekme yazisi: sigana kadar kucult (en az 8.5). */
   private static float tabFont(String s, float maxW) {
      float[] sizes = {11.0F, 10.0F, 9.5F, 9.0F, 8.5F};
      for (float f : sizes) {
         if (Nvg.textWidth(s, f, false) <= maxW) {
            return f;
         }
      }

      return 8.5F;
   }

   private static String fit(String s, float size, float maxW) {
      if (Nvg.textWidth(s, size, false) <= maxW) {
         return s;
      }

      int n = s.length();
      while (n > 1 && Nvg.textWidth(s.substring(0, n) + "…", size, false) > maxW) {
         n--;
      }

      return s.substring(0, n).trim() + "…";
   }

   private static boolean isToggle(MenuScreen.Slider s) {
      return s.min() == 0.0F && s.max() == 1.0F && s.step() == 1.0F;
   }

   private static float rowH(MenuScreen.Slider s) {
      return isToggle(s) ? TOG_H : SLD_H;
   }

   private static float rowsH(MenuScreen.Slider[] sl) {
      float h = 0.0F;
      for (MenuScreen.Slider s : sl) {
         h += rowH(s);
      }

      return h;
   }

   private static boolean isAction(int mod) {
      return mod >= 0 && Kuro.isAction(mod);
   }

   private float mouseX() {
      Window w = Kuro.mc.getWindow();
      return (float)(Kuro.mc.mouse.getX() * ((double)w.getFramebufferWidth() / (double)w.getWidth()) / uiScale);
   }

   private float mouseY() {
      Window w = Kuro.mc.getWindow();
      return (float)(Kuro.mc.mouse.getY() * ((double)w.getFramebufferHeight() / (double)w.getHeight()) / uiScale);
   }

   private float itemsHeight(Group g) {
      float h = 0.0F;
      for (Entry e : g.items) {
         if (e.mod >= 0) {
            MenuScreen.Slider[] sl = MenuScreen.SLIDERS[e.mod];
            h += BTN_STEP + (sl == null ? 0.0F : exp[e.mod].v * (rowsH(sl) + 4.0F));
         } else if (isToggle(e.set)) {
            h += BTN_STEP;
         } else {
            h += SLD_H + 2.0F;
         }
      }

      return h;
   }

   // ---------------------------------------------------------------- Screen

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
      prog = Anim.clamp(prog + (closing ? -dt : dt) / 0.28F, 0.0F, 1.0F);

      Window win = Kuro.mc.getWindow();
      int fw = win.getFramebufferWidth();
      int fh = win.getFramebufferHeight();
      if (fw <= 0 || fh <= 0) {
         return;
      }

      uiScale = Math.min(Theme.scale(fh), (float)fw / (totalW + 24.0F));
      float lw = fw / uiScale;
      float lh = fh / uiScale;
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

   private void draw(float lw, float lh, float dt) {
      hits.clear();
      float e0 = Anim.easeOutCubic(prog);
      Nvg.rect(0.0F, 0.0F, lw, lh, 0.0F, Theme.alpha(0x000000, 0.34F * e0));
      float mx = mouseX();
      float my = mouseY();
      int accent = Theme.accent();
      float x0 = (lw - totalW) * 0.5F;
      float maxH = lh - TOP * 2.0F;
      int setIdx = 0;

      for (int p = 0; p < PANELS.length; p++) {
         Panel pn = PANELS[p];
         float e = Anim.easeOutCubic(Anim.clamp(prog * 1.5F - p * 0.1F, 0.0F, 1.0F));
         if (e <= 0.0F) {
            continue;
         }

         Group g = pn.groups[Math.min(tab[p], pn.groups.length - 1)];
         float x = x0 + p * (PW + GAP);
         float y = TOP;
         float body = itemsHeight(g);
         float h = Math.min(maxH, HEAD + TABS_H + 8.0F + body + 6.0F);
         float viewTop = y + HEAD + TABS_H + 6.0F;
         float viewBot = y + h - 6.0F;
         float viewHt = Math.max(0.0F, viewBot - viewTop);
         contentH[p] = body;
         viewH[p] = viewHt;
         panelX[p] = x;
         panelY[p] = y;
         scrollTarget[p] = Anim.clamp(scrollTarget[p], 0.0F, Math.max(0.0F, body - viewHt));
         scroll[p].to(scrollTarget[p]).tick(dt, 18.0F);

         nvgSave(Nvg.ctx);
         nvgGlobalAlpha(Nvg.ctx, e);
         nvgTranslate(Nvg.ctx, 0.0F, (1.0F - e) * 14.0F);

         Nvg.glow(x, y + 3.0F, PW, h, 8.0F, 18.0F, 0x66000000);
         Nvg.rect(x, y, PW, h, 8.0F, PANEL_BG);
         Nvg.border(x, y, PW, h, 8.0F, 1.0F, PANEL_BORDER);
         Nvg.textC(pn.title, x + PW * 0.5F, y + HEAD * 0.5F + 2.0F, 17.0F, true, accent);

         // alt sekmeler
         float tx = x + 6.0F;
         float tw = (PW - 12.0F) / pn.groups.length;
         for (int i = 0; i < pn.groups.length; i++) {
            boolean sel = i == tab[p];
            float bx = tx + i * tw;
            float by = y + HEAD;
            if (sel) {
               Nvg.border(bx + 2.0F, by, tw - 4.0F, 18.0F, 4.0F, 1.0F, 0xB0FFFFFF);
            }

            float tsz = tabFont(pn.groups[i].name, tw - 6.0F);
            Nvg.textC(fit(pn.groups[i].name, tsz, tw - 4.0F), bx + tw * 0.5F, by + 9.5F, tsz, false, sel ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Hit(T_TAB, p, i, null, bx, by, tw, 18.0F));
         }

         // icerik (kaydirilabilir)
         nvgSave(Nvg.ctx);
         nvgScissor(Nvg.ctx, x + 2.0F, viewTop, PW - 4.0F, viewHt);
         float cy = viewTop - scroll[p].v;
         float bx = x + 6.0F;
         float bw = PW - 12.0F;

         for (Entry en : g.items) {
            if (en.mod >= 0) {
               int id = en.mod;
               boolean action = isAction(id) || id == Kuro.BASES;
               MenuScreen.Slider[] sl = MenuScreen.SLIDERS[id];
               boolean over = mx >= bx && mx <= bx + bw && my >= cy && my <= cy + BTN_H && my >= viewTop && my <= viewBot;
               hover[id].to(over ? 1.0F : 0.0F).tick(dt, 16.0F);
               on[id].to(!action && Kuro.on[id] ? 1.0F : 0.0F).tick(dt, 14.0F);
               exp[id].to(sl != null && exp[id].t > 0.5F ? 1.0F : 0.0F).tick(dt, 14.0F);

               int fill = Theme.mix(Theme.mix(BTN, BTN_HOVER, hover[id].v), BTN_ON, on[id].v);
               Nvg.rect(bx, cy, bw, BTN_H, 6.0F, fill);
               int txt = Theme.mix(0xFFC8C8D2, Theme.TEXT, Math.max(on[id].v, hover[id].v * 0.7F));
               boolean bound = listening != id && Kuro.binds[id] >= 0;
               float keyW = bound ? Nvg.textWidth(Keys.name(Kuro.binds[id]), 9.5F, false) + 14.0F : 0.0F;
               float rightW = sl != null ? 22.0F : 8.0F;
               float side = Math.max(keyW, rightW);
               Nvg.textC(fit(en.name, 12.5F, bw - side * 2.0F), bx + bw * 0.5F, cy + BTN_H * 0.5F + 0.5F, 12.5F, false, txt);
               if (sl != null) {
                  Nvg.textR(exp[id].t > 0.5F ? "-" : "+", bx + bw - 8.0F, cy + BTN_H * 0.5F + 0.5F, 13.0F, false, Theme.TEXT_DIM);
               }

               if (listening == id) {
                  Nvg.rect(bx, cy, bw, BTN_H, 6.0F, 0xE6101013);
                  Nvg.textC("Tuşa bas...", bx + bw * 0.5F, cy + BTN_H * 0.5F + 0.5F, 12.0F, false, accent);
               } else if (Kuro.binds[id] >= 0) {
                  Nvg.textL(Keys.name(Kuro.binds[id]), bx + 6.0F, cy + BTN_H * 0.5F + 0.5F, 9.5F, false, Theme.TEXT_DIM);
               }

               addHit(T_MOD, id, 0, null, bx, cy, bw, BTN_H, viewTop, viewBot);
               cy += BTN_STEP;

               if (sl != null && exp[id].v > 0.01F) {
                  float extra = exp[id].v * (rowsH(sl) + 4.0F);
                  nvgSave(Nvg.ctx);
                  nvgIntersectScissor(Nvg.ctx, bx, cy - 3.0F, bw, extra);
                  nvgGlobalAlpha(Nvg.ctx, exp[id].v * e);
                  float ry = cy;
                  for (int i = 0; i < sl.length; i++) {
                     ry += drawRow(sl[i], id, i, bx, ry, bw, mx, my, viewTop, viewBot, dt, accent);
                  }

                  nvgRestore(Nvg.ctx);
                  cy += extra;
               }
            } else if (isToggle(en.set)) {
               int k = Math.min(setIdx++, setHover.length - 1);
               boolean val = en.set.get().getAsDouble() >= 0.5;
               boolean over = mx >= bx && mx <= bx + bw && my >= cy && my <= cy + BTN_H && my >= viewTop && my <= viewBot;
               setHover[k].to(over ? 1.0F : 0.0F).tick(dt, 16.0F);
               setOn[k].to(val ? 1.0F : 0.0F).tick(dt, 14.0F);
               int fill = Theme.mix(Theme.mix(BTN, BTN_HOVER, setHover[k].v), BTN_ON, setOn[k].v);
               Nvg.rect(bx, cy, bw, BTN_H, 6.0F, fill);
               Nvg.textC(fit(en.name, 12.5F, bw - 16.0F), bx + bw * 0.5F, cy + BTN_H * 0.5F + 0.5F, 12.5F, false, Theme.mix(0xFFC8C8D2, Theme.TEXT, Math.max(setOn[k].v, setHover[k].v * 0.7F)));
               addHit(T_SLIDER, 0, 0, en.set, bx, cy, bw, BTN_H, viewTop, viewBot);
               cy += BTN_STEP;
            } else {
               drawRow(en.set, -1, 0, bx, cy, bw, mx, my, viewTop, viewBot, dt, accent);
               cy += SLD_H + 2.0F;
            }
         }

         nvgRestore(Nvg.ctx);
         nvgRestore(Nvg.ctx);
      }
   }

   private void addHit(int type, int a, int b, MenuScreen.Slider s, float x, float y, float w, float h, float top, float bot) {
      float y0 = Math.max(y, top);
      float y1 = Math.min(y + h, bot);
      if (y1 > y0) {
         hits.add(new Hit(type, a, b, s, x, y0, w, y1 - y0));
      }
   }

   /** Ayar satiri (anahtar veya surgu). Yuksekligi dondurur. */
   private float drawRow(MenuScreen.Slider s, int mod, int idx, float x, float y, float w, float mx, float my, float top, float bot, float dt, int accent) {
      float h = rowH(s);
      double v = s.get().getAsDouble();
      float th = h - 2.0F;
      Nvg.rect(x, y, w, th, 5.0F, ROW_BG);
      if (isToggle(s)) {
         Nvg.textL(fit(s.name(), 11.0F, w - 50.0F), x + 8.0F, y + th * 0.5F + 0.5F, 11.0F, false, Theme.TEXT);
         float sx = x + w - 30.0F;
         float sy = y + (th - 12.0F) * 0.5F;
         boolean o = v >= 0.5;
         Nvg.rect(sx, sy, 22.0F, 12.0F, 6.0F, o ? accent : 0xFF2C2C32);
         Nvg.circle(sx + (o ? 16.0F : 6.0F), sy + 6.0F, 4.5F, 0xFFFFFFFF);
      } else {
         String vs = fmt(v, s.step());
         Nvg.textL(fit(s.name(), 10.5F, w - 24.0F - Nvg.textWidth(vs, 10.5F, false)), x + 8.0F, y + 9.0F, 10.5F, false, Theme.TEXT_DIM);
         Nvg.textR(vs, x + w - 8.0F, y + 9.0F, 10.5F, false, Theme.TEXT);
         float t = Anim.clamp((float)((v - s.min()) / (s.max() - s.min())), 0.0F, 1.0F);
         float tx = x + 8.0F;
         float tw = w - 16.0F;
         float ty = y + 19.0F;
         Nvg.rect(tx, ty, tw, 4.0F, 2.0F, 0xFF2C2C32);
         Nvg.rect(tx, ty, Math.max(4.0F, tw * t), 4.0F, 2.0F, accent);
         Nvg.circle(tx + tw * t, ty + 2.0F, 4.5F, 0xFFFFFFFF);
      }

      addHit(T_SLIDER, mod, idx, s, x, y, w, th, top, bot);
      return h;
   }

   private static String fmt(double v, float step) {
      if (step >= 1.0F) {
         return Integer.toString((int)Math.round(v));
      }

      return step >= 0.1F ? Float.toString(Math.round(v * 10.0) / 10.0F) : Float.toString(Math.round(v * 100.0) / 100.0F);
   }

   // ---------------------------------------------------------------- girdi

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (closing) {
         return true;
      }

      float mx = mouseX();
      float my = mouseY();
      for (int i = hits.size() - 1; i >= 0; i--) {
         Hit h = hits.get(i);
         if (!h.contains(mx, my)) {
            continue;
         }

         switch (h.type) {
            case T_TAB -> {
               if (button == 0 && tab[h.a] != h.b) {
                  tab[h.a] = h.b;
                  scrollTarget[h.a] = 0.0F;
                  scroll[h.a].snap(0.0F);
               }
            }
            case T_MOD -> {
               int id = h.a;
               if (button == 0) {
                  Kuro.toggle(id);
               } else if (button == 1) {
                  if (MenuScreen.SLIDERS[id] != null) {
                     exp[id].to(exp[id].t > 0.5F ? 0.0F : 1.0F);
                  }
               } else if (button == 2 && !isAction(id)) {
                  listening = id;
               }
            }
            case T_SLIDER -> {
               if (button != 0) {
                  break;
               }

               if (isToggle(h.s)) {
                  boolean was = h.s.get().getAsDouble() >= 0.5;
                  h.s.set().accept(was ? 0.0 : 1.0);
                  Config.save();
                  if (!Config.columnMenu) {
                     Kuro.mc.setScreen(new MenuScreen());
                  }
               } else {
                  drag = h;
                  applySlider(h, mx);
               }
            }
            default -> {
            }
         }

         return true;
      }

      return true;
   }

   private static void applySlider(Hit h, float mx) {
      MenuScreen.Slider s = h.s;
      float t = Anim.clamp((mx - (h.x + 8.0F)) / (h.w - 16.0F), 0.0F, 1.0F);
      float v = s.min() + t * (s.max() - s.min());
      v = Math.round(v / s.step()) * s.step();
      s.set().accept(Anim.clamp(v, s.min(), s.max()));
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
         applySlider(drag, mouseX());
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      float mx = mouseX();
      for (int p = 0; p < PANELS.length; p++) {
         if (mx >= panelX[p] && mx <= panelX[p] + PW) {
            float max = Math.max(0.0F, contentH[p] - viewH[p]);
            scrollTarget[p] = Anim.clamp(scrollTarget[p] - (float)verticalAmount * 40.0F, 0.0F, max);
            break;
         }
      }

      return true;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (listening >= 0) {
         if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            listening = -1;
         } else {
            Kuro.binds[listening] = keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode;
            Kuro.saveBinds();
            listening = -1;
         }

         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE || Kuro.menuKey.matchesKey(keyCode, scanCode)) {
         close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
