package me.simplevault;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SimpleVaultPro extends JavaPlugin implements Listener {

    private final Set<UUID> enabledPlayers = new HashSet<>();
    private final Map<UUID, Set<Material>> playerVault = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SimpleVaultPro Enabled!");
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("SimpleVaultPro Disabled!");
    }

    // ===== COMMAND =====

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            if (enabledPlayers.contains(uuid)) {
                enabledPlayers.remove(uuid);
                player.sendMessage(ChatColor.RED + "Đã tắt chế độ hút vật phẩm!");
            } else {
                enabledPlayers.add(uuid);
                player.sendMessage(ChatColor.GREEN + "Đã bật chế độ hút vật phẩm!");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {

            ItemStack item = player.getInventory().getItemInMainHand();

            if (item.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Cầm vật phẩm trên tay để thêm vào kho!");
                return true;
            }

            playerVault.putIfAbsent(uuid, new HashSet<>());
            playerVault.get(uuid).add(item.getType());

            player.sendMessage(ChatColor.YELLOW + "Đã thêm " + item.getType().name() + " vào danh sách hút!");
            saveData();
            return true;
        }

        return true;
    }

    // ===== HÚT KHI ĐÀO BLOCK =====

    @EventHandler
    public void onBlockDrop(BlockDropItemEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!enabledPlayers.contains(uuid)) return;
        if (!playerVault.containsKey(uuid)) return;

        for (Item item : event.getItems()) {
            Material type = item.getItemStack().getType();
            if (playerVault.get(uuid).contains(type)) {
                player.getInventory().addItem(item.getItemStack());
                item.remove();
            }
        }
    }

    // ===== HÚT KHI NHẶT ITEM =====

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {

        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!enabledPlayers.contains(uuid)) return;
        if (!playerVault.containsKey(uuid)) return;

        Material type = event.getItem().getItemStack().getType();

        if (playerVault.get(uuid).contains(type)) {
            player.getInventory().addItem(event.getItem().getItemStack());
            event.getItem().remove();
            event.setCancelled(true);
        }
    }

    // ===== LƯU DATA =====

    private void saveData() {
        FileConfiguration config = getConfig();

        config.set("enabled", null);
        config.set("vault", null);

        List<String> enabledList = new ArrayList<>();
        for (UUID uuid : enabledPlayers) {
            enabledList.add(uuid.toString());
        }
        config.set("enabled", enabledList);

        for (UUID uuid : playerVault.keySet()) {
            List<String> materials = new ArrayList<>();
            for (Material m : playerVault.get(uuid)) {
                materials.add(m.name());
            }
            config.set("vault." + uuid.toString(), materials);
        }

        saveConfig();
    }

    private void loadData() {
        FileConfiguration config = getConfig();

        if (config.contains("enabled")) {
            for (String uuid : config.getStringList("enabled")) {
                enabledPlayers.add(UUID.fromString(uuid));
            }
        }

        if (config.contains("vault")) {
            for (String uuid : config.getConfigurationSection("vault").getKeys(false)) {
                UUID id = UUID.fromString(uuid);
                Set<Material> materials = new HashSet<>();
                for (String mat : config.getStringList("vault." + uuid)) {
                    materials.add(Material.valueOf(mat));
                }
                playerVault.put(id, materials);
            }
        }
    }
}
