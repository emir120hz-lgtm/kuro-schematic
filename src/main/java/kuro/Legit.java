package kuro;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

/** "Legit" yardimcilari: rastgele gecikme, hata payi ve gercek (gorunen) nisan alma. */
public final class Legit {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Legit() {
   }

   /** [a, b] araliginda rastgele tick. */
   public static int rand(float a, float b) {
      int lo = Math.round(Math.min(a, b));
      int hi = Math.round(Math.max(a, b));
      return hi > lo ? lo + ThreadLocalRandom.current().nextInt(hi - lo + 1) : lo;
   }

   /** Makrolar icin: taban gecikme + 0..macroJitter rastgele tick. */
   public static int delay(float base) {
      int j = Math.round(Config.macroJitter);
      return Math.max(0, Math.round(base) + (j > 0 ? ThreadLocalRandom.current().nextInt(j + 1) : 0));
   }

   /** Yuzde olasilikla "kacir" (insan hatasi). */
   public static boolean miss(float percent) {
      return percent > 0.0F && ThreadLocalRandom.current().nextFloat() * 100.0F < percent;
   }

   /** -amp..+amp arasi rastgele sapma. */
   public static float noise(float amp) {
      return (ThreadLocalRandom.current().nextFloat() - 0.5F) * 2.0F * amp;
   }

   /**
    * Kamerayi (gercek yaw/pitch) hedef noktaya dogru yumusakca cevirir.
    * speed: tick basina en fazla derece. noiseAmp: hareket sirasinda rastgele sapma (derece).
    */
   public static void aim(Vec3d pt, float speed, float noiseAmp) {
      ClientPlayerEntity p = mc.player;
      if (p == null || speed <= 0.0F) {
         return;
      }

      Vec3d eye = p.getEyePos();
      double dx = pt.x - eye.x;
      double dy = pt.y - eye.y;
      double dz = pt.z - eye.z;
      float wantYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
      float wantPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
      float dyaw = MathHelper.wrapDegrees(wantYaw - p.getYaw());
      float dpitch = wantPitch - p.getPitch();
      float dist = (float)Math.hypot(dyaw, dpitch);
      if (dist < 0.05F) {
         return;
      }

      // uzakken hizli, yaklasirken yavasla (insan eli gibi)
      float sy = MathHelper.clamp(dyaw * 0.5F, -speed, speed);
      float sp = MathHelper.clamp(dpitch * 0.5F, -speed, speed);
      if (dist > 1.5F && noiseAmp > 0.0F) {
         sy += noise(noiseAmp);
         sp += noise(noiseAmp * 0.6F);
      }

      p.setYaw(p.getYaw() + sy);
      p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90.0F, 90.0F));
   }
}
