package kuro;

import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.network.ClientPlayerEntity;

/** Temel modüller: Sprint, NoFall, Speed, Fullbright, KillAura, TriggerBot. */
public final class Extras {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Extras() {
   }

   // ------------------------------------------------------------------ Sprint

   public static void sprint() {
      ClientPlayerEntity p = mc.player;
      if (Freecam.active || mc.currentScreen != null) {
         return;
      }

      if (mc.options.forwardKey.isPressed()
         && !p.isSneaking()
         && !p.horizontalCollision
         && !p.isUsingItem()
         && p.getHungerManager().getFoodLevel() > 6) {
         p.setSprinting(true);
      }
   }

   // ------------------------------------------------------------------ NoFall

   public static void noFall() {
      ClientPlayerEntity p = mc.player;
      if (p.fallDistance > 2.5F && !p.isOnGround() && !p.isGliding() && mc.getNetworkHandler() != null) {
         mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
      }
   }

   // ------------------------------------------------------------------ Speed

   public static void speed() {
      ClientPlayerEntity p = mc.player;
      if (Freecam.active || Kuro.on[Kuro.FLY] || mc.currentScreen != null || p.isTouchingWater() || p.isClimbing()) {
         return;
      }

      double rad = Math.toRadians((double)p.getYaw());
      double fx = -Math.sin(rad);
      double fz = Math.cos(rad);
      double rx = -Math.cos(rad);
      double rz = -Math.sin(rad);
      double mx = 0.0;
      double mz = 0.0;
      if (mc.options.forwardKey.isPressed()) {
         mx += fx;
         mz += fz;
      }

      if (mc.options.backKey.isPressed()) {
         mx -= fx;
         mz -= fz;
      }

      if (mc.options.rightKey.isPressed()) {
         mx += rx;
         mz += rz;
      }

      if (mc.options.leftKey.isPressed()) {
         mx -= rx;
         mz -= rz;
      }

      double len = Math.sqrt(mx * mx + mz * mz);
      if (len < 1.0E-4) {
         return;
      }

      double sp = Config.speedValue;
      Vec3d v = p.getVelocity();
      p.setVelocity(mx / len * sp, v.y, mz / len * sp);
   }

   // ------------------------------------------------------------------ Fullbright (gece görüşü)

   public static void fullbright() {
      ClientPlayerEntity p = mc.player;
      if (!p.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
         p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false, false));
      }
   }

   public static void fullbrightOff() {
      if (mc.player != null) {
         mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
      }
   }

   // ------------------------------------------------------------------ KillAura

   public static void killAura() {
      ClientPlayerEntity p = mc.player;
      if (mc.currentScreen != null || Freecam.active) {
         return;
      }

      double range = Config.auraRange;
      double best = range * range;
      PlayerEntity target = null;
      for (PlayerEntity o : mc.world.getPlayers()) {
         if (o == p || !o.isAlive() || o.isSpectator() || !p.canSee(o)) {
            continue;
         }

         double d = p.squaredDistanceTo(o);
         if (d < best) {
            best = d;
            target = o;
         }
      }

      if (target == null) {
         return;
      }

      float yawLeft = 0.0F;
      float turn = Config.auraTurn;
      if (turn > 0.0F) {
         // hedefe yumusak dön (tick basina en fazla "turn" derece)
         Vec3d eye = p.getEyePos();
         Vec3d t = target.getPos().add(0.0, target.getHeight() * 0.6, 0.0);
         double dx = t.x - eye.x;
         double dy = t.y - eye.y;
         double dz = t.z - eye.z;
         float wantYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
         float wantPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
         float dyaw = MathHelper.wrapDegrees(wantYaw - p.getYaw());
         float dpitch = wantPitch - p.getPitch();
         float sy = MathHelper.clamp(dyaw, -turn, turn);
         float sp = MathHelper.clamp(dpitch, -turn, turn);
         p.setYaw(p.getYaw() + sy);
         p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90.0F, 90.0F));
         yawLeft = Math.abs(dyaw - sy);
      }

      // sadece neredeyse hedefe bakıyorken vur
      if (yawLeft < 20.0F && p.getAttackCooldownProgress(0.5F) >= 0.95F) {
         mc.interactionManager.attackEntity(p, target);
         p.swingHand(Hand.MAIN_HAND);
      }
   }

   // ------------------------------------------------------------------ TriggerBot

   public static void triggerBot() {
      ClientPlayerEntity p = mc.player;
      if (mc.currentScreen != null) {
         return;
      }

      HitResult hr = mc.crosshairTarget;
      if (hr instanceof EntityHitResult ehr
         && ehr.getEntity() instanceof PlayerEntity t
         && t.isAlive()
         && !t.isSpectator()
         && p.getAttackCooldownProgress(0.5F) >= 0.95F) {
         mc.interactionManager.attackEntity(p, t);
         p.swingHand(Hand.MAIN_HAND);
      }
   }
}
