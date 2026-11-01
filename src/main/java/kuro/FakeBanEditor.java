package kuro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * FakeBan duzenleyici: sahte ekranin baslik / sebep / tarih / Ban ID / itiraz satirini yazdigin
 * basit bir ayar ekrani. Degerler Config'e (kuro_settings.txt) kaydedilir. Tamamen yerel ve kozmetiktir.
 */
public final class FakeBanEditor extends Screen {
   private final Screen parent;
   private TextFieldWidget title;
   private TextFieldWidget reason;
   private TextFieldWidget date;
   private TextFieldWidget id;
   private TextFieldWidget appeal;
   private int fieldX;
   private int fieldY0;
   private static final int ROW = 36;

   public FakeBanEditor(Screen parent) {
      super(Text.literal("FakeBan"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      int w = Math.min(340, this.width - 40);
      fieldX = this.width / 2 - w / 2;
      fieldY0 = Math.max(34, this.height / 2 - 112);

      title = field(0, w, Config.fbanTitle, "Failed to connect to the server");
      reason = field(1, w, Config.fbanReason, "You are banned.");
      date = field(2, w, Config.fbanDate, "boş = bugünün tarihi (gg/aa/yyyy)");
      id = field(3, w, Config.fbanId, "boş = rastgele ID");
      appeal = field(4, w, Config.fbanAppeal, "boş = gösterilmez");

      int by = fieldY0 + ROW * 5 + 4;
      int bw = (w - 8) / 2;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Göster"), b -> {
         apply();
         FakeBan.show();
      }).dimensions(fieldX, by, bw, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Kaydet ve kapat"), b -> close())
         .dimensions(fieldX + bw + 8, by, bw, 20).build());
   }

   private TextFieldWidget field(int row, int w, String value, String hint) {
      TextFieldWidget f = new TextFieldWidget(this.textRenderer, fieldX, fieldY0 + row * ROW + 12, w, 20, Text.literal(hint));
      f.setMaxLength(200);
      f.setPlaceholder(Text.literal(hint));
      f.setText(value == null ? "" : value);
      this.addDrawableChild(f);
      return f;
   }

   private void apply() {
      Config.fbanTitle = title.getText();
      Config.fbanReason = reason.getText();
      Config.fbanDate = date.getText();
      Config.fbanId = id.getText();
      Config.fbanAppeal = appeal.getText();
      Config.save();
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("FakeBan ayarları (Göster: sunucudan çıkıp ana menüde gösterir)"), this.width / 2, fieldY0 - 22, 0xFFFFFF);
      String[] labels = {"Başlık", "Sebep (kırmızı)", "Tarih", "Ban ID", "İtiraz satırı"};
      for (int i = 0; i < labels.length; i++) {
         ctx.drawTextWithShadow(this.textRenderer, labels[i], fieldX, fieldY0 + i * ROW + 1, 0xAAAAAA);
      }
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
