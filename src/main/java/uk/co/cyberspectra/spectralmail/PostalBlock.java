package uk.co.cyberspectra.spectralmail;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Directional physical postal block with model-matched collision and non-occluding rendering. */
public final class PostalBlock extends HorizontalDirectionalBlock {
    public enum Kind { DROP_BOX, LETTER_BOX }

    private static final VoxelShape DROP_NORTH = Shapes.m_83124_(
            Block.m_49796_(2, 0, 2, 14, 14, 14),
            Block.m_49796_(1, 14, 1, 15, 16, 15),
            Block.m_49796_(3, 8, 1, 13, 12, 2));
    private static final VoxelShape DROP_SOUTH = Shapes.m_83124_(
            Block.m_49796_(2, 0, 2, 14, 14, 14),
            Block.m_49796_(1, 14, 1, 15, 16, 15),
            Block.m_49796_(3, 8, 14, 13, 12, 15));
    private static final VoxelShape DROP_WEST = Shapes.m_83124_(
            Block.m_49796_(2, 0, 2, 14, 14, 14),
            Block.m_49796_(1, 14, 1, 15, 16, 15),
            Block.m_49796_(1, 8, 3, 2, 12, 13));
    private static final VoxelShape DROP_EAST = Shapes.m_83124_(
            Block.m_49796_(2, 0, 2, 14, 14, 14),
            Block.m_49796_(1, 14, 1, 15, 16, 15),
            Block.m_49796_(14, 8, 3, 15, 12, 13));

    private static final VoxelShape LETTER_NORTH = Shapes.m_83124_(
            Block.m_49796_(2, 0, 3, 14, 2, 13),
            Block.m_49796_(1, 2, 2, 15, 13, 14),
            Block.m_49796_(0, 13, 1, 16, 15, 15),
            Block.m_49796_(3, 5, 1, 13, 12, 2));
    private static final VoxelShape LETTER_SOUTH = Shapes.m_83124_(
            Block.m_49796_(2, 0, 3, 14, 2, 13),
            Block.m_49796_(1, 2, 2, 15, 13, 14),
            Block.m_49796_(0, 13, 1, 16, 15, 15),
            Block.m_49796_(3, 5, 14, 13, 12, 15));
    private static final VoxelShape LETTER_WEST = Shapes.m_83124_(
            Block.m_49796_(3, 0, 2, 13, 2, 14),
            Block.m_49796_(2, 2, 1, 14, 13, 15),
            Block.m_49796_(1, 13, 0, 15, 15, 16),
            Block.m_49796_(1, 5, 3, 2, 12, 13));
    private static final VoxelShape LETTER_EAST = Shapes.m_83124_(
            Block.m_49796_(3, 0, 2, 13, 2, 14),
            Block.m_49796_(2, 2, 1, 14, 13, 15),
            Block.m_49796_(1, 13, 0, 15, 15, 16),
            Block.m_49796_(14, 5, 3, 15, 12, 13));

    private final Kind kind;

    public PostalBlock(Kind kind) {
        super(makeProperties(kind));
        this.kind = kind;
    }

    /** Production/SRG name of Block#createBlockStateDefinition in Minecraft 1.20.1. */
    @Override
    protected void m_7926_(StateDefinition.Builder<Block, BlockState> builder) {
        builder.m_61104_(HorizontalDirectionalBlock.f_54117_);
    }

    /** Production/SRG name of Block#getStateForPlacement in Minecraft 1.20.1. */
    @Override
    public BlockState m_5573_(BlockPlaceContext context) {
        Direction facing = context.m_8125_().m_122424_();
        return (BlockState) this.m_49966_().m_61124_(HorizontalDirectionalBlock.f_54117_, facing);
    }

    /** Production/SRG name of BlockBehaviour#getShape in Minecraft 1.20.1. */
    @Override
    public VoxelShape m_5940_(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    /** Production/SRG name of BlockBehaviour#getCollisionShape in Minecraft 1.20.1. */
    @Override
    public VoxelShape m_5939_(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    private VoxelShape shapeFor(BlockState state) {
        Direction direction = (Direction) state.m_61143_(HorizontalDirectionalBlock.f_54117_);
        int ordinal = direction == null ? 2 : direction.ordinal();
        if (kind == Kind.DROP_BOX) {
            return switch (ordinal) {
                case 3 -> DROP_SOUTH;
                case 4 -> DROP_WEST;
                case 5 -> DROP_EAST;
                default -> DROP_NORTH;
            };
        }
        return switch (ordinal) {
            case 3 -> LETTER_SOUTH;
            case 4 -> LETTER_WEST;
            case 5 -> LETTER_EAST;
            default -> LETTER_NORTH;
        };
    }

    private static BlockBehaviour.Properties makeProperties(Kind kind) {
        BlockBehaviour.Properties properties = freshProperties();
        properties = properties.m_60955_(); // noOcclusion: render the ground beneath inset models
        if (kind == Kind.DROP_BOX) {
            return properties.m_60913_(3.0F, 6.0F).m_60918_(SoundType.f_56743_);
        }
        return properties.m_60913_(2.0F, 3.0F).m_60918_(SoundType.f_56736_);
    }

    private static BlockBehaviour.Properties freshProperties() {
        for (Method method : BlockBehaviour.Properties.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 0
                    && method.getReturnType() == BlockBehaviour.Properties.class) {
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(null);
                    if (value instanceof BlockBehaviour.Properties properties) return properties;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        throw new IllegalStateException("Could not create Minecraft block properties for Spectral Mail");
    }
}
