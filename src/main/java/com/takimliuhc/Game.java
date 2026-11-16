package com.takimliuhc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class Game {
   private final TeamUHC plugin;
   private final Random rnd = new Random();
   private volatile State state;
   private World world;
   private BukkitTask task;
   private BukkitTask endTask;
   private int elapsed;
   private boolean pvpOn;
   private boolean regenOff;
   private boolean glowOn;
   private final Set<UUID> alive;
   private final Set<UUID> participants;
   private final Map<UUID, Location> deathLocs;
   private final Map<UUID, Integer> kills;
   private final Map<UUID, BukkitTask> disconnectTasks;
   private final Airdrop airdrop;
   private final GameSidebar sidebar;
   private final Map<UUID, Location> startLocs = new HashMap<>();

   public Game(TeamUHC plugin) {
      this.state = Game.State.WAITING;
      this.alive = new HashSet();
      this.participants = new HashSet();
      this.deathLocs = new HashMap();
      this.kills = new HashMap();
      this.disconnectTasks = new HashMap();
      this.plugin = plugin;
      this.airdrop = new Airdrop(plugin);
      this.sidebar = new GameSidebar(plugin, this);
   }


   /** Zorluk: PEACEFUL, EASY, NORMAL, HARD ya da KEEP (dokunma). */
   private void applyDifficulty(String name) {
      if (name == null || name.isBlank() || name.equalsIgnoreCase("KEEP")) {
         return;
      }
      try {
         this.world.setDifficulty(Difficulty.valueOf(name.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
         this.plugin.getLogger().warning("game.difficulty geçersiz: " + name + " (PEACEFUL/EASY/NORMAL/HARD/KEEP)");
      }
   }

   /** Config'teki kalp sayısını can puanına (kalp x 2) çevirir. */
   public double configuredMaxHealth() {
      double hearts = this.plugin.getConfig().getDouble("game.hearts", 10.0);
      hearts = Math.max(1.0, Math.min(hearts, 100.0));
      return hearts * 2.0;
   }

   public static void setMaxHealth(Player p, double hp) {
      AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
      if (attr != null && attr.getBaseValue() != hp) {
         attr.setBaseValue(hp);
      }
   }

   public Location startLocation(UUID id) {
      return this.startLocs.get(id);
   }

   /** PvP açılmadan önce ölen oyuncu elenmeden yeniden doğar mı? */
   public boolean canRespawnBeforePvp() {
      return this.isRunning() && !this.pvpOn && this.plugin.getConfig().getBoolean("game.respawn-before-pvp", true);
   }

   public boolean isRunning() {
      return this.state == Game.State.RUNNING;
   }

   public boolean isActiveMatch() {
      return this.state != Game.State.WAITING;
   }

   public boolean isWaiting() {
      return this.state == Game.State.WAITING;
   }

   public boolean isPvpOn() {
      return this.pvpOn;
   }

   public boolean skipToPvpTime() {
      if (this.isRunning() && !this.pvpOn) {
         int target = Math.max(1, this.plugin.getConfig().getInt("game.pvp-time", 1500));

         while(this.elapsed < target) {
            this.tick(false);
         }

         if (!this.pvpOn) {
            this.pvpOn = true;
            this.plugin.broadcastLines("pvp-enabled");
         }

         if (!this.regenOff) {
            this.regenOff = true;
            this.plugin.broadcastLines("regen-disabled");
         }

         if (this.elapsed >= this.plugin.getConfig().getInt("game.start-protection-seconds", 20)) {
            for(UUID id : this.alive) {
               Player player = Bukkit.getPlayer(id);
               PotionEffect protection;
               if (player != null && (protection = player.getPotionEffect(PotionEffectType.RESISTANCE)) != null && protection.getAmplifier() == 4) {
                  player.removePotionEffect(PotionEffectType.RESISTANCE);
               }
            }
         }

         this.syncBorderToElapsed();
         this.sidebar.updateAll();
         return true;
      } else {
         return false;
      }
   }

   public boolean isRegenOff() {
      return this.regenOff;
   }

   public boolean isAlive(UUID id) {
      return this.alive.contains(id);
   }

   public boolean isParticipant(UUID id) {
      return this.participants.contains(id);
   }

   public int alivePlayerCount() {
      return this.alive.size();
   }

   public int aliveTeamCount() {
      return this.aliveTeams().size();
   }

   public int killsFor(UUID id) {
      return (Integer)this.kills.getOrDefault(id, 0);
   }

   public String elapsedDisplay() {
      return String.format(Locale.ROOT, "%02d:%02d", this.elapsed / 60, this.elapsed % 60);
   }

   public String borderSizeDisplay() {
      return String.valueOf(Math.round(this.world == null ? (double)0.0F : this.world.getWorldBorder().getSize()));
   }

   public void recordKill(UUID id) {
      if (this.isRunning() && this.isParticipant(id) && this.isAlive(id)) {
         this.kills.merge(id, 1, Integer::sum);
      }

   }

   public void refreshSidebar(Player player) {
      this.sidebar.update(player);
   }

   public void reloadConfig() {
      this.airdrop.loadLoot();
      this.sidebar.updateAll();
   }

   public Location deathLocation(UUID id) {
      return (Location)this.deathLocs.get(id);
   }

   public Location spectatorSpawn() {
      World w = this.world != null ? this.world : (World)Bukkit.getWorlds().get(0);
      return w.getSpawnLocation();
   }

   public void start(CommandSender sender) {
      if (this.state != Game.State.WAITING) {
         this.plugin.send(sender, "already-running");
      } else {
         FileConfiguration c = this.plugin.getConfig();
         TeamManager tm = this.plugin.teams();
         World w = Bukkit.getWorld(c.getString("game.world", "world"));
         if (w == null) {
            w = (World)Bukkit.getWorlds().get(0);
         }

         this.world = w;
         tm.removeOffline();
         ArrayList<Player> players = new ArrayList();

         for(Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("uhc.spectate")) {
               p.setGameMode(GameMode.SPECTATOR);
            } else {
               players.add(p);
            }
         }

         this.assignLeftovers(players);
         int min = c.getInt("game.min-teams", 2);
         if (tm.all().size() < min) {
            this.plugin.send(sender, "not-enough-teams", "%min%", String.valueOf(min));
         } else {
            tm.setLocked(true);
            this.alive.clear();
            this.participants.clear();
            this.deathLocs.clear();
            this.startLocs.clear();
            this.kills.clear();
            this.cancelAllDisconnectTimeouts();
            this.elapsed = 0;
            this.pvpOn = false;
            this.regenOff = false;
            this.glowOn = false;
            WorldBorder wb = this.world.getWorldBorder();
            wb.setCenter(c.getDouble("border.center.x", (double)0.0F), c.getDouble("border.center.z", (double)0.0F));
            wb.setSize(c.getDouble("border.start-size", (double)2000.0F));
            wb.setDamageAmount(c.getDouble("border.damage", (double)1.0F));
            wb.setDamageBuffer((double)0.0F);
            wb.setWarningDistance(c.getInt("border.warning-distance", 10));
            this.world.setTime(0L);
            this.world.setStorm(false);
            this.applyDifficulty(c.getString("game.difficulty", "NORMAL"));

            this.scatter(players);

            for(Player p : players) {
               this.resetPlayer(p);
               this.alive.add(p.getUniqueId());
               this.participants.add(p.getUniqueId());
            }

            this.airdrop.loadLoot();
            this.state = Game.State.RUNNING;
            this.sidebar.updateAll();
            this.plugin.broadcastLines("game-start", "%team-size%", String.valueOf(tm.maxSize()), "%pvp-time%", String.valueOf(c.getInt("game.pvp-time", 1500) / 60), "%border-time%", String.valueOf(c.getInt("game.border-start-time", 1500) / 60));
            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> this.tick(), 20L, 20L);
         }
      }
   }

   private void assignLeftovers(List<Player> players) {
      TeamManager tm = this.plugin.teams();
      int size = tm.maxSize();
      List<Player> left = (List)players.stream().filter((p) -> tm.of(p) == null).collect(Collectors.toList());
      if (!this.plugin.getConfig().getBoolean("teams.auto-assign", true)) {
         for(Player p2 : left) {
            tm.create(p2);
         }

      } else {
         Collections.shuffle(left);
         Iterator<Player> it = left.iterator();

         for(TeamManager.UTeam t : tm.all()) {
            while(t.members.size() < size && it.hasNext()) {
               tm.addMember(t, (Player)it.next());
            }
         }

         TeamManager.UTeam cur = null;

         while(it.hasNext()) {
            Player p3 = (Player)it.next();
            if (cur != null && cur.members.size() < size) {
               tm.addMember(cur, p3);
            } else {
               cur = tm.create(p3);
            }
         }

      }
   }

   private void scatter(List<Player> players) {
      FileConfiguration c = this.plugin.getConfig();
      WorldBorder wb = this.world.getWorldBorder();
      Location center = wb.getCenter();
      double half = wb.getSize() / (double)2.0F * 0.9;
      double radius = c.getDouble("teams.spawn-radius", (double)5.0F);
      boolean together = c.getBoolean("teams.spawn-together", true);

      for(TeamManager.UTeam t : this.plugin.teams().all()) {
         Location base = this.safeSpot(center.getX(), center.getZ(), half);

         for(Player p : t.online()) {
            if (players.contains(p)) {
               Location dest = together ? this.safeSpot(base.getX(), base.getZ(), radius) : this.safeSpot(center.getX(), center.getZ(), half);
               p.teleport(dest);
               this.startLocs.put(p.getUniqueId(), dest);
            }
         }
      }

   }

   private Location safeSpot(double cx, double cz, double range) {
      Location loc = null;

      for(int i = 0; i < 20; ++i) {
         double x = cx + (this.rnd.nextDouble() * (double)2.0F - (double)1.0F) * range;
         double z = cz + (this.rnd.nextDouble() * (double)2.0F - (double)1.0F) * range;
         int bx = (int)Math.floor(x);
         int bz = (int)Math.floor(z);
         int y = this.world.getHighestBlockYAt(bx, bz);
         loc = new Location(this.world, (double)bx + (double)0.5F, (double)(y + 1), (double)bz + (double)0.5F);
         if (!this.world.getBlockAt(bx, y, bz).isLiquid()) {
            break;
         }
      }

      return loc;
   }

   private void resetPlayer(Player p) {
      FileConfiguration c = this.plugin.getConfig();
      p.setGameMode(GameMode.SURVIVAL);
      p.getInventory().clear();

      for(PotionEffect e : new ArrayList(p.getActivePotionEffects())) {
         p.removePotionEffect(e.getType());
      }

      double maxHp = this.configuredMaxHealth();
      setMaxHealth(p, maxHp);
      p.setHealth(maxHp);
      p.setFoodLevel(20);
      p.setSaturation(20.0F);
      p.setExp(0.0F);
      p.setLevel(0);
      p.setFireTicks(0);
      p.setFallDistance(0.0F);
      p.setGlowing(false);
      int prot = c.getInt("game.start-protection-seconds", 20);
      if (prot > 0) {
         p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, prot * 20, 4));
      }

   }

   private void tick() {
      this.tick(true);
   }

   private void tick(boolean announceTimers) {
      FileConfiguration c = this.plugin.getConfig();
      ++this.elapsed;
      int pvp = c.getInt("game.pvp-time", 1500);
      int regen = Math.min(c.getInt("game.regen-disable-time", 1500), pvp);
      int bStart = c.getInt("game.border-start-time", 1500);
      int shrink = c.getInt("game.border-shrink-time", 2400);
      int bEnd = bStart + shrink;
      int glow = bEnd + c.getInt("game.glow-delay", 480);
      if (this.elapsed == pvp && !this.pvpOn) {
         this.pvpOn = true;
         this.plugin.broadcastLines("pvp-enabled");
         if (!this.regenOff) {
            this.regenOff = true;
            this.plugin.broadcastLines("regen-disabled");
         }
      }

      if (this.elapsed == regen && !this.regenOff) {
         this.regenOff = true;
         this.plugin.broadcastLines("regen-disabled");
      }

      if (this.elapsed == bStart) {
         this.world.getWorldBorder().setSize(c.getDouble("border.end-size", (double)20.0F), (long)shrink);
         this.plugin.broadcastLines("border-start", "%size%", String.valueOf((int)c.getDouble("border.end-size", (double)20.0F)), "%time%", String.valueOf(shrink / 60));
      }

      if (this.elapsed == bEnd) {
         this.plugin.broadcastLines("border-finished", "%time%", String.valueOf(c.getInt("game.glow-delay", 480) / 60));
      }

      if (this.elapsed == glow && c.getBoolean("glow.enabled", true)) {
         this.glowOn = true;

         for(UUID id : this.alive) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
               p.setGlowing(true);
            }
         }

         this.plugin.broadcastLines("glow-enabled");
      }

      if (c.getBoolean("airdrops.enabled", true)) {
         int first = c.getInt("airdrops.first-drop-seconds", 900);
         int interval = Math.max(30, c.getInt("airdrops.interval-seconds", 300));
         if (this.elapsed >= first && (this.elapsed - first) % interval == 0) {
            this.airdrop.drop(this.world);
         }
      }

      if (announceTimers && c.getBoolean("timers.enabled", true)) {
         this.announce("pvp", pvp);
         this.announce("regen", regen);
         this.announce("border", bStart);
         this.announce("border-end", bEnd);
         this.announce("glow", glow);
      }

      if (announceTimers) {
         this.sidebar.updateAll();
      }

   }

   private void syncBorderToElapsed() {
      FileConfiguration c = this.plugin.getConfig();
      int borderStart = c.getInt("game.border-start-time", 1500);
      if (this.elapsed >= borderStart) {
         int shrinkSeconds = Math.max(1, c.getInt("game.border-shrink-time", 2400));
         int elapsedShrink = Math.min(shrinkSeconds, this.elapsed - borderStart);
         double startSize = c.getDouble("border.start-size", (double)2000.0F);
         double endSize = c.getDouble("border.end-size", (double)20.0F);
         double progress = (double)elapsedShrink / (double)shrinkSeconds;
         double currentSize = startSize + (endSize - startSize) * progress;
         WorldBorder border = this.world.getWorldBorder();
         border.setSize(currentSize);
         if (elapsedShrink < shrinkSeconds) {
            border.setSize(endSize, (long)(shrinkSeconds - elapsedShrink));
         }

      }
   }

   private void announce(String key, int target) {
      if ((!key.equals("pvp") || !this.pvpOn) && (!key.equals("regen") || !this.regenOff)) {
         int remaining = target - this.elapsed;
         if (remaining > 0) {
            int interval = Math.max(1, this.plugin.getConfig().getInt("timers.interval", 60));
            boolean show = remaining % interval == 0 || remaining == 30 || remaining == 10 || remaining <= 5;
            if (show) {
               this.plugin.broadcast("timer-" + key, "%time%", this.fmt(remaining));
            }

         }
      }
   }

   private String fmt(int s) {
      String min = this.plugin.msg("unit-minute");
      String sec = this.plugin.msg("unit-second");
      if (s >= 60) {
         int m = s / 60;
         int r = s % 60;
         return m + " " + min + (r > 0 ? " " + r + " " + sec : "");
      } else {
         return s + " " + sec;
      }
   }

   public void scheduleDisconnectElimination(Player player) {
      UUID id = player.getUniqueId();
      if (this.isRunning() && this.isParticipant(id) && this.isAlive(id)) {
         this.cancelDisconnectTimeout(id);
         String playerName = player.getName();
         Location lastLocation = player.getLocation().clone();
         int graceSeconds = Math.max(0, this.plugin.getConfig().getInt("game.disconnect-grace-seconds", 300));
         if (graceSeconds == 0) {
            this.markDead(id, playerName, lastLocation, true);
         } else {
            BukkitTask pending = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               this.disconnectTasks.remove(id);
               if (this.isRunning() && Bukkit.getPlayer(id) == null && this.isAlive(id)) {
                  this.markDead(id, playerName, lastLocation, true);
               }
            }, (long)graceSeconds * 20L);
            this.disconnectTasks.put(id, pending);
         }
      }
   }

   public void cancelDisconnectTimeout(UUID id) {
      BukkitTask pending = (BukkitTask)this.disconnectTasks.remove(id);
      if (pending != null) {
         pending.cancel();
      }

   }

   private void cancelAllDisconnectTimeouts() {
      for(BukkitTask pending : this.disconnectTasks.values()) {
         pending.cancel();
      }

      this.disconnectTasks.clear();
   }

   public void handleDeath(Player victim, Location where) {
      this.markDead(victim.getUniqueId(), victim.getName(), where, false);
   }

   private void markDead(UUID id, String playerName, Location where, boolean disconnected) {
      if (this.state == Game.State.RUNNING && this.alive.remove(id)) {
         this.cancelDisconnectTimeout(id);
         this.deathLocs.put(id, where == null ? this.spectatorSpawn() : where.clone());
         TeamManager tm = this.plugin.teams();
         TeamManager.UTeam t = tm.of(id);
         if (disconnected) {
            this.plugin.broadcast("quit-eliminated", "%player%", playerName, "%team%", t == null ? "-" : t.display());
         }

         if (t != null) {
            Stream var10000 = t.members.keySet().stream();
            Set var10001 = this.alive;
            Objects.requireNonNull(var10001);
            long left = var10000.filter(var10001::contains).count();

            for(Player m : t.online()) {
               if (this.alive.contains(m.getUniqueId())) {
                  this.plugin.send(m, "teammate-died", "%player%", playerName, "%alive%", String.valueOf(left));
               }
            }

            if (left == 0L) {
               this.plugin.broadcastLines("team-eliminated", "%team%", t.display(), "%teams-left%", String.valueOf(this.aliveTeams().size()));
            }
         }

         this.checkWin();
      }
   }

   private Set<TeamManager.UTeam> aliveTeams() {
      HashSet<TeamManager.UTeam> set = new HashSet();

      for(UUID id : this.alive) {
         TeamManager.UTeam t = this.plugin.teams().of(id);
         if (t != null) {
            set.add(t);
         }
      }

      return set;
   }

   private void checkWin() {
      Set<TeamManager.UTeam> left = this.aliveTeams();
      boolean multi = this.plugin.teams().all().size() > 1;
      if (left.isEmpty()) {
         this.end((TeamManager.UTeam)null);
      } else if (left.size() == 1 && multi) {
         this.end((TeamManager.UTeam)left.iterator().next());
      }

   }

   private void end(TeamManager.UTeam winner) {
      this.state = Game.State.ENDING;
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      if (winner != null) {
         this.plugin.broadcastLines("win", "%team%", winner.display(), "%winners%", String.join(", ", winner.members.values()));
         this.launchWinnerFireworks(winner);
      }

      int delay = this.plugin.getConfig().getInt("game.end-delay", 15);
      this.endTask = Bukkit.getScheduler().runTaskLater(this.plugin, this::resetAll, (long)delay * 20L);
   }

   private void launchWinnerFireworks(TeamManager.UTeam winner) {
      Color color = Color.fromRGB(winner.col.color().value());

      for(Player player : winner.online()) {
         if (this.alive.contains(player.getUniqueId())) {
            for(int i = 0; i < 3; ++i) {
               Location launch = player.getLocation().clone().add((double)0.0F, (double)1.5F + (double)i, (double)0.0F);
               Firework firework = (Firework)player.getWorld().spawn(launch, Firework.class);
               FireworkMeta meta = firework.getFireworkMeta();
               meta.addEffect(FireworkEffect.builder().with(Type.BALL_LARGE).withColor(color).trail(true).build());
               meta.setPower(0);
               firework.setFireworkMeta(meta);
            }
         }
      }

   }

   public void stop(CommandSender sender) {
      if (this.state == Game.State.WAITING) {
         this.plugin.send(sender, "not-running");
      } else {
         this.resetAll();
         this.plugin.send(sender, "stopped");
      }
   }

   public void shutdown() {
      if (this.state != Game.State.WAITING) {
         this.resetAll();
      }

   }

   public void resetAll() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      if (this.endTask != null) {
         this.endTask.cancel();
         this.endTask = null;
      }

      this.cancelAllDisconnectTimeouts();
      this.sidebar.clear();
      this.airdrop.clear();
      if (this.world != null) {
         this.world.getWorldBorder().reset();
      }

      Location spawn = this.spectatorSpawn();

      for(Player p : Bukkit.getOnlinePlayers()) {
         if (this.participants.contains(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR) {
            p.setGlowing(false);
            p.setGameMode(GameMode.SURVIVAL);

            for(PotionEffect e : new ArrayList(p.getActivePotionEffects())) {
               p.removePotionEffect(e.getType());
            }

            p.getInventory().clear();
            setMaxHealth(p, 20.0);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.teleport(spawn);
         }
      }

      this.alive.clear();
      this.participants.clear();
      this.deathLocs.clear();
      this.startLocs.clear();
      this.kills.clear();
      this.pvpOn = false;
      this.regenOff = false;
      this.glowOn = false;
      this.state = Game.State.WAITING;
      TeamManager tm = this.plugin.teams();
      tm.setLocked(false);
      if (this.plugin.getConfig().getBoolean("game.reset-teams-on-end", true)) {
         tm.clearAll();
      }

   }

   public void revive(CommandSender sender, Player target) {
      if (this.state != Game.State.RUNNING) {
         this.plugin.send(sender, "not-running");
      } else {
         UUID id = target.getUniqueId();
         if (this.alive.contains(id)) {
            this.plugin.send(sender, "revive-already-alive");
         } else {
            this.participants.add(id);
            this.alive.add(id);
            this.resetPlayer(target);
            TeamManager.UTeam t = this.plugin.teams().of(target);
            Location dest = null;
            if (t != null) {
               for(Player m : t.online()) {
                  if (!m.equals(target) && this.alive.contains(m.getUniqueId())) {
                     dest = m.getLocation();
                     break;
                  }
               }
            }

            if (dest == null) {
               dest = (Location)this.deathLocs.getOrDefault(id, this.spectatorSpawn());
            }

            target.teleport(dest);
            if (this.glowOn) {
               target.setGlowing(true);
            }

            this.plugin.broadcast("revived", "%player%", target.getName(), "%team%", t == null ? "-" : t.display());
         }
      }
   }

   public static enum State {
      WAITING,
      RUNNING,
      ENDING;

      // $FF: synthetic method
      private static State[] $values() {
         return new State[]{WAITING, RUNNING, ENDING};
      }
   }
}
