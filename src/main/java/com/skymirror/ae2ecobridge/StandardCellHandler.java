package com.skymirror.ae2ecobridge;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import net.minecraft.world.item.ItemStack;

/**
 * 桥接 handler：把「用 AE2 标准 ICellHandler 注册的存储元件」翻译成
 * NeoECOAE 认识的 IECOStorageCell。
 *
 * <p>NeoECOAE 的存储矩阵驱动器（eco_drive）插槽校验走
 * {@code ECOStorageCells.isCellHandled(stack)}，只看它自己的注册表；
 * 而 AE Tetra 这类模组的元件注册在 AE2 的 {@code appeng.api.storage.StorageCells} 里。
 * 这里把后者的 handler 包一层，于是两边都能认。
 */
public final class StandardCellHandler implements IECOCellHandler {

    public static final StandardCellHandler INSTANCE = new StandardCellHandler();

    private StandardCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        // NeoECOAE 自己的元件本来就被原生 handler 处理，不要重复接管
        if (stack.getItem() instanceof IECOStorageCellItem) {
            return false;
        }
        // 只接管「AE2 标准体系里能被识别为存储元件」的物品
        return StorageCells.isCellHandled(stack);
    }

    @Override
    public IECOStorageCell getCellInventory(ItemStack stack, ISaveProvider saveProvider) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ICellHandler aeHandler = StorageCells.getHandler(stack);
        if (aeHandler == null) {
            return null;
        }
        StorageCell aeCell = aeHandler.getCellInventory(stack, saveProvider);
        if (aeCell == null) {
            return null;
        }
        return new BridgedEcoCell(aeCell, stack);
    }

    // releaseCellInventory / clearRuntimeState 在接口里有默认实现，这里无需覆写
}
