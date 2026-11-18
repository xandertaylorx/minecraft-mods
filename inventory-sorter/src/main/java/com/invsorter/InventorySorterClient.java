package com.invsorter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side inventory sorter. Press the sort key (default R) while a chest,
 * barrel, ender chest, shulker box or your inventory is open.
 *
 *   R          sorts the chest/barrel (or your inventory if no chest is open)
 *   Shift + R  sorts your own inventory (the 27 main slots, not the hotbar)
 *
 * Everything is done with ordinary slot clicks, so it works on any server.
 */
public class InventorySorterClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("invsorter");

    /** Slot clicks sent per game tick. Low enough to avoid spam kicks on servers. */
    private static final int CLICKS_PER_TICK = 3;

    private record Click(int slotId, boolean quick) {
        Click(int slotId) { this(slotId, false); }
    }

    private static KeyMapping sortKey;
    private static final ArrayDeque<Click> queue = new ArrayDeque<>();
    private static int queuedContainerId = -1;

    @Override
    public void onInitializeClient() {
        sortKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.invsorter.sort", InputConstants.Type.KEYBOARD, 82 /* R */, KeyMapping.Category.INVENTORY));

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                addButtons(client, containerScreen);
                ScreenEvents.remove(screen).register(s -> {
                    queue.clear(); // screen closed: stop sorting
                    if (isStackMenu(containerScreen.getMenu())) {
                        rememberChest(client, containerScreen.getMenu());   // contents as they are when you walk away
                        if (phase == Phase.IDLE) openedChest = null;
                    }
                });
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyEvent) -> {
                    if (sortKey.matches(keyEvent)) {
                        LOGGER.info("Sort key pressed in {}", containerScreen.getMenu().getClass().getSimpleName());
                        boolean shift = (keyEvent.modifiers() & 1 /* shift */) != 0;
                        startSort(client, containerScreen.getMenu(), shift);
                    }
                });
            }
        });

        // Remember which chest/barrel the player opens so quick stack knows what's inside.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide()) openedChest = normalizeChest(world, hit.getBlockPos());
            return net.minecraft.world.InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> chestMemory.clear());

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientTickEvents.END_CLIENT_TICK.register(InventorySorterClient::stackTick);
        LOGGER.info("Inventory Sorter loaded (client-side).");
    }

    // ---- GUI buttons -------------------------------------------------------------

    private static final int BTN_W = SortIconButton.SIZE;

    private static final java.lang.reflect.Field LEFT = field("leftPos");
    private static final java.lang.reflect.Field TOP = field("topPos");
    private static final java.lang.reflect.Field IMG_W = field("imageWidth");
    private static final java.lang.reflect.Field IMG_H = field("imageHeight");

    private static java.lang.reflect.Field field(String name) {
        try {
            var f = AbstractContainerScreen.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Could not access AbstractContainerScreen.{}", name, e);
            return null;
        }
    }

    private static int get(java.lang.reflect.Field f, Object o) {
        try {
            return f.getInt(o);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Adds "Sort" buttons: one for the chest (if any) and one for your inventory. */
    private void addButtons(Minecraft client, AbstractContainerScreen<?> screen) {
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative) {
            addCreativeButtons(client, creative);
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        boolean hasContainer = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
        if (!hasContainer && !(menu instanceof InventoryMenu)) return;
        if (LEFT == null || TOP == null || IMG_W == null || IMG_H == null) return;

        SortIconButton invButton = new SortIconButton(SortIconButton.Icon.SORT,
                Component.translatable("invsorter.sort_inventory"), () -> startSort(client, menu, true));
        SortIconButton chestButton = null;
        if (hasContainer) {
            chestButton = new SortIconButton(SortIconButton.Icon.SORT,
                    Component.translatable("invsorter.sort_container"), () -> startSort(client, menu, false));
            Screens.getWidgets(screen).add(chestButton);
        }
        Screens.getWidgets(screen).add(invButton);

        SortIconButton stackButton = null;
        if (screen instanceof InventoryScreen) {
            stackButton = new SortIconButton(SortIconButton.Icon.QUICK_STACK,
                    Component.translatable("invsorter.quick_stack"), () -> startQuickStack(client));
            Screens.getWidgets(screen).add(stackButton);
        }
        final SortIconButton stackBtn = stackButton;
        final SortIconButton chestBtn = chestButton;
        boolean inventoryScreen = screen instanceof InventoryScreen;
        Runnable place = () -> {
            int left = get(LEFT, screen), top = get(TOP, screen);
            int w = get(IMG_W, screen), h = get(IMG_H, screen);
            int x = left + w - BTN_W - 4;
            if (chestBtn != null) chestBtn.setPosition(x, top + 4);
            if (stackBtn != null) stackBtn.setPosition(x - BTN_W - 2, top + 68);
            if (inventoryScreen) invButton.setPosition(x, top + 68);   // free space under crafting grid
            else invButton.setPosition(x, top + h - 96);               // on the "Inventory" label row
        };
        place.run();
        // Keep buttons in place when the screen shifts (e.g. recipe book opening).
        ScreenEvents.afterTick(screen).register(s -> place.run());
    }



    /** Creative inventory: the sort and quick stack icons show on the "Survival Inventory" tab (the one with your slots). */
    private void addCreativeButtons(Minecraft client,
                                    net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
        if (LEFT == null || TOP == null || IMG_W == null) return;
        SortIconButton sortBtn = new SortIconButton(SortIconButton.Icon.SORT,
                Component.translatable("invsorter.sort_inventory"), () -> {
                    if (client.player != null) startSort(client, client.player.inventoryMenu, true);
                });
        SortIconButton stackBtn = new SortIconButton(SortIconButton.Icon.QUICK_STACK,
                Component.translatable("invsorter.quick_stack"), () -> startQuickStack(client));
        Screens.getWidgets(screen).add(sortBtn);
        Screens.getWidgets(screen).add(stackBtn);
        Runnable place = () -> {
            boolean show = screen.isInventoryOpen();
            sortBtn.visible = show;
            sortBtn.active = show;
            stackBtn.visible = show;
            stackBtn.active = show;
            int x = get(LEFT, screen) + get(IMG_W, screen) - BTN_W - 6;
            int y = get(TOP, screen) + 6;
            sortBtn.setPosition(x, y);
            stackBtn.setPosition(x - BTN_W - 2, y);
        };
        place.run();
        ScreenEvents.afterTick(screen).register(s -> place.run());
    }

    // ---- chest memory ------------------------------------------------------------

    private static final java.util.Map<String, java.util.Set<net.minecraft.world.item.Item>> chestMemory = new java.util.HashMap<>();
    /** The chest/barrel the player most recently interacted with (null if it was anything else). */
    private static net.minecraft.core.BlockPos openedChest;

    /** Menus quick stack can deposit into: chests, barrels, shulker boxes, furnaces (all kinds), brewing stands, hoppers, dispensers, droppers. */
    public static boolean isStackMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu
                || menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu
                || menu instanceof net.minecraft.world.inventory.BrewingStandMenu
                || menu instanceof net.minecraft.world.inventory.HopperMenu
                || menu instanceof net.minecraft.world.inventory.DispenserMenu;
    }

    /** Blocks quick stack can use. A double chest counts once (see {@link #normalizeChest}). */
    private static boolean isStackBlock(net.minecraft.world.level.block.state.BlockState state) {
        var b = state.getBlock();
        return b instanceof net.minecraft.world.level.block.ChestBlock
                || b instanceof net.minecraft.world.level.block.BarrelBlock
                || b instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                || b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                || b instanceof net.minecraft.world.level.block.BrewingStandBlock
                || b instanceof net.minecraft.world.level.block.HopperBlock
                || b instanceof net.minecraft.world.level.block.DispenserBlock;
    }

    /** Machines whose contents change on their own, so what we saw last time can't be trusted. Always opened. */
    private static boolean isMachine(net.minecraft.world.level.block.state.BlockState state) {
        var b = state.getBlock();
        return b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                || b instanceof net.minecraft.world.level.block.BrewingStandBlock
                || b instanceof net.minecraft.world.level.block.HopperBlock;
    }

    /** For a usable block returns the position used as its key (the non-RIGHT half of a double chest), else null. */
    private static net.minecraft.core.BlockPos normalizeChest(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
            var type = state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
            if (type == net.minecraft.world.level.block.state.properties.ChestType.RIGHT) {
                return pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)).immutable();
            }
            return pos.immutable();
        }
        if (isStackBlock(state)) return pos.immutable();
        return null;
    }

    private static String memoryKey(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return level.dimension() + "@" + pos.asLong();
    }

    private static void rememberChest(Minecraft client, AbstractContainerMenu menu) {
        var pos = openedChest;
        if (pos == null || client.level == null || client.player == null) return;
        if (isMachine(client.level.getBlockState(pos))) return;   // furnaces etc. change by themselves
        java.util.Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>();
        for (Slot slot : menu.slots) {
            if (slot.container == client.player.getInventory()) continue;
            ItemStack st = slot.getItem();
            if (!st.isEmpty()) items.add(st.getItem());
        }
        chestMemory.put(memoryKey(client.level, pos), items);
    }

    /** True while quick stack is running its chest-opening fallback: the chest screens are not drawn. */
    public static boolean hideChestScreens() {
        return phase != Phase.IDLE;
    }

    // ---- quick stack to nearby chests (like Terraria) ----------------------------

    /** Singleplayer / LAN: items are moved directly, so there is no reach limit. This is how far we look. */
    private static final double DIRECT_RADIUS = 10.0;
    /** On a server we must open each chest, so it has to be within the server's reach (4.5 + a safety margin). */
    private static final double SERVER_REACH_BUFFER = 0.4;
    private static final double SERVER_SCAN_RADIUS = 7.0;

    private enum Phase { IDLE, NEXT, WAIT_OPEN, TRANSFER, WAIT_CLOSE }

    private static Phase phase = Phase.IDLE;
    private static int phaseTicks;
    private static final ArrayDeque<net.minecraft.core.BlockPos> stackTargets = new ArrayDeque<>();
    private static int movedStacks;
    private static int chestsUsed;


    /**
     * Chests and barrels the player can use, nearest first. {@code direct} = items are moved without opening
     * anything (singleplayer), so a big radius is fine; otherwise the chest must be within the player's reach.
     */
    private static List<net.minecraft.core.BlockPos> findContainers(net.minecraft.world.level.Level level,
                                                                    net.minecraft.world.entity.player.Player player,
                                                                    boolean direct) {
        var eye = player.getEyePosition();
        double radius = direct ? DIRECT_RADIUS : SERVER_SCAN_RADIUS;
        int r = (int) Math.ceil(radius);
        var origin = player.blockPosition();
        List<net.minecraft.core.BlockPos> found = new ArrayList<>();
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-r - 1, -r - 1, -r - 1), origin.offset(r + 1, r + 1, r + 1))) {
            var state = level.getBlockState(pos);
            boolean ok = isStackBlock(state);
            if (ok && state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                // double chest: only use one half
                ok = state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                        != net.minecraft.world.level.block.state.properties.ChestType.RIGHT;
            }
            if (!ok) continue;
            boolean inRange = direct
                    ? pos.distToCenterSqr(eye) <= radius * radius
                    : player.isWithinBlockInteractionRange(pos, SERVER_REACH_BUFFER);
            if (inRange) found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(eye)));
        return found;
    }

    /**
     * Singleplayer / LAN host: we own the server, so move the items directly. Instant, no chests
     * opening. Runs on the server thread.
     */
    private static void quickStackDirect(net.minecraft.server.MinecraftServer server, java.util.UUID id) {
        var sp = server.getPlayerList().getPlayer(id);
        if (sp == null) return;
        var level = sp.level();
        Inventory inv = sp.getInventory();
        int stacks = 0, chests = 0;
        for (var pos : findContainers(level, sp, true)) {
            var state = level.getBlockState(pos);
            net.minecraft.world.Container box;
            if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock cb) {
                box = net.minecraft.world.level.block.ChestBlock.getContainer(cb, state, level, pos, true);
            } else {
                box = level.getBlockEntity(pos) instanceof net.minecraft.world.Container c ? c : null;
            }
            if (box == null) continue;
            boolean used = false;
            for (int i = 9; i <= 35; i++) {
                ItemStack mine = inv.getItem(i);
                if (mine.isEmpty() || !containsItem(box, mine)) continue;
                int before = mine.getCount();
                insert(box, mine);
                if (mine.getCount() != before) {
                    stacks++;
                    used = true;
                }
            }
            if (used) {
                box.setChanged();
                chests++;
            }
        }
        sp.inventoryMenu.broadcastChanges();
        sp.sendOverlayMessage(stacks == 0
                ? Component.translatable("invsorter.nothing_stacked")
                : Component.translatable("invsorter.stacked", stacks, chests));
    }

    private static boolean containsItem(net.minecraft.world.Container box, ItemStack stack) {
        for (int i = 0; i < box.getContainerSize(); i++) {
            // canPlaceItem excludes furnace output slots, so smelted ingots never count as "already in there"
            if (ItemStack.isSameItem(box.getItem(i), stack) && box.canPlaceItem(i, stack)) return true;
        }
        return false;
    }

    /** Furnaces and brewing stands: only top up stacks already there, never drop things into other slots. */
    private static boolean mergeOnly(net.minecraft.world.Container box) {
        return box instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                || box instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
    }

    /** Puts as much of {@code stack} as fits into {@code box}: merge into partial stacks first, then empty slots. */
    private static void insert(net.minecraft.world.Container box, ItemStack stack) {
        for (int pass = 0; pass < (mergeOnly(box) ? 1 : 2) && !stack.isEmpty(); pass++) {
            for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack there = box.getItem(i);
                if (!box.canPlaceItem(i, stack)) continue;
                if (pass == 0) {
                    if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, stack)) continue;
                    int room = box.getMaxStackSize(there) - there.getCount();
                    int move = Math.min(room, stack.getCount());
                    if (move <= 0) continue;
                    there.grow(move);
                    stack.shrink(move);
                } else if (there.isEmpty()) {
                    int move = Math.min(box.getMaxStackSize(stack), stack.getCount());
                    box.setItem(i, stack.split(move));
                }
            }
        }
    }

    private static void startQuickStack(Minecraft client) {
        var player = client.player;
        var level = client.level;
        if (player == null || level == null || client.gameMode == null) return;
        if (phase != Phase.IDLE || !queue.isEmpty()) return;
        if (!player.containerMenu.getCarried().isEmpty()) return;

        // Singleplayer / LAN host: do it directly on the integrated server (instant, nothing opens).
        var server = client.getSingleplayerServer();
        if (server != null) {
            var id = player.getUUID();
            server.execute(() -> quickStackDirect(server, id));
            return;
        }

        boolean hasItems = false;
        for (int i = 9; i <= 35; i++) if (!player.getInventory().getItem(i).isEmpty()) hasItems = true;
        if (!hasItems) return;

        var eye = player.getEyePosition();
        List<net.minecraft.core.BlockPos> found = findContainers(level, player, false);
        java.util.Set<net.minecraft.world.item.Item> mine = new java.util.HashSet<>();
        for (int i = 9; i <= 35; i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (!st.isEmpty()) mine.add(st.getItem());
        }
        int inRange = found.size();
        // Skip chests we KNOW hold nothing we're carrying. Chests we've never opened are checked by opening them.
        found.removeIf(p -> {
            var known = chestMemory.get(memoryKey(level, p));
            return known != null && java.util.Collections.disjoint(known, mine);
        });
        LOGGER.info("Quick stack: {} chests in range", found.size());
        if (found.isEmpty()) {
            player.sendOverlayMessage(Component.translatable(
                    inRange == 0 ? "invsorter.no_chests" : "invsorter.none_remembered"));
            return;
        }
        stackTargets.clear();
        stackTargets.addAll(found);
        movedStacks = 0;
        chestsUsed = 0;
        var current = client.gui.screen();
        returnScreen = current instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen ? current : null;
        phase = Phase.NEXT;
        phaseTicks = 0;
    }

    /** The creative inventory screen to bring back when quick stack finishes (null = the normal inventory). */
    private static net.minecraft.client.gui.screens.Screen returnScreen;

    private static void finishQuickStack(Minecraft client) {
        phase = Phase.IDLE;
        stackTargets.clear();
        if (client.player != null) {
            client.player.sendOverlayMessage(movedStacks == 0
                    ? Component.translatable("invsorter.nothing_stacked")
                    : Component.translatable("invsorter.stacked", movedStacks, chestsUsed));
            client.gui.setScreen(returnScreen != null ? returnScreen : new InventoryScreen(client.player));
            returnScreen = null;
        }
    }

    private static void stackTick(Minecraft client) {
        if (phase == Phase.IDLE) return;
        var player = client.player;
        if (player == null || client.gameMode == null || client.level == null) {
            phase = Phase.IDLE;
            stackTargets.clear();
            queue.clear();
            return;
        }
        phaseTicks++;

        switch (phase) {
            case NEXT -> {
                if (player.containerMenu != player.inventoryMenu) return; // wait until the last chest is closed
                var pos = stackTargets.poll();
                if (pos == null) {
                    finishQuickStack(client);
                    return;
                }
                var center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
                var dir = net.minecraft.core.Direction.getApproximateNearest(player.getEyePosition().subtract(center));
                var hit = new net.minecraft.world.phys.BlockHitResult(
                        center.add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5), dir, pos, false);
                openedChest = pos;
                var result = client.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                if (result.consumesAction()) {
                    phase = Phase.WAIT_OPEN;
                    phaseTicks = 0;
                }
            }
            case WAIT_OPEN -> {
                if (isStackMenu(player.containerMenu)) {
                    var menu = player.containerMenu;
                    if (phaseTicks < 2) return; // give the server a moment to send the contents
                    Inventory inv = player.getInventory();
                    rememberChest(client, menu);   // record what's really inside (also for never-opened chests)
                    int moved = 0;
                    for (int i = 0; i < menu.slots.size(); i++) {
                        Slot slot = menu.slots.get(i);
                        if (slot.container != inv || slot.getContainerSlot() < 9 || slot.getContainerSlot() > 35) continue;
                        ItemStack mine = slot.getItem();
                        if (mine.isEmpty()) continue;
                        boolean chestHasIt = false;
                        for (Slot there : menu.slots) {
                            if (there.container == inv) continue;
                            // mayPlace skips output slots (a furnace's finished ingots are not "already in there")
                            if (ItemStack.isSameItem(there.getItem(), mine) && there.mayPlace(mine)) {
                                chestHasIt = true;
                                break;
                            }
                        }
                        if (chestHasIt) {
                            queue.add(new Click(i, true));
                            moved++;
                        }
                    }
                    queuedContainerId = menu.containerId;
                    movedStacks += moved;
                    if (moved > 0) chestsUsed++;
                    phase = Phase.TRANSFER;
                    phaseTicks = 0;
                } else if (phaseTicks > 15) { // chest never opened (blocked, locked...): skip it
                    phase = Phase.NEXT;
                    phaseTicks = 0;
                }
            }
            case TRANSFER -> {
                if (queue.isEmpty() || phaseTicks > 100) {
                    queue.clear();
                    player.closeContainer();
                    phase = Phase.WAIT_CLOSE;
                    phaseTicks = 0;
                }
            }
            case WAIT_CLOSE -> {
                if (phaseTicks >= 2) {
                    phase = Phase.NEXT;
                    phaseTicks = 0;
                }
            }
            default -> {}
        }
    }

    // ---- executing queued clicks -------------------------------------------------

    private void tick(Minecraft client) {
        if (queue.isEmpty()) return;

        if (client.player == null || client.gameMode == null) {
            queue.clear();
            return;
        }

        for (int i = 0; i < CLICKS_PER_TICK && !queue.isEmpty(); i++) {
            Click c = queue.poll();
            client.gameMode.handleContainerInput(
                    queuedContainerId, c.slotId(), 0,
                    c.quick() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, client.player);
        }
    }

    // ---- planning ----------------------------------------------------------------

    private static void startSort(Minecraft client, AbstractContainerMenu menu, boolean playerInventory) {
        if (client.player == null || client.gameMode == null) return;
        if (!queue.isEmpty()) return; // already sorting
        if (!menu.getCarried().isEmpty()) {
            LOGGER.info("Not sorting: item on cursor");
            return;
        }

        boolean hasContainer = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
        boolean isInventoryOnly = menu instanceof InventoryMenu;
        if (!hasContainer && !isInventoryOnly) return;

        Inventory inv = client.player.getInventory();
        List<Integer> region = new ArrayList<>();
        boolean wantInventory = playerInventory || !hasContainer;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            boolean isPlayerSlot = slot.container == inv;
            if (wantInventory) {
                // main inventory only: container slots 9..35 (skip hotbar, armor, offhand)
                if (isPlayerSlot && slot.getContainerSlot() >= 9 && slot.getContainerSlot() <= 35) region.add(i);
            } else if (!isPlayerSlot) {
                region.add(i);
            }
        }
        LOGGER.info("Sort region has {} slots (inventory={})", region.size(), wantInventory);
        if (region.isEmpty()) return;

        ItemStack[] stacks = new ItemStack[region.size()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = menu.slots.get(region.get(i)).getItem().copy();
        }

        buildCreativeOrder(client);
        List<Click> plan = plan(stacks, region);
        LOGGER.info("Sort plan: {} clicks", plan.size());
        if (plan.isEmpty()) return;

        queuedContainerId = menu.containerId;
        queue.addAll(plan);
    }

    /** Builds the list of left-clicks that merges partial stacks, then sorts. */
    private static List<Click> plan(ItemStack[] cur, List<Integer> region) {
        List<Click> out = new ArrayList<>();
        int n = cur.length;

        // Pass 1: merge partial stacks of the same item.
        for (int i = 0; i < n; i++) {
            if (cur[i].isEmpty() || cur[i].getCount() >= cur[i].getMaxStackSize()) continue;
            for (int j = i + 1; j < n; j++) {
                if (cur[i].getCount() >= cur[i].getMaxStackSize()) break;
                if (cur[j].isEmpty() || !ItemStack.isSameItemSameComponents(cur[i], cur[j])) continue;

                int move = Math.min(cur[i].getMaxStackSize() - cur[i].getCount(), cur[j].getCount());
                out.add(new Click(region.get(j))); // pick up j
                out.add(new Click(region.get(i))); // drop onto i (merges)
                cur[i].grow(move);
                cur[j].shrink(move);
                if (!cur[j].isEmpty()) {
                    out.add(new Click(region.get(j))); // put the leftovers back
                } else {
                    cur[j] = ItemStack.EMPTY;
                }
            }
        }

        // Pass 2: selection sort using cursor swaps.
        List<ItemStack> target = new ArrayList<>();
        for (ItemStack s : cur) if (!s.isEmpty()) target.add(s.copy());
        target.sort(STACK_ORDER);

        for (int i = 0; i < target.size(); i++) {
            ItemStack want = target.get(i);
            if (!cur[i].isEmpty() && ItemStack.isSameItemSameComponents(cur[i], want)) continue;

            int j = -1;
            // Prefer a stack with the exact count wanted (full stacks first).
            for (int k = i + 1; k < n && j < 0; k++) {
                if (!cur[k].isEmpty() && ItemStack.isSameItemSameComponents(cur[k], want)
                        && cur[k].getCount() == want.getCount()) j = k;
            }
            for (int k = i + 1; k < n && j < 0; k++) {
                if (!cur[k].isEmpty() && ItemStack.isSameItemSameComponents(cur[k], want)) j = k;
            }
            if (j < 0) continue;

            if (cur[i].isEmpty()) {
                out.add(new Click(region.get(j))); // pick up j
                out.add(new Click(region.get(i))); // place at i
            } else {
                out.add(new Click(region.get(i))); // pick up i
                out.add(new Click(region.get(j))); // swap with j
                out.add(new Click(region.get(i))); // put j's old stack... into i
            }
            // Cursor swap result: i gets j's stack, j gets i's old stack.
            ItemStack tmp = cur[i];
            cur[i] = cur[j];
            cur[j] = tmp;
        }
        return out;
    }

    // ---- smart ordering ----------------------------------------------------------

    /** Position of each item in the creative inventory (blocks, tools, combat, food, ...). */
    private static java.util.Map<net.minecraft.world.item.Item, Integer> creativeOrder;

    private static void buildCreativeOrder(Minecraft client) {
        if (creativeOrder != null) return;
        java.util.Map<net.minecraft.world.item.Item, Integer> order = new java.util.HashMap<>();
        try {
            var conn = client.getConnection();
            if (conn != null) {
                net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
                        conn.enabledFeatures(), false, conn.registryAccess());
            }
            int index = 0;
            for (var tab : net.minecraft.world.item.CreativeModeTabs.allTabs()) {
                if (tab.getType() != net.minecraft.world.item.CreativeModeTab.Type.CATEGORY) continue;
                for (ItemStack stack : tab.getDisplayItems()) {
                    order.putIfAbsent(stack.getItem(), index++);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not read creative tab order, falling back to name order", t);
        }
        LOGGER.info("Smart sort order built for {} items", order.size());
        if (!order.isEmpty()) creativeOrder = order;
    }

    private static int orderOf(ItemStack s) {
        if (creativeOrder == null) return Integer.MAX_VALUE;
        return creativeOrder.getOrDefault(s.getItem(), Integer.MAX_VALUE - 1);
    }

    /**
     * Smart order: follows the creative inventory, so related things sit together
     * (stone with stone bricks, tools with tools, food with food). Items not in any tab
     * go last by name. Within the same item: by name, then biggest stacks first.
     */
    private static final Comparator<ItemStack> STACK_ORDER = Comparator
            .comparingInt(InventorySorterClient::orderOf)
            .thenComparing(s -> s.getItem().toString())
            .thenComparing(s -> s.getHoverName().getString().toLowerCase())
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
}
