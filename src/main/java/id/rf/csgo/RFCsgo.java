
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RF-CSGO berhasil diaktifkan!");
    }

    @Override
    public void onDisable() {
        restorePlayerNames();
    }

    // =====================================
    // COMMANDS
    // =====================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        String c = command.getName().toLowerCase();

        // RANDOM TEAM
        if (c.equals("random")) {
            if (matchActive) {
                msg(sender, "&cPertandingan sedang berlangsung!");
                return true;
            }

            List<Player> players =
                    new ArrayList<>(Bukkit.getOnlinePlayers());

            if (players.size() < 2) {
                msg(sender, "&cMinimal harus ada 2 pemain online!");
                return true;
            }

            restorePlayerNames();
            redTeam.clear();
            blueTeam.clear();

            Collections.shuffle(players);

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

        // DUEL 1 VS 1
        if (c.equals("byone")) {
            if (matchActive) {
                msg(sender, "&cPertandingan sedang berlangsung!");
                return true;
            }

            if (args.length != 2) {
                msg(sender, "&cGunakan: /byone <player1> <player2>");
                return true;
            }

            Player p1 = Bukkit.getPlayerExact(args[0]);
            Player p2 = Bukkit.getPlayerExact(args[1]);

            if (p1 == null || p2 == null || p1.equals(p2)) {
                msg(sender, "&cPemain tidak ditemukan atau nama sama!");
                return true;
            }

            restorePlayerNames();
            redTeam.clear();
            blueTeam.clear();

            if (Math.random() < 0.5) {
                redTeam.add(p1.getUniqueId());
                blueTeam.add(p2.getUniqueId());
                setTeamName(p1, true);
                setTeamName(p2, false);
            } else {
                redTeam.add(p2.getUniqueId());
                blueTeam.add(p1.getUniqueId());
                setTeamName(p2, true);
                setTeamName(p1, false);
            }

            broadcast("&aDuel 1 vs 1 berhasil disiapkan!");
            return true;
        }

        // SET SPAWN
        if (c.equals("setspawn")) {
            if (!(sender instanceof Player)) {
                msg(sender, "&cCommand ini hanya untuk pemain!");
                return true;
            }

            if (args.length != 1) {
                msg(sender, "&cGunakan: /setspawn <merah|biru|duel>");
                return true;
            }

            String team = args[0].toLowerCase();

            if (!team.equals("merah")
                    && !team.equals("biru")
                    && !team.equals("duel")) {
                msg(sender, "&cPilihan: merah, biru, atau duel.");
                return true;
            }

            Player player = (Player) sender;

            getConfig().set("spawns." + team, player.getLocation());
            saveConfig();

            msg(sender, "&aSpawn " + team + " berhasil disimpan!");
            return true;
        }

        // START MATCH
        if (c.equals("startmatch")) {
            if (matchActive) {
                msg(sender, "&cPertandingan sudah berlangsung!");
                return true;
            }

            if (redTeam.isEmpty() || blueTeam.isEmpty()) {
                msg(sender, "&cGunakan /random atau /byone dahulu!");
                return true;
            }

            if (getSpawn("merah") == null
                    || getSpawn("biru") == null) {
                msg(sender, "&cAtur spawn Merah dan Biru dahulu!");
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

        // STOP MATCH
        if (c.equals("stopmatch")) {
            if (!matchActive) {
                msg(sender, "&cTidak ada pertandingan aktif!");
                return true;
            }

            matchActive = false;
            roundEnding = false;

            clearParticipantInventories();
            restorePlayerNames();

            broadcast("&cPertandingan dihentikan!");
            return true;
        }

        // RESET SCORE
        if (c.equals("resetmatch")) {
            redScore = 0;
            blueScore = 0;
            round = 0;

            msg(sender, "&aSkor berhasil direset!");
            return true;
        }

        // SHOW SCORE
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

    // =====================================
    // ROUND SYSTEM
    // =====================================

    private void startRound() {
        if (!matchActive) {
            return;
        }

        round++;
        roundEnding = false;

        broadcast("&eRONDE " + round + " DIMULAI!");

        teleportAliveTeam(redTeam, getSpawn("merah"));
        teleportAliveTeam(blueTeam, getSpawn("biru"));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!matchActive) {
            return;
        }

        if (!isParticipant(event.getEntity())) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(
                this, this::checkRoundEnd, 2L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!matchActive || !isParticipant(event.getPlayer())) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(
                this, this::checkRoundEnd, 2L);
    }

    private void checkRoundEnd() {
        if (!matchActive || roundEnding) {
            return;
        }

        boolean redDead = isTeamEliminated(redTeam);
        boolean blueDead = isTeamEliminated(blueTeam);

        if (!redDead && !blueDead) {
            return;
        }

        roundEnding = true;

        if (redDead && blueDead) {
            broadcast("&eRonde seri! Tidak ada poin.");
        } else if (redDead) {
            blueScore++;
            broadcast("&9TIM BIRU memenangkan ronde!");
        } else {
            redScore++;
            broadcast("&cTIM MERAH memenangkan ronde!");
        }

        broadcast("&6SKOR: &cMerah " + redScore
                + " &7- &9" + blueScore + " Biru");

        if (redScore >= 2 || blueScore >= 2 || round >= 3) {
            Bukkit.getScheduler().runTaskLater(
                    this, this::finishMatch, 40L);
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!matchActive) {
                return;
            }

            broadcast("&eBersiap untuk ronde berikutnya!");
            startRound();

        }, 100L);
    }

    private boolean isTeamEliminated(Set<UUID> team) {
        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null
                    && player.isOnline()
                    && !player.isDead()
                    && player.getHealth() > 0) {
                return false;
            }
        }

        return true;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!matchActive) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();

        if (redTeam.contains(uuid)) {
            Location spawn = getSpawn("merah");

            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        } else if (blueTeam.contains(uuid)) {
            Location spawn = getSpawn("biru");

            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        }
    }

    // =====================================
    // FINISH MATCH
    // =====================================

    private void finishMatch() {
        if (!matchActive) {
            return;
        }

        matchActive = false;
        roundEnding = false;

        if (redScore > blueScore) {
            broadcast("&cTIM MERAH MENANG PERTANDINGAN!");
        } else if (blueScore > redScore) {
            broadcast("&9TIM BIRU MENANG PERTANDINGAN!");
        } else {
            broadcast("&ePERTANDINGAN BERAKHIR SERI!");
        }

        broadcast("&6HASIL AKHIR: &c" + redScore
                + " &7- &9" + blueScore);

        clearParticipantInventories();
        restorePlayerNames();

        redTeam.clear();
        blueTeam.clear();
    }

    private void clearParticipantInventories() {
        Set<UUID> participants = new LinkedHashSet<>();
        participants.addAll(redTeam);
        participants.addAll(blueTeam);

        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null) {
                continue;
            }

            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
        }
    }

    // =====================================
    // TEAM SYSTEM
    // =====================================

    private boolean isParticipant(Player player) {
        UUID uuid = player.getUniqueId();

        return redTeam.contains(uuid)
                || blueTeam.contains(uuid);
    }

    private void teleportAliveTeam(
            Set<UUID> team, Location spawn) {

        if (spawn == null) {
            return;
        }

        for (UUID uuid : team) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null
                    && player.isOnline()
                    && !player.isDead()) {
                player.teleport(spawn);
            }
        }
    }

    private void setTeamName(Player player, boolean red) {
        if (red) {
            player.setDisplayName(
                    color("&c[MERAH] &f" + player.getName()));

            player.setPlayerListName(
                    color("&c[MERAH] &f" + player.getName()));
        } else {
            player.setDisplayName(
                    color("&9[BIRU] &f" + player.getName()));

            player.setPlayerListName(
                    color("&9[BIRU] &f" + player.getName()));
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

    // =====================================
    // SPAWN SYSTEM
    // =====================================

    private Location getSpawn(String name) {
        return getConfig().getLocation("spawns." + name);
    }

    // =====================================
    // MESSAGE SYSTEM
    // =====================================

    private void broadcast(String message) {
        Bukkit.broadcastMessage(
                color("&8[&6RF-CSGO&8] " + message));
    }

    private void msg(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes(
                '&', message);
    }
        }
                
