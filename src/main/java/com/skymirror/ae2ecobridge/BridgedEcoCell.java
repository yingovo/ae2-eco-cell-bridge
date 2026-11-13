package com.skymirror.ae2ecobridge;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 把任意 AE2 {@link StorageCell} 包装成 NeoECOAE 的 {@link IECOStorageCell}。
 *
 * <p>读写全部委托给原 cell。容量与「类型分类」按元件本身的 key 类型来：
 * 物品盘归物品类，流体盘归流体质，魔力/FE/空气盘各自找 ECO 里对应类型的元件来借用；
 * 找不到对应类型时退回物品类。
 */
public class BridgedEcoCell implements IECOStorageCell {

    private static final long FALLBACK_BYTES = 1_048_576L;
    private static final int FALLBACK_BYTES_PER_TYPE = 8;
    private static final int FALLBACK_TYPES = 63;

    private static final ECOCellType FALLBACK_TYPE = new ECOCellType(
            new ResourceLocation(Ae2EcoBridge.MODID, "bridged_item"),
            Component.translatable("block.ae2ecobridge.bridged_cell"),
            1);

    /** 每种 key 类型借到的标准 ECO 元件类型 / 等级（null 表示没找到）。 */
    private static final Map<String, ECOCellType> TYPE_CACHE = new HashMap<>();
    private static final Map<String, IECOTier> TIER_CACHE = new HashMap<>();
    private static boolean resolved;

    private final StorageCell delegate;
    private final ItemStack stack;
    private final long totalBytes;
    private final int bytesPerType;
    private final int totalTypes;

    public BridgedEcoCell(StorageCell delegate, ItemStack stack) {
        this.delegate = delegate;
        this.stack = stack;

        long bytes = FALLBACK_BYTES;
        int perType = FALLBACK_BYTES_PER_TYPE;
        int types = FALLBACK_TYPES;

        long[] real = readCapacityFromItem(stack);
        if (real != null) {
            bytes = Math.max(1L, real[0]);
            perType = (int) Math.max(1L, real[1]);
            types = (int) Math.max(1L, real[2]);
        }

        this.totalBytes = bytes;
        this.bytesPerType = perType;
        this.totalTypes = types;
    }

    private static long[] readCapacityFromItem(ItemStack stack) {
        Item item = stack.getItem();
        try {
            Method getBytes = item.getClass().getMethod("getBytes", ItemStack.class);
            Method getBytesPerType = item.getClass().getMethod("getBytesPerType", ItemStack.class);
            Method getTotalTypes = item.getClass().getMethod("getTotalTypes", ItemStack.class);
            long bytes = ((Number) getBytes.invoke(item, stack)).longValue();
            long perType = ((Number) getBytesPerType.invoke(item, stack)).longValue();
            long types = ((Number) getTotalTypes.invoke(item, stack)).longValue();
            return new long[]{bytes, perType, types};
        } catch (Throwable ignored) {
            // 落到 IBasicCellItem
        }
        try {
            if (item instanceof IBasicCellItem basic) {
                return new long[]{
                        basic.getBytes(stack),
                        basic.getBytesPerType(stack),
                        basic.getTotalTypes(stack)
                };
            }
        } catch (Throwable ignored) {
            // 用兜底值
        }
        return null;
    }

    public ItemStack getCellStack() {
        return stack;
    }

    // ------------------------------------------------------------------
    // 委托给 AE2 原 cell
    // ------------------------------------------------------------------

    @Override
    public CellState getStatus() {
        return delegate.getStatus();
    }

    @Override
    public double getIdleDrain() {
        return delegate.getIdleDrain();
    }

    @Override
    public void persist() {
        delegate.persist();
    }

    @Override
    public boolean canFitInsideCell() {
        return delegate.canFitInsideCell();
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        delegate.getAvailableStacks(out);
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }

    // ------------------------------------------------------------------
    // 按 key 类型借用 ECO 的分类
    // ------------------------------------------------------------------

    /** 磁盘当前的存储类型（读 AE Tetra Plus 写在物品 NBT 上的标记）。 */
    private String keyTypeId() {
        try {
            String id = stack.getOrCreateTag().getString("ae2tpKeyType");
            return id.isEmpty() ? "item" : id;
        } catch (Throwable t) {
            return "item";
        }
    }

    /** 实际用于 ECO 分类的类型：全类型核心按内容判断归属。 */
    private String effectiveTypeId() {
        String id = keyTypeId();
        if (!"all".equals(id)) {
            return id;
        }
        try {
            KeyCounter counter = snapshot();
            String detected = null;
            for (AEKey key : counter.keySet()) {
                if (key == null) {
                    continue;
                }
                String one = idOfType(key.getType());
                if (one == null) {
                    return "item";
                }
                if (detected == null) {
                    detected = one;
                } else if (!detected.equals(one)) {
                    return "item"; // 混装：ECO 没有混合类型，归物品
                }
            }
            return detected == null ? "item" : detected;
        } catch (Throwable t) {
            return "item";
        }
    }

    private static String idOfType(AEKeyType type) {
        if (type == AEKeyType.items()) {
            return "item";
        }
        if (type == AEKeyType.fluids()) {
            return "fluid";
        }
        if (type == reflectType("appbot.ae2.ManaKeyType", "TYPE")) {
            return "mana";
        }
        if (type == reflectType("com.glodblock.github.appflux.common.me.key.type.FluxKeyType", "TYPE")) {
            return "fe";
        }
        if (type == reflectType("com.wintercogs.appliedpneumatics.common.me.keys.types.AirKeyType", "INSTANCE")) {
            return "air";
        }
        return null;
    }

    @Override
    public IECOTier getTier() {
        resolveEcoTypes();
        IECOTier tier = TIER_CACHE.get(effectiveTypeId());
        if (tier == null) {
            tier = TIER_CACHE.get("item");
        }
        return tier != null ? tier : ECOTier.L4;
    }

    @Override
    public ECOCellType getCellType() {
        resolveEcoTypes();
        ECOCellType type = TYPE_CACHE.get(effectiveTypeId());
        if (type == null) {
            // 没有对应分类时直接借用标准的物品元件类型，避免出现自定义的“bridged”条目
            type = TYPE_CACHE.get("item");
        }
        return type != null ? type : FALLBACK_TYPE;
    }

    @Override
    public long getTotalBytes() {
        return totalBytes;
    }

    @Override
    public long getTotalItemTypes() {
        return totalTypes;
    }

    @Override
    public long getStoredItemTypes() {
        return snapshot().keySet().size();
    }

    @Override
    public long getUsedBytes() {
        KeyCounter counter = snapshot();
        long used = (long) counter.keySet().size() * bytesPerType;
        for (AEKey key : counter.keySet()) {
            long amount = counter.get(key);
            int perByte = 8;
            try {
                perByte = Math.max(1, key.getType().getAmountPerByte());
            } catch (Throwable ignored) {
                perByte = 8;
            }
            used += (amount + perByte - 1L) / perByte;
        }
        return Math.min(used, totalBytes);
    }

    private KeyCounter snapshot() {
        KeyCounter counter = new KeyCounter();
        delegate.getAvailableStacks(counter);
        return counter;
    }

    /**
     * 把 ECO 自己的元件按「key 类型」分类缓存起来：
     * 物品 / 流体 / 与扩展同类型的元件各取一个，用于向主机声明这张盘属于哪一类。
     */
    private static void resolveEcoTypes() {
        if (resolved) {
            return;
        }
        resolved = true;
        String[] ids = {"item", "fluid", "mana", "fe", "air", "all"};
        Map<AEKeyType, String> mapping = new HashMap<>();
        try {
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (!(item instanceof IBasicECOCellItem eco)) {
                    continue;
                }
                AEKeyType type = eco.getKeyType();
                ECOCellType cellType = eco.getCellType();
                IECOTier tier = eco.getTier();
                if (type == null || cellType == null) {
                    continue;
                }
                for (String id : ids) {
                    if (TYPE_CACHE.containsKey(id)) {
                        continue;
                    }
                    if (matches(id, type)) {
                        TYPE_CACHE.put(id, cellType);
                        TIER_CACHE.put(id, tier);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 保持兜底
        }
    }

    /** 判断 ECO 元件的 key 类型是否符合我们某个逻辑类型。 */
    private static boolean matches(String id, AEKeyType type) {
        switch (id) {
            case "item":
                return type == AEKeyType.items();
            case "fluid":
                return type == AEKeyType.fluids();
            case "mana":
                return type == reflectType("appbot.ae2.ManaKeyType", "TYPE");
            case "fe":
                return type == reflectType("com.glodblock.github.appflux.common.me.key.type.FluxKeyType", "TYPE");
            case "air":
                return type == reflectType("com.wintercogs.appliedpneumatics.common.me.keys.types.AirKeyType", "INSTANCE");
            case "all":
                return false;
            default:
                return false;
        }
    }

    private static AEKeyType reflectType(String className, String fieldName) {
        try {
            Class<?> cls = Class.forName(className);
            Object value = cls.getField(fieldName).get(null);
            if (value instanceof AEKeyType type) {
                return type;
            }
        } catch (Throwable ignored) {
            // 没装该扩展
        }
        return null;
    }
}
