package kuro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Legit AutoCrystal: insan gibi oynar.
 *  - tepki suresi (rastgele) bekler, sonra kamerayi GERCEKTEN hedefe cevirir (hizi/sapmasi ayarli)
 *  - sadece nisan hedefin ustundeyken (crosshair) tiklar -> vanilla menzil ve gorus kurallari gecerli
 *  - koyma/kirma arasinda rastgele gecikme, istege bagli hata payi, istege bagli "sag tik basiliyken calis"
 */
public final class AutoCrystal {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final double ENTITY_REACH = 3.0;

   private static int cd;
   private static int react = -1;
   private static int idle;
   private static int tick;
   private static BlockPos placePos;
   private static Vec3d placeAim;
   private static EndCrystalEntity lastCrystal;
   private static Vec3d crystalOff = Vec3d.ZERO;

   private record Cand(BlockPos pos, double score) {
   }

   private AutoCrystal() {
   }

   public static void reset() {
      react = -1;
      placePos = null;
      placeAim = null;
      lastCrystal = null;
      idle = 0;
   }

   public static void tick() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || mc.currentScreen != null || Freecam.active) {
         reset();
         return;
      }

      tick++;
      if (cd > 0) {
         cd--;
      }

      if (Config.acHold >= 0.5F && !mc.options.useKey.isPressed()) {
         reset();
         return;
      }

      PlayerEntity target = nearest(p, Config.acTargetRange);
      if (target == null) {
         idleTick();
         return;
      }

      float selfHp = p.getHealth() + p.getAbsorptionAmount();
      Vec3d eye = p.getEyePos();

      // ---- 1) kristal kir ----
      EndCrystalEntity bc = null;
      float bestTd = 0.0F;
      for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(5.0), e -> true)) {
         if (Mods.distToBox(eye, c.getBoundingBox()) > ENTITY_REACH - 0.15 || !p.canSee(c)) {
            continue;
         }

         Vec3d cp = c.getPos();
         float td = Mods.dmg(cp, target);
         float sd = Mods.dmg(cp, p);
         if (sd >= selfHp - 1.0F || sd > Config.acMaxSelf || td < Math.max(2.0F, Config.acMinDmg * 0.5F)) {
            continue;
         }

         if (td > bestTd) {
            bestTd = td;
            bc = c;
         }
      }

      if (bc != null) {
         if (!ready()) {
            return;
         }

         if (bc != lastCrystal) {
            lastCrystal = bc;
            crystalOff = new Vec3d(Legit.noise(0.45F), Legit.noise(0.45F), Legit.noise(0.45F));
         }

         Legit.aim(bc.getBoundingBox().getCenter().add(crystalOff), Config.acAimSpeed, Config.acAimNoise);
         HitResult hr = mc.crosshairTarget;
         if (cd <= 0 && hr instanceof EntityHitResult ehr && ehr.getEntity() == bc) {
            if (Legit.miss(Config.acMiss)) {
               cd = 2;
               return;
            }

            mc.interactionManager.attackEntity(p, bc);
            p.swingHand(Hand.MAIN_HAND);
            cd = Legit.rand(Config.acBreakMin, Config.acBreakMax);
         }

         return;
      }

      // ---- 2) kristal koy ----
      Hand hand = crystalHand(p);
      if (hand == null) {
         int slot = Config.acSwap >= 0.5F ? hotbarCrystal(p) : -1;
         if (slot >= 0 && ready()) {
            p.getInventory().selectedSlot = slot;
            cd = Legit.rand(Config.acPlaceMin, Config.acPlaceMax);
         } else if (slot < 0) {
            idleTick();
         }

         return;
      }

      if (placePos == null || tick % 3 == 0 || !stillValid(placePos)) {
         pickPlace(p, target, eye, selfHp);
      }

      if (placePos == null) {
         idleTick();
         return;
      }

      if (!ready()) {
         return;
      }

      Legit.aim(placeAim, Config.acAimSpeed, Config.acAimNoise);
      HitResult hr = mc.crosshairTarget;
      if (cd <= 0
         && hr instanceof BlockHitResult bhr
         && hr.getType() == HitResult.Type.BLOCK
         && bhr.getBlockPos().equals(placePos)
         && bhr.getSide() == Direction.UP) {
         if (Legit.miss(Config.acMiss)) {
            cd = 2;
            return;
         }

         mc.interactionManager.interactBlock(p, hand, bhr);
         p.swingHand(hand);
         cd = Legit.rand(Config.acPlaceMin, Config.acPlaceMax);
      }
   }

   /** Tepki suresi: yeni bir firsat belirince rastgele sure bekle. */
   private static boolean ready() {
      idle = 0;
      if (react < 0) {
         react = Legit.rand(Config.acReactMin, Config.acReactMax);
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
         placePos = null;
         lastCrystal = null;
      }
   }

   private static PlayerEntity nearest(ClientPlayerEntity p, float range) {
      PlayerEntity best = null;
      double bd = (double)range * range;
      for (PlayerEntity o : mc.world.getPlayers()) {
         if (o == p || !o.isAlive() || o.isSpectator()) {
            continue;
         }

         double d = p.squaredDistanceTo(o);
         if (d < bd) {
            bd = d;
            best = o;
         }
      }

      return best;
   }

   private static Hand crystalHand(ClientPlayerEntity p) {
      if (p.getMainHandStack().isOf(Items.END_CRYSTAL)) {
         return Hand.MAIN_HAND;
      }

      return p.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null;
   }

   private static int hotbarCrystal(ClientPlayerEntity p) {
      for (int i = 0; i < 9; i++) {
         if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
            return i;
         }
      }

      return -1;
   }

   private static boolean stillValid(BlockPos pos) {
      BlockState st = mc.world.getBlockState(pos);
      if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) {
         return false;
      }

      BlockPos up = pos.up();
      if (!mc.world.isAir(up)) {
         return false;
      }

      Box box = new Box(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
      return mc.world.getOtherEntities(null, box).isEmpty();
   }

   /** Ustten gorunur mu? (arada blok varsa koyamaz, tipki bir insan gibi) */
   private static boolean visibleTop(Vec3d eye, BlockPos pos) {
      Vec3d end = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.997, pos.getZ() + 0.5);
      BlockHitResult r = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
      return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(pos) && r.getSide() == Direction.UP;
   }

   private static void pickPlace(ClientPlayerEntity p, PlayerEntity target, Vec3d eye, float selfHp) {
      placePos = null;
      placeAim = null;
      double range = Math.min(Config.acPlaceRange, 4.5F);
      int r = (int)Math.ceil(range);
      BlockPos o = p.getBlockPos();
      Vec3d tp = target.getPos();
      List<Cand> list = new ArrayList<>();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               int x = o.getX() + dx;
               int y = o.getY() + dy;
               int z = o.getZ() + dz;
               double fx = x + 0.5;
               double fy = y + 1.0;
               double fz = z + 0.5;
               double ex = fx - eye.x;
               double ey = fy - eye.y;
               double ez = fz - eye.z;
               if (ex * ex + ey * ey + ez * ez > range * range) {
                  continue;
               }

               double tx = fx - tp.x;
               double ty = fy - tp.y;
               double tz = fz - tp.z;
               if (tx * tx + ty * ty + tz * tz > 64.0) {
                  continue;
               }

               BlockPos pos = new BlockPos(x, y, z);
               BlockState st = mc.world.getBlockState(pos);
               if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) {
                  continue;
               }

               if (!stillValid(pos)) {
                  continue;
               }

               Vec3d cc = new Vec3d(fx, fy, fz);
               float td = Mods.dmg(cc, target);
               float sd = Mods.dmg(cc, p);
               if (td < Config.acMinDmg || sd > Config.acMaxSelf || sd >= selfHp - 1.0F) {
                  continue;
               }

               list.add(new Cand(pos, td - sd * 0.7));
            }
         }
      }

      list.sort(Comparator.comparingDouble((Cand c) -> -c.score()));
      for (int i = 0; i < list.size() && i < 6; i++) {
         BlockPos pos = list.get(i).pos();
         if (visibleTop(eye, pos)) {
            placePos = pos;
            // yuzeyde rastgele bir noktaya nisan al (hep tam ortaya degil)
            placeAim = new Vec3d(pos.getX() + 0.5 + Legit.noise(0.28F), pos.getY() + 0.997, pos.getZ() + 0.5 + Legit.noise(0.28F));
            return;
         }
      }
   }
}
