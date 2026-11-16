package com.takimliuhc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class Airdrop {
   private final TeamUHC plugin;
   private final Random rnd = new Random();
   private final List<LootEntry> loot = new ArrayList();
   private final List<Location> chests = new ArrayList();

   public Airdrop(TeamUHC plugin) {
      this.plugin = plugin;
   }

   public void loadLoot() {
      this.loot.clear();

      for(String line : this.plugin.getConfig().getStringList("airdrops.loot")) {
         try {
            String[] parts = line.trim().split(":");
            String spec = parts[0].trim().toUpperCase(Locale.ROOT);
            String name = spec;
            PotionType potionType = null;
            int potionSeparator = spec.indexOf(64);
            if (potionSeparator >= 0) {
               name = spec.substring(0, potionSeparator).trim();
               String potionName = spec.substring(potionSeparator + 1).trim();
               if (potionName.isEmpty()) {
                  this.plugin.getLogger().warning("Airdrop potion tipi eksik -> " + line);
                  continue;
               }

               try {
                  potionType = PotionType.valueOf(potionName);
               } catch (IllegalArgumentException var15) {
                  this.plugin.getLogger().warning("Airdrop potion tipi geçersiz -> " + line);
                  continue;
               }
            }

            String[] stringArray;
            if (parts.length > 1) {
               stringArray = parts[1].trim().split("-");
            } else {
               String[] stringArray2;
               stringArray = stringArray2 = new String[1];
               stringArray2[0] = "1";
            }

            int min = Integer.parseInt(stringArray[0].trim());
            int max = stringArray.length > 1 ? Integer.parseInt(stringArray[1].trim()) : min;
            int weight = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 10;
            if (max < min) {
               max = min;
            }

            if (weight < 1) {
               weight = 1;
            }

            if (name.equals("GOLDEN_HEAD")) {
               this.loot.add(new LootEntry((Material)null, (PotionType)null, true, min, max, weight));
            } else {
               Material m = Material.matchMaterial(name);
               if (m != null && !m.isAir() && m.isItem()) {
                  if (potionType != null && m != Material.POTION && m != Material.SPLASH_POTION && m != Material.LINGERING_POTION) {
                     this.plugin.getLogger().warning("Airdrop potion effects require a potion material -> " + line);
                  } else {
                     this.loot.add(new LootEntry(m, potionType, false, min, max, weight));
                  }
               } else {
                  this.plugin.getLogger().warning("Airdrop loot: geçersiz materyal -> " + line);
               }
            }
         } catch (Exception var16) {
            this.plugin.getLogger().warning("Airdrop loot satırı okunamadı -> " + line);
         }
      }

      if (this.loot.isEmpty()) {
         this.plugin.getLogger().warning("Airdrop loot listesi boş! config.yml -> airdrops.loot kontrol et.");
      }

   }

   public void drop(World world) {
      FileConfiguration c = this.plugin.getConfig();
      if (!this.loot.isEmpty()) {
         WorldBorder wb = world.getWorldBorder();
         double half = Math.max((double)5.0F, wb.getSize() / (double)2.0F - c.getDouble("airdrops.border-margin", (double)30.0F));
         Location center = wb.getCenter();
         Location spot = null;

         for(int i = 0; i < 40; ++i) {
            double x = center.getX() + (this.rnd.nextDouble() * (double)2.0F - (double)1.0F) * half;
            double z = center.getZ() + (this.rnd.nextDouble() * (double)2.0F - (double)1.0F) * half;
            int bx = (int)Math.floor(x);
            int bz = (int)Math.floor(z);
            int y = world.getHighestBlockYAt(bx, bz);
            Block ground = world.getBlockAt(bx, y, bz);
            Block above = world.getBlockAt(bx, y + 1, bz);
            if (!ground.isLiquid() && above.getType().isAir()) {
               spot = above.getLocation();
               break;
            }
         }

         if (spot != null) {
            Block block = spot.getBlock();
            block.setType(Material.CHEST);
            BlockState state = block.getState();
            if (state instanceof Chest) {
               Chest chest = (Chest)state;
               this.fill(chest.getBlockInventory());
               this.chests.add(spot);
               world.strikeLightningEffect(spot);
               this.plugin.broadcastLines("airdrop-dropped", "%x%", String.valueOf(spot.getBlockX()), "%y%", String.valueOf(spot.getBlockY()), "%z%", String.valueOf(spot.getBlockZ()));
            }
         }
      }
   }

   private void fill(Inventory inv) {
      FileConfiguration c = this.plugin.getConfig();
      int min = Math.max(1, c.getInt("airdrops.items-per-chest.min", 6));
      int max = Math.max(min, c.getInt("airdrops.items-per-chest.max", 10));
      int count = Math.min(inv.getSize(), min + this.rnd.nextInt(max - min + 1));
      ArrayList<Integer> slots = new ArrayList();

      for(int i = 0; i < inv.getSize(); ++i) {
         slots.add(i);
      }

      Collections.shuffle(slots, this.rnd);
      HashSet<LootEntry> used = new HashSet();

      LootEntry e;
      for(int i = 0; i < count && (e = this.pick(used)) != null; ++i) {
         used.add(e);
         int amount = e.min() + this.rnd.nextInt(e.max() - e.min() + 1);
         ItemStack item;
         if (e.goldenHead()) {
            item = this.plugin.goldenHeadItem();
            item.setAmount(Math.max(1, Math.min(amount, 64)));
         } else {
            item = new ItemStack(e.mat(), Math.max(1, Math.min(amount, e.mat().getMaxStackSize())));
            if (e.potionType() != null) {
               PotionMeta meta = (PotionMeta)item.getItemMeta();
               meta.setBasePotionType(e.potionType());
               item.setItemMeta(meta);
            }
         }

         inv.setItem((Integer)slots.get(i), item);
      }

   }

   private LootEntry pick(Set<LootEntry> used) {
      int total = 0;

      for(LootEntry e : this.loot) {
         if (!used.contains(e)) {
            total += e.weight();
         }
      }

      if (total <= 0) {
         return null;
      } else {
         int r = this.rnd.nextInt(total);

         for(LootEntry e : this.loot) {
            if (!used.contains(e) && (r -= e.weight()) < 0) {
               return e;
            }
         }

         return null;
      }
   }

   public void clear() {
      for(Location l : this.chests) {
         Block b = l.getBlock();
         BlockState blockState = b.getState();
         if (blockState instanceof Chest ch) {
            ch.getBlockInventory().clear();
         }

         if (b.getType() == Material.CHEST) {
            b.setType(Material.AIR);
         }
      }

      this.chests.clear();
   }

   private static record LootEntry(Material mat, PotionType potionType, boolean goldenHead, int min, int max, int weight) {
   }
}
