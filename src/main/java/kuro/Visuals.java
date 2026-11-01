package kuro;

import net.minecraft.client.MinecraftClient;

/** Dunya gorselleri: istemci tarafi saat ve yagmur/firtina kapatma. */
public final class Visuals {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Visuals() {
   }

   public static void worldTime() {
      if (mc.world == null) {
         return;
      }

      mc.world.getLevelProperties().setTimeOfDay((long)Config.wtTime);
      if (Config.wtNoRain >= 0.5F) {
         mc.world.setRainGradient(0.0F);
         mc.world.setThunderGradient(0.0F);
      }
   }

   private static boolean zooming;
   private static int origFov;
   private static boolean bobSaved;
   private static boolean origBob;

   /** Zoom: FOV secenegini dusurur; kapaninca / dunyadan cikinca eski degere dondurur. */
   public static void zoomTick() {
      boolean want = Kuro.on[Kuro.ZOOM] && mc.player != null;
      var o = mc.options.getFov();
      if (want) {
         if (!zooming) {
            origFov = o.getValue();
            zooming = true;
         }

         int t = Math.max(30, Math.min(110, (int)Config.zoomFov));
         if (o.getValue() != t) {
            o.setValue(t);
         }
      } else if (zooming) {
         o.setValue(origFov);
         zooming = false;
      }
   }

   /** NoBob: yuruyus sallanmasini kapat, kapaninca eski degeri geri yaz. */
   public static void bobTick() {
      var o = mc.options.getBobView();
      if (Kuro.on[Kuro.NOBOB] && mc.player != null) {
         if (!bobSaved) {
            origBob = o.getValue();
            bobSaved = true;
         }

         if (o.getValue()) {
            o.setValue(false);
         }
      } else if (bobSaved) {
         o.setValue(origBob);
         bobSaved = false;
      }
   }
}
