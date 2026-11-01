package kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * AntiTrap: yakinda armor stand veya sandikli (depolu) vagon gorurse en yakin olandan baslayip hizla vurarak kirar.
 * (Armor stand, 5 tick icinde ikinci vurusta kirilir; bu yuzden her tick vurur.)
 */
public final class AntiTrap {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static int cd;

   private AntiTrap() {
   }

   public static void tick() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null) {
         return;
      }

      if (cd > 0) {
         cd--;
         return;
      }

      double range = Math.min(Config.atRange, 6.0F);
      Vec3d eye = p.getEyePos();
      Entity best = null;
      double bd = Double.MAX_VALUE;
      for (Entity e : mc.world.getEntities()) {
         if (!e.isAlive() || e.isRemoved()) {
            continue;
         }

         boolean ok = Config.atArmor >= 0.5F && e instanceof ArmorStandEntity || Config.atCart >= 0.5F && e instanceof StorageMinecartEntity;
         if (!ok) {
            continue;
         }

         double d = Mods.distToBox(eye, e.getBoundingBox());
         if (d <= range && d < bd) {
            bd = d;
            best = e;
         }
      }

      if (best == null) {
         return;
      }

      if (Config.atAim >= 0.5F) {
         Legit.aim(best.getBoundingBox().getCenter(), 90.0F, 0.0F);
      }

      if (Config.atWait >= 0.5F && p.getAttackCooldownProgress(0.5F) < 0.95F) {
         return;
      }

      mc.interactionManager.attackEntity(p, best);
      p.swingHand(Hand.MAIN_HAND);
      cd = Math.max(0, Math.round(Config.atDelay) - 1);
   }
}
