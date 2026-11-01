package kuro;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/** Yardimci hile modulleri: AutoClicker, AutoTool, AutoEat, ChestStealer, AutoArmor, AutoRespawn, AutoLog, Step. */
public final class Auto {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private Auto() {
   }

   private static boolean usable() {
      return mc.player != null && mc.world != null && mc.currentScreen == null && !Freecam.active;
   }

   /** Tusu fiziksel durumuna geri dondurur (sahte basma sonrasi). */
   private static void release(KeyBinding kb) {
      InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
      long win = mc.getWindow().getHandle();
      boolean ph = k.getCategory() == InputUtil.Type.KEYSYM
         ? InputUtil.isKeyPressed(win, k.getCode())
         : k.getCategory() == InputUtil.Type.MOUSE && GLFW.glfwGetMouseButton(win, k.getCode()) == GLFW.GLFW_PRESS;
      kb.setPressed(ph);
   }

   private static int toSlotId(int inv) {
      return inv < 9 ? 36 + inv : inv;
   }

   // ------------------------------------------------------------------ AutoClicker
   // Sol tik basiliyken, nisan bir varliga denk geldiginde rastgele CPS ile vurur.
   private static int ckCd;

   public static void clicker() {
      if (ckCd > 0) {
         ckCd--;
      }

      if (!usable() || ckCd > 0 || !mc.options.attackKey.isPressed()) {
         return;
      }

      if (!(mc.crosshairTarget instanceof EntityHitResult ehr) || !ehr.getEntity().isAttackable()) {
         return;
      }

      ClientPlayerEntity p = mc.player;
      mc.interactionManager.attackEntity(p, ehr.getEntity());
      p.swingHand(Hand.MAIN_HAND);
      float lo = Math.min(Config.ckMin, Config.ckMax);
      float hi = Math.max(Config.ckMin, Config.ckMax);
      float cps = lo + ThreadLocalRandom.current().nextFloat() * (hi - lo);
      ckCd = Math.max(1, Math.round(20.0F / Math.max(1.0F, cps)));
   }

   // ------------------------------------------------------------------ AutoTool
   private static int atRestore = -1;
   private static int atWait = -1;
   private static int atBack;

   public static void tool() {
      ClientPlayerEntity p = mc.player;
      BlockHitResult bhr = mc.crosshairTarget instanceof BlockHitResult b2 && b2.getType() == HitResult.Type.BLOCK ? b2 : null;
      if (!usable() || bhr == null || !mc.options.attackKey.isPressed()) {
         atWait = -1;
         if (atRestore >= 0 && ++atBack > 3) {
            p.getInventory().selectedSlot = atRestore;
            atRestore = -1;
         }

         return;
      }

      atBack = 0;
      BlockState st = mc.world.getBlockState(bhr.getBlockPos());
      if (st.isAir()) {
         return;
      }

      int cur = p.getInventory().selectedSlot;
      float bestS = p.getInventory().getStack(cur).getMiningSpeedMultiplier(st);
      int best = -1;
      for (int i = 0; i < 9; i++) {
         float s = p.getInventory().getStack(i).getMiningSpeedMultiplier(st);
         if (s > bestS * 1.05F) {
            bestS = s;
            best = i;
         }
      }

      if (best < 0) {
         return;
      }

      if (atWait < 0) {
         atWait = Legit.rand(Config.atMin, Config.atMax);
      }

      if (atWait-- > 0) {
         return;
      }

      atWait = -1;
      if (atRestore < 0) {
         atRestore = cur;
      }

      p.getInventory().selectedSlot = best;
   }

   public static void toolOff() {
      if (atRestore >= 0 && mc.player != null) {
         mc.player.getInventory().selectedSlot = atRestore;
      }

      atRestore = -1;
   }

   // ------------------------------------------------------------------ AutoEat
   private static boolean eating;
   private static int eatRestore = -1;
   private static int eatWait;

   private static boolean isFood(ItemStack s) {
      return s.get(DataComponentTypes.FOOD) != null
         && !s.isOf(Items.POISONOUS_POTATO)
         && !s.isOf(Items.PUFFERFISH)
         && !s.isOf(Items.SPIDER_EYE)
         && !s.isOf(Items.ROTTEN_FLESH);
   }

   public static void eat() {
      ClientPlayerEntity p = mc.player;
      if (eating) {
         eatWait++;
         mc.options.useKey.setPressed(true);
         if (eatWait > 5 && !p.isUsingItem() || mc.currentScreen != null) {
            eatOff();
         }

         return;
      }

      int food = p.getHungerManager().getFoodLevel();
      if (!usable() || p.isUsingItem() || food > Config.aeHunger || food >= 20) {
         return;
      }

      for (int i = 0; i < 9; i++) {
         if (isFood(p.getInventory().getStack(i))) {
            eatRestore = p.getInventory().selectedSlot;
            p.getInventory().selectedSlot = i;
            eating = true;
            eatWait = 0;
            return;
         }
      }
   }

   public static void eatOff() {
      if (eating && mc.player != null) {
         release(mc.options.useKey);
         if (eatRestore >= 0) {
            mc.player.getInventory().selectedSlot = eatRestore;
         }
      }

      eating = false;
      eatRestore = -1;
   }

   // ------------------------------------------------------------------ ChestStealer
   private static int csCd;
   private static Object csScreen;
   private static int csLastSlot = -1;
   private static int csTries;

   public static void stealer() {
      if (!(mc.currentScreen instanceof GenericContainerScreen gs) || mc.player == null) {
         csScreen = null;
         return;
      }

      if (csScreen != gs) {
         csScreen = gs;
         csLastSlot = -1;
         csTries = 0;
         csCd = Legit.rand(Config.csMin + 2, Config.csMax + 4);
      }

      if (csCd-- > 0) {
         return;
      }

      GenericContainerScreenHandler h = gs.getScreenHandler();
      int n = h.getRows() * 9;
      for (int i = 0; i < n; i++) {
         if (!h.getSlot(i).hasStack()) {
            continue;
         }

         if (i == csLastSlot) {
            if (++csTries > 3) {
               continue; // envanter dolu olabilir; bu slotu atla
            }
         } else {
            csLastSlot = i;
            csTries = 0;
         }

         mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
         csCd = Legit.rand(Config.csMin, Config.csMax);
         return;
      }

      if (Config.csClose >= 0.5F && csTries <= 3) {
         mc.player.closeHandledScreen();
      }
   }

   // ------------------------------------------------------------------ AutoArmor
   private static int aaCd;
   private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};

   public static void armor() {
      ClientPlayerEntity p = mc.player;
      if (aaCd > 0) {
         aaCd--;
         return;
      }

      if (mc.world == null || !(mc.currentScreen == null || mc.currentScreen instanceof InventoryScreen) || Freecam.active) {
         return;
      }

      for (int a = 0; a < 4; a++) {
         if (!p.getEquippedStack(ARMOR[a]).isEmpty()) {
            continue;
         }

         for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith(SUFFIX[a])) {
               mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, toSlotId(i), 0, SlotActionType.QUICK_MOVE, p);
               aaCd = Legit.rand(Config.aaMin, Config.aaMax);
               return;
            }
         }
      }
   }

   // ------------------------------------------------------------------ AutoRespawn
   private static int rsCd = -1;

   public static void respawn() {
      if (mc.currentScreen instanceof DeathScreen && mc.player != null) {
         if (rsCd < 0) {
            rsCd = Legit.rand(Config.arMin, Config.arMax);
         }

         if (rsCd-- == 0) {
            mc.player.requestRespawn();
            rsCd = -1;
         }
      } else {
         rsCd = -1;
      }
   }

   // ------------------------------------------------------------------ AutoLog
   public static void log() {
      ClientPlayerEntity p = mc.player;
      if (mc.isInSingleplayer() || mc.getNetworkHandler() == null || !p.isAlive()) {
         return;
      }

      float hp = p.getHealth() + p.getAbsorptionAmount();
      if (hp <= Config.alHealth) {
         Kuro.setOn(Kuro.AUTOLOG, false);
         mc.getNetworkHandler().getConnection().disconnect(Text.literal("[Kuro] AutoLog: can " + Math.round(hp)));
      }
   }

   // ------------------------------------------------------------------ Step
   private static final Identifier STEP_ID = Identifier.tryParse("kuro:step");
   private static RegistryEntry<EntityAttribute> stepAttr;

   public static void step() {
      ClientPlayerEntity p = mc.player;
      if (stepAttr == null) {
         Identifier id = Identifier.tryParse("minecraft:step_height");
         if (id != null) {
            var o = Registries.ATTRIBUTE.getEntry(id);
            if (o.isPresent()) {
               stepAttr = o.get();
            }
         }
      }

      if (p == null || stepAttr == null) {
         return;
      }

      EntityAttributeInstance inst = p.getAttributeInstance(stepAttr);
      if (inst == null) {
         return;
      }

      EntityAttributeModifier cur = inst.getModifier(STEP_ID);
      if (!Kuro.on[Kuro.STEP]) {
         if (cur != null) {
            inst.removeModifier(STEP_ID);
         }

         return;
      }

      double add = (double)Config.stepHeight - inst.getBaseValue();
      if (cur == null || Math.abs(cur.value() - add) > 1.0E-6) {
         if (cur != null) {
            inst.removeModifier(STEP_ID);
         }

         inst.addTemporaryModifier(new EntityAttributeModifier(STEP_ID, add, EntityAttributeModifier.Operation.ADD_VALUE));
      }
   }
}
