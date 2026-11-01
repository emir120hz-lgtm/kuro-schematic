package kuro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * XRay: secilmeyen bloklarin yuzeylerini cizmez (BlockMixin), secilen bloklar her yonden gorunur.
 * Chunk gorunurluk (occlusion) hesabi da kapatilir, boylece duvarlarin arkasi cizilir.
 */
public final class Xray {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   /** Mixin (chunk iscisi threadleri) okur; sadece tick() degistirir. */
   public static volatile boolean active;
   /** Cizim kancasi en az bir kez calisti mi (calismadiysa baska bir render modu araya giriyor olabilir). */
   public static volatile boolean hooked;
   private static int waited;
   private static boolean warned;

   private static volatile Set<Block> blocks = Set.of();
   private static volatile List<TagKey<Block>> tags = List.of();
   private static boolean needRebuild = true;
   private static int pending = -1;

   private Xray() {
   }

   public static void markRebuild() {
      needRebuild = true;
   }

   /** Mixin: bu blok XRay'de gorunsun mu? */
   public static boolean visible(BlockState st) {
      if (blocks.contains(st.getBlock())) {
         return true;
      }

      List<TagKey<Block>> t = tags;
      for (int i = 0; i < t.size(); i++) {
         if (st.isIn(t.get(i))) {
            return true;
         }
      }

      return false;
   }

   public static void tick() {
      boolean want = Kuro.on[Kuro.XRAY];
      if (needRebuild) {
         build();
         needRebuild = false;
         if (want && active) {
            pending = 8; // slider surukleme sirasinda her tikte degil, kisa bir beklemeden sonra yenile
         }
      }

      if (want != active) {
         active = want;
         hooked = false;
         waited = 0;
         warned = false;
         pending = -1;
         // chunkCullingEnabled artik Kuro.java'da CLEARRENDER/FREECAM/XRAY icin ortak ve tek
         // yerden yonetiliyor (bkz. cullOverride), burada ayrica yazilmiyor.
         reload();
      } else if (want) {
         if (!hooked && !warned && mc.player != null && ++waited > 120) {
            warned = true;
            mc.player.sendMessage(net.minecraft.text.Text.literal("[Kuro] X-Ray çizim kancası çalışmadı. Sodium/Iris gibi bir render modu varsa kaldırıp dene ya da Sodium sürümünü paylaş."), false);
         }

         if (pending > 0 && --pending == 0) {
            pending = -1;
            reload();
         }
      }
   }

   private static void reload() {
      if (mc.worldRenderer != null && mc.world != null) {
         mc.worldRenderer.reload();
      }
   }

   private static void build() {
      Set<Block> bs = new HashSet<>();
      List<TagKey<Block>> ts = new ArrayList<>();
      for (BlockSel.Entry en : BlockSel.ALL) {
         if (XraySel.has(en.key())) {
            for (String id : en.ids()) {
               add(id, bs, ts);
            }
         }
      }

      for (String c : XraySel.custom()) {
         add(c, bs, ts);
      }

      try {
         Path f = FabricLoader.getInstance().getGameDir().resolve("kuro_xray_extra.txt");
         if (!Files.exists(f)) {
            Files.writeString(f, "# XRay icin ek bloklar: satir basina bir blok id (ornek: bookshelf) veya tag:minecraft:beds\n");
         }

         for (String line : Files.readAllLines(f)) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
               add(line, bs, ts);
            }
         }
      } catch (IOException ignored) {
      }

      blocks = bs;
      tags = ts;
   }

   private static void add(String line, Set<Block> bs, List<TagKey<Block>> ts) {
      boolean tag = line.startsWith("tag:");
      String name = (tag ? line.substring(4) : line).trim();
      Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
      if (id == null) {
         return;
      }

      if (tag) {
         ts.add(TagKey.of(RegistryKeys.BLOCK, id));
      } else if (Registries.BLOCK.containsId(id)) {
         bs.add(Registries.BLOCK.get(id));
      }
   }
}
