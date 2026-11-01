package kuro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

/**
 * "Donut" sekmesi: sunucu tabanlarini / oyuncu yapilarini bulmaya yarayan ESP ve tarayicilar.
 * Tum kod bu projeye ozgu yazildi; her modulun adi/islevi Kuro.NAMES / DESCS icinde.
 * Blok taramalari zaman butceli (ana thread), tunel / kuyu gibi geometri taramalari ayri bir is parcaciginda calisir.
 */
public final class Donut {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   public static final int FIRST = Kuro.PILLAGERESP;
   public static final int LAST = Kuro.SKELETONESP;
   private static final int N = LAST - FIRST + 1;

   private static final long SCAN_BUDGET_NS = 1_500_000L;
   private static final int RESCAN_TICKS = 100;
   private static final int CAP_PER_CHUNK = 300;
   private static final int MAX_BOXES = 3000;

   /** Cizilecek kutu (dunya koordinati) + renk (RRGGBB). */
   record DHit(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
      static DHit block(BlockPos p, int color) {
         return new DHit(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, color);
      }
   }

   @SuppressWarnings("unchecked")
   private static final Map<Long, List<DHit>>[] hits = new Map[N];
   private static final Set<String> notified = ConcurrentHashMap.newKeySet();
   private static final Set<Long> pending = ConcurrentHashMap.newKeySet();
   private static final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
   private static final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
   private static final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "kuro-donut");
      t.setDaemon(true);
      return t;
   });
   private static World lastWorld;
   private static int timer = RESCAN_TICKS;
   private static int entTimer;

   static {
      for (int i = 0; i < N; i++) {
         hits[i] = new ConcurrentHashMap<>();
      }
   }

   private Donut() {
   }

   private static boolean on(int id) {
      return Kuro.on[id];
   }

   public static boolean anyOn() {
      for (int i = FIRST; i <= LAST; i++) {
         if (Kuro.on[i]) {
            return true;
         }
      }

      return false;
   }

   private static boolean anyBlockOn() {
      for (int i = Kuro.AMETHYSTESP; i < Kuro.SKELETONESP; i++) {
         if (Kuro.on[i]) {
            return true;
         }
      }

      return false;
   }

   private static boolean anyEntityOn() {
      for (int i = FIRST; i <= Kuro.INVISESP; i++) {
         if (Kuro.on[i]) {
            return true;
         }
      }

      return Kuro.on[Kuro.COLLECTESP] || Kuro.on[Kuro.SKELETONESP];
   }

   public static void onToggle(int id, boolean v) {
      if (id < FIRST || id > LAST) {
         return;
      }

      if (!v) {
         hits[id - FIRST].clear();
      } else {
         timer = RESCAN_TICKS;
      }

      notified.clear();
   }

   private static void clearAll() {
      for (Map<Long, List<DHit>> m : hits) {
         m.clear();
      }

      queue.clear();
      notified.clear();
      timer = RESCAN_TICKS;
   }

   private static void say(String s) {
      if (Config.dnChat >= 0.5F) {
         messages.add(s);
      }

      if (Config.dnWebhook >= 0.5F) {
         Webhook.send("[Kuro/Donut] " + s);
      }
   }

   private static void notifyOnce(String key, String text) {
      if (notified.size() > 4000) {
         notified.clear();
      }

      if (notified.add(key)) {
         say(text);
      }
   }

   // ------------------------------------------------------------------ tick

   public static void tick() {
      if (mc.world == null || mc.player == null) {
         return;
      }

      String m;
      while ((m = messages.poll()) != null) {
         mc.player.sendMessage(Text.literal("[Donut] " + m), false);
      }

      if (mc.world != lastWorld) {
         lastWorld = mc.world;
         clearAll();
      }

      if (anyEntityOn() && ++entTimer >= 10) {
         entTimer = 0;
         for (Entity e : mc.world.getEntities()) {
            int mod = entityModule(e);
            if (mod >= 0) {
               notifyOnce("e" + e.getId(), Kuro.NAMES[mod] + " bulundu: " + (int)e.getX() + " " + (int)e.getY() + " " + (int)e.getZ());
            }
         }
      }

      if (!anyBlockOn()) {
         return;
      }

      if (queue.isEmpty() && ++timer >= RESCAN_TICKS) {
         timer = 0;
         int r = Math.min((Integer)mc.options.getViewDistance().getValue(), Math.round(Config.dnRadius));
         ChunkPos pc = mc.player.getChunkPos();
         List<ChunkPos> list = new ArrayList<>();
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (mc.world.getChunkManager().getWorldChunk(pc.x + dx, pc.z + dz) != null) {
                  list.add(new ChunkPos(pc.x + dx, pc.z + dz));
               }
            }
         }

         int keep = r + 2;
         for (Map<Long, List<DHit>> hm : hits) {
            hm.keySet().removeIf(k -> {
               ChunkPos c = new ChunkPos(k);
               return Math.abs(c.x - pc.x) > keep || Math.abs(c.z - pc.z) > keep;
            });
         }

         list.sort(Comparator.comparingInt(c -> (c.x - pc.x) * (c.x - pc.x) + (c.z - pc.z) * (c.z - pc.z)));
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

   // ------------------------------------------------------------------ entity siniflandirma

   /** Varligin ait oldugu (acik) modul, yoksa -1. */
   static int entityModule(Entity e) {
      if (e == mc.player) {
         return -1;
      }

      if (on(Kuro.PILLAGERESP) && e instanceof PillagerEntity) {
         return Kuro.PILLAGERESP;
      }

      if (on(Kuro.VILLAGERESP) && (e instanceof VillagerEntity || e instanceof ZombieVillagerEntity)) {
         return Kuro.VILLAGERESP;
      }

      if (on(Kuro.WANDERESP) && (e instanceof WanderingTraderEntity || e instanceof LlamaEntity)) {
         return Kuro.WANDERESP;
      }

      if (on(Kuro.DROWNEDESP) && e instanceof DrownedEntity d && (d.getMainHandStack().isOf(Items.TRIDENT) || d.getOffHandStack().isOf(Items.TRIDENT))) {
         return Kuro.DROWNEDESP;
      }

      if (on(Kuro.COLLECTESP) && e instanceof ItemFrameEntity f && !f.getHeldItemStack().isEmpty()) {
         return Kuro.COLLECTESP;
      }

      if (on(Kuro.INVISESP) && e.isInvisible() && e instanceof LivingEntity) {
         boolean player = e instanceof PlayerEntity;
         if (player ? Config.ivPlayers >= 0.5F : Config.ivMobs >= 0.5F) {
            return Kuro.INVISESP;
         }
      }

      return -1;
   }

   private static int entityColor(int mod, Entity e) {
      return switch (mod) {
         case Kuro.PILLAGERESP -> 0xFF4040;
         case Kuro.VILLAGERESP -> e instanceof ZombieVillagerEntity ? 0x40FF40 : 0x40C0FF;
         case Kuro.WANDERESP -> 0x4080FF;
         case Kuro.DROWNEDESP -> 0x40FFD0;
         case Kuro.COLLECTESP -> 0xFFE060;
         default -> 0xFFFFFF;
      };
   }

   // ------------------------------------------------------------------ chunk tarama

   private interface Match {
      boolean test(BlockState st, int y);
   }

   /** Chunk icinde quick'e uyan ve m'yi saglayan bloklari (en fazla cap) toplar. */
   private static List<BlockPos> collect(WorldChunk c, ChunkPos cp, Predicate<BlockState> quick, Match m, int minY, int maxY, int cap) {
      List<BlockPos> out = new ArrayList<>();
      ChunkSection[] secs = c.getSectionArray();
      int bottom = c.getBottomSectionCoord();
      for (int i = 0; i < secs.length && out.size() < cap; i++) {
         ChunkSection s = secs[i];
         int base = (bottom + i) << 4;
         if (s == null || s.isEmpty() || base + 15 < minY || base > maxY || !s.hasAny(quick)) {
            continue;
         }

         for (int y = 0; y < 16 && out.size() < cap; y++) {
            int wy = base + y;
            if (wy < minY || wy > maxY) {
               continue;
            }

            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  BlockState st = s.getBlockState(x, y, z);
                  if (quick.test(st) && m.test(st, wy)) {
                     out.add(new BlockPos(cp.getStartX() + x, wy, cp.getStartZ() + z));
                     if (out.size() >= cap) {
                        return out;
                     }
                  }
               }
            }
         }
      }

      return out;
   }

   private static void put(int mod, long key, List<DHit> l) {
      Map<Long, List<DHit>> hm = hits[mod - FIRST];
      if (l.isEmpty()) {
         hm.remove(key);
      } else {
         hm.put(key, l);
      }
   }

   private static List<DHit> boxes(List<BlockPos> l, int color) {
      List<DHit> out = new ArrayList<>(l.size());
      for (BlockPos p : l) {
         out.add(DHit.block(p, color));
      }

      return out;
   }

   private static String xz(ChunkPos cp) {
      return (cp.getCenterX()) + " " + (cp.getCenterZ());
   }

   private static void scanChunk(ChunkPos cp) {
      WorldChunk c = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
      if (c == null) {
         return;
      }

      long key = cp.toLong();
      int bottomY = c.getBottomY();
      int topY = bottomY + c.getHeight() - 1;

      if (on(Kuro.AMETHYSTESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.AMETHYST_BLOCK) || s.isOf(Blocks.BUDDING_AMETHYST), (s, y) -> true, bottomY, topY, 600);
         if (l.size() >= Math.round(Config.amMin)) {
            List<DHit> h = boxes(l.size() > 150 ? l.subList(0, 150) : l, 0xC060FF);
            if (Config.amChunk >= 0.5F) {
               int minY = Integer.MAX_VALUE;
               int maxY = Integer.MIN_VALUE;
               for (BlockPos p : l) {
                  minY = Math.min(minY, p.getY());
                  maxY = Math.max(maxY, p.getY());
               }

               h.add(new DHit(cp.getStartX(), minY, cp.getStartZ(), cp.getStartX() + 16, maxY + 1, cp.getStartZ() + 16, 0xFF80FF));
            }

            put(Kuro.AMETHYSTESP, key, h);
            notifyOnce("am" + key, "Ametist jeodu: " + xz(cp) + " (" + l.size() + " blok)");
         } else {
            put(Kuro.AMETHYSTESP, key, List.of());
         }
      }

      if (on(Kuro.KELPESP)) {
         int ky = Math.round(Config.klY);
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.KELP), (s, y) -> y == ky, ky, ky, 200);
         if (l.size() >= Math.round(Config.klMin)) {
            put(Kuro.KELPESP, key, boxes(l, 0x40FF80));
            notifyOnce("kl" + key, "Şüpheli kelp chunk'ı: " + xz(cp) + " (" + l.size() + " kelp ucu Y=" + ky + ")");
         } else {
            put(Kuro.KELPESP, key, List.of());
         }
      }

      if (on(Kuro.DEEPSLATEESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.DEEPSLATE), (s, y) -> true, Math.round(Config.dsMinY), Math.round(Config.dsMaxY), CAP_PER_CHUNK);
         put(Kuro.DEEPSLATEESP, key, boxes(l, 0x8090A0));
         if (!l.isEmpty()) {
            notifyOnce("ds" + key, "Deepslate (doğal olmayan olabilir): " + xz(cp));
         }
      }

      if (on(Kuro.ROTDEEPESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.DEEPSLATE), (s, y) -> s.get(Properties.AXIS) != Direction.Axis.Y,
            Math.round(Config.rdMinY), Math.round(Config.rdMaxY), CAP_PER_CHUNK);
         put(Kuro.ROTDEEPESP, key, boxes(l, 0xFF9040));
         if (!l.isEmpty()) {
            notifyOnce("rd" + key, "Döndürülmüş deepslate: " + xz(cp));
         }
      }

      if (on(Kuro.DRIPESP)) {
         int need = Math.round(Config.dpLen);
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.POINTED_DRIPSTONE), (s, y) -> true, bottomY, topY, 800);
         List<DHit> h = new ArrayList<>();
         for (BlockPos p : l) {
            Direction toTip = mc.world.getBlockState(p).get(Properties.VERTICAL_DIRECTION);
            if (mc.world.getBlockState(p.offset(toTip.getOpposite())).isOf(Blocks.POINTED_DRIPSTONE)) {
               continue;
            }

            int len = 1;
            BlockPos q = p.offset(toTip);
            while (len < 64 && mc.world.getBlockState(q).isOf(Blocks.POINTED_DRIPSTONE)) {
               len++;
               q = q.offset(toTip);
            }

            if (len >= need) {
               BlockPos tip = p.offset(toTip, len - 1);
               h.add(new DHit(Math.min(p.getX(), tip.getX()), Math.min(p.getY(), tip.getY()), Math.min(p.getZ(), tip.getZ()),
                  Math.max(p.getX(), tip.getX()) + 1, Math.max(p.getY(), tip.getY()) + 1, Math.max(p.getZ(), tip.getZ()) + 1,
                  toTip == Direction.DOWN ? 0xFFC040 : 0xC09040));
            }
         }

         put(Kuro.DRIPESP, key, h);
         if (!h.isEmpty()) {
            notifyOnce("dp" + key, "Uzun sarkıt/dikit: " + xz(cp));
         }
      }

      if (on(Kuro.VINEESP)) {
         int need = Math.round(Config.vnLen);
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.VINE), (s, y) -> true, bottomY, topY, 1500);
         List<DHit> h = new ArrayList<>();
         for (BlockPos p : l) {
            if (mc.world.getBlockState(p.up()).isOf(Blocks.VINE)) {
               continue;
            }

            int len = 1;
            BlockPos q = p.down();
            while (len < 80 && mc.world.getBlockState(q).isOf(Blocks.VINE)) {
               len++;
               q = q.down();
            }

            if (len >= need) {
               h.add(new DHit(p.getX(), p.getY() - len + 1, p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0x40E040));
            }
         }

         put(Kuro.VINEESP, key, h);
         if (!h.isEmpty()) {
            notifyOnce("vn" + key, "Uzun sarmaşık: " + xz(cp));
         }
      }

      if (on(Kuro.BERRYESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.SWEET_BERRY_BUSH), (s, y) -> true, bottomY, topY, 200);
         put(Kuro.BERRYESP, key, boxes(l, 0xFF4060));
         if (!l.isEmpty()) {
            notifyOnce("br" + key, "Tatlı çilek çalısı: " + xz(cp));
         }
      }

      if (on(Kuro.BEEESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.BEE_NEST) || s.isOf(Blocks.BEEHIVE), (s, y) -> true, bottomY, topY, 200);
         put(Kuro.BEEESP, key, boxes(l, 0xFFD030));
         if (!l.isEmpty()) {
            notifyOnce("bh" + key, "Arı kovanı/yuvası: " + xz(cp));
         }
      }

      if (on(Kuro.PISTONESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.PISTON) || s.isOf(Blocks.STICKY_PISTON), (s, y) -> true, bottomY, topY, 300);
         List<DHit> h = new ArrayList<>();
         for (BlockPos p : l) {
            h.add(DHit.block(p, mc.world.getBlockState(p).isOf(Blocks.STICKY_PISTON) ? 0x80FF80 : 0xC0C0C0));
         }

         put(Kuro.PISTONESP, key, h);
         if (!h.isEmpty()) {
            notifyOnce("pi" + key, "Piston: " + xz(cp));
         }
      }

      if (on(Kuro.SPAWNERESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.SPAWNER), (s, y) -> true, bottomY, topY, 100);
         put(Kuro.SPAWNERESP, key, boxes(l, 0xFF2020));
         for (BlockPos p : l) {
            notifyOnce("sp" + p.asLong(), "Spawner: " + p.getX() + " " + p.getY() + " " + p.getZ());
         }
      }

      if (on(Kuro.VOIDESP)) {
         put(Kuro.VOIDESP, key, bedrockVoids(c, cp, bottomY));
      }

      if (on(Kuro.STASHESP)) {
         int stores = 0;
         int spawners = 0;
         int y1 = Integer.MAX_VALUE;
         int y2 = Integer.MIN_VALUE;
         for (BlockEntity be : c.getBlockEntities().values()) {
            boolean store = be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
            boolean spawner = be instanceof MobSpawnerBlockEntity;
            if (store || spawner) {
               if (store) {
                  stores++;
               } else {
                  spawners++;
               }

               y1 = Math.min(y1, be.getPos().getY());
               y2 = Math.max(y2, be.getPos().getY());
            }
         }

         boolean flag = stores >= Math.round(Config.stMin) || (spawners > 0 && Config.stSpawner >= 0.5F);
         if (flag) {
            put(Kuro.STASHESP, key, List.of(new DHit(cp.getStartX(), y1, cp.getStartZ(), cp.getStartX() + 16, y2 + 1, cp.getStartZ() + 16, spawners > 0 ? 0xFF3030 : 0xFFA040)));
            notifyOnce("st" + key, "Stash/base? " + xz(cp) + " | depo: " + stores + ", spawner: " + spawners);
         } else {
            put(Kuro.STASHESP, key, List.of());
         }
      }

      if (on(Kuro.CHUNKESP)) {
         int fy = Math.round(Config.cfMinY);
         List<BlockPos> l = collect(c, cp, s -> s.isOf(Blocks.DEEPSLATE) || s.isOf(Blocks.COBBLED_DEEPSLATE),
            (s, y) -> y >= fy || (s.isOf(Blocks.DEEPSLATE) && s.get(Properties.AXIS) != Direction.Axis.Y), bottomY, topY, 400);
         if (l.size() >= Math.round(Config.cfMin)) {
            int y1 = Integer.MAX_VALUE;
            int y2 = Integer.MIN_VALUE;
            for (BlockPos p : l) {
               y1 = Math.min(y1, p.getY());
               y2 = Math.max(y2, p.getY());
            }

            put(Kuro.CHUNKESP, key, List.of(new DHit(cp.getStartX(), y1, cp.getStartZ(), cp.getStartX() + 16, y2 + 1, cp.getStartZ() + 16, 0x60A0FF)));
            notifyOnce("cf" + key, "Şüpheli chunk: " + xz(cp) + " (" + l.size() + " deepslate)");
         } else {
            put(Kuro.CHUNKESP, key, List.of());
         }
      }

      if (on(Kuro.LIGHTESP)) {
         int lum = Math.round(Config.lgMin);
         List<BlockPos> l = collect(c, cp,
            s -> s.getLuminance() >= lum && !s.isOf(Blocks.LAVA) && !s.isOf(Blocks.FIRE) && !s.isOf(Blocks.SOUL_FIRE) && !s.isOf(Blocks.MAGMA_BLOCK)
               && !s.isOf(Blocks.CAVE_VINES) && !s.isOf(Blocks.CAVE_VINES_PLANT) && !s.isOf(Blocks.GLOW_LICHEN),
            (s, y) -> true, Math.round(Config.lgMinY), Math.round(Config.lgMaxY), CAP_PER_CHUNK);
         put(Kuro.LIGHTESP, key, boxes(l, 0xFFF080));
      }

      if (on(Kuro.COLLECTESP)) {
         List<BlockPos> l = collect(c, cp, s -> s.getBlock() instanceof AbstractBannerBlock, (s, y) -> true, bottomY, topY, 200);
         put(Kuro.COLLECTESP, key, boxes(l, 0xFFE060));
         if (!l.isEmpty()) {
            notifyOnce("bn" + key, "Banner: " + xz(cp));
         }
      }

      if (on(Kuro.TUNNELESP) || on(Kuro.HOLE1ESP)) {
         snapshotGeometry(c, cp, key, bottomY, topY);
      }
   }

   // ------------------------------------------------------------------ bedrock bosluklari

   private static List<DHit> bedrockVoids(WorldChunk c, ChunkPos cp, int bottomY) {
      List<DHit> out = new ArrayList<>();
      if (bottomY != -64) {
         return out;
      }

      ChunkSection[] secs = c.getSectionArray();
      if (secs.length == 0 || secs[0] == null || secs[0].isEmpty()) {
         return out;
      }

      ChunkSection s = secs[0];
      final int H = 5; // y = -64 .. -60
      boolean[] air = new boolean[16 * H * 16];
      for (int y = 0; y < H; y++) {
         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               air[(y * 16 + z) * 16 + x] = s.getBlockState(x, y, z).isAir();
            }
         }
      }

      boolean[] seen = new boolean[air.length];
      int minSize = Math.round(Config.vdMin);
      int[] stack = new int[air.length];
      for (int i = 0; i < air.length; i++) {
         if (!air[i] || seen[i]) {
            continue;
         }

         int sp = 0;
         stack[sp++] = i;
         seen[i] = true;
         int size = 0;
         int x1 = 99;
         int y1 = 99;
         int z1 = 99;
         int x2 = -1;
         int y2 = -1;
         int z2 = -1;
         while (sp > 0) {
            int cur = stack[--sp];
            int x = cur & 15;
            int z = (cur >> 4) & 15;
            int y = cur >> 8;
            size++;
            x1 = Math.min(x1, x);
            x2 = Math.max(x2, x);
            y1 = Math.min(y1, y);
            y2 = Math.max(y2, y);
            z1 = Math.min(z1, z);
            z2 = Math.max(z2, z);
            int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] o : d) {
               int nx = x + o[0];
               int ny = y + o[1];
               int nz = z + o[2];
               if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 0 || ny >= H) {
                  continue;
               }

               int ni = (ny * 16 + nz) * 16 + nx;
               if (air[ni] && !seen[ni]) {
                  seen[ni] = true;
                  stack[sp++] = ni;
               }
            }
         }

         if (size >= minSize) {
            out.add(new DHit(cp.getStartX() + x1, -64 + y1, cp.getStartZ() + z1, cp.getStartX() + x2 + 1, -64 + y2 + 1, cp.getStartZ() + z2 + 1, 0xFF40A0));
            notifyOnce("vd" + cp.toLong() + ":" + i, "Bedrock boşluğu: " + (cp.getStartX() + x1) + " " + (-64 + y1) + " " + (cp.getStartZ() + z1) + " (" + size + " blok)");
         }
      }

      return out;
   }

   // ------------------------------------------------------------------ tunel / kuyu / 1x1 delik

   private static void snapshotGeometry(WorldChunk c, ChunkPos cp, long key, int bottomY, int topY) {
      if (!pending.add(key)) {
         return;
      }

      int minY = Math.max(bottomY, Math.round(Config.tnMinY));
      int maxY = Math.min(topY, Math.round(Config.tnMaxY));
      if (maxY - minY < 3) {
         pending.remove(key);
         return;
      }

      int h = maxY - minY + 1;
      byte[] snap = new byte[16 * 16 * h]; // 0 hava, 1 opak blok, 2 diger
      ChunkSection[] secs = c.getSectionArray();
      int bottom = c.getBottomSectionCoord();
      for (int i = 0; i < secs.length; i++) {
         ChunkSection s = secs[i];
         int base = (bottom + i) << 4;
         if (s == null || s.isEmpty() || base + 15 < minY || base > maxY) {
            continue;
         }

         for (int y = 0; y < 16; y++) {
            int wy = base + y;
            if (wy < minY || wy > maxY) {
               continue;
            }

            int ry = wy - minY;
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  BlockState st = s.getBlockState(x, y, z);
                  snap[(ry * 16 + z) * 16 + x] = (byte)(st.isAir() ? 0 : st.isOpaque() ? 1 : 2);
               }
            }
         }
      }

      boolean tunnels = on(Kuro.TUNNELESP);
      boolean holes1 = on(Kuro.HOLE1ESP);
      int mode = Math.round(Config.tnMode);
      int depth = Math.round(Config.thDepth);
      int tunLen = Math.round(Config.tunLen);
      int maxH = Math.round(Config.tunH);
      int cx = cp.getStartX();
      int cz = cp.getStartZ();
      pool.submit(() -> {
         try {
            if (tunnels) {
               put(Kuro.TUNNELESP, key, geometry(snap, h, minY, cx, cz, mode, depth, tunLen, maxH));
            }

            if (holes1) {
               put(Kuro.HOLE1ESP, key, oneByOne(snap, h, minY, cx, cz));
            }
         } catch (RuntimeException ignored) {
         } finally {
            pending.remove(key);
         }
      });
   }

   private static boolean solid(byte[] s, int h, int x, int y, int z) {
      return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < h && s[(y * 16 + z) * 16 + x] == 1;
   }

   private static boolean air(byte[] s, int h, int x, int y, int z) {
      return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < h && s[(y * 16 + z) * 16 + x] == 0;
   }

   private static List<DHit> geometry(byte[] s, int h, int minY, int cx, int cz, int mode, int depth, int tunLen, int maxH) {
      List<DHit> out = new ArrayList<>();
      if (mode == 0 || mode == 1) {
         for (int x = 1; x < 15; x++) {
            for (int z = 1; z < 15; z++) {
               int run = 0;
               for (int y = 0; y <= h; y++) {
                  boolean ok = y < h && air(s, h, x, y, z) && solid(s, h, x - 1, y, z) && solid(s, h, x + 1, y, z) && solid(s, h, x, y, z - 1) && solid(s, h, x, y, z + 1);
                  if (ok) {
                     run++;
                  } else {
                     if (run >= depth && out.size() < CAP_PER_CHUNK) {
                        out.add(new DHit(cx + x, minY + y - run, cz + z, cx + x + 1, minY + y, cz + z + 1, 0x40C0FF));
                     }

                     run = 0;
                  }
               }
            }
         }
      }

      if (mode == 0 || mode == 2) {
         byte[] tx = new byte[s.length];
         byte[] tz = new byte[s.length];
         for (int y = 1; y < h - 1; y++) {
            for (int z = 1; z < 15; z++) {
               for (int x = 1; x < 15; x++) {
                  if (!air(s, h, x, y, z) || !solid(s, h, x, y - 1, z)) {
                     continue;
                  }

                  int hh = 0;
                  while (hh <= maxH && air(s, h, x, y + hh, z)) {
                     hh++;
                  }

                  if (hh < 1 || hh > maxH || !solid(s, h, x, y + hh, z)) {
                     continue;
                  }

                  boolean wallZ = true;
                  boolean wallX = true;
                  for (int k = 0; k < hh; k++) {
                     wallZ &= solid(s, h, x, y + k, z - 1) && solid(s, h, x, y + k, z + 1);
                     wallX &= solid(s, h, x - 1, y + k, z) && solid(s, h, x + 1, y + k, z);
                  }

                  int idx = (y * 16 + z) * 16 + x;
                  if (wallZ) {
                     tx[idx] = (byte)hh;
                  }

                  if (wallX) {
                     tz[idx] = (byte)hh;
                  }
               }
            }
         }

         for (int y = 1; y < h - 1; y++) {
            for (int z = 0; z < 16; z++) {
               int run = 0;
               byte cur = 0;
               for (int x = 0; x <= 16; x++) {
                  byte v = x < 16 ? tx[(y * 16 + z) * 16 + x] : 0;
                  if (v != 0 && v == cur) {
                     run++;
                  } else {
                     if (cur != 0 && run >= tunLen && out.size() < CAP_PER_CHUNK) {
                        out.add(new DHit(cx + x - run, minY + y, cz + z, cx + x, minY + y + cur, cz + z + 1, 0xFFC040));
                     }

                     cur = v;
                     run = v != 0 ? 1 : 0;
                  }
               }
            }

            for (int x = 0; x < 16; x++) {
               int run = 0;
               byte cur = 0;
               for (int z = 0; z <= 16; z++) {
                  byte v = z < 16 ? tz[(y * 16 + z) * 16 + x] : 0;
                  if (v != 0 && v == cur) {
                     run++;
                  } else {
                     if (cur != 0 && run >= tunLen && out.size() < CAP_PER_CHUNK) {
                        out.add(new DHit(cx + x, minY + y, cz + z - run, cx + x + 1, minY + y + cur, cz + z, 0xFFC040));
                     }

                     cur = v;
                     run = v != 0 ? 1 : 0;
                  }
               }
            }
         }
      }

      return out;
   }

   private static List<DHit> oneByOne(byte[] s, int h, int minY, int cx, int cz) {
      List<DHit> out = new ArrayList<>();
      for (int y = 1; y < h - 1 && out.size() < CAP_PER_CHUNK; y++) {
         for (int z = 1; z < 15; z++) {
            for (int x = 1; x < 15; x++) {
               if (air(s, h, x, y, z) && solid(s, h, x - 1, y, z) && solid(s, h, x + 1, y, z) && solid(s, h, x, y - 1, z)
                  && solid(s, h, x, y + 1, z) && solid(s, h, x, y, z - 1) && solid(s, h, x, y, z + 1)) {
                  out.add(new DHit(cx + x, minY + y, cz + z, cx + x + 1, minY + y + 1, cz + z + 1, 0xFF60FF));
               }
            }
         }
      }

      return out;
   }

   // ------------------------------------------------------------------ cizim

   private static int boxCount;

   /** Render.render icinden (matris cam'e gore kaydirilmis halde) cagrilir. */
   static void render(Matrix4f m, Vec3d cam, Vec3d look, float td) {
      if (!anyOn() || mc.world == null) {
         return;
      }

      com.mojang.blaze3d.systems.RenderSystem.lineWidth(Math.max(1.0F, Config.dnThick));
      BufferBuilder b = Tessellator.getInstance().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
      float alpha = Config.dnAlpha / 100.0F;
      boolean tracer = Config.dnTracer >= 0.5F;
      Vec3d start = cam.add(look);
      boxCount = 0;

      for (int i = 0; i < N; i++) {
         if (!Kuro.on[FIRST + i] || FIRST + i <= Kuro.INVISESP) {
            continue;
         }

         for (List<DHit> l : hits[i].values()) {
            for (int k = 0, n = l.size(); k < n; k++) {
               if (boxCount >= MAX_BOXES) {
                  break;
               }

               DHit h = l.get(k);
               double mx = (h.x1 + h.x2) * 0.5;
               double my = (h.y1 + h.y2) * 0.5;
               double mz = (h.z1 + h.z2) * 0.5;
               double dx = mx - cam.x;
               double dy = my - cam.y;
               double dz = mz - cam.z;
               double d2 = dx * dx + dy * dy + dz * dz;
               if (d2 > 160.0 * 160.0 || (d2 > 9.0 && dx * look.x + dy * look.y + dz * look.z < 0.0)) {
                  continue;
               }

               draw(b, m, h.x1, h.y1, h.z1, h.x2, h.y2, h.z2, h.color, alpha);
               if (tracer) {
                  col(b, m, start, mx, my, mz, h.color, alpha * 0.7F);
               }

               boxCount++;
            }
         }
      }

      if (anyEntityOn()) {
         double range = Config.dnRange;
         double r2 = range * range;
         for (Entity e : mc.world.getEntities()) {
            int mod = entityModule(e);
            if (mod < 0) {
               continue;
            }

            Vec3d off = e.getLerpedPos(td).subtract(e.getPos());
            Box bb = e.getBoundingBox().offset(off);
            Vec3d c = bb.getCenter();
            if (c.squaredDistanceTo(cam) > r2) {
               continue;
            }

            int color = entityColor(mod, e);
            draw(b, m, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, color, alpha);
            if (tracer) {
               col(b, m, start, c.x, c.y, c.z, color, alpha * 0.7F);
            }
         }
      }

      if (on(Kuro.SKELETONESP)) {
         skeleton(b, m, cam, td, alpha);
      }

      Render.flush(b);
      com.mojang.blaze3d.systems.RenderSystem.lineWidth(1.0F);
   }

   private static void bone(BufferBuilder b, Matrix4f m, Vec3d a, Vec3d c, float al) {
      Render.line(b, m, a.x, a.y, a.z, c.x, c.y, c.z, 0.9F, 0.95F, 1.0F, al);
   }

   private static void skeleton(BufferBuilder b, Matrix4f m, Vec3d cam, float td, float al) {
      double range = Config.dnRange;
      for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
         if (p == mc.player) {
            continue;
         }

         Vec3d base = p.getLerpedPos(td);
         if (base.squaredDistanceTo(cam) > range * range) {
            continue;
         }

         double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw));
         double rx = -Math.cos(yaw);
         double rz = -Math.sin(yaw);
         double h = p.getHeight();
         Vec3d neck = base.add(0.0, h * 0.82, 0.0);
         Vec3d head = base.add(0.0, h * 0.95, 0.0);
         Vec3d hip = base.add(0.0, h * 0.42, 0.0);
         Vec3d sl = neck.add(-rx * 0.3, 0.0, -rz * 0.3);
         Vec3d sr = neck.add(rx * 0.3, 0.0, rz * 0.3);
         Vec3d hl = hip.add(-rx * 0.12, 0.0, -rz * 0.12);
         Vec3d hr = hip.add(rx * 0.12, 0.0, rz * 0.12);
         bone(b, m, head, neck, al);
         bone(b, m, neck, hip, al);
         bone(b, m, sl, sr, al);
         bone(b, m, sl, sl.add(0.0, -h * 0.35, 0.0), al);
         bone(b, m, sr, sr.add(0.0, -h * 0.35, 0.0), al);
         bone(b, m, hl, hr, al);
         bone(b, m, hl, base.add(-rx * 0.12, 0.0, -rz * 0.12), al);
         bone(b, m, hr, base.add(rx * 0.12, 0.0, rz * 0.12), al);
      }
   }

   private static void draw(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, int c, float a) {
      Render.box(b, m, x1, y1, z1, x2, y2, z2, (c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F, a);
   }

   private static void col(BufferBuilder b, Matrix4f m, Vec3d s, double x, double y, double z, int c, float a) {
      Render.line(b, m, s.x, s.y, s.z, x, y, z, (c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F, a);
   }
}
