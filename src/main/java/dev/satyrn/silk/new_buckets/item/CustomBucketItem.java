package dev.satyrn.silk.new_buckets.item;

import dev.satyrn.silk.new_buckets.NewBucketsMod;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidDrainable;
import net.minecraft.block.FluidFillable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * A custom bucket handler for the buckets implemented by this mod.
 * @author Isabel Maskrey
 * @since 1.0.0
 */
public abstract class CustomBucketItem extends BucketItem implements DisableRepairItem {
    private final Fluid fluid;

    /**
     * Initializes a new breakable bucket.
     * @param fluid The fluid which this bucket contains.
     * @param settings The item initialization settings.
     * @since 1.0.0
     */
    protected CustomBucketItem(Fluid fluid, Settings settings) {
        super(fluid, settings);
        this.fluid = fluid;
    }

    /**
     * Tries to get the filled item for this block.
     * @param fluid The fluid with which to attempt to fill the bucket.
     * @return The filled item type. Returns {@code Items.AIR} if no item exists.
     * @since 1.0.0
     */
    protected Item getFilledItem(Fluid fluid) {
        return Items.AIR;
    }

    /**
     * Gets the emptied item for this bucket.
     * @return The emptied item for this bucket
     * @since 1.0.0
     */
    protected abstract Item getEmptyItem();

    /**
     * Plays the fill sound for the bucket.
     * @param user The player who used the bucket
     * @param fluid The fluid which was filled.
     * @since 1.0.0
     */
    protected void playFillSound(PlayerEntity user, Fluid fluid) {
        user.playSound(fluid.isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL, 1.0F, 1.0F);
    }

    /**
     * Uses the item.
     * @param world The world in which the item was used.
     * @param user The player which used the item.
     * @param hand The hand in which the item was held.
     * @return The result of the action.
     * @since 1.0.0
     */
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        BlockHitResult hitResult = raycast(world, user, this.fluid == Fluids.EMPTY ? RaycastContext.FluidHandling.SOURCE_ONLY : RaycastContext.FluidHandling.NONE);
        if (hitResult.getType() == HitResult.Type.MISS) {
            return TypedActionResult.pass(itemStack);
        } else if (hitResult.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(itemStack);
        } else {
            BlockPos blockPos = hitResult.getBlockPos();
            Direction direction = hitResult.getSide();
            BlockPos blockPos2 = blockPos.offset(direction);
            if (world.canPlayerModifyAt(user, blockPos) && user.canPlaceOn(blockPos2, direction, itemStack)) {
                BlockState blockState;
                if (this.fluid == Fluids.EMPTY) {
                    blockState = world.getBlockState(blockPos);
                    if (blockState.getBlock() instanceof FluidDrainable) {
                        Fluid fluid = blockState.getFluidState().getFluid();
                        Item filledItem = this.getFilledItem(fluid);
                        if (filledItem != Items.AIR) {
                            fluid = ((FluidDrainable) blockState.getBlock()).tryDrainFluid(world, blockPos, blockState);
                            if (fluid != Fluids.EMPTY) {
                                user.incrementStat(Stats.USED.getOrCreateStat(this));
                                this.playFillSound(user, fluid);
                                ItemStack filledBucketStack = ItemUsage.method_30012(itemStack, user, new ItemStack(filledItem));
                                if (!user.isCreative()) {
                                    filledBucketStack.setDamage(itemStack.getDamage());
                                    NewBucketsMod.copyEnchantmentItemTags(itemStack, filledBucketStack);
                                }
                                if (!world.isClient) {
                                    Criteria.FILLED_BUCKET.trigger((ServerPlayerEntity) user, new ItemStack(fluid.getBucketItem()));
                                }

                                return TypedActionResult.success(filledBucketStack, world.isClient());
                            }
                        }
                    }

                    return TypedActionResult.fail(itemStack);
                } else {
                    blockState = world.getBlockState(blockPos);
                    BlockPos blockPos3 = blockState.getBlock() instanceof FluidFillable && this.fluid == Fluids.WATER ? blockPos : blockPos2;
                    if (this.placeFluid(user, world, blockPos3, hitResult)) {
                        this.onEmptied(world, itemStack, blockPos3);
                        if (user instanceof ServerPlayerEntity) {
                            Criteria.PLACED_BLOCK.trigger((ServerPlayerEntity)user, blockPos3, itemStack);
                        }
                        user.incrementStat(Stats.USED.getOrCreateStat(this));
                        itemStack.damage(this.getDamageForFluid(this.fluid), user, (playerEntity) -> playerEntity.sendToolBreakStatus(hand));
                        return TypedActionResult.success(this.getEmptiedStack(itemStack, user), world.isClient());
                    } else {
                        return TypedActionResult.fail(itemStack);
                    }
                }
            } else {
                return TypedActionResult.fail(itemStack);
            }
        }
    }

    /**
     * Gets the emptied item stack for this bucket.
     * @param sourceStack The current item stack.
     * @param player The player which used the item.
     * @return The emptied item stack.
     * @since 1.0.0
     */
    @Override
    protected ItemStack getEmptiedStack(ItemStack sourceStack, PlayerEntity player) {
        if (player.abilities.creativeMode || sourceStack.isEmpty()) {
            return sourceStack;
        }

        ItemStack targetStack = new ItemStack(this.getEmptyItem());
        targetStack.setDamage(sourceStack.getDamage());
        NewBucketsMod.copyEnchantmentItemTags(sourceStack, targetStack);

        return targetStack;
    }

    /**
     * Gets the damage that the bucket will accrue when dispensing a specific liquid.
     * @param fluid The fluid being dispensed
     * @return The amount of damage that the stack should take.
     * @since 1.0.0
     */
    protected int getDamageForFluid(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return 0;
        }
        if (fluid.isIn(FluidTags.LAVA)) {
            return 2;
        }
        return 1;
    }

    /**
     * Checks if the item can be repaired by another stack.
     * @param stack The item to repair.
     * @param ingredient The ingredient to repair with.
     * @return {@code false} if there is any fluid in the bucket; otherwise, calls super implementation.
     * @since 1.0.0
     */
    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        if (this.fluid != Fluids.EMPTY) {
            return false;
        }
        return super.canRepair(stack, ingredient);
    }

    /**
     * Disables crafting repair for items which contain a fluid.
     * @param itemStack The item stack.
     * @return {@code true} if the bucket is empty and can be repaired in the crafting grid; otherwise, {@code false}.
     * @since 1.0.0
     */
    public boolean canRepairViaCrafting(ItemStack itemStack) {
        return this.fluid == Fluids.EMPTY;
    }
}
