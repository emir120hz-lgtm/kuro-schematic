package kuro;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Safe Anchor: yakindaki respawn anchor ile senin aranda engel yoksa, ikinizin arasina (onune)
 * GLOWSTONE koyar -> patlama hasari engellenir. Sadece glowstone kullanir.
 * Anchor'in kendisine tiklamaz (glowstone anchor'i sarj eder); bunun yerine aradaki bos blogun
 * yanindaki saglam bir blogun yuzune koyar.
 * Legit: tepki suresi bekler, kamerayi gercekten cevirir, sadece nisan o yuzdeyken tiklar.
 * Nether'de anchor patlamadigi icin orada calismaz.
 */
public final class SafeAnchor {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private static int cd;
   private static int react = -1;
   private static int idle;
   private static int restore = -1;
   private static int prevSlot = -1;
   private static int stuck;
   private static int warn;
   private static Vec3d aimOff = Vec3d.ZERO;
   private static BlockPos lastAnchor;

   private record Plan(BlockPos support, Direction side) {
   }

   private SafeAnchor() {
   }

   public static void reset() {
      react = -1;
      idle = 0;
      stuck = 0;
      lastAnchor = null;
      restoreSlot();
   }

   private static void restoreSlot() {
      if (prevSlot >= 0 && mc.player != null) {
         mc.player.getInventory().selectedSlot = prevSlot;
      }

      prevSlot = -1;
      restore = -1;
   }

   public static void tick() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || mc.currentScreen != null || Freecam.active) {
         reset();
         return;
      }

      if (restore >= 0 && restore-- == 0) {
         restoreSlot();
      }

      if (cd > 0) {
         cd--;
      }

      if (warn > 0) {
         warn--;
      }

      if (mc.world.getRegistryKey() == World.NETHER) {
         reset();
         return;
      }

      Vec3d eye = p.getEyePos();
      BlockPos anchor = nearestAnchor(p, eye);
      if (anchor == null) {
         idleTick();
         return;
      }

      // arada zaten bir blok varsa korunuyoruz
      if (shielded(eye, anchor)) {
         idle = 0;
         stuck = 0;
         return;
      }

      int slot = glowstoneSlot(p);
      if (slot < 0) {
         if (warn <= 0) {
            Mods.msg("SafeAnchor: hotbar'da glowstone yok!");
            warn = 200;
         }

         return;
      }

      if (!ready()) {
         return;
      }

      Plan plan = plan(p, eye, anchor);
      if (plan == null) {
         return;
      }

      if (!anchor.equals(lastAnchor)) {
         lastAnchor = anchor;
         aimOff = new Vec3d(Legit.noise(0.25F), Legit.noise(0.25F), Legit.noise(0.25F));
      }

      // saglam blogun yuzunun ortasina (hafif sapmali) nisan al
      Direction d = plan.side();
      Vec3d aim = Vec3d.ofCenter(plan.support()).add(d.getOffsetX() * 0.5, d.getOffsetY() * 0.5, d.getOffsetZ() * 0.5);
      aim = aim.add(d.getOffsetX() == 0 ? aimOff.x : 0.0, d.getOffsetY() == 0 ? aimOff.y : 0.0, d.getOffsetZ() == 0 ? aimOff.z : 0.0);
      Legit.aim(aim, Config.saAimSpeed, Config.acAimNoise);

      HitResult hr = mc.crosshairTarget;
      if (cd <= 0
         && hr instanceof BlockHitResult bhr
         && hr.getType() == HitResult.Type.BLOCK
         && bhr.getBlockPos().equals(plan.support())
         && bhr.getSide() == d) {
         if (prevSlot < 0) {
            prevSlot = p.getInventory().selectedSlot;
         }

         p.getInventory().selectedSlot = slot;
         mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
         p.swingHand(Hand.MAIN_HAND);
         cd = Legit.rand(Config.saDelayMin, Config.saDelayMax);
         restore = 2;
         stuck = 0;
      } else if (++stuck > 40) {
         // nisan bir turlu tutmuyor (arada engel vs.): bir sure birak
         stuck = 0;
         cd = 60;
         react = -1;
      }
   }

   private static boolean ready() {
      idle = 0;
      if (react < 0) {
         react = Legit.rand(Config.saReactMin, Config.saReactMax);
      }

      if (react > 0) {
         react--;
         return false;
      }

      return true;
   }

   private static void idleTick() {
      if (++idle > 4) {
         react = -1;
         stuck = 0;
         lastAnchor = null;
      }
   }

   /**
    * Goz -> anchor dogrusu uzerinde, onumuzdeki bos bir blok konumu ve o konuma glowstone
    * koyabilecegimiz saglam bir komsu blok yuzu bulur.
    */
   private static Plan plan(ClientPlayerEntity p, Vec3d eye, BlockPos anchor) {
      Vec3d target = Vec3d.ofCenter(anchor);
      Vec3d dir = target.subtract(eye);
      double len = dir.length();
      if (len < 0.8) {
         return null;
      }

      dir = dir.multiply(1.0 / len);
      Box pb = p.getBoundingBox();
      BlockPos prev = null;
      for (double t = 0.7; t < len - 0.5; t += 0.35) {
         BlockPos pos = BlockPos.ofFloored(eye.add(dir.multiply(t)));
         if (pos.equals(prev) || pos.equals(anchor)) {
            continue;
         }

         prev = pos;
         if (!mc.world.getBlockState(pos).isReplaceable()) {
            continue;
         }

         Box bx = new Box(pos);
         if (pb.intersects(bx) || !mc.world.getOtherEntities(p, bx, e -> !(e instanceof ItemEntity)).isEmpty()) {
            continue;
         }

         for (Direction d : Direction.values()) {
            BlockPos sup = pos.offset(d);
            if (sup.equals(anchor)) {
               continue;
            }

            BlockState ss = mc.world.getBlockState(sup);
            if (!ss.isSolidBlock(mc.world, sup) || unusable(ss.getBlock())) {
               continue;
            }

            Direction side = d.getOpposite();
            Vec3d fc = Vec3d.ofCenter(sup).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            if (fc.squaredDistanceTo(eye) > 16.0) {
               continue;
            }

            // yuz bize bakmali
            double facing = (eye.x - fc.x) * side.getOffsetX() + (eye.y - fc.y) * side.getOffsetY() + (eye.z - fc.z) * side.getOffsetZ();
            if (facing <= 0.0) {
               continue;
            }

            return new Plan(sup, side);
         }
      }

      return null;
   }

   /** Sag tikta GUI acan / etkilesen bloklara tiklama. */
   private static boolean unusable(Block b) {
      return b instanceof BlockWithEntity || b instanceof CraftingTableBlock || b instanceof RespawnAnchorBlock;
   }

   private static BlockPos nearestAnchor(ClientPlayerEntity p, Vec3d eye) {
      double range = Config.saRange;
      int r = (int)Math.ceil(range);
      BlockPos o = p.getBlockPos();
      BlockPos best = null;
      double bd = range * range;
      boolean onlyCharged = Config.saOnlyCharged >= 0.5F;

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               BlockPos pos = new BlockPos(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
               BlockState st = mc.world.getBlockState(pos);
               if (!st.isOf(Blocks.RESPAWN_ANCHOR)) {
                  continue;
               }

               if (onlyCharged && st.get(RespawnAnchorBlock.CHARGES) <= 0) {
                  continue;
               }

               double d = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
               if (d < bd) {
                  bd = d;
                  best = pos;
               }
            }
         }
      }

      return best;
   }

   /** Anchor ile goz arasinda baska bir blok varsa korunmus say. */
   private static boolean shielded(Vec3d eye, BlockPos anchor) {
      Vec3d c = Vec3d.ofCenter(anchor);
      BlockHitResult r = mc.world.raycast(new RaycastContext(eye, c, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
      return r.getType() == HitResult.Type.BLOCK && !r.getBlockPos().equals(anchor);
   }

   /** Sadece glowstone (hotbar). */
   private static int glowstoneSlot(ClientPlayerEntity p) {
      for (int i = 0; i < 9; i++) {
         ItemStack s = p.getInventory().getStack(i);
         if (s.isOf(Items.GLOWSTONE)) {
            return i;
         }
      }

      return -1;
   }
}
