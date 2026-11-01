package kuro;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Yeni savaş modülleri (hepsi rastgele gecikme ve insan benzeri davranışla): CobWebPlacer, ElytraSwap, JumpReset, Misplace, Reach, ShieldBreaker. */
public final class Combat {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Combat() {
   }

   private static boolean usable() {
      return mc.player != null && mc.world != null && mc.currentScreen == null && !Freecam.active;
   }

   // ------------------------------------------------------------------ CobWebPlacer
   // Sağ tık basılıyken en yakın oyuncunun ayağına (gerçekten nişan alarak) cobweb koyar.
   private static int webCd;
   private static int webRestore = -1;
   private static int webRestoreCd;

   public static void cobweb() {
      ClientPlayerEntity p = mc.player;
      if (webCd > 0) {
         webCd--;
      }

      if (webRestoreCd > 0 && --webRestoreCd == 0 && webRestore >= 0) {
         p.getInventory().selectedSlot = webRestore;
         webRestore = -1;
      }

      if (!usable() || !mc.options.useKey.isPressed()) {
         if (webRestore >= 0 && webRestoreCd == 0) {
            p.getInventory().selectedSlot = webRestore;
            webRestore = -1;
         }

         return;
      }

      PlayerEntity target = null;
      double best = (double)Config.webRange * Config.webRange;
      for (PlayerEntity o : mc.world.getPlayers()) {
         if (o != p && o.isAlive() && !o.isSpectator()) {
            double d = p.squaredDistanceTo(o);
            if (d < best) {
               best = d;
               target = o;
            }
         }
      }

      if (target == null) {
         return;
      }

      BlockPos feet = target.getBlockPos();
      BlockPos below = feet.down();
      if (!mc.world.getBlockState(feet).isAir() || mc.world.getBlockState(below).isAir()) {
         return;
      }

      int slot = -1;
      for (int i = 0; i < 9; i++) {
         if (p.getInventory().getStack(i).isOf(Items.COBWEB)) {
            slot = i;
            break;
         }
      }

      if (slot < 0) {
         return;
      }

      Vec3d face = new Vec3d((double)below.getX() + 0.5, (double)below.getY() + 1.0, (double)below.getZ() + 0.5);
      if (p.getEyePos().squaredDistanceTo(face) > 20.0) {
         return;
      }

      if (p.getInventory().selectedSlot != slot) {
         if (webRestore < 0) {
            webRestore = p.getInventory().selectedSlot;
         }

         p.getInventory().selectedSlot = slot;
         webCd = Legit.rand(Config.webMin, Config.webMax);
         return;
      }

      webRestoreCd = 0;
      Legit.aim(face, Config.webAim, 0.4F);
      if (webCd > 0) {
         return;
      }

      if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getBlockPos().equals(below) && bhr.getSide() == Direction.UP) {
         if (Legit.miss(Config.macroMiss)) {
            webCd = 2;
            return;
         }

         mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
         p.swingHand(Hand.MAIN_HAND);
         webCd = Legit.rand(Config.webMin, Config.webMax) + 2;
         if (webRestore >= 0) {
            webRestoreCd = Legit.rand(3.0F, 6.0F);
         }
      }
   }

   // ------------------------------------------------------------------ ElytraSwap
   // Tuşa basınca giyilen elytra <-> göğüslük değişir; envanter tıklamaları rastgele aralıklarla yapılır.
   private static int esStep = -1;
   private static int esCd;
   private static int esSrc;

   private static boolean isChestplate(ItemStack s) {
      if (s.isEmpty() || s.isOf(Items.ELYTRA)) {
         return false;
      }

      EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
      return eq != null && eq.slot() == EquipmentSlot.CHEST;
   }

   private static int toSlotId(int inv) {
      return inv < 9 ? 36 + inv : inv;
   }

   /** @return eşya bulunup işlem başladıysa true */
   public static boolean elytraSwap() {
      ClientPlayerEntity p = mc.player;
      if (p == null || esStep >= 0 || !p.currentScreenHandler.getCursorStack().isEmpty()) {
         return false;
      }

      boolean wearingElytra = p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
      for (int i = 0; i < 36; i++) {
         ItemStack s = p.getInventory().getStack(i);
         if (wearingElytra ? isChestplate(s) : s.isOf(Items.ELYTRA)) {
            esSrc = toSlotId(i);
            esStep = 0;
            esCd = Legit.rand(Config.esMin, Config.esMax);
            return true;
         }
      }

      return false;
   }

   public static void elytraTick() {
      if (esStep < 0) {
         return;
      }

      ClientPlayerEntity p = mc.player;
      if (p == null || mc.interactionManager == null) {
         esStep = -1;
         return;
      }

      if (esCd-- > 0) {
         return;
      }

      int sync = p.playerScreenHandler.syncId;
      if (esStep == 0) {
         mc.interactionManager.clickSlot(sync, esSrc, 0, SlotActionType.PICKUP, p);
      } else if (esStep == 1) {
         mc.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, p);
      } else {
         mc.interactionManager.clickSlot(sync, esSrc, 0, SlotActionType.PICKUP, p);
         esStep = -1;
         return;
      }

      esStep++;
      esCd = Legit.rand(Config.esMin, Config.esMax);
   }

   // ------------------------------------------------------------------ JumpReset
   // Vurulunca (rastgele gecikmeyle) zıplayarak itmeyi azaltır.
   private static boolean jrArmed = true;
   private static int jrTimer = -1;
   private static boolean jrHeld;

   private static void jrRelease() {
      KeyBinding jk = mc.options.jumpKey;
      InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(jk);
      boolean phys = k.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(mc.getWindow().getHandle(), k.getCode());
      jk.setPressed(phys);
      jrHeld = false;
   }

   public static void jumpReset() {
      ClientPlayerEntity p = mc.player;
      if (jrHeld) {
         jrRelease();
      }

      if (p.hurtTime == 0) {
         jrArmed = true;
      } else if (jrArmed && p.maxHurtTime > 0 && p.hurtTime >= p.maxHurtTime - 1) {
         jrArmed = false;
         if (p.isOnGround() && ThreadLocalRandom.current().nextFloat() * 100.0F < Config.jrChance) {
            jrTimer = Legit.rand(Config.jrMin, Config.jrMax);
         }
      }

      if (jrTimer >= 0 && jrTimer-- == 0) {
         if (usable() && p.isOnGround() && !p.isTouchingWater() && !p.isGliding()) {
            mc.options.jumpKey.setPressed(true);
            jrHeld = true;
         }

         jrTimer = -1;
      }
   }

   public static void jumpResetOff() {
      if (jrHeld) {
         jrRelease();
      }

      jrTimer = -1;
   }

   // ------------------------------------------------------------------ Misplace
   // Sağ tık basılıyken nişan bir oyuncunun/varlığın üstündeyse, onun ARKASINDAKİ obsidyen/bedrock'a kristal koyar.
   private static int mpCd;

   public static void misplace() {
      if (mpCd > 0) {
         mpCd--;
      }

      if (!usable() || mpCd > 0 || !mc.options.useKey.isPressed()) {
         return;
      }

      ClientPlayerEntity p = mc.player;
      Hand hand = p.getOffHandStack().isOf(Items.END_CRYSTAL)
         ? Hand.OFF_HAND
         : (p.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND : null);
      if (hand == null) {
         return;
      }

      if (!(mc.crosshairTarget instanceof EntityHitResult ehr) || ehr.getEntity() instanceof EndCrystalEntity) {
         return;
      }

      HitResult bl = p.raycast((double)Config.mpRange, 1.0F, false);
      if (!(bl instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS || bhr.getSide() != Direction.UP) {
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

      Box box = new Box((double)up.getX(), (double)up.getY(), (double)up.getZ(), (double)(up.getX() + 1), (double)(up.getY() + 2), (double)(up.getZ() + 1));
      if (!mc.world.getOtherEntities(null, box).isEmpty()) {
         return;
      }

      if (Legit.miss(Config.macroMiss)) {
         mpCd = 2;
         return;
      }

      mc.interactionManager.interactBlock(p, hand, bhr);
      p.swingHand(hand);
      mpCd = Legit.delay(Config.mpDelay);
   }

   // ------------------------------------------------------------------ Reach
   // Saldırı menzilini attribute ile (vanilla'nın kendi sistemiyle) küçük miktarda uzatır.
   private static final Identifier REACH_ID = Identifier.tryParse("kuro:reach");
   private static RegistryEntry<EntityAttribute> reachAttr;

   private static RegistryEntry<EntityAttribute> reachAttr() {
      if (reachAttr == null) {
         for (String s : new String[]{"minecraft:entity_interaction_range", "minecraft:player.entity_interaction_range"}) {
            Identifier id = Identifier.tryParse(s);
            if (id != null) {
               var o = Registries.ATTRIBUTE.getEntry(id);
               if (o.isPresent()) {
                  reachAttr = o.get();
                  break;
               }
            }
         }
      }

      return reachAttr;
   }

   public static void reach() {
      ClientPlayerEntity p = mc.player;
      RegistryEntry<EntityAttribute> a = reachAttr();
      if (p == null || a == null) {
         return;
      }

      EntityAttributeInstance inst = p.getAttributeInstance(a);
      if (inst == null) {
         return;
      }

      EntityAttributeModifier cur = inst.getModifier(REACH_ID);
      if (!Kuro.on[Kuro.REACH]) {
         if (cur != null) {
            inst.removeModifier(REACH_ID);
         }

         return;
      }

      double add = (double)Config.reachValue - inst.getBaseValue();
      if (cur == null || Math.abs(cur.value() - add) > 1.0E-6) {
         if (cur != null) {
            inst.removeModifier(REACH_ID);
         }

         inst.addTemporaryModifier(new EntityAttributeModifier(REACH_ID, add, EntityAttributeModifier.Operation.ADD_VALUE));
      }
   }

   // ------------------------------------------------------------------ ShieldBreaker
   // Nişan aldığın oyuncu kalkan tutuyorsa hotbar'daki baltaya geçer, vurur, eski slota döner.
   private static int sbTimer = -1;
   private static int sbRestore = -1;
   private static int sbBackCd;

   public static void shieldBreaker() {
      ClientPlayerEntity p = mc.player;
      if (sbBackCd > 0 && --sbBackCd == 0 && sbRestore >= 0) {
         p.getInventory().selectedSlot = sbRestore;
         sbRestore = -1;
      }

      if (!usable()) {
         return;
      }

      if (!(mc.crosshairTarget instanceof EntityHitResult ehr)
         || !(ehr.getEntity() instanceof PlayerEntity t)
         || !t.isAlive()
         || !t.isBlocking()) {
         sbTimer = -1;
         if (sbRestore >= 0 && sbBackCd == 0) {
            sbBackCd = Legit.rand(3.0F, 6.0F);
         }

         return;
      }

      sbBackCd = 0;
      if (!p.getMainHandStack().isIn(ItemTags.AXES)) {
         int slot = -1;
         for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isIn(ItemTags.AXES)) {
               slot = i;
               break;
            }
         }

         if (slot < 0) {
            return;
         }

         if (sbTimer < 0) {
            sbTimer = Legit.rand(Config.sbMin, Config.sbMax);
         }

         if (sbTimer-- > 0) {
            return;
         }

         sbTimer = -1;
         if (sbRestore < 0) {
            sbRestore = p.getInventory().selectedSlot;
         }

         p.getInventory().selectedSlot = slot;
         return;
      }

      if (Config.sbAuto < 0.5F) {
         return;
      }

      if (Config.sbCharge >= 0.5F && p.getAttackCooldownProgress(0.5F) < 0.95F) {
         return;
      }

      if (sbTimer < 0) {
         sbTimer = Legit.rand(Config.sbMin, Config.sbMax);
      }

      if (sbTimer-- > 0) {
         return;
      }

      sbTimer = -1;
      if (Legit.miss(Config.macroMiss)) {
         return;
      }

      mc.interactionManager.attackEntity(p, t);
      p.swingHand(Hand.MAIN_HAND);
      if (sbRestore >= 0) {
         sbBackCd = Legit.rand(3.0F, 6.0F);
      }
   }
}
