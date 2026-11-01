package kuro;

import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.Camera;

/**
 * Freecam: kamera karakterden ayrilir, karakter yerinde durur.
 * Kamera konumunu CameraMixin uygular, fare donusunu EntityMixin, oyuncu girdisini KeyboardInputMixin kilitler.
 */
public final class Freecam {
   public static boolean active;
   public static double x;
   public static double y;
   public static double z;
   public static double px;
   public static double py;
   public static double pz;
   public static float yaw;
   public static float pitch;
   private static double vx;
   private static double vy;
   private static double vz;

   private Freecam() {
   }

   public static void enable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      Camera cam = mc.gameRenderer.getCamera();
      Vec3d p = cam.getPos();
      x = px = p.x;
      y = py = p.y;
      z = pz = p.z;
      yaw = cam.getYaw();
      pitch = cam.getPitch();
      vx = vy = vz = 0.0;
      active = true;
   }

   public static void disable() {
      active = false;
   }

   /** Fare hareketi (Entity.changeLookDirection ile ayni olcek). */
   public static void look(double dx, double dy) {
      yaw += (float)dx * 0.15F;
      pitch = MathHelper.clamp(pitch + (float)dy * 0.15F, -90.0F, 90.0F);
   }

   /** Her client tick'inde cagrilir. */
   public static void tick() {
      if (!active) {
         return;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      px = x;
      py = y;
      pz = z;

      double tx = 0.0;
      double ty = 0.0;
      double tz = 0.0;
      if (mc.currentScreen == null) {
         GameOptions o = mc.options;
         double yr = Math.toRadians(yaw);
         double pr = Math.toRadians(pitch);
         // bakis yonu (egim dahil) ve yan vektor
         double fx = -Math.sin(yr) * Math.cos(pr);
         double fy = -Math.sin(pr);
         double fz = Math.cos(yr) * Math.cos(pr);
         double rx = -Math.cos(yr);
         double rz = -Math.sin(yr);

         if (o.forwardKey.isPressed()) {
            tx += fx;
            ty += fy;
            tz += fz;
         }

         if (o.backKey.isPressed()) {
            tx -= fx;
            ty -= fy;
            tz -= fz;
         }

         if (o.rightKey.isPressed()) {
            tx += rx;
            tz += rz;
         }

         if (o.leftKey.isPressed()) {
            tx -= rx;
            tz -= rz;
         }

         if (o.jumpKey.isPressed()) {
            ty += 1.0;
         }

         if (o.sneakKey.isPressed()) {
            ty -= 1.0;
         }

         double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
         if (len > 1.0E-4) {
            double speed = Config.freecamSpeed * (o.sprintKey.isPressed() ? 2.5 : 1.0);
            tx = tx / len * speed;
            ty = ty / len * speed;
            tz = tz / len * speed;
         }
      }

      // yumusak ivmelenme
      vx += (tx - vx) * 0.35;
      vy += (ty - vy) * 0.35;
      vz += (tz - vz) * 0.35;
      x += vx;
      y += vy;
      z += vz;
   }
}
