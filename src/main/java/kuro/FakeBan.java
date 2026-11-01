package kuro;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * FakeBan: sunucudan temiz sekilde cikip ana menude, yazdigin metinle vanilla "Baglanti Kesildi"
 * ekranini gosterir (gercek bir kopma/ban ile ayni akis). Ekrandaki "Sunucu listesine don" dugmesi
 * normal cok oyunculu ekrana goturur.
 *
 * Onemli: Bu islem GERCEKTEN sunucudan ayrilmani saglar (yeniden girmen/siraya girmen gerekir). Sunucuya
 * ban/komut gondermez, baska oyunculari etkilemez; gosterilen metin tamamen senin yazdigin metindir.
 * Tek oyunculu dunyada dunyadan cikmaz, ekran oyunun ustunde acilir.
 */
public final class FakeBan {
   private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
   private static final String ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

   private FakeBan() {
   }

   public static void show() {
      MinecraftClient mc = Kuro.mc;
      if (mc.player == null || mc.world == null) {
         return;
      }

      Text title = Text.literal(blankOr(Config.fbanTitle, "Failed to connect to the server"));
      String reason = blankOr(Config.fbanReason, "You are banned.");
      String date = blankOr(Config.fbanDate, LocalDate.now().format(DATE_FMT));
      String banId = blankOr(Config.fbanId, "#" + randomId());
      String appeal = Config.fbanAppeal == null ? "" : Config.fbanAppeal.trim();

      MutableText body = Text.literal(reason).formatted(Formatting.RED);
      body = body.append(Text.literal("\n\nDate: ").formatted(Formatting.GRAY));
      body = body.append(Text.literal(date).formatted(Formatting.WHITE));
      body = body.append(Text.literal("\n\nBan ID: ").formatted(Formatting.GRAY));
      body = body.append(Text.literal(banId).formatted(Formatting.GRAY));
      if (!appeal.isEmpty()) {
         body = body.append(Text.literal("\n" + appeal).formatted(Formatting.GRAY));
      }

      if (mc.isInSingleplayer() || mc.getNetworkHandler() == null) {
         // tek oyunculu: dunyadan cikma, ekrani oyunun ustunde goster
         mc.setScreen(new DisconnectedScreen(null, title, body));
         return;
      }

      // cok oyunculu: vanilla "Ayril" ile ayni akis (GameMenuScreen.disconnect), sonra ana menuden goster
      mc.world.disconnect();
      mc.disconnect();
      mc.setScreen(new DisconnectedScreen(new MultiplayerScreen(new TitleScreen()), title, body));
   }

   private static String blankOr(String v, String fallback) {
      return v == null || v.isBlank() ? fallback : v;
   }

   private static String randomId() {
      StringBuilder sb = new StringBuilder(8);
      for (int i = 0; i < 8; i++) {
         sb.append(ID_CHARS.charAt(ThreadLocalRandom.current().nextInt(ID_CHARS.length())));
      }

      return sb.toString();
   }
}
