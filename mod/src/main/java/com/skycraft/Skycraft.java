package com.skycraft;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Skycraft.MODID)
public class Skycraft {
    public static final String MODID = "skycraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Skycraft() {
        LOGGER.info("Skycraft Core loading");
    }
}
