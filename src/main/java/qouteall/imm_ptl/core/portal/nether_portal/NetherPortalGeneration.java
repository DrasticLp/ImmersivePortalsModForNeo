package qouteall.imm_ptl.core.portal.nether_portal;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.LimitedLogger;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class NetherPortalGeneration {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    public static IntBox findAirCubePlacement(
            ServerLevel toWorld,
            BlockPos mappedPosInOtherDimension,
            Direction.Axis axis,
            BlockPos neededAreaSize,
            boolean allowForcePlacement
    ) {
        BlockPos randomShift = new BlockPos(
                toWorld.getRandom().nextBoolean() ? 1 : -1,
                0,
                toWorld.getRandom().nextBoolean() ? 1 : -1
        );

        IntBox foundAirCube =
                axis == Direction.Axis.Y ?
                        NetherPortalMatcher.findHorizontalPortalPlacement(
                                neededAreaSize, toWorld, mappedPosInOtherDimension.offset(randomShift)
                        ) :
                        NetherPortalMatcher.findVerticalPortalPlacement(
                                neededAreaSize, toWorld, mappedPosInOtherDimension.offset(randomShift)
                        );

        if (foundAirCube == null) {
            LOGGER.info("Cannot find normal portal placement");
            foundAirCube = NetherPortalMatcher.findCubeAirAreaAtAnywhere(
                    neededAreaSize, toWorld, mappedPosInOtherDimension, 32
            );

            if (foundAirCube != null) {
                if (isFloating(toWorld, foundAirCube)) {
                    foundAirCube = NetherPortalMatcher.levitateBox(toWorld, foundAirCube, 50);
                }
            }
        }

        if (foundAirCube == null) {
            if (allowForcePlacement) {
                Helper.err("Cannot find air cube within 32 blocks? " +
                        "Force placed portal. It will occupy normal blocks.");

                return IntBox.fromBasePointAndSize(mappedPosInOtherDimension, neededAreaSize);
            } else {
                return null;
            }
        }
        return foundAirCube;
    }

    private static boolean isFloating(ServerLevel toWorld, IntBox foundAirCube) {
        return foundAirCube.getSurfaceLayer(Direction.DOWN).stream().noneMatch(
                blockPos -> toWorld.getBlockState(blockPos.below()).isSolid()
        );
    }

    public static void setPortalContentBlock(
            ServerLevel world,
            BlockPos pos,
            Direction.Axis normalAxis
    ) {
        world.setBlockAndUpdate(
                pos,
                PortalPlaceholderBlock.instance.defaultBlockState().setValue(
                        PortalPlaceholderBlock.AXIS, normalAxis
                )
        );
    }

    public static void startGeneratingPortal(
            ServerLevel fromWorld, ServerLevel toWorld,
            BlockPortalShape fromShape,
            BlockPos toPos,
            int existingFrameSearchingRadius,
            Predicate<BlockState> otherSideFramePredicate,
            Consumer<BlockPortalShape> newFrameGenerateFunc,
            Consumer<PortalGenInfo> portalEntityGeneratingFunc,
            //return null for not generate new frame
            Supplier<PortalGenInfo> newFramePlacer,
            BooleanSupplier portalIntegrityChecker,

            //currying
            FrameSearching.FrameSearchingFunc<PortalGenInfo> matchShapeByFramePos
    ) {
        ResourceKey<Level> fromDimension = fromWorld.dimension();
        ResourceKey<Level> toDimension = toWorld.dimension();

        MinecraftServer server = fromWorld.getServer();

        Vec3 indicatorPos = fromShape.innerAreaBox.getCenterVec();

        LoadingIndicatorEntity indicatorEntity =
                LoadingIndicatorEntity.entityType.create(fromWorld);
        indicatorEntity.isValid = true;
        indicatorEntity.setPos(
                indicatorPos.x, indicatorPos.y, indicatorPos.z
        );
        indicatorEntity.setBox(fromShape.innerAreaBox);
        fromWorld.addFreshEntity(indicatorEntity);

        Runnable onGenerateNewFrame = () -> {
            indicatorEntity.inform(Component.translatable(
                    "imm_ptl.generating_new_frame"
            ));

            PortalGenInfo info = newFramePlacer.get();

            if (info != null) {
                newFrameGenerateFunc.accept(info.toShape);

                portalEntityGeneratingFunc.accept(info);

                O_O.postPortalSpawnEventForge(info);
            }
        };

        boolean otherSideChunkAlreadyGenerated = McHelper.getDoesRegionFileExist(toDimension, toPos);

        int frameSearchingRadius = Math.floorDiv(existingFrameSearchingRadius, 16) + 1;

        /**
         * if the other side chunk is already generated, generate 128 range for searching the frame
         * if the other side chunk is not yet generated, generate 1 or 2 chunk range for searching the frame placing position
         * when generating chunks by getBlockState, subsequent setBlockState may leave lighting issues
         * {@link net.minecraft.server.world.ServerLightingProvider#light(Chunk, boolean)}
         *  may get invoked twice for a chunk.
         * Maybe related to https://bugs.mojang.com/browse/MC-170010
         * Rough experiments shows that the lighting issue won't possibly manifest when manipulating blocks
         *  after the chunk has been fully generated.
         */
        int loaderRadius = otherSideChunkAlreadyGenerated ?
                frameSearchingRadius :
                (fromShape.getShapeInnerLength() < 16 ? 1 : 2);
        ChunkLoader chunkLoader = new ChunkLoader(
                new DimensionalChunkPos(toDimension, new ChunkPos(toPos)), loaderRadius
        );

        ImmPtlChunkTracking.addGlobalAdditionalChunkLoader(server, chunkLoader);

        Runnable finalizer = () -> {
            indicatorEntity.remove(Entity.RemovalReason.KILLED);
            ImmPtlChunkTracking.removeGlobalAdditionalChunkLoader(server, chunkLoader);
        };

        ServerTaskList.of(server).addTask(() -> {

            boolean isPortalIntact = portalIntegrityChecker.getAsBoolean();

            if (!isPortalIntact) {
                finalizer.run();
                return true;
            }

            int loadedChunks = chunkLoader.getLoadedChunkNum(server);
            int allChunksNeedsLoading = chunkLoader.getChunkNum();
            if (loadedChunks < allChunksNeedsLoading) {
                indicatorEntity.inform(Component.translatable(
                        "imm_ptl.loading_chunks", loadedChunks, allChunksNeedsLoading
                ));
                return false;
            }

            if (!otherSideChunkAlreadyGenerated) {
                onGenerateNewFrame.run();
                finalizer.run();
                return true;
            }

            ChunkLoader chunkLoader1 = new ChunkLoader(
                    chunkLoader.getCenter(), frameSearchingRadius
            );
            ServerLevel world = McHelper.getServerWorld(server, chunkLoader1.dimension());

            FastBlockAccess chunkRegion = chunkLoader1.createFastBlockAccess(world);

            indicatorEntity.inform(Component.translatable("imm_ptl.searching_for_frame"));

            BlockPos.MutableBlockPos temp1 = new BlockPos.MutableBlockPos();

            FrameSearching.startSearchingPortalFrameAsync(
                    chunkRegion, frameSearchingRadius,
                    toPos, otherSideFramePredicate,
                    matchShapeByFramePos,
                    (info) -> {
                        portalEntityGeneratingFunc.accept(info);
                        finalizer.run();

                        O_O.postPortalSpawnEventForge(info);
                    },
                    () -> {
                        onGenerateNewFrame.run();
                        finalizer.run();
                    });

            return true;
        });
    }

    public static boolean isOtherGenerationRunning(ServerLevel fromWorld, Vec3 indicatorPos) {

        boolean isOtherGenerationRunning = McHelper.getEntitiesNearby(
                fromWorld, indicatorPos, LoadingIndicatorEntity.class, 1
        ).stream().findAny().isPresent();
        if (isOtherGenerationRunning) {
            Helper.log(
                    "Aborted Portal Generation Because Another Generation is Running Nearby"
            );
            return true;
        }
        return false;
    }

    private static final LimitedLogger limitedLogger = new LimitedLogger(50);

    public static boolean checkPortalGeneration(ServerLevel fromWorld, BlockPos startingPos) {
        if (!fromWorld.hasChunkAt(startingPos)) {
            Helper.log("Cancel Portal Generation Because Chunk Not Loaded");
            return false;
        }

        limitedLogger.log(String.format("Portal Generation Attempted %s %s %s %s",
                fromWorld.dimension().location(), startingPos.getX(), startingPos.getY(), startingPos.getZ()
        ));
        return true;
    }

    public static BlockPortalShape findFrameShape(
            ServerLevel fromWorld, BlockPos startingPos,
            Predicate<BlockState> thisSideAreaPredicate,
            Predicate<BlockState> thisSideFramePredicate
    ) {
        return Arrays.stream(Direction.Axis.values())
                .map(
                        axis -> {
                            return BlockPortalShape.findShapeWithoutRegardingStartingPos(
                                    startingPos,
                                    axis,
                                    (pos) -> thisSideAreaPredicate.test(fromWorld.getBlockState(pos)),
                                    (pos) -> thisSideFramePredicate.test(fromWorld.getBlockState(pos))
                            );
                        }
                ).filter(
                        Objects::nonNull
                ).findFirst().orElse(null);
    }

    public static void embodyNewFrame(
            ServerLevel toWorld,
            BlockPortalShape toShape,
            BlockState frameBlockState
    ) {
        toShape.frameAreaWithCorner.forEach(blockPos ->
                toWorld.setBlockAndUpdate(blockPos, frameBlockState)
        );
    }

    public static void fillInPlaceHolderBlocks(
            ServerLevel world,
            BlockPortalShape blockPortalShape
    ) {
        blockPortalShape.area.forEach(
                blockPos -> setPortalContentBlock(
                        world, blockPos, blockPortalShape.axis
                )
        );
    }


}
