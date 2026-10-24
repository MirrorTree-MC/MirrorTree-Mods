package top.bearcabbage.mirrortree.mixin;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
 @Accessor("chunkTypeCache") Long2ByteMap mirrortree$getChunkTypeCache();
}
