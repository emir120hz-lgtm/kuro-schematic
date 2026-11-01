package kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;

/**
 * Freelook: karakterin yonu sabit kalir, sadece kamera doner. Varsayilan: tusu basili tut (birakinca yumusakca geri doner),
 * 3. sahis kamerasina gecer (kapaninca eski bakisa doner), hassasiyet ayarlanabilir.
 * Kapatinca kamera karakterin yonune yumusakca geri kayar.
 */
public final class Freelook {
   public static boolean active;
   public static float yaw;
   public static float pitch;
   /** 0 = karakterin bakisi, 1 = freelook bakisi */
   private static float blend;
   private static long last = System.nanoTime();
   /** Freelook 3. sahis moduna biz gectiysek true; kapaninca eski bakisa doneriz. */
   private static boolean switchedPerspective;

   private Freelook() {
   }

   public static void enable() {
      ClientPlayerEntity p = MinecraftClient.getInstance().player;
      if (p != null) {
         yaw = p.getYaw();
         pitch = p.getPitch();
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      if (Config.flThird >= 0.5F && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
         mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
         switchedPerspective = true;
      }

      active = true;
   }

   public static void disable() {
      active = false;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (switchedPerspective) {
         switchedPerspective = false;
         if (mc.options != null && mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
         }
      }
   }

   public static void look(double dx, double dy) {
      float k = 0.15F * Config.flSens;
      yaw += (float)dx * k;
      pitch = MathHelper.clamp(pitch + (float)dy * k, -90.0F, 90.0F);
   }

   /** Kamera guncellenmeden hemen once cagrilir: gecisi (blend) ilerletir. */
   public static void updateBlend() {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - last) / 1.0E9F);
      last = now;
      float target = active ? 1.0F : 0.0F;
      blend += (target - blend) * (1.0F - (float)Math.exp(-16.0 * dt));
      if (Math.abs(target - blend) < 0.0005F) {
         blend = target;
      }
   }

   public static boolean applies() {
      return active || blend > 0.0F;
   }

   public static float camYaw(float playerYaw) {
      return playerYaw + MathHelper.wrapDegrees(yaw - playerYaw) * blend;
   }

   public static float camPitch(float playerPitch) {
      return playerPitch + (pitch - playerPitch) * blend;
   }
}
