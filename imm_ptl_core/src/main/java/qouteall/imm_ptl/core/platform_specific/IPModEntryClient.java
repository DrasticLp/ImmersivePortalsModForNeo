package qouteall.imm_ptl.core.platform_specific;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.IPModMainClient;
import qouteall.imm_ptl.core.compat.IPModInfoChecking;
import qouteall.imm_ptl.core.compat.iris_compatibility.ExperimentalIrisPortalRenderer;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.*;
import qouteall.imm_ptl.core.portal.global_portals.GlobalTrackedPortal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.global_portals.WorldWrappingPortal;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;
import qouteall.imm_ptl.core.render.LoadingIndicatorRenderer;
import qouteall.imm_ptl.core.render.PortalEntityRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.Arrays;

public class IPModEntryClient {
    
    public static void initPortalRenderers(EntityRenderersEvent.RegisterRenderers event) {
        
        Arrays.stream(new EntityType<?>[]{
            Portal.entityType,
            NetherPortalEntity.entityType,
            EndPortalEntity.entityType,
            Mirror.entityType,
            BreakableMirror.entityType,
            GlobalTrackedPortal.entityType,
            WorldWrappingPortal.entityType,
            VerticalConnectingPortal.entityType,
            GeneralBreakablePortal.entityType
        }).peek(
            Validate::notNull
        ).forEach(
            entityType -> event.registerEntityRenderer(
                entityType,
                (EntityRendererProvider) PortalEntityRenderer::new
            )
        );

        event.registerEntityRenderer(LoadingIndicatorEntity.entityType, LoadingIndicatorRenderer::new);

//        EntityRendererRegistry.register(
//            LoadingIndicatorEntity.entityType,
//            LoadingIndicatorRenderer::new
//        );
        
    }

    public void onInitializeClient(IEventBus modEventBus) {
        IPModMainClient.init();

//        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {initPortalRenderers(event);});
        
        boolean isSodiumPresent =
            ModList.get().isLoaded("sodium");
        if (isSodiumPresent) {
            Helper.log("Sodium is present");
            
            SodiumInterface.invoker = new SodiumInterface.OnSodiumPresent();
            
            // Sodium compat is pretty ok now. No warning needed.
//            IPGlobal.clientTaskList.addTask(MyTaskList.oneShotTask(() -> {
//                if (IPGlobal.enableWarning) {
//                    CHelper.printChat(
//                        Component.translatable("imm_ptl.sodium_warning")
//                            .append(IPMcHelper.getDisableWarningText())
//                    );
//                }
//            }));
        }
        else {
            Helper.log("Sodium is not present");
        }
        
        if (ModList.get().isLoaded("iris")) {
            Helper.log("Iris is present");
            IrisInterface.invoker = new IrisInterface.OnIrisPresent();
            ExperimentalIrisPortalRenderer.init();
            
            IPGlobal.clientTaskList.addTask(MyTaskList.oneShotTask(() -> {
                if (IPConfig.getConfig().shouldDisplayWarning("iris")) {
                    CHelper.printChat(
                        Component.translatable("imm_ptl.iris_warning")
                            .append(IPMcHelper.getDisableWarningText("iris"))
                    );
                }
            }));
        }
        else {
            Helper.log("Iris is not present");
        }
        
        IPModInfoChecking.initClient();
    }
    
}
