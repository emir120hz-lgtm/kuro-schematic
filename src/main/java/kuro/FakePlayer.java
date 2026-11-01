package kuro;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Sahte oyuncu: sadece bizim ekranimizda var; dunyanin oyuncu listesinde oldugu icin butun modullerimiz onu normal oyuncu gibi hedefler.
 * Kristal patlamalarindan ve (sunucuya gitmeden) vuruslarimizdan hasar alir; istege bagli totem tutar.
 */
public final class FakePlayer {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private static OtherClientPlayerEntity fake;
   private static ClientWorld world;
   private static boolean needSpawn = true;
   private static int respawnTimer = -1;
   private static int deadTicks;
   private static boolean lastTotem;
   private static int lastArmor = -1;
   private static float lastHealth = -1.0F;
   private static Map<Integer, Vec3d> crystals = new HashMap<>();

   private FakePlayer() {
   }

   private static RegistryEntry<EntityAttribute> attr(String... ids) {
      for (String s : ids) {
         Identifier id = Identifier.tryParse(s);
         if (id != null) {
            var o = Registries.ATTRIBUTE.getEntry(id);
            if (o.isPresent()) {
               return o.get();
            }
         }
      }

      return null;
   }

   private static void setBase(RegistryEntry<EntityAttribute> a, double v) {
      if (a != null && fake != null) {
         EntityAttributeInstance inst = fake.getAttributeInstance(a);
         if (inst != null) {
            inst.setBaseValue(v);
         }
      }
   }

   // ------------------------------------------------------------------ yasam dongusu

   private static void spawn() {
      ClientPlayerEntity p = mc.player;
      OtherClientPlayerEntity f = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "FakePlayer"));
      f.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
      f.setHeadYaw(p.getYaw());
      f.setBodyYaw(p.getYaw());
      f.setId(-4242); // sunucu kimlikleriyle (pozitif) cakismasin
      fake = f;
      world = mc.world;
      deadTicks = 0;
      lastArmor = -1;
      lastHealth = -1.0F;
      lastTotem = false;
      applySettings(true);
      mc.world.addEntity(f);
      crystals.clear();
   }

   /** Ayarlari (can, zirh, totem) canli uygular. */
   private static void applySettings(boolean force) {
      if (fake == null) {
         return;
      }

      float hp = Math.max(1.0F, Config.fpHealth);
      if (force || hp != lastHealth) {
         setBase(attr("minecraft:max_health", "minecraft:generic.max_health"), hp);
         fake.setHealth(hp);
         lastHealth = hp;
      }

      int armor = Math.round(Config.fpArmor);
      if (force || armor != lastArmor) {
         setBase(attr("minecraft:armor", "minecraft:generic.armor"), armor);
         boolean show = armor > 0;
         fake.equipStack(EquipmentSlot.HEAD, show ? new ItemStack(Items.NETHERITE_HELMET) : ItemStack.EMPTY);
         fake.equipStack(EquipmentSlot.CHEST, show ? new ItemStack(Items.NETHERITE_CHESTPLATE) : ItemStack.EMPTY);
         fake.equipStack(EquipmentSlot.LEGS, show ? new ItemStack(Items.NETHERITE_LEGGINGS) : ItemStack.EMPTY);
         fake.equipStack(EquipmentSlot.FEET, show ? new ItemStack(Items.NETHERITE_BOOTS) : ItemStack.EMPTY);
         lastArmor = armor;
      }

      boolean totem = Config.fpTotem >= 0.5F;
      if (force || totem != lastTotem) {
         fake.equipStack(EquipmentSlot.OFFHAND, totem ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
         lastTotem = totem;
      }
   }

   public static void tick() {
      if (mc.world == null || mc.player == null) {
         return;
      }

      if (world != mc.world) {
         fake = null;
         world = mc.world;
         needSpawn = true;
         crystals.clear();
      }

      if (fake == null) {
         if (needSpawn) {
            needSpawn = false;
            spawn();
         } else if (respawnTimer > 0 && --respawnTimer == 0) {
            spawn();
         }

         return;
      }

      if (fake.isRemoved()) {
         fake = null;
         respawnTimer = Config.fpRespawn >= 0.5F ? 60 : -1;
         return;
      }

      if (fake.getHealth() <= 0.0F) {
         if (++deadTicks > 30) {
            mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
         }

         return;
      }

      applySettings(false);
      trackCrystals();
   }

   /** Yakindaki bir kristal yok olduysa patladi say ve hasari uygula. */
   private static void trackCrystals() {
      Map<Integer, Vec3d> now = new HashMap<>();
      for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, fake.getBoundingBox().expand(16.0), e -> true)) {
         now.put(c.getId(), c.getPos());
      }

      float total = 0.0F;
      for (Map.Entry<Integer, Vec3d> en : crystals.entrySet()) {
         if (!now.containsKey(en.getKey())) {
            total += Mods.dmg(en.getValue(), fake);
         }
      }

      crystals = now;
      if (total > 0.0F) {
         hurt(total);
      }
   }

   public static void despawn() {
      if (fake != null && mc.world != null) {
         mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
      }

      fake = null;
      needSpawn = true;
      respawnTimer = -1;
      crystals.clear();
   }

   /** Dunyadan cikinca. */
   public static void reset() {
      fake = null;
      world = null;
      needSpawn = true;
      respawnTimer = -1;
      crystals.clear();
   }

   // ------------------------------------------------------------------ hasar

   private static void hurt(float amount) {
      if (fake == null || amount <= 0.0F || fake.getHealth() <= 0.0F || Config.fpDmg < 0.5F) {
         return;
      }

      float left = fake.getHealth() - amount;
      fake.handleStatus((byte)2);
      if (left > 0.0F) {
         fake.setHealth(left);
         return;
      }

      boolean off = fake.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
      boolean main = fake.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
      if (off || main) {
         fake.setHealth(1.0F);
         fake.handleStatus((byte)35);
         if (Config.fpInfinite < 0.5F) {
            fake.equipStack(off ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            lastTotem = fake.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
            if (!lastTotem && Config.fpTotem >= 0.5F) {
               // totem bitti: ayar acik kalsa da yeniden verme (sonsuz kapali)
               lastTotem = true;
            }
         }

         return;
      }

      fake.setHealth(0.0F);
      fake.handleStatus((byte)3);
   }

   /**
    * Bir vurus sahte oyuncuya gidiyorsa sunucuya gonderilmez; hasar burada hesaplanir.
    * @return true ise vanilla saldiri iptal edilir
    */
   public static boolean melee(PlayerEntity p, Entity target) {
      if (fake == null || target != fake) {
         return false;
      }

      if (fake.hurtTime <= 0 && fake.getHealth() > 0.0F) {
         RegistryEntry<EntityAttribute> a = attr("minecraft:attack_damage", "minecraft:generic.attack_damage");
         double base = a != null ? p.getAttributeValue(a) : 1.0;
         float cd = p.getAttackCooldownProgress(0.5F);
         float dmg = (float)base * (0.2F + cd * cd * 0.8F);
         if (cd > 0.9F && p.fallDistance > 0.0F && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()) {
            dmg *= 1.5F;
         }

         float armor = (float)fake.getArmor();
         dmg *= 1.0F - MathHelper.clamp(armor - dmg / 2.5F, armor * 0.2F, 20.0F) / 25.0F;
         hurt(dmg);
      }

      p.resetLastAttackedTicks();
      return true;
   }
}
