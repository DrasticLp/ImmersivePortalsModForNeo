package qouteall.imm_ptl.core;

import com.mojang.logging.LogUtils;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TickEvent;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.imm_ptl.core.chunk_loading.*;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.commands.AxisArgumentType;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.commands.SubCommandArgumentType;
import qouteall.imm_ptl.core.commands.TimingFunctionArgumentType;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.debug.DebugUtil;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.*;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.RotationAnimation;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.GlobalTrackedPortal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.global_portals.WorldWrappingPortal;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.imm_ptl.core.portal.shape.RectangularPortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;

import java.io.File;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public class IPModMain {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static void init() {
        loadConfig();
        
        Helper.LOGGER.info("Immersive Portals Mod Initializing");
        
        ImmPtlNetworking.init();
        ImmPtlNetworkConfig.init();

        NeoForge.EVENT_BUS.addListener(IPGlobal.PostClientTickEvent.class, postClientTickEvent -> IPGlobal.clientTaskList.processTasks());

        NeoForge.EVENT_BUS.addListener(TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                // TODO make it per-server
                IPGlobal.serverTaskList.processTasks();
            }
        });

        NeoForge.EVENT_BUS.addListener(IPGlobal.PreGameRenderEvent.class, preGameRenderEvent -> IPGlobal.preGameRenderTaskList.processTasks());
        
        IPGlobal.clientCleanupSignal.connect(() -> {
            if (ClientWorldLoader.getIsInitialized()) {
                IPGlobal.clientTaskList.forceClearTasks();
            }
        });
        IPGlobal.serverCleanupSignal.connect(IPGlobal.serverTaskList::forceClearTasks);
        
        IPGlobal.serverTeleportationManager = new ServerTeleportationManager();
        
        RectangularPortalShape.init();
        SpecialFlatPortalShape.init();
        BoxPortalShape.init();
        
        ImmPtlChunkTracking.init();
        
        WorldInfoSender.init();
        
        GlobalPortalStorage.init();
        
        EntitySync.init();
        
        ServerTeleportationManager.init();
        
        CollisionHelper.init();
        
        PortalExtension.init();
        
        GcMonitor.initCommon();
        
        ServerPerformanceMonitor.init();
        
        ImmPtlChunkTickets.init();
        
        IPPortingLibCompat.init();
        
        BlockManipulationServer.init();

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> {
            PortalCommand.register(event.getDispatcher());
        });
        SubCommandArgumentType.init();
        TimingFunctionArgumentType.init();
        AxisArgumentType.init();
    
        DebugUtil.init();
        
        // intrinsic animation driver types
        RotationAnimation.init();
        NormalAnimation.init();
//        OscillationAnimation.init();
    }
    
    private static void loadConfig() {
        // upgrade old config
        Path gameDir = O_O.getGameDir();
        File oldConfigFile = gameDir.resolve("config").resolve("immersive_portals_fabric.json").toFile();
        if (oldConfigFile.exists()) {
            File dest = gameDir.resolve("config").resolve("immersive_portals.json").toFile();
            boolean succeeded = oldConfigFile.renameTo(dest);
            if (succeeded) {
                Helper.log("Upgraded old config file");
            }
            else {
                Helper.err("Failed to upgrade old config file");
            }
        }
        
        Helper.log("Loading Immersive Portals config");
        IPGlobal.configHolder = AutoConfig.register(IPConfig.class, GsonConfigSerializer::new);
        IPGlobal.configHolder.registerSaveListener((configHolder, ipConfig) -> {
            ipConfig.onConfigChanged();
            return InteractionResult.SUCCESS;
        });
        IPConfig ipConfig = IPConfig.getConfig();
        ipConfig.onConfigChanged();
    }
    
    public static void registerBlocks(BiConsumer<ResourceLocation, PortalPlaceholderBlock> regFunc) {
        regFunc.accept(
            new ResourceLocation("immersive_portals", "nether_portal_block"),
            PortalPlaceholderBlock.instance
        );
    }
    
    public static void registerEntityTypes(BiConsumer<ResourceLocation, EntityType<?>> regFunc) {
    
        regFunc.accept(
            new ResourceLocation("immersive_portals", "portal"),
            Portal.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "nether_portal_new"),
            NetherPortalEntity.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "end_portal"),
            EndPortalEntity.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "mirror"),
            Mirror.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "breakable_mirror"),
            BreakableMirror.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "global_tracked_portal"),
            GlobalTrackedPortal.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "border_portal"),
            WorldWrappingPortal.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "end_floor_portal"),
            VerticalConnectingPortal.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "general_breakable_portal"),
            GeneralBreakablePortal.entityType
        );
        
        regFunc.accept(
            new ResourceLocation("immersive_portals", "loading_indicator"),
            LoadingIndicatorEntity.entityType
        );
    }
}
