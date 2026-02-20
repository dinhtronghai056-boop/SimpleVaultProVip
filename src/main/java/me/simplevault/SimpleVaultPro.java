package me.simplevault;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SimpleVaultPro extends JavaPlugin implements Listener {

    private final Map<UUID, Map<Material, Long>> vault = new HashMap<>();
    private final Map<UUID, Set<Material>> autoCollect = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    private final String GUI_TITLE = "§6Kho Cá Nhân";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Auto save mỗi 5 phút
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 6000, 6000);

        getLogger().info("SimpleVaultPro enabled!");
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("SimpleVaultPro disabled!");
    }

    // ================= COMMAND =================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("kho")) {

            if (!p.hasPermission("kho.use")) {
                p.sendMessage("§cBạn không có quyền dùng lệnh này!");
                return true;
            }

            openGUI(p);
        }
        return true;
    }

    // ================= GUI =================
    private void openGUI(Player p) {

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        Map<Material, Long> data =
                vault.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());

        for (Material mat : data.keySet()) {

            long amount = data.get(mat);
            if (amount <= 0) continue;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName("§e" + mat.name());
            meta.setLore(Arrays.asList(
                    "§7Số lượng: §a" + amount,
                    "§fLeft Click: Rút 64",
                    "§fShift Click: Rút tất cả"
            ));

            item.setItemMeta(meta);
            inv.addItem(item);
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        Material mat = item.getType();
        Map<Material, Long> data =
                vault.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());

        long amount = data.getOrDefault(mat, 0L);
        if (amount <= 0) return;

        if (e.isLeftClick() && !e.isShiftClick()) {
            long give = Math.min(64, amount);
            p.getInventory().addItem(new ItemStack(mat, (int) give));
            data.put(mat, amount - give);
        }

        if (e.isShiftClick()) {
            p.getInventory().addItem(new ItemStack(mat, (int) amount));
            data.put(mat, 0L);
        }

        openGUI(p);
    }

    // ================= DOUBLE RIGHT CLICK TO TOGGLE =================
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {

        if (e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) return;

        UUID uuid = p.getUniqueId();
        Material mat = item.getType();

        Set<Material> list =
                autoCollect.computeIfAbsent(uuid, k -> new HashSet<>());

        long now = System.currentTimeMillis();

        if (lastClick.containsKey(uuid)
                && now - lastClick.get(uuid) < 1500
                && list.contains(mat)) {

            list.remove(mat);
            p.sendMessage("§cĐã tắt hút: §e" + mat.name());
            lastClick.remove(uuid);
            return;
        }

        list.add(mat);
        p.sendMessage("§aĐã bật hút: §e" + mat.name());
        lastClick.put(uuid, now);
    }

    // ================= HÚT KHI ĐÀO QUẶNG =================
    @EventHandler
    public void onBreak(BlockBreakEvent e) {

        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        Set<Material> list =
                autoCollect.getOrDefault(p.getUniqueId(), new HashSet<>());

        Collection<ItemStack> drops =
                e.getBlock().getDrops(p.getInventory().getItemInMainHand());

        Map<Material, Long> data =
                vault.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());

        boolean collected = false;

        for (ItemStack drop : drops) {

            if (!list.contains(drop.getType())) continue;

            long current = data.getOrDefault(drop.getType(), 0L);
            data.put(drop.getType(), current + drop.getAmount());
            collected = true;
        }

        if (collected) {
            e.setDropItems(false);
            e.setExpToDrop(0);
        }
    }

    // ================= HÚT KHI NHẶT ITEM =================
    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent e) {

        Player p = e.getPlayer();
        ItemStack item = e.getItem().getItemStack();

        Set<Material> list =
                autoCollect.getOrDefault(p.getUniqueId(), new HashSet<>());

        if (!list.contains(item.getType())) return;

        Map<Material, Long> data =
                vault.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());

        long current = data.getOrDefault(item.getType(), 0L);
        data.put(item.getType(), current + item.getAmount());

        e.setCancelled(true);
        e.getItem().remove();
    }

    // ================= SAVE / LOAD =================
    private void saveData() {

        FileConfiguration config = getConfig();
        config.set("data", null);
        config.set("auto", null);

        for (UUID uuid : vault.keySet()) {
            for (Material mat : vault.get(uuid).keySet()) {
                config.set("data." + uuid + "." + mat.name(),
                        vault.get(uuid).get(mat));
            }
        }

        for (UUID uuid : autoCollect.keySet()) {
            for (Material mat : autoCollect.get(uuid)) {
                config.set("auto." + uuid + "." + mat.name(), true);
            }
        }

        saveConfig();
    }

    private void loadData() {

        if (getConfig().contains("data")) {

            for (String uuidStr :
                    getConfig().getConfigurationSection("data").getKeys(false)) {

                UUID uuid = UUID.fromString(uuidStr);
                Map<Material, Long> data = new HashMap<>();

                for (String mat :
                        getConfig().getConfigurationSection("data." + uuidStr).getKeys(false)) {

                    data.put(Material.valueOf(mat),
                            getConfig().getLong("data." + uuidStr + "." + mat));
                }

                vault.put(uuid, data);
            }
        }

        if (getConfig().contains("auto")) {

            for (String uuidStr :
                    getConfig().getConfigurationSection("auto").getKeys(false)) {

                UUID uuid = UUID.fromString(uuidStr);
                Set<Material> list = new HashSet<>();

                for (String mat :
                        getConfig().getConfigurationSection("auto." + uuidStr).getKeys(false)) {

                    list.add(Material.valueOf(mat));
                }

                autoCollect.put(uuid, list);
            }
        }
    }
}
