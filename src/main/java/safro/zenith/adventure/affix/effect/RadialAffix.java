package safro.zenith.adventure.affix.effect;

import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.fabricators_of_create.porting_lib.event.common.BlockEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import safro.zenith.adventure.affix.Affix;
import safro.zenith.adventure.affix.AffixHelper;
import safro.zenith.adventure.affix.AffixInstance;
import safro.zenith.adventure.affix.AffixType;
import safro.zenith.adventure.loot.LootCategory;
import safro.zenith.adventure.loot.LootRarity;
import safro.zenith.api.placebo.json.PSerializer;

import java.util.*;
import java.util.function.Consumer;

public class RadialAffix extends Affix {

	//Formatter::off
	public static final Codec<RadialAffix> CODEC = RecordCodecBuilder.create(inst -> inst
		.group(
			LootRarity.mapCodec(Codec.list(RadialData.CODEC)).fieldOf("values").forGetter(a -> a.values))
			.apply(inst, RadialAffix::new)
		);
	//Formatter::on
	public static final PSerializer<RadialAffix> SERIALIZER = PSerializer.fromCodec("Radial Affix", CODEC);

	private static Set<UUID> breakers = new HashSet<>();

	protected final Map<LootRarity, List<RadialData>> values;

	public RadialAffix(Map<LootRarity, List<RadialData>> values) {
		super(AffixType.ABILITY);
		this.values = values;
	}

	@Override
	public boolean canApplyTo(ItemStack stack, LootRarity rarity) {
		return LootCategory.forItem(stack).isBreaker() && this.values.containsKey(rarity);
	}

	@Override
	public void addInformation(ItemStack stack, LootRarity rarity, float level, Consumer<Component> list) {
		RadialData data = this.getTrueLevel(rarity, level);
		list.accept(Component.translatable("affix." + this.getId() + ".desc", data.x, data.y).withStyle(ChatFormatting.YELLOW));
	}

	// EventPriority.LOW
	public void onBreak(BlockEvents.BreakEvent e) {
		Player player = e.getPlayer();
		ItemStack tool = player.getMainHandItem();
		Level world = player.level;
		if (!world.isClientSide && tool.hasTag()) {
			AffixInstance inst = AffixHelper.getAffixes(tool).get(this);
			if (inst != null) {
				float hardness = e.getState().getDestroySpeed(e.getWorld(), e.getPos());
				breakExtraBlocks((ServerPlayer) player, e.getPos(), tool, getTrueLevel(inst.rarity(), inst.level()), hardness);
			}
		}
	}

	private RadialData getTrueLevel(LootRarity rarity, float level) {
		var list = this.values.get(rarity);
		return list.get(Math.min(list.size() - 1, (int) Mth.lerp(level, 0, list.size())));
	}

	@Override
	public PSerializer<? extends Affix> getSerializer() {
		return SERIALIZER;
	}

	static record RadialData(int x, int y, int xOff, int yOff) {
		//Formatter::off
		public static Codec<RadialData> CODEC = RecordCodecBuilder.create(inst -> inst
			.group(
				Codec.INT.fieldOf("x").forGetter(RadialData::x),
				Codec.INT.fieldOf("y").forGetter(RadialData::y),
				Codec.INT.fieldOf("xOff").forGetter(RadialData::xOff),
				Codec.INT.fieldOf("yOff").forGetter(RadialData::yOff))
				.apply(inst, RadialData::new)
			);
		//Formatter::on
	}

	/**
	 * Performs the actual extra breaking of blocks
	 * @param player The player breaking the block
	 * @param pos The position of the originally broken block
	 * @param tool The tool being used (which has this affix on it)
	 * @param level The level of this affix, in this case, the mode of operation.
	 */
	public static void breakExtraBlocks(ServerPlayer player, BlockPos pos, ItemStack tool, RadialData level, float hardness) {
		if (!breakers.add(player.getUUID())) return; //Prevent multiple break operations from cascading, and don't execute when sneaking.ew
		if (!player.isShiftKeyDown()) try {
			breakBlockRadius(player, pos, level.x, level.y, level.xOff, level.yOff, hardness);
		} catch (Exception e) {
			e.printStackTrace();
		}
		breakers.remove(player.getUUID());
	}

	@SuppressWarnings("deprecation")
	public static void breakBlockRadius(ServerPlayer player, BlockPos pos, int x, int y, int xOff, int yOff, float hardness) {
		Level world = player.level;
		if (x < 2 && y < 2) return;
		int lowerY = (int) Math.ceil(-y / 2D), upperY = (int) Math.round(y / 2D);
		int lowerX = (int) Math.ceil(-x / 2D), upperX = (int) Math.round(x / 2D);

		Vec3 base = player.getEyePosition(0);
		Vec3 look = player.getLookAngle();
		double reach = ReachEntityAttributes.getReachDistance(player, ReachEntityAttributes.REACH.getDefaultValue());
		Vec3 target = base.add(look.x * reach, look.y * reach, look.z * reach);
		HitResult trace = world.clip(new ClipContext(base, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

		if (trace == null || trace.getType() != Type.BLOCK) return;
		BlockHitResult res = (BlockHitResult) trace;

		Direction face = res.getDirection(); //Face of the block currently being looked at by the player.

		for (int iy = lowerY; iy < upperY; iy++) {
			for (int ix = lowerX; ix < upperX; ix++) {
				BlockPos genPos = new BlockPos(pos.getX() + ix + xOff, pos.getY() + iy + yOff, pos.getZ());

				if (player.getDirection().getAxis() == Axis.X) {
					genPos = new BlockPos(genPos.getX() - (ix + xOff), genPos.getY(), genPos.getZ() + ix + xOff);
				}

				if (face.getAxis().isVertical()) {
					genPos = rotateDown(genPos, iy + yOff, player.getDirection());
				}

				if (genPos.equals(pos)) continue;
				BlockState state = world.getBlockState(genPos);
				float stateHardness = state.getDestroySpeed(world, genPos);
		//		if (!state.isAir() && stateHardness != -1 && stateHardness <= hardness * 3F && isEffective(state, player)) PlaceboUtil.tryHarvestBlock(player, genPos); TODO placeboutil
			}
		}

	}

	static BlockPos rotateDown(BlockPos pos, int y, Direction horizontal) {
		Vec3i vec = horizontal.getNormal();
		return new BlockPos(pos.getX() + vec.getX() * y, pos.getY() - y, pos.getZ() + vec.getZ() * y);
	}

	static boolean isEffective(BlockState state, Player player) {
		return player.hasCorrectToolForDrops(state);
	}

}