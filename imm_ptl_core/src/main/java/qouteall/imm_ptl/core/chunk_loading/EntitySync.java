package qouteall.imm_ptl.core.chunk_loading;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IETrackedEntity;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.q_misc_util.dimension.DimensionEvents;
import qouteall.q_misc_util.my_util.LimitedLogger;

public class EntitySync {
    private static final LimitedLogger limitedLogger = new LimitedLogger(100);
    
    public static void init() {
        NeoForge.EVENT_BUS.addListener(DimensionEvents.BeforeRemovingDimensionEvent.class,
                beforeRemovingDimensionEvent -> EntitySync.forceRemoveDimension(beforeRemovingDimensionEvent.dimension));
    }
    
    /**
     * Replace {@link ChunkMap#tick()}
     * regarding the players in all dimensions
     */
    public static void update(MinecraftServer server) {
        server.getProfiler().push("ip_entity_tracking_update");
        
        for (ServerLevel world : server.getAllLevels()) {
            PacketRedirection.withForceRedirect(
                world,
                () -> {
                    ChunkMap chunkMap = world.getChunkSource().chunkMap;
                    Int2ObjectMap<ChunkMap.TrackedEntity> entityTrackerMap =
                        ((IEChunkMap) chunkMap).ip_getEntityTrackerMap();
                    DistanceManager distanceManager = chunkMap.getDistanceManager();
                    
                    for (ChunkMap.TrackedEntity trackedEntity : entityTrackerMap.values()) {
                        IETrackedEntity ieTrackedEntity = (IETrackedEntity) trackedEntity;
                        ieTrackedEntity.ip_updateEntityTrackingStatus();
                    }
                }
            );
        }
        
        server.getProfiler().pop();
    }
    
    public static void tick(MinecraftServer server) {
        server.getProfiler().push("ip_entity_tracking_tick");
        
        for (ServerLevel world : server.getAllLevels()) {
            PacketRedirection.withForceRedirect(
                world,
                () -> {
                    ChunkMap chunkMap = world.getChunkSource().chunkMap;
                    Int2ObjectMap<ChunkMap.TrackedEntity> entityTrackerMap =
                        ((IEChunkMap) chunkMap).ip_getEntityTrackerMap();
                    DistanceManager distanceManager = chunkMap.getDistanceManager();
                    
                    for (ChunkMap.TrackedEntity trackedEntity : entityTrackerMap.values()) {
                        IETrackedEntity ieTrackedEntity = (IETrackedEntity) trackedEntity;
                        
                        long chunkPos = ieTrackedEntity.ip_getEntity().chunkPosition().toLong();
                        if (distanceManager.inEntityTickingRange(chunkPos)) {
                            ieTrackedEntity.ip_sendChanges();
                        }
                    }
                }
            );
            
            
        }
        
        server.getProfiler().pop();
    }
    
    private static void forceRemoveDimension(ResourceKey<Level> dimension) {
    
    }
    
}
