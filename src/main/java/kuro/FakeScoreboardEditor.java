package kuro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** FakeScoreboard duzenleyici: gercek scoreboard'da "bul -> yerine" kurallari. */
public final class FakeScoreboardEditor extends Screen {
   private static final int ROWS = 8;
   private static final int ROW_H = 26;
   private final Screen parent;
   private final TextFieldWidget[] find = new TextFieldWidget[ROWS];
   private final TextFieldWidget[] repl = new TextFieldWidget[ROWS];
   private int x0;
   private int y0;
   private int colW;

   public FakeScoreboardEditor(Screen parent) {
      super(Text.literal("FakeScoreboard"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      int total = Math.min(360, this.width - 40);
      colW = (total - 10) / 2;
      x0 = this.width / 2 - total / 2;
      y0 = Math.max(44, this.height / 2 - 120);

      String[][] rules = ScoreboardEdit.rules();
      for (int i = 0; i < ROWS; i++) {
         int y = y0 + i * ROW_H;
         find[i] = box(x0, y, "bul", i < rules.length ? rules[i][0] : "");
         repl[i] = box(x0 + colW + 10, y, "yerine", i < rules.length ? rules[i][1] : "");
      }

      int by = y0 + ROWS * ROW_H + 6;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Kuralları temizle"), b -> {
         for (int i = 0; i < ROWS; i++) {
            find[i].setText("");
            repl[i].setText("");
         }
      }).dimensions(x0, by, colW, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Kaydet ve kapat"), b -> close())
         .dimensions(x0 + colW + 10, by, colW, 20).build());
   }

   private TextFieldWidget box(int x, int y, String hint, String value) {
      TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, colW, 20, Text.literal(hint));
      f.setMaxLength(120);
      f.setPlaceholder(Text.literal(hint));
      f.setText(value);
      this.addDrawableChild(f);
      return f;
   }

   private void apply() {
      List<String> out = new ArrayList<>();
      for (int i = 0; i < ROWS; i++) {
         String a = find[i].getText().replace(ScoreboardEdit.RULE_SEP, "").replace(ScoreboardEdit.PAIR_SEP, "");
         String b = repl[i].getText().replace(ScoreboardEdit.RULE_SEP, "").replace(ScoreboardEdit.PAIR_SEP, "");
         if (!a.isEmpty()) {
            out.add(a + ScoreboardEdit.PAIR_SEP + b);
         }
      }

      Config.fsbRules = String.join(ScoreboardEdit.RULE_SEP, out);
      Config.save();
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("FakeScoreboard: gerçek scoreboard'daki yazıları değiştir"), this.width / 2, y0 - 34, 0xFFFFFF);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Kuralı yazınca hemen çalışır (ayrı açma yok). Büyük/küçük harf ve boşluklar birebir eşleşir."), this.width / 2, y0 - 22, 0xAAAAAA);
   }

   @Override
   public void removed() {
      apply();
   }

   @Override
   public void close() {
      this.client.setScreen(parent);
   }
}
