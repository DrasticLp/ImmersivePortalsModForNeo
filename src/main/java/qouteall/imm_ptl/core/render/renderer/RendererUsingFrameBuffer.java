package qouteall.imm_ptl.core.render.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.*;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.List;

public class RendererUsingFrameBuffer extends PortalRenderer {
    SecondaryFrameBuffer secondaryFrameBuffer = new SecondaryFrameBuffer();

    @Override
    public void onBeforeTranslucentRendering(Matrix4f matrixStack) {
        renderPortals(matrixStack);
    }

    @Override
    public void onAfterTranslucentRendering(Matrix4f matrixStack) {

    }

    @Override
    public void onHandRenderingEnded() {

    }

    @Override
    public void finishRendering() {

    }

    @Override
    public void prepareRendering() {
        secondaryFrameBuffer.prepare();

        GlStateManager._enableDepthTest();

        GL11.glDisable(GL11.GL_STENCIL_TEST);

        IPPortingLibCompat.setIsStencilEnabled(client.getMainRenderTarget(), false);
//        ((IEFrameBuffer) client.getMainRenderTarget()).setIsStencilBufferEnabledAndReload(false);
    }

    protected void doRenderPortal(
            PortalRenderable portal,
            Matrix4f matrixStack
    ) {
        if (PortalRendering.isRendering()) {
            //only support one-layer portal
            return;
        }

        if (!testShouldRenderPortal(portal, matrixStack)) {
            return;
        }

        PortalRendering.pushPortalLayer(portal.getPortalLike());

        RenderTarget oldFrameBuffer = client.getMainRenderTarget();

        ((IEMinecraftClient) client).ip_setFrameBuffer(secondaryFrameBuffer.fb);
        secondaryFrameBuffer.fb.bindWrite(true);

        GlStateManager._clearColor(1, 0, 1, 1);
        GlStateManager._clearDepth(1);
        GlStateManager._clear(
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                Minecraft.ON_OSX
        );
        GL11.glDisable(GL11.GL_STENCIL_TEST);

        renderPortalContent(portal);

        ((IEMinecraftClient) client).ip_setFrameBuffer(oldFrameBuffer);
        oldFrameBuffer.bindWrite(true);

        PortalRendering.popPortalLayer();

        CHelper.enableDepthClamp();
        renderSecondBufferIntoMainBuffer(portal, matrixStack);
        CHelper.disableDepthClamp();

        MyRenderHelper.debugFramebufferDepth();
    }

    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
        //nothing
    }

    @Override
    public boolean replaceFrameBufferClearing() {
        return false;
    }

    private boolean testShouldRenderPortal(
            PortalRenderable portal,
            Matrix4f matrixStack
    ) {
        FrontClipping.updateInnerClipping(matrixStack);
        return QueryManager.renderAndGetDoesAnySamplePass(() -> {
            ViewAreaRenderer.renderPortalArea(
                    portal, Vec3.ZERO,
                    matrixStack,
                    RenderSystem.getProjectionMatrix(),
                    true, true,
                    true, true
            );
        });
    }

    private void renderSecondBufferIntoMainBuffer(PortalRenderable portal, Matrix4f matrixStack) {
        MyRenderHelper.drawPortalAreaWithFramebuffer(
                portal,
                secondaryFrameBuffer.fb,
                matrixStack,
                RenderSystem.getProjectionMatrix()
        );
    }

    protected void renderPortals(Matrix4f matrixStack) {
        List<PortalRenderable> portalsToRender = getPortalsToRender(matrixStack);

        for (PortalRenderable portal : portalsToRender) {
            doRenderPortal(portal, matrixStack);
        }
    }
}
