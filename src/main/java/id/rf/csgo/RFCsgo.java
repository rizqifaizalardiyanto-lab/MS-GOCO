
package id.rf.csgo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class RFCsgo extends JavaPlugin implements Listener {

    private final Set<UUID> redTeam = new LinkedHashSet<>();
    private final Set<UUID> blueTeam = new LinkedHashSet<>();

    private int redScore = 0;
    private int blueScore = 0;
    private int round = 0;

    private boolean matchActive = false;
    private boolean roundEnding = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RF-CSGO berhasil diaktifkan!");
    }

    @Override
    public void onDisable() {
        restorePlayerNames();
    }

    // =========================
    // COMMANDS
    // =========================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        String c = command.getName().toLowerCase();

        if (c.equals("random")) {
            if (matchActive) {
                sender.sendMessage(color("&cPertandingan sedang berlangsung!"));
                return true;
            }

            List<Player> players =
                    new ArrayList<>(Bukkit.getOnlinePlayers());

            if (players.size() < 2) {
                sender.sendMessage(color("&cMinimal 2 pemain online!"));
                return true;
            }

            Collections.shuffle(players);
            redTeam.clear();
            blueTeam.clear();

            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);

                if (i % 2 == 0) {
                    redTeam.add(p.getUniqueId());
                    setTeamName(p, true);
                } else {
                    blueTeam.add(p.getUniqueId());
                    setTeamName(p, false);
                }
            }

            broadcast("&aPemain berhasil dibagi secara acak!");
            broadcast("&cTim Merah: &f" + redTeam.size()
                    + " &7| &9Tim Biru: &f" + blueTeam.size());
            return true;
        }

        if (c.equals("byone")) {
            if (matchActive) {
                sender.sendMessage(color("&cPertandingan sedang berlangsung!"));
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(color("&cGunakan: /byone <player1> <player2>"));
                return true;
            }

            Player p1 = Bukkit.getPlayerExact(args[0]);
            Player p2 = Bukkit.getPlayerExact(args[1]);

            if (p1 == null || p2 == null || p1.equals(p2)) {
                sender.sendMessage(color("&cNama pemain tidak valid!"));
                return true;
            }

            redTeam.clear();
            blueTeam.clear();

            redTeam.add(p1.getUniqueId());
            blueTeam.add(p2.getUniqueId());

            if (Math.random() < 0.5) {
                setTeamName(p1, true);
                setTeamName(p2, false);
            } else {
                redTeam.clear();
                blueTeam.clear();

                redTeam.add(p2.getUniqueId());
                blueTeam.add(p1.getUniqueId());

                setTeamName(p2, true);
                setTeamName(p1, false);
            }

            broadcast("&aDuel 1 vs 1 berhasil disiapkan!");
            return true;
        }

        if (c.equals("setspawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Command ini hanya bisa digunakan pemain.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(color("&cGunakan: /setspawn <merah|biru|duel>"));
                return true;
            }

            String team = args[0].toLowerCase();

            if (!team.equals("merah")
                    && !team.equals("biru")
                    && !team.equals("duel")) {
                sender.sendMessage(color("&cPilihan: merah, biru, atau duel."));
                return true;
            }

            saveSpawn(team, player.getLocation());

            sender.sendMessage(color("&aSpawn " + team + " berhasil disimpan!"));
            return true;
        }

        if (c.equals("startmatch")) {
            if (matchActive) {
                sender.sendMessage(color("&cPertandingan sudah berlangsung!"));
                return true;
            }

            if (redTeam.isEmpty() || blueTeam.isEmpty()) {
                sender.sendMessage(color("&cBagi pemain dahulu dengan /random atau /byone."));
                return true;
            }

            if (getSpawn("merah") == null || getSpawn("biru") == null) {
                sender.sendMessage(color("&cAtur spawn Merah dan Biru dahulu!"));
                return true;
            }

            redScore = 0;
            blueScore = 0;
            round = 0;
            matchActive = true;

            broadcast("&6Pertandingan RF-CSGO dimulai!");
            startRound();
            return true;
        }

        if (c.equals("stopmatch")) {
            if (!matchActive) {
                sender.sendMessage(color("&cTidak ada pertandingan aktif."));
                return true;
            }

            matchActive = false;
            roundEnding = false;

            clearParticipantInventories();
            broadcast("&cPertandingan dihentikan!");
            return true;
        }

        if (c.equals("resetmatch")) {
            redScore = 0;
            blueScore = 0;
            round = 0;

            sender.sendMessage(color("&aSkor pertandingan berhasil direset!"));
            return true;
        }

        if (c.equals("score")) {
            msg(sender, "&6===== SKOR PERTANDINGAN =====");
            msg(sender, "&c🔴 TIM MERAH: &f" + redScore);
            msg(sender, "&9🔵 TIM BIRU: &f" + blueScore);
            msg(sender, "&eRONDE KE: &f" + round);
            msg(sender, "&6==============================");
            return true;
        }

        return false;
    }

    // =========================
    // ROUND SYSTEM
    // =========================

    private void startRound() {
        if (!matchActive) return;

        round++;
        roundEnding = false;

        broadcast("&eRONDE " + round + " DIMULAI!");

        teleportTeam(redTeam, getSpawn("merah"));
        teleportTeam(blueTeam, getSpawn("biru"));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!matchActive) return;

        Player player = event.getEntity();

        if (!isParticipant(player)) return;

        Bukkit.getScheduler().runTaskLater(this, this::checkRoundEnd, 1L);
    }

    private void checkRoundEnd() {
        if (!matchActive || roundEnding) return;

        boolean redDead = isTeamEliminated(redTeam);
        boolean blueDead = isTeamEliminated(blueTeam);

        if (!redDead && !blueDead) return;

        roundEnding = true;

        if (redDead && blueDead) {
            broadcast("&eRonde seri! Tidak ada tim yang mendapat poin.");
        } else if (redDead) {
            blueScore++;
            broadcast("&9TIM BIRU memenangkan ronde!");
        } else {
            redScore++;
            broadcast("&cTIM MERAH memenangkan ronde!");
        }

        broadcast("&6Skor: &cMerah " + redScore
                + " &7- &9" + blueScore + " Biru");

        if (redScore >= 2 || blueScore >= 2) {
            Bukkit.getScheduler().runTaskLater(this, this::finishMatch, 40L);
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!matchActive) return;

            respawnTeams();
            startRound();
        }, 60L);
    }

    private boolean isTeamEliminated(Set<UUID> team) {
        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null
                    && !player.isDead()
                    && player.getHealth() > 0) {
                return false;
            }
        }

        return true;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!matchActive) return;

        Player player = event.getPlayer();

        if (redTeam.contains(player.getUniqueId())) {
            Location spawn = getSpawn("merah");
            if (spawn != null) event.setRespawnLocation(spawn);
        } else if (blueTeam.contains(player.getUniqueId())) {
            Location spawn = getSpawn("biru");
            if (spawn != null) event.setRespawnLocation(spawn);
        }
    }

    private void respawnTeams() {
        respawnTeam(redTeam, "merah");
        respawnTeam(blueTeam, "biru");
    }

    private void respawnTeam(Set<UUID> team, String spawnName) {
        Location spawn = getSpawn(spawnName);
        if (spawn == null) return;

        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            player.spigot().respawn();
            player.teleport(spawn);
            player.setFireTicks(0);
            player.setFoodLevel(20);
            player.setSaturation(20);

            if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                double maxHealth =
                        player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(maxHealth);
            }
        }
    }

    // =========================
    // FINISH MATCH
    // =========================

    private void finishMatch() {
        if (!matchActive) return;

        matchActive = false;
        roundEnding = false;

        if (redScore > blueScore) {
            broadcast("&cTIM MERAH MENANG PERTANDINGAN!");
        } else {
            broadcast("&9TIM BIRU MENANG PERTANDINGAN!");
        }

        broadcast("&6HASIL AKHIR: &c" + redScore
                + " &7- &9" + blueScore);

        clearParticipantInventories();
        redTeam.clear();
        blueTeam.clear();
    }

    private void clearParticipantInventories() {
        Set<UUID> participants = new LinkedHashSet<>();
        participants.addAll(redTeam);
        participants.addAll(blueTeam);

        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
    }

    // =========================
    // TEAM SYSTEM
    // =========================

    private boolean isParticipant(Player player) {
        UUID uuid = player.getUniqueId();
        return redTeam.contains(uuid) || blueTeam.contains(uuid);
    }

    private void teleportTeam(Set<UUID> team, Location spawn) {
        if (spawn == null) return;

        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(spawn);
            }
        }
    }

    private void setTeamName(Player player, boolean red) {
        if (red) {
            player.setDisplayName(color("&c[MERAH] &f" + player.getName()));
            player.setPlayerListName(color("&c[MERAH] &f" + player.getName()));
        } else {
            player.setDisplayName(color("&9[BIRU] &f" + player.getName()));
            player.setPlayerListName(color("&9[BIRU] &f" + player.getName()));
        }
    }

    private void restorePlayerNames() {
        for (UUID uuid : redTeam) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setDisplayName(player.getName());
                player.setPlayerListName(player.getName());
            }
        }

        for (UUID uuid : blueTeam) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setDisplayName(player.getName());
                player.setPlayerListName(player.getName());
            }
        }
    }

    // =========================
    // SPAWN CONFIGURATION
    // =========================

    private void saveSpawn(String name, Location location) {
        String path = "spawns." + name;

        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());

        saveConfig();
    }

    private Location getSpawn(String name) {
        String path = "spawns." + name;

        if (!getConfig().contains(path + ".world")) {
            return null;
        }

        String worldName = getConfig().getString(path + ".world");
        org.bukkit.World world = Bukkit.getWorld(worldName);

        if (world == null) return null;

        double x = getConfig().getDouble(path + ".x");
        double y = getConfig().getDouble(path + ".y");
        double z = getConfig().getDouble(path + ".z");
        float yaw = (float) getConfig().getDouble(path + ".yaw");
        float pitch = (float) getConfig().getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    // =========================
    // MESSAGE HELPERS
    // =========================

    private void broadcast(String message) {
        Bukkit.broadcastMessage(color("&8[&6RF-CSGO&8] " + message));
    }

    private void msg(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
          }
                                               
package id.rf.csgo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class RFCsgo extends JavaPlugin implements Listener {

    private final Set<UUID> redTeam = new LinkedHashSet<>();
    private final Set<UUID> blueTeam = new LinkedHashSet<>();

    private int redScore = 0;
    private int blueScore = 0;
    private int round = 0;

    private boolean matchActive = false;
    private boolean roundEnding = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RF-CSGO berhasil diaktifkan!");
    }

    @Override
    public void onDisable() {
        restorePlayerNames();
    }

    // =========================
    // COMMANDS
    // =========================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        String c = command.getName().toLowerCase();

        if (c.equals("random")) {
            if (matchActive) {
                sender.sendMessage(color("&cPertandingan sedang berlangsung!"));
                return true;
            }

            List<Player> players =
                    new ArrayList<>(Bukkit.getOnlinePlayers());

            if (players.size() < 2) {
                sender.sendMessage(color("&cMinimal 2 pemain online!"));
                return true;
            }

            Collections.shuffle(players);
            redTeam.clear();
            blueTeam.clear();

            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);

                if (i % 2 == 0) {
                    redTeam.add(p.getUniqueId());
                    setTeamName(p, true);
                } else {
                    blueTeam.add(p.getUniqueId());
                    setTeamName(p, false);
                }
            }

            broadcast("&aPemain berhasil dibagi secara acak!");
            broadcast("&cTim Merah: &f" + redTeam.size()
                    + " &7| &9Tim Biru: &f" + blueTeam.size());
            return true;
        }

        if (c.equals("byone")) {
            if (matchActive) {
                sender.sendMessage(color("&cPertandingan sedang berlangsung!"));
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(color("&cGunakan: /byone <player1> <player2>"));
                return true;
            }

            Player p1 = Bukkit.getPlayerExact(args[0]);
            Player p2 = Bukkit.getPlayerExact(args[1]);

            if (p1 == null || p2 == null || p1.equals(p2)) {
                sender.sendMessage(color("&cNama pemain tidak valid!"));
                return true;
            }

            redTeam.clear();
            blueTeam.clear();

            redTeam.add(p1.getUniqueId());
            blueTeam.add(p2.getUniqueId());

            if (Math.random() < 0.5) {
                setTeamName(p1, true);
                setTeamName(p2, false);
            } else {
                redTeam.clear();
                blueTeam.clear();

                redTeam.add(p2.getUniqueId());
                blueTeam.add(p1.getUniqueId());

                setTeamName(p2, true);
                setTeamName(p1, false);
            }

            broadcast("&aDuel 1 vs 1 berhasil disiapkan!");
            return true;
        }

        if (c.equals("setspawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Command ini hanya bisa digunakan pemain.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(color("&cGunakan: /setspawn <merah|biru|duel>"));
                return true;
            }

            String team = args[0].toLowerCase();

            if (!team.equals("merah")
                    && !team.equals("biru")
                    && !team.equals("duel")) {
                sender.sendMessage(color("&cPilihan: merah, biru, atau duel."));
                return true;
            }

            saveSpawn(team, player.getLocation());

            sender.sendMessage(color("&aSpawn " + team + " berhasil disimpan!"));
            return true;
        }

        if (c.equals("startmatch")) {
            if (matchActive) {
                sender.sendMessage(color("&cPertandingan sudah berlangsung!"));
                return true;
            }

            if (redTeam.isEmpty() || blueTeam.isEmpty()) {
                sender.sendMessage(color("&cBagi pemain dahulu dengan /random atau /byone."));
                return true;
            }

            if (getSpawn("merah") == null || getSpawn("biru") == null) {
                sender.sendMessage(color("&cAtur spawn Merah dan Biru dahulu!"));
                return true;
            }

            redScore = 0;
            blueScore = 0;
            round = 0;
            matchActive = true;

            broadcast("&6Pertandingan RF-CSGO dimulai!");
            startRound();
            return true;
        }

        if (c.equals("stopmatch")) {
            if (!matchActive) {
                sender.sendMessage(color("&cTidak ada pertandingan aktif."));
                return true;
            }

            matchActive = false;
            roundEnding = false;

            clearParticipantInventories();
            broadcast("&cPertandingan dihentikan!");
            return true;
        }

        if (c.equals("resetmatch")) {
            redScore = 0;
            blueScore = 0;
            round = 0;

            sender.sendMessage(color("&aSkor pertandingan berhasil direset!"));
            return true;
        }

        if (c.equals("score")) {
            msg(sender, "&6===== SKOR PERTANDINGAN =====");
            msg(sender, "&c🔴 TIM MERAH: &f" + redScore);
            msg(sender, "&9🔵 TIM BIRU: &f" + blueScore);
            msg(sender, "&eRONDE KE: &f" + round);
            msg(sender, "&6==============================");
            return true;
        }

        return false;
    }

    // =========================
    // ROUND SYSTEM
    // =========================

    private void startRound() {
        if (!matchActive) return;

        round++;
        roundEnding = false;

        broadcast("&eRONDE " + round + " DIMULAI!");

        teleportTeam(redTeam, getSpawn("merah"));
        teleportTeam(blueTeam, getSpawn("biru"));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!matchActive) return;

        Player player = event.getEntity();

        if (!isParticipant(player)) return;

        Bukkit.getScheduler().runTaskLater(this, this::checkRoundEnd, 1L);
    }

    private void checkRoundEnd() {
        if (!matchActive || roundEnding) return;

        boolean redDead = isTeamEliminated(redTeam);
        boolean blueDead = isTeamEliminated(blueTeam);

        if (!redDead && !blueDead) return;

        roundEnding = true;

        if (redDead && blueDead) {
            broadcast("&eRonde seri! Tidak ada tim yang mendapat poin.");
        } else if (redDead) {
            blueScore++;
            broadcast("&9TIM BIRU memenangkan ronde!");
        } else {
            redScore++;
            broadcast("&cTIM MERAH memenangkan ronde!");
        }

        broadcast("&6Skor: &cMerah " + redScore
                + " &7- &9" + blueScore + " Biru");

        if (redScore >= 2 || blueScore >= 2) {
            Bukkit.getScheduler().runTaskLater(this, this::finishMatch, 40L);
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!matchActive) return;

            respawnTeams();
            startRound();
        }, 60L);
    }

    private boolean isTeamEliminated(Set<UUID> team) {
        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null
                    && !player.isDead()
                    && player.getHealth() > 0) {
                return false;
            }
        }

        return true;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!matchActive) return;

        Player player = event.getPlayer();

        if (redTeam.contains(player.getUniqueId())) {
            Location spawn = getSpawn("merah");
            if (spawn != null) event.setRespawnLocation(spawn);
        } else if (blueTeam.contains(player.getUniqueId())) {
            Location spawn = getSpawn("biru");
            if (spawn != null) event.setRespawnLocation(spawn);
        }
    }

    private void respawnTeams() {
        respawnTeam(redTeam, "merah");
        respawnTeam(blueTeam, "biru");
    }

    private void respawnTeam(Set<UUID> team, String spawnName) {
        Location spawn = getSpawn(spawnName);
        if (spawn == null) return;

        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            player.spigot().respawn();
            player.teleport(spawn);
            player.setFireTicks(0);
            player.setFoodLevel(20);
            player.setSaturation(20);

            if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                double maxHealth =
                        player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(maxHealth);
            }
        }
    }

    // =========================
    // FINISH MATCH
    // =========================

    private void finishMatch() {
        if (!matchActive) return;

        matchActive = false;
        roundEnding = false;

        if (redScore > blueScore) {
            broadcast("&cTIM MERAH MENANG PERTANDINGAN!");
        } else {
            broadcast("&9TIM BIRU MENANG PERTANDINGAN!");
        }

        broadcast("&6HASIL AKHIR: &c" + redScore
                + " &7- &9" + blueScore);

        clearParticipantInventories();
        redTeam.clear();
        blueTeam.clear();
    }

    private void clearParticipantInventories() {
        Set<UUID> participants = new LinkedHashSet<>();
        participants.addAll(redTeam);
        participants.addAll(blueTeam);

        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
    }

    // =========================
    // TEAM SYSTEM
    // =========================

    private boolean isParticipant(Player player) {
        UUID uuid = player.getUniqueId();
        return redTeam.contains(uuid) || blueTeam.contains(uuid);
    }

    private void teleportTeam(Set<UUID> team, Location spawn) {
        if (spawn == null) return;

        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(spawn);
            }
        }
    }

    private void setTeamName(Player player, boolean red) {
        if (red) {
            player.setDisplayName(color("&c[MERAH] &f" + player.getName()));
            player.setPlayerListName(color("&c[MERAH] &f" + player.getName()));
        } else {
            player.setDisplayName(color("&9[BIRU] &f" + player.getName()));
            player.setPlayerListName(color("&9[BIRU] &f" + player.getName()));
        }
    }

    private void restorePlayerNames() {
        for (UUID uuid : redTeam) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setDisplayName(player.getName());
                player.setPlayerListName(player.getName());
            }
        }

        for (UUID uuid : blueTeam) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setDisplayName(player.getName());
                player.setPlayerListName(player.getName());
            }
        }
    }

    // =========================
    // SPAWN CONFIGURATION
    // =========================

    private void saveSpawn(String name, Location location) {
        String path = "spawns." + name;

        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());

        saveConfig();
    }

    private Location getSpawn(String name) {
        String path = "spawns." + name;

        if (!getConfig().contains(path + ".world")) {
            return null;
        }

        String worldName = getConfig().getString(path + ".world");
        org.bukkit.World world = Bukkit.getWorld(worldName);

        if (world == null) return null;

        double x = getConfig().getDouble(path + ".x");
        double y = getConfig().getDouble(path + ".y");
        double z = getConfig().getDouble(path + ".z");
        float yaw = (float) getConfig().getDouble(path + ".yaw");
        float pitch = (float) getConfig().getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    // =========================
    // MESSAGE HELPERS
    // =========================

    private void broadcast(String message) {
        Bukkit.broadcastMessage(color("&8[&6RF-CSGO&8] " + message));
    }

    private void msg(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}

