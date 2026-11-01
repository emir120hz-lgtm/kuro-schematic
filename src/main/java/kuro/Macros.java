package kuro;

import net.minecraft.util.Hand;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Tus basili macrolar. Ikisi de "sag tik" basili tutulurken calisir ve her adimi
 * oyuncunun normal etkilesim yolundan (interactBlock / attackEntity) yapar.
 *
 * Crystal Macro: obsidyen / bedrock ustune bakarken kristal koyar, kristale bakarken kirar.
 * Anchor Macro : anchor'a bakarken glowstone ile doldurur, sonra baska slotla patlatir;
 *                eline anchor aliyorsan baktigin yere anchor koyar.
 */
public final class Macros {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private static int placeCd;
   private static int breakCd;
   private static int anchorCd;
   private static int safeSlot = -1;

   private Macros() {
   }

   private static boolean active() {
      return mc.player != null && mc.world != null && mc.currentScreen == null && mc.options.useKey.isPressed();
   }

   // ------------------------------------------------------------------ Crystal Macro

   public static void crystal() {
      if (placeCd > 0) {
         placeCd--;
      }

      if (breakCd > 0) {
         breakCd--;
      }

      if (!active()) {
         return;
      }

      ClientPlayerEntity p = mc.player;
      HitResult hr = mc.crosshairTarget;
      if (hr == null) {
         return;
      }

      // 1) baktigin kristal: kir
      if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystalEntity crystal) {
         if (breakCd <= 0) {
            if (Legit.miss(Config.macroMiss)) {
               breakCd = 2;
               return;
            }

            mc.interactionManager.attackEntity(p, crystal);
            p.swingHand(Hand.MAIN_HAND);
            breakCd = Legit.delay(Config.crystalBreak);
         }

         return;
      }

      // 2) baktigin obsidyen / bedrock'in ustu: kristal koy
      Hand hand = p.getMainHandStack().isOf(Items.END_CRYSTAL)
         ? Hand.MAIN_HAND
         : (p.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null);
      if (hand == null || placeCd > 0 || !(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
         return;
      }

      if (bhr.getSide() != Direction.UP) {
         return;
      }

      BlockPos pos = bhr.getBlockPos();
      BlockState st = mc.world.getBlockState(pos);
      if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) {
         return;
      }

      BlockPos up = pos.up();
      if (!mc.world.isAir(up)) {
         return;
      }

      Box box = new Box(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
      if (!mc.world.getOtherEntities(null, box).isEmpty()) {
         return;
      }

      if (Legit.miss(Config.macroMiss)) {
         placeCd = 2;
         return;
      }

      mc.interactionManager.interactBlock(p, hand, bhr);
      p.swingHand(hand);
      placeCd = Legit.delay(Config.crystalPlace);
   }

   // ------------------------------------------------------------------ Anchor Macro

   public static void anchor() {
      if (anchorCd > 0) {
         anchorCd--;
      }

      if (!active()) {
         safeSlot = -1;
         return;
      }

      if (anchorCd > 0) {
         return;
      }

      ClientPlayerEntity p = mc.player;
      HitResult hr = mc.crosshairTarget;
      if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
         return;
      }

      if (Legit.miss(Config.macroMiss)) {
         anchorCd = 2;
         return;
      }

      PlayerInventory inv = p.getInventory();
      BlockState st = mc.world.getBlockState(bhr.getBlockPos());
      int delay = Math.max(1, Legit.delay(Config.anchorDelay));

      if (st.isOf(Blocks.RESPAWN_ANCHOR)) {
         if (st.get(RespawnAnchorBlock.CHARGES) <= 0) {
            // bos: glowstone ile doldur
            int g = hotbarSlot(inv, Items.GLOWSTONE);
            if (g < 0) {
               return;
            }

            if (safeSlot < 0 && !inv.getStack(inv.selectedSlot).isOf(Items.GLOWSTONE)) {
               safeSlot = inv.selectedSlot;
            }

            inv.selectedSlot = g;
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
            p.swingHand(Hand.MAIN_HAND);
            anchorCd = delay;
         } else if (mc.world.getRegistryKey() != World.NETHER) {
            // dolu: glowstone olmayan bir slotla patlat (Nether'de anchor patlamaz)
            int s = safeSlot >= 0 && !inv.getStack(safeSlot).isOf(Items.GLOWSTONE) ? safeSlot : firstSlotWithout(inv, Items.GLOWSTONE);
            if (s < 0) {
               return;
            }

            inv.selectedSlot = s;
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
            p.swingHand(Hand.MAIN_HAND);
            safeSlot = -1;
            anchorCd = delay;
         }

         return;
      }

      // elinde anchor varsa baktigin yere koy
      if (p.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
         mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
         p.swingHand(Hand.MAIN_HAND);
         anchorCd = delay;
      }
   }

   private static int hotbarSlot(PlayerInventory inv, Item item) {
      for (int i = 0; i < 9; i++) {
         if (inv.getStack(i).isOf(item)) {
            return i;
         }
      }

      return -1;
   }

   private static int firstSlotWithout(PlayerInventory inv, Item item) {
      for (int i = 0; i < 9; i++) {
         if (!inv.getStack(i).isOf(item) && !inv.getStack(i).isOf(Items.RESPAWN_ANCHOR)) {
            return i;
         }
      }

      return -1;
   }
}
