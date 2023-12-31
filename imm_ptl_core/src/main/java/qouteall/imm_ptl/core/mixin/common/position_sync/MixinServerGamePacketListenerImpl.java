package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.ducks.IEServerPlayNetworkHandler;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.LimitedLogger;

import java.util.Set;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 900)
public abstract class MixinServerGamePacketListenerImpl implements IEServerPlayNetworkHandler {
    @Shadow
    public ServerPlayer player;
    @Shadow
    private Vec3 awaitingPositionFromClient;
    @Shadow
    private int awaitingTeleport;
    @Shadow
    private int awaitingTeleportTime;
    @Shadow
    private int tickCount;
    
    @Shadow
    private double vehicleLastGoodX;
    
    @Shadow
    private double vehicleLastGoodY;
    
    @Shadow
    private double vehicleLastGoodZ;
    
    @Shadow
    private double vehicleFirstGoodX;
    
    @Shadow
    private double vehicleFirstGoodY;
    
    @Shadow
    private double vehicleFirstGoodZ;
    
    @Shadow
    private Entity lastVehicle;
    
    @Shadow
    private boolean clientVehicleIsFloating;
    
    @Shadow
    @Final
    private static Logger LOGGER;
    
    private static final LimitedLogger ip_limitedLogger = new LimitedLogger(20);
    
    private int ip_dubiousMoveCount = 0;
    
    //do not process move packet when client dimension and server dimension are not synced
    @Inject(
        method = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
        at = @At(
            value = "INVOKE",
            shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"
        ),
        cancellable = true
    )
    private void onProcessMovePacket(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ResourceKey<Level> packetDimension = ((IEPlayerMoveC2SPacket) packet).ip_getPlayerDimension();
        
        if (packetDimension == null) {
            // this actually never happens, because the vanilla client will disconnect immediately
            // when receiving the position sync packet that has the extra dimension field
            Helper.err("Player move packet is missing dimension info. Maybe the player client doesn't have ImmPtl");
            IPGlobal.serverTaskList.addTask(() -> {
                player.connection.disconnect(Component.literal(
                    "The client does not have Immersive Portals mod"
                ));
                return true;
            });
            return;
        }
        
        if (player.level().dimension() != packetDimension) {
            ip_limitedLogger.lInfo(LOGGER, "[ImmPtl] Ignoring player move packet %s %s".formatted(player, packetDimension.location()));
            
            ip_dubiousMoveCount += 1;
            
            if (ip_dubiousMoveCount > 200) {
                LOGGER.info(
                    "[ImmPtl] Force move player {} {} {}",
                    player, player.level().dimension().location(), player.position()
                );
                IPGlobal.serverTeleportationManager.forceTeleportPlayer(
                    player, player.level().dimension(), player.position()
                );
                ip_dubiousMoveCount = 0;
            }
            
            ci.cancel();
        }
        else {
            ip_dubiousMoveCount = 0;
        }
    }
    
    // TODO improve position validation
    @Redirect(
        method = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isChangingDimension()Z"
        ),
        require = 0 // don't crash with carpet
    )
    private boolean redirectIsInTeleportationState(ServerPlayer player) {
        if (shouldAcceptDubiousMovement(player)) {
            return true;
        }
        return player.isChangingDimension();
    }
    
    /**
     * @reason make PlayerPositionLookS2CPacket contain dimension data and do some special handling
     * @author qouteall
     */
    @Overwrite
    @IPVanillaCopy
    public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> nonRelative) {
        // it may request teleport while this.player is marked removed during respawn
        
        if (player.getRemovalReason() != null) {
            LOGGER.error(
                "[ImmPtl] Tries to send player pos packet to a removed player",
                new Throwable());
            return;
        }
        
        double xDiff = nonRelative.contains(RelativeMovement.X) ? this.player.getX() : 0.0;
        double yDiff = nonRelative.contains(RelativeMovement.Y) ? this.player.getY() : 0.0;
        double zDiff = nonRelative.contains(RelativeMovement.Z) ? this.player.getZ() : 0.0;
        float yawDiff = nonRelative.contains(RelativeMovement.Y_ROT) ? this.player.getYRot() : 0.0f;
        float pitchDiff = nonRelative.contains(RelativeMovement.X_ROT) ? this.player.getXRot() : 0.0f;
        
        this.awaitingPositionFromClient = new Vec3(x, y, z);
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }
        
        this.awaitingTeleportTime = this.tickCount;
        this.player.absMoveTo(x, y, z, yaw, pitch);
        ClientboundPlayerPositionPacket lookPacket = new ClientboundPlayerPositionPacket(
            x - xDiff, y - yDiff, z - zDiff, yaw - yawDiff, pitch - pitchDiff, nonRelative, this.awaitingTeleport
        );
        
        ((IEPlayerPositionLookS2CPacket) lookPacket).ip_setPlayerDimension(player.level().dimension());
        
        this.player.connection.send(lookPacket);
    }
    
    // make the server to consider player movement valid when touching portal,
    // so it will not send teleport packet to client
    // TODO refactor this
    @Inject(
        method = "isPlayerCollidingWithAnythingNew",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onIsPlayerCollidingWithAnythingNew(
        LevelReader levelReader, AABB aABB, double d, double e, double f,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (shouldAcceptDubiousMovement(player)) {
            cir.setReturnValue(false);
        }
    }
    
//    @Overwrite
//    @IPVanillaCopy
//    private boolean isPlayerCollidingWithAnythingNew(
//        LevelReader levelReader, AABB originalBB,
//        double newX, double newY, double newZ
//    ) {
//        AABB originalBBClipped = ((IEEntity) player).ip_getActiveCollisionBox(originalBB);
//
//        if (originalBBClipped == null) {
//            return false;
//        }
//
//        AABB newBB = this.player.getBoundingBox()
//            .move(newX - this.player.getX(), newY - this.player.getY(), newZ - this.player.getZ());
//
//        AABB newBBClipped = ((IEEntity) player).ip_getActiveCollisionBox(newBB);
//
//        if (newBBClipped == null) {
//            return false;
//        }
//
//        Iterable<VoxelShape> newCollisions =
//            levelReader.getCollisions(this.player, newBBClipped.deflate(1.0E-5f));
//
//        VoxelShape originalBBCollision = Shapes.create(originalBBClipped.deflate(1.0E-5f));
//
//        for (VoxelShape shape : newCollisions) {
//            // if there are new collisions that does not intersect with the original bounding box
//            // then it's colliding with something new
//            if (!Shapes.joinIsNotEmpty(shape, originalBBCollision, BooleanOp.AND)) {
//                return true;
//            }
//        }
//        return false;
//    }
    
    // TODO check teleport accept packet
//    @Inject(
//        method = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handleAcceptTeleportPacket(Lnet/minecraft/network/protocol/game/ServerboundAcceptTeleportationPacket;)V",
//        at = @At("HEAD"),
//        cancellable = true
//    )
//    private void onOnTeleportConfirm(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
//        if (awaitingPositionFromClient == null) {
//            ci.cancel();
//        }
//    }
    
    // TODO refactor to avoid using this
    private static boolean shouldAcceptDubiousMovement(ServerPlayer player) {
        if (IPGlobal.serverTeleportationManager.isJustTeleported(player, 100)) {
            return true;
        }
        if (IPGlobal.looseMovementCheck) {
            return true;
        }
        if (((IEEntity) player).ip_isRecentlyCollidingWithPortal()) {
            return true;
        }
        boolean portalsNearby = IPMcHelper.getNearbyPortals(player, 16).findFirst().isPresent();
        return portalsNearby;
    }
    
}
