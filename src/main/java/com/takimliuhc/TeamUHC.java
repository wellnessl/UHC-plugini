package com.takimliuhc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public final class TeamUHC extends JavaPlugin {
   private TeamManager teams;
   private Game game;
   private NamespacedKey goldenHeadKey;

   public void onEnable() {
      this.saveDefaultConfig();
      this.goldenHeadKey = new NamespacedKey(this, "golden_head");
      this.teams = new TeamManager(this);
      this.game = new Game(this);
      Commands cmds = new Commands(this);

      for(String name : new String[]{"uhc", "team", "tc"}) {
         PluginCommand pc = this.getCommand(name);
         if (pc != null) {
            pc.setExecutor(cmds);
            pc.setTabCompleter(cmds);
         }
      }

      this.getServer().getPluginManager().registerEvents(new GameListener(this), this);
      this.registerGoldenHead();

      // Oyun başlamadan önce gündüz/gece döngüsünü sabit tut
      Bukkit.getScheduler().runTaskTimer(this, () -> {
         if (this.game == null || !this.game.isWaiting() || !this.getConfig().getBoolean("lobby.freeze-time", true)) {
            return;
         }
         World w = Bukkit.getWorld(this.getConfig().getString("game.world", "world"));
         if (w == null) {
            w = Bukkit.getWorlds().get(0);
         }
         w.setTime(this.getConfig().getLong("lobby.time", 6000L));
      }, 20L, 20L);
   }

   public void onDisable() {
      if (this.game != null) {
         this.game.shutdown();
      }

      if (this.teams != null) {
         this.teams.clearAll();
      }

      Bukkit.removeRecipe(this.goldenHeadKey);
   }

   public void reload() {
      this.reloadConfig();
      this.registerGoldenHead();
      if (this.game != null) {
         this.game.reloadConfig();
      }

   }

   public TeamManager teams() {
      return this.teams;
   }

   public Game game() {
      return this.game;
   }

   public NamespacedKey goldenHeadKey() {
      return this.goldenHeadKey;
   }

   private static String apply(String s, String... kv) {
      for(int i = 0; i + 1 < kv.length; i += 2) {
         s = s.replace(kv[i], kv[i + 1]);
      }

      return s;
   }

   public String msg(String key, String... kv) {
      Object raw = this.getConfig().getString("messages." + key);
      if (raw == null && "quit-eliminated".equals(key)) {
         raw = "&c⚡ &f%player% &7(%team%&7) &csunucudan ayrıldığı için elendi.";
      }

      if (raw == null) {
         raw = "&cEksik mesaj: " + key;
      }

      return apply((String)raw, kv);
   }

   public List<String> lines(String key, String... kv) {
      ArrayList<String> out = new ArrayList();
      String path = "messages." + key;
      if (this.getConfig().isList(path)) {
         for(String l : this.getConfig().getStringList(path)) {
            out.add(apply(l, kv));
         }
      } else {
         out.add(this.msg(key, kv));
      }

      return out;
   }

   public void send(CommandSender to, String key, String... kv) {
      String var10001 = this.getConfig().getString("prefix", "");
      to.sendMessage(Msg.c(var10001 + this.msg(key, kv)));
   }

   public void broadcast(String key, String... kv) {
      String var10000 = this.getConfig().getString("prefix", "");
      Bukkit.broadcast(Msg.c(var10000 + this.msg(key, kv)));
   }

   public void broadcastLines(String key, String... kv) {
      for(String l : this.lines(key, kv)) {
         Bukkit.broadcast(Msg.c(l));
      }

   }

   public boolean isGoldenHead(ItemStack item) {
      return item != null && item.getType() == Material.PLAYER_HEAD && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().has(this.goldenHeadKey, PersistentDataType.BYTE) : false;
   }

   public ItemStack goldenHeadItem() {
      ItemStack it = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta m = (SkullMeta)it.getItemMeta();
      this.applyTexture(m, this.getConfig().getString("golden-head.texture", ""));
      m.displayName(Msg.item(this.getConfig().getString("golden-head.name", "&6Altın Kafa")));
      ArrayList<Component> lore = new ArrayList();

      for(String l : this.getConfig().getStringList("golden-head.lore")) {
         lore.add(Msg.item(l));
      }

      if (!lore.isEmpty()) {
         m.lore(lore);
      }

      m.getPersistentDataContainer().set(this.goldenHeadKey, PersistentDataType.BYTE, (byte)1);
      it.setItemMeta(m);
      return it;
   }

   private void applyTexture(SkullMeta meta, String tex) {
      if (tex != null && !tex.isBlank()) {
         try {
            String raw = tex.trim();
            String urlStr;
            if (raw.startsWith("http")) {
               urlStr = raw;
            } else {
               String json = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
               Matcher mt = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
               if (!mt.find()) {
                  throw new IllegalArgumentException("texture JSON içinde url yok");
               }

               urlStr = mt.group(1);
            }

            UUID id = UUID.nameUUIDFromBytes(("goldenhead:" + urlStr).getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createPlayerProfile(id, "GoldenHead");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(urlStr).toURL());
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
         } catch (Exception ex) {
            this.getLogger().warning("golden-head.texture okunamadı: " + ex.getMessage());
         }

      }
   }

   private void registerGoldenHead() {
      Bukkit.removeRecipe(this.goldenHeadKey);
      if (this.getConfig().getBoolean("golden-head.enabled", true)) {
         ShapedRecipe r = new ShapedRecipe(this.goldenHeadKey, this.goldenHeadItem());
         r.shape(new String[]{"GGG", "GHG", "GGG"});
         r.setIngredient('G', Material.GOLD_INGOT);
         r.setIngredient('H', Material.PLAYER_HEAD);
         Bukkit.addRecipe(r);
      }
   }
}
