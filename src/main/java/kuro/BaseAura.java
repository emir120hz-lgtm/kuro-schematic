package kuro;

import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * BaseAura: hedef oyuncunun yakininda uygun kristal noktasi yoksa obsidyen koyar, uzerine kristal koyar ve patlatir.
 * Nisan insan gibi yumusak doner; "Duvar arkasi" aciksa nisan almadan (blok arkasindan) koyar/vurur.
 * Legit mod: tepki suresi (rastgele gecikme), gercek nisan, duvar arkasi kapali.
 * Obsidyen tetigi: hedef obsidyenin ustune gectigi anda (tepki suresinden sonra) kristal ya da respawn anchor kombosu baslar.
 */
public final class BaseAura {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final Set<Block> NO_SUPPORT = Set.of(
      Blocks.CRAFTING_TABLE, Blocks.NOTE_BLOCK, Blocks.RESPAWN_ANCHOR, Blocks.SMITHING_TABLE, Blocks.LOOM,
      Blocks.CARTOGRAPHY_TABLE, Blocks.GRINDSTONE, Blocks.ENCHANTING_TABLE, Blocks.BEACON, Blocks.TNT
   );

   private record Plan(BlockPos base, boolean needObs, BlockPos support, Direction face) {
   }

   private record AnchorPlan(BlockPos pos, BlockPos support, Direction face) {
   }

   private static Plan plan;
   // anchor kombosu durumu
   private static BlockPos aPos;
   private static BlockPos aSupport;
   private static Direction aFace;
   private static int aTicks;
   // tetik / tepki suresi
   private static boolean armed;
   private static int react;
   private static int idle;
   private static int cd;
   private static int tick;
   private static int msgCd;
   private static float statTd;
   private static float statSd = 999.0F;
   private static int statGeo;

   private static void status(String s) {
      if (Config.baStatus >= 0.5F && mc.player != null && msgCd <= 0) {
         mc.player.sendMessage(net.minecraft.text.Text.literal("[BaseAura] " + s), true);
         msgCd = 30;
      }
   }

   /** Kendi hasari: patlama koruman (yuzde) ile azaltilir. */
   private static float selfDmg(Vec3d cc, ClientPlayerEntity p) {
      return selfDmg(cc, p, 12.0);
   }

   private static float selfDmg(Vec3d cc, ClientPlayerEntity p, double diameter) {
      return Mods.dmg(cc, p, diameter) * (1.0F - Config.baProt / 100.0F);
   }

   private BaseAura() {
   }

   public static void reset() {
      plan = null;
      aPos = null;
      aSupport = null;
      aFace = null;
      aTicks = 0;
      armed = false;
      react = 0;
      idle = 0;
      cd = 0;
   }

   public static void tick() {
      ClientPlayerEntity p = mc.player;
      if (cd > 0) {
         cd--;
      }

      if (msgCd > 0) {
         msgCd--;
      }

      if (mc.world == null || mc.currentScreen != null || Freecam.active) {
         plan = null;
         aPos = null;
         return;
      }

      if (Config.baHold >= 0.5F && !mc.options.useKey.isPressed()) {
         plan = null;
         aPos = null;
         armed = false;
         status("Sağ tık basılı tutmalısın (ayardan kapatılabilir)");
         return;
      }

      PlayerEntity target = null;
      double bd = (double)Config.baTargetRange * Config.baTargetRange;
      for (PlayerEntity o : mc.world.getPlayers()) {
         if (o != p && o.isAlive() && !o.isSpectator()) {
            double d = p.squaredDistanceTo(o);
            if (d < bd) {
               bd = d;
               target = o;
            }
         }
      }

      if (target == null) {
         plan = null;
         aPos = null;
         armed = false;
         status("Hedef yok: " + Math.round(Config.baTargetRange) + " blok içinde oyuncu yok");
         return;
      }

      float selfHp = p.getHealth() + p.getAbsorptionAmount();
      Vec3d eye = p.getEyePos();
      boolean legit = Config.baLegit >= 0.5F;
      boolean walls = !legit && Config.baWalls >= 0.5F;

      // 1) kristal patlat
      EndCrystalEntity bc = null;
      float bestTd = 0.0F;
      for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(6.0), e -> true)) {
         if (Mods.distToBox(eye, c.getBoundingBox()) > Config.baBreakRange) {
            continue;
         }

         Vec3d cp = c.getPos();
         float td = Mods.dmg(cp, target);
         float sd = selfDmg(cp, p);
         if (td < Config.baMinDmg || sd > Config.baMaxSelf || sd >= selfHp - 1.0F) {
            continue;
         }

         if (td > bestTd) {
            bestTd = td;
            bc = c;
         }
      }

      if (bc != null) {
         if (!walls) {
            Legit.aim(bc.getBoundingBox().getCenter(), Config.baAim, Config.baNoise);
         }

         boolean ok = walls || (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == bc);
         if (ok && cd <= 0) {
            if (Legit.miss(Config.baMiss)) {
               cd = 2;
               return;
            }

            mc.interactionManager.attackEntity(p, bc);
            p.swingHand(Hand.MAIN_HAND);
            cd = Legit.rand(Config.baBreakMin, Config.baBreakMax);
         }

         return;
      }

      // 2) tetik: hedef obsidyenin ustune gectigi an + tepki suresi (legit)
      if (Config.baStep >= 0.5F && aPos == null && !onObsidian(target)) {
         armed = false;
         plan = null;
         status("Hedef obsidyenin üstünde değil");
         return;
      }

      if (!armed) {
         armed = true;
         react = legit ? Legit.rand(Config.baReactMin, Config.baReactMax) : 0;
      }

      if (react > 0) {
         react--;
         return;
      }

      // 3) anchor kombosu devam ediyorsa onu surdur
      int method = Math.round(Config.baMethod);
      if (aPos != null && anchorStep(p, target, eye, selfHp, walls)) {
         idle = 0;
         return;
      }

      boolean useCrystal = method != 1 && hasCrystal(p);
      boolean useAnchor = method != 0
         && mc.world.getRegistryKey() != World.NETHER
         && hotbarSlot(p, Items.RESPAWN_ANCHOR) >= 0
         && hotbarSlot(p, Items.GLOWSTONE) >= 0
         && safeSlot(p) >= 0;

      // 4) kristal: mevcut obsidyene koy / obsidyen koy
      if (useCrystal) {
         if (plan == null || tick++ % 2 == 0 || !valid(plan)) {
            plan = findPlan(p, target, eye, selfHp, walls);
         }

         if (plan != null) {
            idle = 0;
            crystalFlow(p, walls);
            return;
         }
      }

      // 5) respawn anchor: koy, glowstone ile doldur, patlat
      if (useAnchor) {
         AnchorPlan ap = findAnchor(p, target, eye, selfHp, walls);
         if (ap != null) {
            aPos = ap.pos;
            aSupport = ap.support;
            aFace = ap.face;
            aTicks = 0;
            if (anchorStep(p, target, eye, selfHp, walls)) {
               idle = 0;
               return;
            }
         }
      }

      // hicbiri olmadi: bir sure bosta kalirsa bir sonraki durumda tepki suresi yeniden baslasin
      if (++idle > 20) {
         idle = 0;
         armed = false;
      }

      if (!useCrystal && !useAnchor) {
         status(method == 1 ? "Anchor kombosu için hotbar'da anchor + glowstone + boş/başka eşya slotu gerekli (Nether'de çalışmaz)"
            : "Hotbar'da kristal yok" + (method == 2 ? " ve anchor kombosu da kurulamıyor" : ""));
      } else if (hotbarObsidian(p) < 0 && Config.baObsidian >= 0.5F && useCrystal) {
         status("Plan yok; hotbar'da obsidyen de yok");
      } else if (statGeo == 0) {
         status("Yakında uygun blok/boşluk yok (hedefe veya sana çok uzak)");
      } else if (statTd < Config.baMinDmg) {
         status("Hedefe verilecek hasar düşük (en iyi " + Math.round(statTd) + ", istenen " + Math.round(Config.baMinDmg) + ")");
      } else {
         status("Kendi hasarın çok yüksek (en az " + Math.round(statSd) + ", limit " + Math.round(Config.baMaxSelf) + "): limiti veya koruma yüzdesini artır");
      }
   }

   /** Hedef, obsidyen / aglayan obsidyen bir blogun ustunde mi duruyor? */
   private static boolean onObsidian(PlayerEntity t) {
      if (!t.isOnGround()) {
         return false;
      }

      BlockState st = mc.world.getBlockState(BlockPos.ofFloored(t.getX(), t.getBoundingBox().minY - 0.05, t.getZ()));
      return st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.CRYING_OBSIDIAN);
   }

   private static void crystalFlow(ClientPlayerEntity p, boolean walls) {
      if (!plan.needObs) {
         Hand hand = Mods.crystalHand(p);
         if (hand == null) {
            status("Hotbar'da kristal yok");
            return;
         }

         Vec3d hit = new Vec3d((double)plan.base.getX() + 0.5, (double)plan.base.getY() + 1.0, (double)plan.base.getZ() + 0.5);
         place(p, hand, plan.base, Direction.UP, hit, walls, Config.baPlaceMin, Config.baPlaceMax);
      } else {
         int slot = hotbarObsidian(p);
         if (slot < 0) {
            status("Hotbar'da obsidyen yok");
            return;
         }

         if (p.getInventory().selectedSlot != slot) {
            p.getInventory().selectedSlot = slot;
            cd = Legit.rand(Config.baObsMin, Config.baObsMax);
            return;
         }

         Vec3d fc = Vec3d.ofCenter(plan.support).add(
            plan.face.getOffsetX() * 0.5, plan.face.getOffsetY() * 0.5, plan.face.getOffsetZ() * 0.5
         );
         place(p, Hand.MAIN_HAND, plan.support, plan.face, fc, walls, Config.baObsMin, Config.baObsMax);
      }
   }

   // ------------------------------------------------------------------ Anchor kombosu

   /** @return true = bu tick anchor ile ilgilenildi (bekleme dahil); false = vazgecildi, aPos temizlendi. */
   private static boolean anchorStep(ClientPlayerEntity p, PlayerEntity target, Vec3d eye, float selfHp, boolean walls) {
      if (aPos == null || ++aTicks > 80) {
         aPos = null;
         return false;
      }

      BlockState st = mc.world.getBlockState(aPos);
      Vec3d c = Vec3d.ofCenter(aPos);
      float td = Mods.dmg(c, target, 10.0);
      float sd = selfDmg(c, p, 10.0);
      if (td < Config.baMinDmg || sd > Config.baMaxSelf || sd >= selfHp - 1.0F) {
         aPos = null;
         return false;
      }

      PlayerInventory inv = p.getInventory();
      if (!st.isOf(Blocks.RESPAWN_ANCHOR)) {
         // henuz konmadi: anchor'i sec, destek yuzune sag tikla
         if (!st.isAir() || aSupport == null || aFace == null) {
            aPos = null;
            return false;
         }

         int slot = hotbarSlot(p, Items.RESPAWN_ANCHOR);
         if (slot < 0) {
            aPos = null;
            return false;
         }

         if (inv.selectedSlot != slot) {
            inv.selectedSlot = slot;
            cd = Legit.rand(Config.baObsMin, Config.baObsMax);
            return true;
         }

         Vec3d fc = Vec3d.ofCenter(aSupport).add(aFace.getOffsetX() * 0.5, aFace.getOffsetY() * 0.5, aFace.getOffsetZ() * 0.5);
         place(p, Hand.MAIN_HAND, aSupport, aFace, fc, walls, Config.baPlaceMin, Config.baPlaceMax);
         return true;
      }

      int charges = st.get(RespawnAnchorBlock.CHARGES);
      boolean charge = charges <= 0;
      int slot;
      if (charge) {
         slot = hotbarSlot(p, Items.GLOWSTONE);
      } else {
         ItemStack cur = inv.getStack(inv.selectedSlot);
         slot = !cur.isOf(Items.GLOWSTONE) && !cur.isOf(Items.RESPAWN_ANCHOR) ? inv.selectedSlot : safeSlot(p);
      }

      if (slot < 0) {
         aPos = null;
         return false;
      }

      if (inv.selectedSlot != slot) {
         inv.selectedSlot = slot;
         cd = Legit.rand(Config.baObsMin, Config.baObsMax);
         return true;
      }

      BlockHitResult bhr;
      if (walls) {
         bhr = new BlockHitResult(c, Direction.UP, aPos, false);
      } else {
         Legit.aim(c, Config.baAim, Config.baNoise);
         if (!(mc.crosshairTarget instanceof BlockHitResult b) || b.getType() != HitResult.Type.BLOCK || !b.getBlockPos().equals(aPos)) {
            return true; // nisan henuz anchor'in uzerinde degil
         }

         bhr = b;
      }

      if (cd > 0) {
         return true;
      }

      if (Legit.miss(Config.baMiss)) {
         cd = 2;
         return true;
      }

      mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
      p.swingHand(Hand.MAIN_HAND);
      cd = Legit.rand(Config.baPlaceMin, Config.baPlaceMax);
      if (!charge) {
         aPos = null; // patladi; bir sonraki tickte yeni kombo planlanir
      }

      return true;
   }

   private static AnchorPlan findAnchor(ClientPlayerEntity p, PlayerEntity target, Vec3d eye, float selfHp, boolean walls) {
      double reach = Math.min(Config.baPlaceRange, 4.5F);
      double reach2 = reach * reach;
      int r = (int)Math.ceil(reach);
      BlockPos o = p.getBlockPos();
      Vec3d tp = target.getPos();
      AnchorPlan best = null;
      double bestScore = -999.0;
      statTd = 0.0F;
      statSd = 999.0F;
      statGeo = 0;

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               BlockPos pos = o.add(dx, dy, dz);
               Vec3d cc = Vec3d.ofCenter(pos);
               if (eye.squaredDistanceTo(cc) > reach2 || cc.squaredDistanceTo(tp) > 100.0) {
                  continue;
               }

               if (!mc.world.getBlockState(pos).isAir() || !mc.world.getOtherEntities(null, new Box(pos)).isEmpty()) {
                  continue;
               }

               float td = Mods.dmg(cc, target, 10.0);
               float sd = selfDmg(cc, p, 10.0);
               statGeo++;
               if (td > statTd) {
                  statTd = td;
               }

               if (td >= Config.baMinDmg && sd < statSd) {
                  statSd = sd;
               }

               if (td < Config.baMinDmg || sd > Config.baMaxSelf || sd >= selfHp - 1.0F) {
                  continue;
               }

               double score = (double)td - (double)sd * 0.7;
               if (score <= bestScore) {
                  continue;
               }

               for (Direction d : Direction.values()) {
                  BlockPos nb = pos.offset(d);
                  BlockState ns = mc.world.getBlockState(nb);
                  if (ns.isAir() || !ns.isFullCube(mc.world, nb) || ns.hasBlockEntity() || NO_SUPPORT.contains(ns.getBlock())) {
                     continue;
                  }

                  Direction face = d.getOpposite();
                  Vec3d sc = Vec3d.ofCenter(nb);
                  Vec3d fc = sc.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                  if (eye.squaredDistanceTo(fc) > reach2) {
                     continue;
                  }

                  Vec3d inner = sc.add(face.getOffsetX() * 0.497, face.getOffsetY() * 0.497, face.getOffsetZ() * 0.497);
                  if (walls || los(eye, inner, nb, p)) {
                     best = new AnchorPlan(pos, nb, face);
                     bestScore = score;
                     break;
                  }
               }
            }
         }
      }

      return best;
   }

   private static int hotbarSlot(ClientPlayerEntity p, Item item) {
      for (int i = 0; i < 9; i++) {
         if (p.getInventory().getStack(i).isOf(item)) {
            return i;
         }
      }

      return -1;
   }

   private static boolean hasCrystal(ClientPlayerEntity p) {
      return p.getOffHandStack().isOf(Items.END_CRYSTAL) || hotbarSlot(p, Items.END_CRYSTAL) >= 0;
   }

   /** Anchor'i patlatmak icin glowstone / anchor olmayan bir hotbar slotu (bos slot da olur). */
   private static int safeSlot(ClientPlayerEntity p) {
      for (int i = 0; i < 9; i++) {
         ItemStack st = p.getInventory().getStack(i);
         if (!st.isOf(Items.GLOWSTONE) && !st.isOf(Items.RESPAWN_ANCHOR)) {
            return i;
         }
      }

      return -1;
   }

   /** Verilen blok yuzune sag tikla (nisan al ya da duvar arkasindan dogrudan). */
   private static void place(ClientPlayerEntity p, Hand hand, BlockPos block, Direction face, Vec3d hit, boolean walls, float dMin, float dMax) {
      BlockHitResult bhr;
      if (walls) {
         bhr = new BlockHitResult(hit, face, block, false);
      } else {
         Legit.aim(hit, Config.baAim, Config.baNoise);
         if (!(mc.crosshairTarget instanceof BlockHitResult b)
            || b.getType() != HitResult.Type.BLOCK
            || !b.getBlockPos().equals(block)
            || b.getSide() != face) {
            return;
         }

         bhr = b;
      }

      if (cd > 0) {
         return;
      }

      if (Legit.miss(Config.baMiss)) {
         cd = 2;
         return;
      }

      mc.interactionManager.interactBlock(p, hand, bhr);
      p.swingHand(hand);
      cd = Legit.rand(dMin, dMax);
   }

   private static int hotbarObsidian(ClientPlayerEntity p) {
      return hotbarSlot(p, Items.OBSIDIAN);
   }

   private static boolean valid(Plan pl) {
      BlockState st = mc.world.getBlockState(pl.base);
      boolean base = st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK);
      if (pl.needObs ? !st.isAir() : !base) {
         return false;
      }

      return mc.world.isAir(pl.base.up());
   }

   private static boolean los(Vec3d eye, Vec3d point, BlockPos expected, ClientPlayerEntity p) {
      BlockHitResult r = mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
      return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(expected);
   }

   private static Plan findPlan(ClientPlayerEntity p, PlayerEntity target, Vec3d eye, float selfHp, boolean walls) {
      double reach = Math.min(Config.baPlaceRange, 4.5F);
      double reach2 = reach * reach;
      int r = (int)Math.ceil(reach);
      BlockPos o = p.getBlockPos();
      Vec3d tp = target.getPos();
      boolean allowObs = Config.baObsidian >= 0.5F && hotbarObsidian(p) >= 0;
      Plan best = null;
      double bestScore = -999.0;
      statTd = 0.0F;
      statSd = 999.0F;
      statGeo = 0;

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               BlockPos pos = o.add(dx, dy, dz);
               Vec3d cc = new Vec3d((double)pos.getX() + 0.5, (double)pos.getY() + 1.0, (double)pos.getZ() + 0.5);
               if (eye.squaredDistanceTo(cc) > reach2 || cc.squaredDistanceTo(tp) > 64.0) {
                  continue;
               }

               BlockState st = mc.world.getBlockState(pos);
               boolean isBase = st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK);
               if (!isBase && !(allowObs && st.isAir())) {
                  continue;
               }

               BlockPos up = pos.up();
               if (!mc.world.isAir(up)) {
                  continue;
               }

               Box crystalBox = new Box((double)up.getX(), (double)up.getY(), (double)up.getZ(), (double)(up.getX() + 1), (double)(up.getY() + 2), (double)(up.getZ() + 1));
               if (!mc.world.getOtherEntities(null, crystalBox).isEmpty()) {
                  continue;
               }

               if (!isBase && !mc.world.getOtherEntities(null, new Box(pos)).isEmpty()) {
                  continue;
               }

               float td = Mods.dmg(cc, target);
               float sd = selfDmg(cc, p);
               statGeo++;
               if (td > statTd) {
                  statTd = td;
               }

               if (td >= Config.baMinDmg && sd < statSd) {
                  statSd = sd;
               }

               if (td < Config.baMinDmg || sd > Config.baMaxSelf || sd >= selfHp - 1.0F) {
                  continue;
               }

               double score = (double)td - (double)sd * 0.7 - (isBase ? 0.0 : 1.5);
               if (score <= bestScore) {
                  continue;
               }

               if (isBase) {
                  Vec3d top = new Vec3d(cc.x, (double)pos.getY() + 0.997, cc.z);
                  if (walls || los(eye, top, pos, p)) {
                     best = new Plan(pos, false, null, null);
                     bestScore = score;
                  }

                  continue;
               }

               for (Direction d : Direction.values()) {
                  BlockPos nb = pos.offset(d);
                  BlockState ns = mc.world.getBlockState(nb);
                  if (ns.isAir() || !ns.isFullCube(mc.world, nb) || ns.hasBlockEntity() || NO_SUPPORT.contains(ns.getBlock())) {
                     continue;
                  }

                  Direction face = d.getOpposite();
                  Vec3d c = Vec3d.ofCenter(nb);
                  Vec3d fc = c.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                  if (eye.squaredDistanceTo(fc) > reach2) {
                     continue;
                  }

                  Vec3d inner = c.add(face.getOffsetX() * 0.497, face.getOffsetY() * 0.497, face.getOffsetZ() * 0.497);
                  if (walls || los(eye, inner, nb, p)) {
                     best = new Plan(pos, true, nb, face);
                     bestScore = score;
                     break;
                  }
               }
            }
         }
      }

      return best;
   }
}
