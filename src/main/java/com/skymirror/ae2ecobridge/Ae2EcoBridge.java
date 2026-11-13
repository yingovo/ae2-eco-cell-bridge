package com.skymirror.ae2ecobridge;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 入口：在通用初始化阶段把「标准 AE2 元件 -> ECO 存储矩阵」的桥接 handler
 * 注册进 NeoECOAE 的元件注册表（ECOStorageCells）。
 *
 * 注册之后，ECO - LD 存储矩阵驱动器 的插槽校验
 * （WithECORestrictedInputSlot -> ECOStorageCells.isCellHandled）
 * 就会认这些元件了。
 */
@Mod(Ae2EcoBridge.MODID)
public class Ae2EcoBridge {

    public static final String MODID = "ae2ecobridge";

    public Ae2EcoBridge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        // enqueueWork：确保在主线程、且各模组注册表都已就绪后执行
        event.enqueueWork(() -> {
            try {
                ECOStorageCells.register(StandardCellHandler.INSTANCE);
            } catch (Throwable t) {
                // 不让兼容失败拖垮整个游戏启动
                System.err.println("[AE2 ECO Bridge] 注册元件 handler 失败: " + t);
            }
        });
    }
}
