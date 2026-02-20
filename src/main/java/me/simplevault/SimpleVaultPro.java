package me.simplevault;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SimpleVaultPro extends JavaPlugin implements Listener {

    private Map<UUID, Map<Material, Long>> vault = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ===== COMMAND =====
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("kho")) {
            openGUI(p);
        }

        return true;
    }

    // ===== GUI =====
    private void openGUI(Player p) {

        Inventory inv = Bukkit.createInventory(null, 54, "Kho Ca Nhan");

        Map<Material, Long> data = vault.getOrDefault(p.getUniqueId(), new HashMap<>());

        for (Material mat : data.keySet()) {

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName("§e" + mat.name());
            meta.setLore(Arrays.asList("§7So luong: §a" + data.get(mat)));
            item.setItemMeta(meta);

            inv.addItem(item);
        }

        p.openInventory(inv);
    }

    // ===== CLICK GUI =====
    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!e.getView().getTitle().equals("Kho Ca Nhan")) return;

        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack item = e.getCurrentItem();
        if (item == null) return;

        Material mat = item.getType();
        Map<Material, Long> data = vault.getOrDefault(p.getUniqueId(), new HashMap<>());

        long amount = data.getOrDefault(mat, 0L);

        if (e.isLeftClick()) {
            long give = Math.min(64, amount);
            p.getInventory().addItem(new ItemStack(mat, (int) give));
            data.put(mat, amount - give);
        }

        if (e.isShiftClick()) {
            p.getInventory().addItem(new ItemStack(mat, (int) amount));
            data.put(mat, 0L);
        }

        vault.put(p.getUniqueId(), data);
        openGUI(p);
    }

    // ===== CLICK PHẢI CẤT =====
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {

        if (e.getAction().toString().contains("RIGHT_CLICK")) {

            Player p = e.getPlayer();
            ItemStack item = p.getInventory().getItemInMainHand();

            if (item == null) return;
            if (item.getType().name().contains("SHULKER_BOX")) return;

            Material mat = item.getType();

            if (!isOre(mat)) return;

            Map<Material, Long> data = vault.getOrDefault(p.getUniqueId(), new HashMap<>());

            long current = data.getOrDefault(mat, 0L);
            data.put(mat, current + item.getAmount());

            p.getInventory().setItemInMainHand(null);
            vault.put(p.getUniqueId(), data);
        }
    }

    // ===== HÚT QUẶNG =====
    @EventHandler
    public void onBreak(BlockBreakEvent e) {

        Player p = e.getPlayer();
        Material mat = e.getBlock().getType();

        if (!isOre(mat)) return;
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        Map<Material, Long> data = vault.getOrDefault(p.getUniqueId(), new HashMap<>());
        long current = data.getOrDefault(mat, 0L);

        int amount = 1;

        if (p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.LOOT_BONUS_BLOCKS)) {
            int lvl = p.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
            amount += lvl;
        }

        data.put(mat, current + amount);
        vault.put(p.getUniqueId(), data);

        e.setDropItems(false);
    }

    // ===== CHỈ KHOÁNG SẢN =====
    private boolean isOre(Material m) {
        return m.name().contains("ORE") || m == Material.DIAMOND || m == Material.EMERALD;
    }

    // ===== SAVE =====
    private void saveData() {

        FileConfiguration config = getConfig();

        for (UUID uuid : vault.keySet()) {
            for (Material mat : vault.get(uuid).keySet()) {
                config.set("data." + uuid + "." + mat.name(), vault.get(uuid).get(mat));
            }
        }

        saveConfig();
    }

    private void loadData() {

        if (!getConfig().contains("data")) return;

        for (String uuidStr : getConfig().getConfigurationSection("data").getKeys(false)) {

            UUID uuid = UUID.fromString(uuidStr);
            Map<Material, Long> data = new HashMap<>();

            for (String mat : getConfig().getConfigurationSection("data." + uuidStr).getKeys(false)) {
                data.put(Material.valueOf(mat),
                        getConfig().getLong("data." + uuidStr + "." + mat));
            }

            vault.put(uuid, data);
        }
    }
}
