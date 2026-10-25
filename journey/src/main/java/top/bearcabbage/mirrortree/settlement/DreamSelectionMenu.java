package top.bearcabbage.mirrortree.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import top.bearcabbage.mirrortree.dream.MTDream;

/** Vanilla chest protocol: buttons are server-owned; no inventory or cursor actions are accepted. */
public final class DreamSelectionMenu extends ChestMenu {
    public static final int RANDOM_SLOT = 11, SETTLEMENTS_SLOT = 15;
    private static final int PER_PAGE = 18;
    private final ServerPlayer owner;
    private final BlockPos bed;
    private final boolean settlementPage;
    private final int page;
    private final List<SettlementWarps.Settlement> settlements;

    private DreamSelectionMenu(int id, Inventory inventory, ServerPlayer player, BlockPos bed,
            boolean settlementPage, int page, List<SettlementWarps.Settlement> settlements) {
        super(MenuType.GENERIC_9x3, id, inventory, new SimpleContainer(27), 3);
        this.owner = player;
        this.bed = bed.immutable();
        this.settlementPage = settlementPage;
        this.page = Math.clamp(page, 0, Math.max(0, (settlements.size() - 1) / PER_PAGE));
        this.settlements = settlements;
        if (!settlementPage) {
            icon(4, Items.BELL, "选择你的入梦方式", "首次入梦后，将以所选位置作为你的起点。");
            icon(RANDOM_SLOT, Items.RED_STAINED_GLASS, "随机入梦", "在主世界半径 3000 内寻找安全落点。");
            icon(SETTLEMENTS_SLOT, Items.BLUE_STAINED_GLASS, "加入聚落", "查看主世界中可以加入的聚落。");
            icon(22, Items.BARRIER, "取消", "留在卧室，稍后再选。");
        } else {
            int start = this.page * PER_PAGE;
            for (int i = 0; i < PER_PAGE && start + i < settlements.size(); i++) {
                var settlement = settlements.get(start + i);
                var pos = settlement.blockPos();
                icon(i, Items.LIGHT_BLUE_BANNER, settlement.displayName(),
                        "主世界 · " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), "点击加入这个聚落。");
            }
            if (settlements.isEmpty()) icon(4, Items.BARRIER, "暂无可用聚落", "仍可选择随机入梦。");
            if (this.page > 0) icon(18, Items.ARROW, "上一页");
            icon(22, Items.COAL, "返回", "返回入梦方式选择。");
            icon(24, Items.RED_STAINED_GLASS, "随机入梦");
            if ((this.page + 1) * PER_PAGE < settlements.size()) icon(26, Items.ARROW, "下一页");
        }
    }

    public static void open(ServerPlayer player, BlockPos bed) { open(player, bed, false, 0); }

    private static void open(ServerPlayer player, BlockPos bed, boolean settlements, int page) {
        List<SettlementWarps.Settlement> points = SettlementWarps.accessible(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, user) ->
                new DreamSelectionMenu(id, inventory, player, bed, settlements, page, points),
                Component.literal(settlements ? "请选择你要加入的聚落" : "选择你的入梦方式")));
    }

    private void icon(int slot, Item item, String label, String... description) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
        stack.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(description)
                .map(line -> (Component) Component.literal(line)).toList()));
        getContainer().setItem(slot, stack);
    }

    @Override public boolean stillValid(Player player) { return player == owner && MTDream.canChoose(owner, bed); }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (player != owner || owner.containerMenu != this) return;
        if (!stillValid(owner)) { owner.closeContainer(); return; }
        if (input == ContainerInput.PICKUP && (button == 0 || button == 1)) {
            if (!settlementPage) {
                if (slot == RANDOM_SLOT) select(null);
                else if (slot == SETTLEMENTS_SLOT) open(owner, bed, true, 0);
                else if (slot == 22) owner.closeContainer();
            } else if (slot >= 0 && slot < PER_PAGE && page * PER_PAGE + slot < settlements.size()) {
                select(settlements.get(page * PER_PAGE + slot).name());
            } else if (slot == 22) open(owner, bed);
            else if (slot == 24) select(null);
            else if (slot == 18 && page > 0) open(owner, bed, true, page - 1);
            else if (slot == 26 && (page + 1) * PER_PAGE < settlements.size()) open(owner, bed, true, page + 1);
        }
        // Undo client prediction for shift-click, drag, number keys, double-click and fake button items.
        if (owner.containerMenu == this) broadcastFullState();
    }

    private void select(String name) {
        owner.closeContainer();
        MTDream.select(owner, bed, name);
    }
}
