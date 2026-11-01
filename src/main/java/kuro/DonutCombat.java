package kuro;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import kuro.mixin.HandledScreenAccessor;

/** AimAssist, KeyPearl, HoverTotem ve AutoDoubleHand. */
public final class DonutCombat {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private static int pPhase;
   private static int pPrev = -1;
   private static int hoverTicks;
   private static int hoverNeed = -1;
   private static int ddPrev = -1;

   private DonutCombat() {
   }

   public static void tick() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null) {
         return;
      }

      pearlTick(p);
      if (Kuro.on[Kuro.HOVERTOTEM]) {
         hoverTotem(p);
      } else {
         hoverTicks = 0;
         hoverNeed = -1;
      }

      if (mc.currentScreen != null || Freecam.active) {
         return;
      }

      if (Kuro.on[Kuro.AIMASSIST]) {
         aimAssist(p);
      }

      if (Kuro.on[Kuro.DOUBLEHAND]) {
         doubleHand(p);
      } else {
         ddPrev = -1;
      }
   }

   // ------------------------------------------------------------ AimAssist

   private static void aimAssist(ClientPlayerEntity p) {
      if (!mc.options.attackKey.isPressed()) {
         return;
      }

      Vec3d eye = p.getEyePos();
      Vec3d look = p.getRotationVec(1.0F);
      double range = Config.aaRange;
      double cosFov = Math.cos(Math.toRadians(Config.aaFov * 0.5));
      PlayerEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (PlayerEntity t : mc.world.getPlayers()) {
         if (t == p || !t.isAlive() || t.isSpectator()) {
            continue;
         }

         Vec3d c = t.getBoundingBox().getCenter();
         Vec3d d = c.subtract(eye);
         double dist = d.length();
         if (dist > range || dist < 0.1) {
            continue;
         }

         double dot = d.multiply(1.0 / dist).dotProduct(look);
         if (dot < cosFov) {
            continue;
         }

         double score = 1.0 - dot; // nisana en yakin olan
         if (score < bestScore) {
            bestScore = score;
            best = t;
         }
      }

      if (best != null) {
         Legit.aim(best.getBoundingBox().getCenter(), Config.aaSpeed, Config.aaNoise);
      }
   }

   // ------------------------------------------------------------ KeyPearl

   public static boolean pearl() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.interactionManager == null) {
         return false;
      }

      if (pPhase != 0) {
         return true;
      }

      for (int i = 0; i < 9; i++) {
         if (p.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) {
            pPrev = p.getInventory().selectedSlot;
            p.getInventory().selectedSlot = i;
            pPhase = 1;
            return true;
         }
      }

      return false;
   }

   private static void pearlTick(ClientPlayerEntity p) {
      if (pPhase == 1) {
         mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
         p.swingHand(Hand.MAIN_HAND);
         pPhase = 2;
      } else if (pPhase == 2) {
         if (pPrev >= 0) {
            p.getInventory().selectedSlot = pPrev;
         }

         pPrev = -1;
         pPhase = 0;
      }
   }

   // ------------------------------------------------------------ HoverTotem

   private static void hoverTotem(ClientPlayerEntity p) {
      if (!(mc.currentScreen instanceof InventoryScreen hs) || mc.interactionManager == null) {
         hoverTicks = 0;
         hoverNeed = -1;
         return;
      }

      Slot s = ((HandledScreenAccessor)(HandledScreen<?>)hs).kuro$getFocusedSlot();
      if (s == null || !s.hasStack() || !s.getStack().isOf(Items.TOTEM_OF_UNDYING) || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         hoverTicks = 0;
         hoverNeed = -1;
         return;
      }

      if (hoverNeed < 0) {
         float lo = Math.min(Config.htMin, Config.htMax);
         float hi = Math.max(Config.htMin, Config.htMax);
         hoverNeed = Math.round(lo) + ThreadLocalRandom.current().nextInt(Math.round(hi - lo) + 1);
      }

      if (++hoverTicks > hoverNeed) {
         mc.interactionManager.clickSlot(hs.getScreenHandler().syncId, s.id, 40, SlotActionType.SWAP, p);
         hoverTicks = 0;
         hoverNeed = -1;
      }
   }

   // ------------------------------------------------------------ AutoDoubleHand

   private static void doubleHand(ClientPlayerEntity p) {
      float hp = p.getHealth() + p.getAbsorptionAmount();
      if (hp <= Config.ddHealth && p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         if (p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
         }

         for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
               if (ddPrev < 0) {
                  ddPrev = p.getInventory().selectedSlot;
               }

               p.getInventory().selectedSlot = i;
               return;
            }
         }
      } else if (hp > Config.ddHealth + 4.0F && ddPrev >= 0) {
         p.getInventory().selectedSlot = ddPrev;
         ddPrev = -1;
      }
   }
}
