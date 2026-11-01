package kuro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Items;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;

public class Mods {
   static final MinecraftClient mc = MinecraftClient.getInstance();
   static final double MIN_TARGET_DMG = 5.0;
   static final double MAX_SELF_DMG = 6.0;
   static final int MIN_BASE_SCORE = 5;
   static final int MAX_HITS_PER_CHUNK = 2000;
   static final Map<Long, List<Hit>> storageHits = new HashMap<>();
   static final Map<Long, List<Hit>> blockHits = new HashMap<>();
   static final Set<Block> targets = new HashSet<>();
   static final Map<TagKey<Block>, Integer> tagTargets = new HashMap<>();
   static final Map<Block, Integer> targetColor = new HashMap<>();
   static final Set<String> logged = new HashSet<>();
   static final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
   static World lastWorld;
   static int timer = 0;
   static int flyTick = 0;
   static int crystalCd = 0;
   static int totemCd = 0;

   static void msg(String s) {
      if (mc.player != null) {
         mc.player.sendMessage(Text.literal("[Kuro] " + s), false);
      }
   }

   static void fly() {
      ClientPlayerEntity p = mc.player;
      double rad = Math.toRadians((double)p.getYaw());
      double fx = -Math.sin(rad);
      double fz = Math.cos(rad);
      double rx = -Math.cos(rad);
      double rz = -Math.sin(rad);
      double mx = 0.0;
      double mz = 0.0;
      double my = 0.0;
      GameOptions o = mc.options;
      if (Freecam.active) {
         if (++flyTick % 30 == 0) {
            p.setVelocity(0.0, -0.04, 0.0);
         } else {
            p.setVelocity(0.0, 0.0, 0.0);
         }

         return;
      }

      if (o.forwardKey.isPressed()) {
         mx += fx;
         mz += fz;
      }

      if (o.backKey.isPressed()) {
         mx -= fx;
         mz -= fz;
      }

      if (o.rightKey.isPressed()) {
         mx += rx;
         mz += rz;
      }

      if (o.leftKey.isPressed()) {
         mx -= rx;
         mz -= rz;
      }

      double len = Math.sqrt(mx * mx + mz * mz);
      if (len > 0.0) {
         mx = mx / len * Config.flyH;
         mz = mz / len * Config.flyH;
      }

      if (o.jumpKey.isPressed()) {
         my += Config.flyV;
      }

      if (o.sneakKey.isPressed()) {
         my -= Config.flyV;
      }

      if (my == 0.0 && ++flyTick % 30 == 0) {
         my = -0.04;
      }

      p.setVelocity(mx, my, mz);
   }

   // ------------------------------------------------------------------ AutoTotem
   // Legit mod: rastgele insan gibi gecikme, can esigi ve (hotbar'da ise) slot sec + F (el degistir) ile calisir;
   // totem sadece envanterdeyse envanter acikken tiklar. Legit kapaliyken eski hizli envanter tiklamasi.
   static int totemTimer = -1;
   static int totemPhase = 0; // 0 bos, 1 slot secildi (F bekliyor), 2 slot geri alinacak
   static int totemRestore = -1;

   static void totem() {
      ClientPlayerEntity p = mc.player;

      if (totemPhase == 1) {
         // slot secildi; simdi "ele al" (F) paketi
         if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(
               new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN)
            );
         }

         totemPhase = 2;
         return;
      }

      if (totemPhase == 2) {
         if (totemRestore >= 0) {
            p.getInventory().selectedSlot = totemRestore;
         }

         totemRestore = -1;
         totemPhase = 0;
         return;
      }

      if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         totemTimer = -1;
         return;
      }

      // can esigi: sadece canin (+ emilim) esigin altindayken doldur
      if (p.getHealth() + p.getAbsorptionAmount() > Config.totemHealth) {
         totemTimer = -1;
         return;
      }

      int hot = -1;
      for (int i = 0; i < 9; i++) {
         if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
            hot = i;
            break;
         }
      }

      int inv = -1;
      for (int i = 9; i < 36; i++) {
         if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
            inv = i;
            break;
         }
      }

      if (hot < 0 && inv < 0) {
         totemTimer = -1;
         return;
      }

      boolean legit = Config.totemLegit >= 0.5F;
      if (legit && hot < 0 && !(mc.currentScreen instanceof InventoryScreen)) {
         return; // legit: totem envanterde, insan gibi once envanteri acmalisin
      }

      if (!legit && mc.currentScreen instanceof HandledScreen) {
         return;
      }

      // rastgele gecikme
      if (totemTimer < 0) {
         int lo = Math.round(Math.min(Config.totemMin, Config.totemMax));
         int hi = Math.round(Math.max(Config.totemMin, Config.totemMax));
         totemTimer = lo + (hi > lo ? ThreadLocalRandom.current().nextInt(hi - lo + 1) : 0);
      }

      if (totemTimer-- > 0) {
         return;
      }

      totemTimer = -1;

      if (legit && hot >= 0 && !(mc.currentScreen instanceof InventoryScreen)) {
         totemRestore = p.getInventory().selectedSlot;
         p.getInventory().selectedSlot = hot;
         totemPhase = 1;
      } else {
         int i = hot >= 0 ? hot : inv;
         int slot = i < 9 ? 36 + i : i;
         mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
      }
   }

   static void crystal() {
      ClientPlayerEntity p = mc.player;
      if (crystalCd-- <= 0) {
         PlayerEntity target = null;
         double best = 144.0;

         for (PlayerEntity o : mc.world.getPlayers()) {
            if (o != p && o.isAlive() && !o.isSpectator()) {
               double d = p.squaredDistanceTo(o);
               if (d < best) {
                  best = d;
                  target = o;
               }
            }
         }

         if (target != null) {
            float selfHp = p.getHealth() + p.getAbsorptionAmount();
            Vec3d eye = p.getEyePos();
            EndCrystalEntity bestC = null;
            float bestTd = 0.0F;

            for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(5.0), e -> true)) {
               if (!(distToBox(eye, c.getBoundingBox()) > 3.0)) {
                  Vec3d cp = c.getPos();
                  float td = dmg(cp, target);
                  float sd = dmg(cp, p);
                  if (!(sd >= selfHp - 1.0F) && (!(td < 3.0F) || !(sd < 4.0F)) && td > bestTd) {
                     bestTd = td;
                     bestC = c;
                  }
               }
            }

            if (bestC != null) {
               mc.interactionManager.attackEntity(p, bestC);
               p.swingHand(Hand.MAIN_HAND);
               crystalCd = 1;
            } else {
               Hand hand = crystalHand(p);
               if (hand != null) {
                  BlockPos origin = p.getBlockPos();
                  BlockPos bestPos = null;
                  double bestScore = -999.0;

                  for (int dx = -4; dx <= 4; dx++) {
                     for (int dy = -4; dy <= 3; dy++) {
                        for (int dz = -4; dz <= 4; dz++) {
                           BlockPos pos = origin.add(dx, dy, dz);
                           Vec3d center = Vec3d.ofCenter(pos);
                           if (!(eye.squaredDistanceTo(center) > 20.25)) {
                              BlockState st = mc.world.getBlockState(pos);
                              if (st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK)) {
                                 BlockPos up = pos.up();
                                 if (mc.world.isAir(up)) {
                                    Box box = new Box(
                                       (double)up.getX(),
                                       (double)up.getY(),
                                       (double)up.getZ(),
                                       (double)(up.getX() + 1),
                                       (double)(up.getY() + 2),
                                       (double)(up.getZ() + 1)
                                    );
                                    if (mc.world.getOtherEntities(null, box).isEmpty()) {
                                       Vec3d cc = new Vec3d(
                                          (double)pos.getX() + 0.5, (double)(pos.getY() + 1), (double)pos.getZ() + 0.5
                                       );
                                       if (cc.squaredDistanceTo(target.getPos()) > 64.0) {
                                          continue;
                                       }

                                       float td = dmg(cc, target);
                                       float sd = dmg(cc, p);
                                       if (!((double)td < Config.acMinDmg) && !((double)sd > Config.acMaxSelf) && !(sd >= selfHp - 1.0F)) {
                                          double score = (double)td - (double)sd * 0.7;
                                          if (score > bestScore) {
                                             bestScore = score;
                                             bestPos = pos;
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  if (bestPos != null) {
                     Vec3d hitVec = new Vec3d(
                        (double)bestPos.getX() + 0.5, (double)bestPos.getY() + 1.0, (double)bestPos.getZ() + 0.5
                     );
                     mc.interactionManager.interactBlock(p, hand, new BlockHitResult(hitVec, Direction.UP, bestPos, false));
                     p.swingHand(hand);
                     crystalCd = 1;
                  }
               }
            }
         }
      }
   }

   static Hand crystalHand(ClientPlayerEntity p) {
      if (p.getOffHandStack().isOf(Items.END_CRYSTAL)) {
         return Hand.OFF_HAND;
      } else if (p.getMainHandStack().isOf(Items.END_CRYSTAL)) {
         return Hand.MAIN_HAND;
      } else {
         for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
               p.getInventory().selectedSlot = i;
               return Hand.MAIN_HAND;
            }
         }

         return null;
      }
   }

   static double distToBox(Vec3d p, Box b) {
      double dx = Math.max(Math.max(b.minX - p.x, 0.0), p.x - b.maxX);
      double dy = Math.max(Math.max(b.minY - p.y, 0.0), p.y - b.maxY);
      double dz = Math.max(Math.max(b.minZ - p.z, 0.0), p.z - b.maxZ);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private static net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> toughAttr;

   /** Zirh sertligi (netherite = 12). Bulunamazsa eski sabit 2. */
   static float toughness(LivingEntity e) {
      if (toughAttr == null) {
         for (String s : new String[]{"minecraft:armor_toughness", "minecraft:generic.armor_toughness"}) {
            Identifier id = Identifier.tryParse(s);
            if (id != null) {
               var o = Registries.ATTRIBUTE.getEntry(id);
               if (o.isPresent()) {
                  toughAttr = o.get();
                  break;
               }
            }
         }
      }

      return toughAttr == null ? 2.0F : (float)e.getAttributeValue(toughAttr);
   }

   static float dmg(Vec3d c, LivingEntity e) {
      return dmg(c, e, 12.0);
   }

   /** diameter: kristal 12 (guc 6), respawn anchor 10 (guc 5). */
   static float dmg(Vec3d c, LivingEntity e, double diameter) {
      double d = Math.sqrt(e.squaredDistanceTo(c)) / diameter;
      if (d > 1.0) {
         return 0.0F;
      } else {
         double impact = 1.0 - d;
         float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0);
         float armor = (float)e.getArmor();
         float toughness = toughness(e);
         float f = 2.0F + toughness / 4.0F;
         float g = MathHelper.clamp(armor - dmg / f, armor * 0.2F, 20.0F);
         dmg *= 1.0F - g / 25.0F;
         net.minecraft.entity.effect.StatusEffectInstance res = e.getStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE);
         if (res != null) {
            dmg *= Math.max(0.0F, 1.0F - 0.2F * (res.getAmplifier() + 1));
         }

         return Math.max(dmg, 0.0F);
      }
   }

   // Tarama: kare basina zaman butcesi (ms) -> FPS dususu olmaz; yeniden tarama araligi 5 sn.
   static final long SCAN_BUDGET_NS = 1_500_000L;
   static final int RESCAN_TICKS = 100;
   static final int MAX_SCAN_RADIUS = 12;

   static void scanTick() {
      if (mc.world != lastWorld) {
         storageHits.clear();
         blockHits.clear();
         queue.clear();
         lastWorld = mc.world;
         timer = RESCAN_TICKS;
      }

      if (queue.isEmpty() && ++timer >= RESCAN_TICKS) {
         timer = 0;
         int r = Math.min((Integer)mc.options.getViewDistance().getValue(), MAX_SCAN_RADIUS);
         // Freecam acikken kameranin etrafini tara (karakterin degil)
         ChunkPos pc = Freecam.active ? new ChunkPos((int)Math.floor(Freecam.x) >> 4, (int)Math.floor(Freecam.z) >> 4) : mc.player.getChunkPos();
         List<ChunkPos> list = new ArrayList<>();

         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (mc.world.getChunkManager().getWorldChunk(pc.x + dx, pc.z + dz) != null) {
                  list.add(new ChunkPos(pc.x + dx, pc.z + dz));
               }
            }
         }

         // yuklu olmayan / cok uzaklasan chunklarin sonuclarini at (bellek sizintisi onlenir)
         int keep = r + 2;
         storageHits.keySet().removeIf(k -> outside(k, pc, keep));
         blockHits.keySet().removeIf(k -> outside(k, pc, keep));

         list.sort(
            Comparator.comparingInt(
               c -> (c.x - pc.x) * (c.x - pc.x) + (c.z - pc.z) * (c.z - pc.z)
            )
         );
         queue.addAll(list);
      }

      long start = System.nanoTime();
      while (!queue.isEmpty()) {
         scanChunk(queue.poll());
         if (System.nanoTime() - start > SCAN_BUDGET_NS) {
            break;
         }
      }
   }

   static boolean outside(long key, ChunkPos pc, int r) {
      ChunkPos c = new ChunkPos(key);
      return Math.abs(c.x - pc.x) > r || Math.abs(c.z - pc.z) > r;
   }

   /** ESP kapaliyken tarama sonuclarini serbest birak. */
   static void clearScan() {
      if (!storageHits.isEmpty() || !blockHits.isEmpty() || !queue.isEmpty()) {
         storageHits.clear();
         blockHits.clear();
         queue.clear();
      }
   }

   static void scanChunk(ChunkPos cp) {
      WorldChunk c = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
      if (c != null) {
         long key = cp.toLong();
         if (Kuro.on[3]) {
            List<Hit> l = new ArrayList<>();

            for (BlockEntity be : c.getBlockEntities().values()) {
               int col = storageColor(be);
               if (col != 0) {
                  l.add(new Hit(be.getPos(), col));
               }
            }

            if (l.isEmpty()) {
               storageHits.remove(key);
            } else {
               storageHits.put(key, l);
            }
         }

         if (Kuro.on[4]) {
            List<Hit> l = new ArrayList<>();
            ChunkSection[] secs = c.getSectionArray();
            int bottom = c.getBottomSectionCoord();

            for (int i = 0; i < secs.length && l.size() < 2000; i++) {
               ChunkSection s = secs[i];
               if (s != null && !s.isEmpty() && s.hasAny(Mods::isTarget)) {
                  int baseY = bottom + i << 4;

                  for (int x = 0; x < 16; x++) {
                     for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                           BlockState st = s.getBlockState(x, y, z);
                           if (isTarget(st)) {
                              l.add(new Hit(new BlockPos(cp.getStartX() + x, baseY + y, cp.getStartZ() + z), blockColor(st)));
                           }
                        }
                     }
                  }
               }
            }

            if (l.isEmpty()) {
               blockHits.remove(key);
            } else {
               blockHits.put(key, l);
            }
         }
      }
   }

   static int storageColor(BlockEntity be) {
      if (be instanceof TrappedChestBlockEntity) {
         return 16724016;
      } else if (be instanceof ChestBlockEntity) {
         return 16752672;
      } else if (be instanceof EnderChestBlockEntity) {
         return 11550975;
      } else if (be instanceof BarrelBlockEntity) {
         return 10510384;
      } else if (be instanceof ShulkerBoxBlockEntity) {
         return 16732384;
      } else if (be instanceof HopperBlockEntity) {
         return 3199231;
      } else if (be instanceof AbstractFurnaceBlockEntity) {
         return 10526880;
      } else {
         return be instanceof DispenserBlockEntity ? 8454016 : 0;
      }
   }

   static boolean isTarget(BlockState st) {
      return targets.contains(st.getBlock()) || !tagTargets.isEmpty() && tagColor(st) != 0;
   }

   /** Etiketle eslesen blogun rengi (eslesmezse 0). */
   static int tagColor(BlockState st) {
      for (Map.Entry<TagKey<Block>, Integer> t : tagTargets.entrySet()) {
         if (st.isIn(t.getKey())) {
            return t.getValue();
         }
      }

      return 0;
   }

   static int blockColor(BlockState st) {
      Integer c = targetColor.get(st.getBlock());
      if (c != null) {
         return c;
      }

      int t = tagColor(st);
      return t != 0 ? t : 0xFFFFFF;
   }

   /** Menuden secilen bloklar + (varsa) kuro_blocks_extra.txt dosyasindaki ek bloklardan hedef listesini yeniden kurar. */
   static void rebuildTargets() {
      targets.clear();
      tagTargets.clear();
      targetColor.clear();
      for (BlockSel.Entry en : BlockSel.ALL) {
         if (BlockSel.has(en.key())) {
            for (String id : en.ids()) {
               addTarget(id, en.color());
            }
         }
      }

      for (String c : BlockSel.custom()) {
         addTarget(c, 0xFFFFFF);
      }

      try {
         Path f = FabricLoader.getInstance().getGameDir().resolve("kuro_blocks_extra.txt");
         if (!Files.exists(f)) {
            Files.writeString(f, "# Menude olmayan ek bloklar: satir basina bir blok id (ornek: bookshelf) veya tag:minecraft:beds\n");
         }

         for (String line : Files.readAllLines(f)) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
               addTarget(line, 0xFFFFFF);
            }
         }
      } catch (IOException ignored) {
      }

      // eski sonuclari at, hemen yeniden tara
      blockHits.clear();
      queue.clear();
      timer = RESCAN_TICKS;
   }

   /** Menuden yazilan blok id'sini dogrular; gecerliyse "namespace:path" dondurur, degilse null. */
   static String validBlockId(String raw) {
      String name = raw.trim().toLowerCase();
      if (name.isEmpty()) {
         return null;
      }

      Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
      return id != null && Registries.BLOCK.containsId(id) ? id.toString() : null;
   }

   private static void addTarget(String line, int color) {
      boolean tag = line.startsWith("tag:");
      String name = (tag ? line.substring(4) : line).trim();
      Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
      if (id == null) {
         return;
      }

      if (tag) {
         tagTargets.put(TagKey.of(RegistryKeys.BLOCK, id), color);
      } else if (Registries.BLOCK.containsId(id)) {
         Block b = Registries.BLOCK.get(id);
         targets.add(b);
         targetColor.put(b, color);
      }
   }

   static void printBases() {
      if (mc.player != null && mc.world != null) {
         if (storageHits.isEmpty()) {
            msg("Depo bulunamadi. StorageESP'yi ac (V) ve etrafta gez.");
         } else {
            Map<Long, Integer> score = new HashMap<>();

            for (long k : storageHits.keySet()) {
               ChunkPos c = new ChunkPos(k);
               int s = 0;

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     List<Hit> l = storageHits.get(new ChunkPos(c.x + dx, c.z + dz).toLong());
                     if (l != null) {
                        s += l.size();
                     }
                  }
               }

               score.put(k, s);
            }

            List<Entry<Long, Integer>> sorted = new ArrayList<>(score.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            String dim = mc.world.getRegistryKey().getValue().getPath();
            List<ChunkPos> picked = new ArrayList<>();

            for (Entry<Long, Integer> e : sorted) {
               if (e.getValue() < 5 || picked.size() >= 8) {
                  break;
               }

               ChunkPos c = new ChunkPos(e.getKey());
               boolean near = false;

               for (ChunkPos q : picked) {
                  if (Math.abs(q.x - c.x) <= 3 && Math.abs(q.z - c.z) <= 3) {
                     near = true;
                  }
               }

               if (!near) {
                  picked.add(c);
                  msg("Base? x=" + c.getCenterX() + " z=" + c.getCenterZ() + " | " + e.getValue() + " depo | " + dim);
                  logBase(dim, c, e.getValue());
               }
            }

            if (picked.isEmpty()) {
               msg("Yeterli yogunlukta depo kumesi yok (en az 5 depo / 3x3 chunk).");
            }
         }
      }
   }

   static void logBase(String dim, ChunkPos c, int score) {
      String key = dim + ":" + c.x + ":" + c.z;
      if (logged.add(key)) {
         try {
            Path f = FabricLoader.getInstance().getGameDir().resolve("kuro_bases.txt");
            String line = LocalDateTime.now() + " " + dim + " x=" + c.getCenterX() + " z=" + c.getCenterZ() + " depo=" + score + "\n";
            Files.writeString(f, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException var6) {
         }
      }
   }

   static {
      rebuildTargets();
   }

   static record Hit(BlockPos pos, int color) {
   }
}
