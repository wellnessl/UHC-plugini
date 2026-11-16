package com.takimliuhc;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;

public final class GameListener implements Listener {
   private final TeamUHC plugin;

   public GameListener(TeamUHC plugin) {
      this.plugin = plugin;
   }

   private Game game() {
      return this.plugin.game();
   }

   private TeamManager tm() {
      return this.plugin.teams();
   }

   private FileConfiguration cfg() {
      return this.plugin.getConfig();
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPvp(EntityDamageByEntityEvent e) {
      Entity entity = e.getEntity();
      if (entity instanceof Player victim) {
         Entity entity2 = e.getDamager();
         if (entity2 instanceof Player damager) {
            ;
         } else {
            Projectile projectile;
            ProjectileSource projectileSource;
            if (entity2 instanceof Projectile && (projectileSource = (projectile = (Projectile)entity2).getShooter()) instanceof Player) {
               Player shooter;
               damager = shooter = (Player)projectileSource;
            }
         }

         if (damager != null && !damager.equals(victim)) {
            if (this.game().isRunning()) {
               if (!this.game().isPvpOn()) {
                  e.setCancelled(true);
               } else {
                  if (!this.cfg().getBoolean("teams.friendly-fire", false) && this.tm().sameTeam(damager.getUniqueId(), victim.getUniqueId())) {
                     e.setCancelled(true);
                     this.plugin.send(damager, "friendly-fire-blocked");
                  }

               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onLobbyDamage(EntityDamageEvent e) {
      if (e.getEntity() instanceof Player && this.game().isWaiting() && this.cfg().getBoolean("game.lobby-protect", true)) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPrePvpFallDamage(EntityDamageEvent e) {
      if (e.getEntity() instanceof Player && this.game().isRunning() && !this.game().isPvpOn() && e.getCause() == DamageCause.FALL) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onLobbyHunger(FoodLevelChangeEvent e) {
      if (e.getEntity() instanceof Player && this.game().isWaiting() && this.cfg().getBoolean("game.lobby-protect", true)) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onRegen(EntityRegainHealthEvent e) {
      if (e.getEntity() instanceof Player) {
         if (this.game().isRegenOff()) {
            EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
            if (r == RegainReason.SATIATED || r == RegainReason.REGEN || r == RegainReason.EATING) {
               e.setCancelled(true);
            }

         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onItemConsume(PlayerItemConsumeEvent e) {
      Player player = e.getPlayer();
      if (this.game().isRunning() && this.game().isPvpOn() && this.game().isParticipant(player.getUniqueId())) {
         if (this.isRestorativePotion(e.getItem())) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline() && this.game().isRunning() && this.game().isPvpOn()) {
                  this.restoreFood(player, 4);
               }

            });
         } else {
            Material type = e.getItem().getType();
            if (type.isEdible() && type != Material.GOLDEN_APPLE && type != Material.ENCHANTED_GOLDEN_APPLE) {
               e.setCancelled(true);
            }
         }
      }
   }

   private boolean isRestorativePotion(ItemStack item) {
      ItemMeta itemMeta;
      if (item != null && item.getType() == Material.POTION && (itemMeta = item.getItemMeta()) instanceof PotionMeta) {
         PotionMeta meta = (PotionMeta)itemMeta;
         PotionType baseType = meta.getBasePotionType();
         return baseType != PotionType.REGENERATION && baseType != PotionType.LONG_REGENERATION && baseType != PotionType.STRONG_REGENERATION && baseType != PotionType.HEALING && baseType != PotionType.STRONG_HEALING ? meta.getCustomEffects().stream().anyMatch((effect) -> effect.getType().equals(PotionEffectType.REGENERATION) || effect.getType().equals(PotionEffectType.INSTANT_HEALTH)) : true;
      } else {
         return false;
      }
   }

   private void restoreFood(Player player, int amount) {
      int food = Math.min(20, player.getFoodLevel() + amount);
      player.setFoodLevel(food);
      player.setSaturation(Math.min((float)food, player.getSaturation() + (float)amount));
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent e) {
      Player v = e.getEntity();
      if (this.game().canRespawnBeforePvp() && this.game().isAlive(v.getUniqueId())) {
         if (this.cfg().getBoolean("game.respawn-keep-inventory", true)) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
         }
         return;
      }
      if (this.game().isRunning() && this.game().isAlive(v.getUniqueId())) {
         if (this.cfg().getBoolean("heads.drop-on-death", true)) {
            e.getDrops().add(this.makeHead(v));
         }

         Player k = v.getKiller();
         TeamManager.UTeam vt = this.tm().of(v);
         TeamManager.UTeam kt = k == null ? null : this.tm().of(k);
         String vTeam = vt == null ? "-" : vt.display();
         String text = k != null ? this.plugin.msg("death-killer", "%victim%", v.getName(), "%victim_team%", vTeam, "%killer%", k.getName(), "%killer_team%", kt == null ? "-" : kt.display()) : this.plugin.msg("death", "%victim%", v.getName(), "%victim_team%", vTeam);
         e.deathMessage(Msg.c(text));
         if (k != null && !k.equals(v) && this.game().isParticipant(k.getUniqueId()) && this.game().isAlive(k.getUniqueId())) {
            this.game().recordKill(k.getUniqueId());
         }

         Location where = v.getLocation();
         this.game().handleDeath(v, where);
      }
   }

   private ItemStack makeHead(Player v) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta m = (SkullMeta)head.getItemMeta();
      m.setOwningPlayer(v);
      m.displayName(Msg.item(this.cfg().getString("heads.name", "&6%player% Kafası").replace("%player%", v.getName())));
      head.setItemMeta(m);
      return head;
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent e) {
      Player p = e.getPlayer();
      UUID id = p.getUniqueId();
      if (this.game().canRespawnBeforePvp() && this.game().isAlive(id)) {
         Location start = this.game().startLocation(id);
         if (start != null) {
            e.setRespawnLocation(start);
         }
         return;
      }
      if (this.game().isActiveMatch() && this.game().isParticipant(id) && !this.game().isAlive(id)) {
         Location loc = this.game().deathLocation(id);
         if (loc != null) {
            e.setRespawnLocation(loc);
         }

         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (this.game().isActiveMatch() && this.game().isParticipant(id) && !this.game().isAlive(id)) {
               p.setGameMode(GameMode.SPECTATOR);
            }

         });
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent e) {
      Player p = e.getPlayer();
      UUID id = p.getUniqueId();
      if (this.game().isActiveMatch()) {
         this.game().cancelDisconnectTimeout(id);
         if (!this.game().isParticipant(id)) {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(this.game().spectatorSpawn());
            this.plugin.send(p, "joined-as-spectator");
         } else if (!this.game().isAlive(id)) {
            p.setGameMode(GameMode.SPECTATOR);
            Location loc = this.game().deathLocation(id);
            p.teleport(loc == null ? this.game().spectatorSpawn() : loc);
         } else {
            p.setGameMode(GameMode.SURVIVAL);
         }

         this.game().refreshSidebar(p);
      } else {
         if (this.game().isWaiting()) {
            Game.setMaxHealth(p, 20.0);
            TeamManager.UTeam t = this.tm().of(p);

            for(String line : this.plugin.lines("welcome", "%team%", t == null ? "-" : t.display())) {
               p.sendMessage(Msg.c(line));
            }
         }

      }
   }


   // ---------------- Lobi korumaları (oyun başlamadan önce) ----------------

   private boolean lobbyBlocked(Player p) {
      return this.game().isWaiting() && !(p.getGameMode() == GameMode.CREATIVE && p.hasPermission("uhc.build"));
   }

   @EventHandler(ignoreCancelled = true)
   public void onLobbyBreak(BlockBreakEvent e) {
      if (this.cfg().getBoolean("lobby.protect-blocks", true) && this.lobbyBlocked(e.getPlayer())) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onLobbyPlace(BlockPlaceEvent e) {
      if (this.cfg().getBoolean("lobby.protect-blocks", true) && this.lobbyBlocked(e.getPlayer())) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onLobbyBucketEmpty(PlayerBucketEmptyEvent e) {
      if (this.cfg().getBoolean("lobby.protect-blocks", true) && this.lobbyBlocked(e.getPlayer())) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onLobbyBucketFill(PlayerBucketFillEvent e) {
      if (this.cfg().getBoolean("lobby.protect-blocks", true) && this.lobbyBlocked(e.getPlayer())) {
         e.setCancelled(true);
      }
   }

   private boolean lobbyNoExplosions() {
      return this.game().isWaiting() && this.cfg().getBoolean("lobby.disable-explosions", true);
   }

   @EventHandler(ignoreCancelled = true)
   public void onLobbyExplosionPrime(ExplosionPrimeEvent e) {
      if (this.lobbyNoExplosions()) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onLobbyEntityExplode(EntityExplodeEvent e) {
      if (this.lobbyNoExplosions()) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onLobbyBlockExplode(BlockExplodeEvent e) {
      if (this.lobbyNoExplosions()) {
         e.setCancelled(true);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      Player p = e.getPlayer();
      this.tm().clearInvites(p);
      if (this.game().isWaiting()) {
         this.tm().leave(p);
      } else {
         this.game().scheduleDisconnectElimination(p);
      }

   }

   @EventHandler
   public void onAdvancement(PlayerAdvancementDoneEvent e) {
      if (this.cfg().getBoolean("game.hide-advancements", true)) {
         e.message((Component)null);
      }

   }

   @EventHandler
   public void onUseHead(PlayerInteractEvent e) {
      Action act = e.getAction();
      if (act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK) {
         EquipmentSlot hand = e.getHand();
         if (hand != null) {
            Player p = e.getPlayer();
            ItemStack item = p.getInventory().getItem(hand);
            if (this.plugin.isGoldenHead(item)) {
               e.setCancelled(true);
               FileConfiguration c = this.cfg();
               p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, c.getInt("golden-head.regen-seconds", 9) * 20, c.getInt("golden-head.regen-amplifier", 1)));
               p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, c.getInt("golden-head.absorption-seconds", 120) * 20, c.getInt("golden-head.absorption-amplifier", 0)));
               if (this.game().isRunning() && this.game().isPvpOn()) {
                  this.restoreFood(p, 4);
               }

               p.playSound(p.getLocation(), "entity.generic.eat", 1.0F, 1.0F);
               if (p.getGameMode() != GameMode.CREATIVE) {
                  item.setAmount(item.getAmount() - 1);
                  p.getInventory().setItem(hand, item.getAmount() <= 0 ? null : item);
               }

            }
         }
      }
   }

   @EventHandler
   public void onPrepareCraft(PrepareItemCraftEvent e) {
      Recipe var3 = e.getRecipe();
      if (var3 instanceof ShapedRecipe sr) {
         if (sr.getKey().equals(this.plugin.goldenHeadKey())) {
            for(ItemStack it : e.getInventory().getMatrix()) {
               if (this.plugin.isGoldenHead(it)) {
                  e.getInventory().setResult((ItemStack)null);
                  return;
               }
            }

            return;
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent e) {
      Player p = e.getPlayer();
      String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
      if (this.game().isActiveMatch()) {
         e.setCancelled(true);
         UUID senderId = p.getUniqueId();
         Component message = e.message();
         Bukkit.getScheduler().runTask(this.plugin, () -> this.routeMatchChat(senderId, plain, message));
      } else {
         TeamManager.UTeam t = this.tm().of(p);
         String prefix = this.cfg().getString("teams.chat.prefix", "!");
         if (t != null && !prefix.isEmpty() && plain.startsWith(prefix) && plain.length() > prefix.length()) {
            e.setCancelled(true);
            this.tm().teamChat(t, p, plain.substring(prefix.length()).trim());
         } else {
            e.renderer(ChatRenderer.viewerUnaware((source, name, messagex) -> {
               TeamManager.UTeam st = this.tm().of(source);
               TextComponent tag = st == null ? Component.empty() : Component.text("[" + st.name + "] ", st.col.color());
               return ((TextComponent)((TextComponent)tag.append(Component.text(source.getName(), NamedTextColor.WHITE))).append(Component.text(": ", NamedTextColor.GRAY))).append(messagex);
            }));
         }
      }
   }

   private void routeMatchChat(UUID senderId, String plain, Component message) {
      Player sender = Bukkit.getPlayer(senderId);
      if (sender != null && this.game().isActiveMatch()) {
         if (this.isDeadChatPlayer(sender)) {
            String format = this.cfg().getString("spectator-chat.format", "&8[Ölüler] &7%player%&8: &7%message%");
            Component line = Msg.c(format.replace("%player%", sender.getName())).replaceText((TextReplacementConfig)TextReplacementConfig.builder().matchLiteral("%message%").replacement(message).build());

            for(Player recipient : Bukkit.getOnlinePlayers()) {
               if (this.isDeadChatPlayer(recipient)) {
                  recipient.sendMessage(line);
               }
            }

         } else {
            TeamManager.UTeam team = this.tm().of(sender);
            String prefix = this.cfg().getString("teams.chat.prefix", "!");
            if (team != null && !prefix.isEmpty() && plain.startsWith(prefix) && plain.length() > prefix.length()) {
               this.tm().teamChat(team, sender, plain.substring(prefix.length()).trim());
            } else {
               Component tag = team == null ? Component.empty() : Component.text("[" + team.name + "] ", team.col.color());
               Component line = tag.append(Component.text(sender.getName(), NamedTextColor.WHITE)).append(Component.text(": ", NamedTextColor.GRAY)).append(message);

               for(Player recipient : Bukkit.getOnlinePlayers()) {
                  recipient.sendMessage(line);
               }

            }
         }
      }
   }

   private boolean isDeadChatPlayer(Player player) {
      UUID id = player.getUniqueId();
      return player.getGameMode() == GameMode.SPECTATOR || this.game().isParticipant(id) && !this.game().isAlive(id);
   }
}
