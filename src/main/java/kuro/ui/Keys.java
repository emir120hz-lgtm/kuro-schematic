package kuro.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.util.InputUtil;

/** Tus kodundan kisa, buyuk harfli gosterim adi uretir. */
public final class Keys {
   private static final Map<Integer, String> CACHE = new HashMap<>();

   private Keys() {
   }

   public static String name(int code) {
      if (code < 0) {
         return "YOK";
      }

      return CACHE.computeIfAbsent(code, c -> {
         String s = InputUtil.Type.KEYSYM.createFromCode(c).getLocalizedText().getString();
         s = s.replace("Left ", "L").replace("Right ", "R").replace("Control", "CTRL").replace("Shift", "SHIFT").replace("Alt", "ALT");
         s = s.replace("Backspace", "BKSP").replace("Escape", "ESC").replace("Enter", "ENTER").replace("Caps Lock", "CAPS");
         s = s.replace(" ", "").toUpperCase();
         return s.length() > 7 ? s.substring(0, 7) : s;
      });
   }
}
