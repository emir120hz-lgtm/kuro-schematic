package kuro;

import java.util.HashSet;
import java.util.Set;

/** BlockESP icin menuden secilebilen blok katalogu. Secimler Config.blocks icinde (virgullu anahtarlar) saklanir. */
public final class BlockSel {
   public static final String DEFAULT = "spawner,beacon,ancient_debris,diamond,enchanting_table,end_portal_frame,respawn_anchor,bed";

   /** ids: blok id'leri; "tag:" ile baslayan satir blok etiketi demektir. */
   public record Entry(String key, String label, int color, String[] ids) {
   }

   private static Entry e(String key, String label, int color, String... ids) {
      return new Entry(key, label, color, ids);
   }

   public static final Entry[] ALL = new Entry[]{
      e("spawner", "Spawner", 0xFF2020, "spawner", "trial_spawner"),
      e("chest", "Sandık", 0xFFA020, "chest"),
      e("trapped_chest", "Tuzaklı sandık", 0xFF4030, "trapped_chest"),
      e("ender_chest", "Ender sandığı", 0xB020FF, "ender_chest"),
      e("barrel", "Varil", 0xA06030, "barrel"),
      e("shulker", "Shulker kutusu", 0xFF60E0, "shulker_box", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box",
         "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box", "gray_shulker_box",
         "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box", "brown_shulker_box",
         "green_shulker_box", "red_shulker_box", "black_shulker_box"),
      e("furnace", "Fırın", 0xA0A0A0, "furnace", "blast_furnace", "smoker"),
      e("hopper", "Huni / Dropper / Dispenser", 0x30D0FF, "hopper", "dropper", "dispenser"),
      e("bed", "Yatak", 0xFFFFC0, "tag:minecraft:beds"),
      e("respawn_anchor", "Respawn anchor", 0xFF40FF, "respawn_anchor"),
      e("beacon", "Beacon", 0x60FFFF, "beacon"),
      e("enchanting_table", "Büyü masası", 0x6080FF, "enchanting_table"),
      e("end_portal_frame", "End portal çerçevesi", 0x80FF80, "end_portal_frame"),
      e("crafting_table", "Crafting table", 0xC08040, "crafting_table"),
      e("tnt", "TNT", 0xFF3030, "tnt"),
      e("obsidian", "Obsidyen", 0x7040C0, "obsidian", "crying_obsidian"),
      e("ancient_debris", "Ancient debris", 0xC07040, "ancient_debris"),
      e("diamond", "Elmas cevheri", 0x40E0FF, "diamond_ore", "deepslate_diamond_ore"),
      e("emerald", "Zümrüt cevheri", 0x30FF60, "emerald_ore", "deepslate_emerald_ore"),
      e("gold", "Altın cevheri", 0xFFD020, "gold_ore", "deepslate_gold_ore", "nether_gold_ore"),
      e("iron", "Demir cevheri", 0xD8B090, "iron_ore", "deepslate_iron_ore"),
      e("copper", "Bakır cevheri", 0xE08050, "copper_ore", "deepslate_copper_ore"),
      e("coal", "Kömür cevheri", 0x606060, "coal_ore", "deepslate_coal_ore"),
      e("redstone", "Redstone cevheri", 0xFF2020, "redstone_ore", "deepslate_redstone_ore"),
      e("lapis", "Lapis cevheri", 0x2040FF, "lapis_ore", "deepslate_lapis_ore"),
      e("quartz", "Quartz cevheri", 0xF0F0F0, "nether_quartz_ore"),
      e("netherite", "Netherite bloğu", 0x5A4A5A, "netherite_block")
   };

   private static final Set<String> on = new HashSet<>();

   private BlockSel() {
   }

   public static void load() {
      on.clear();
      for (String s : Config.blocks.split(",")) {
         String k = s.trim();
         if (!k.isEmpty()) {
            on.add(k);
         }
      }
   }

   /** Menuden eklenen ozel bloklar (Config.customBlocks, virgullu). */
   public static java.util.List<String> custom() {
      java.util.List<String> l = new java.util.ArrayList<>();
      for (String s : Config.customBlocks.split(",")) {
         if (!s.isBlank()) {
            l.add(s.trim());
         }
      }

      return l;
   }

   /** @return null = eklendi, aksi halde hata metni */
   public static String addCustom(String raw) {
      String id = Mods.validBlockId(raw);
      if (id == null) {
         return "Geçersiz blok: " + raw.trim();
      }

      java.util.List<String> l = custom();
      if (l.contains(id)) {
         return "Zaten ekli";
      }

      l.add(id);
      Config.customBlocks = String.join(",", l);
      Mods.rebuildTargets();
      return null;
   }

   public static void removeCustom(int index) {
      java.util.List<String> l = custom();
      if (index >= 0 && index < l.size()) {
         l.remove(index);
         Config.customBlocks = String.join(",", l);
         Mods.rebuildTargets();
      }
   }

   public static boolean has(String key) {
      return on.contains(key);
   }

   public static void set(String key, boolean v) {
      if (v ? on.add(key) : on.remove(key)) {
         StringBuilder sb = new StringBuilder();
         for (Entry en : ALL) {
            if (on.contains(en.key())) {
               if (sb.length() > 0) {
                  sb.append(',');
               }

               sb.append(en.key());
            }
         }

         Config.blocks = sb.toString();
         Mods.rebuildTargets();
      }
   }
}
