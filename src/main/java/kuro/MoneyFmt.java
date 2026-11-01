package kuro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

/**
 * MoneyFormatter (ChickenSMP-MoneyFormatter 1.0.0'in 1.21.4 icin yeniden yazimi):
 * sohbette gelen para miktarlarini K / M / B olarak kisaltir (ornek: $1.500.000 -> $1.5M).
 * Sadece gorunumu degistirir; ag/dosya/surec erisimi yoktur.
 */
public final class MoneyFmt {
   private static final Pattern MONEY = Pattern.compile(
      "(?<![\\w])([$₺€£]\\s*)([0-9][0-9.,]*)(?![\\w])|(?<![\\w])([0-9][0-9.,]*)(\\s*(?:[$₺€£]|TL|TRY|USD|EUR))(?![\\w])",
      Pattern.CASE_INSENSITIVE
   );

   private MoneyFmt() {
   }

   public static void init() {
      ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> Kuro.on[Kuro.MONEYFMT] && !overlay ? format(message) : message);
   }

   private static Text format(Text message) {
      String s = message.getString();
      Matcher m = MONEY.matcher(s);
      StringBuffer out = new StringBuffer();
      while (m.find()) {
         String pre;
         String num;
         String post;
         if (m.group(2) != null) {
            pre = m.group(1);
            num = m.group(2);
            post = "";
         } else {
            pre = "";
            num = m.group(3);
            post = m.group(4);
         }

         m.appendReplacement(out, Matcher.quoteReplacement(pre + convert(num) + post));
      }

      m.appendTail(out);
      String res = out.toString();
      if (res.equals(s)) {
         return message;
      }

      return Text.literal(res).setStyle(message.getStyle());
   }

   private static String convert(String raw) {
      try {
         BigDecimal v = new BigDecimal(raw.replace(",", "").replace(".", ""));
         if (v.compareTo(new BigDecimal("1000000000")) >= 0) {
            return scale(v, 1000000000L, "B");
         }

         if (v.compareTo(new BigDecimal("1000000")) >= 0) {
            return scale(v, 1000000L, "M");
         }

         if (v.compareTo(new BigDecimal("1000")) >= 0) {
            return scale(v, 1000L, "K");
         }
      } catch (Exception ignored) {
      }

      return raw;
   }

   private static String scale(BigDecimal v, long div, String suffix) {
      return v.divide(BigDecimal.valueOf(div), 1, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + suffix;
   }
}
