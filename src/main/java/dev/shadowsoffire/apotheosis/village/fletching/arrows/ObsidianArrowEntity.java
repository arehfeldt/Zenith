package dev.shadowsoffire.apotheosis.village.fletching.arrows;

import dev.shadowsoffire.apotheosis.village.VillageModule;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class ObsidianArrowEntity extends AbstractArrow {

    public ObsidianArrowEntity(EntityType<? extends AbstractArrow> t, Level world) {
        super(t, world);
    }

    public ObsidianArrowEntity(Level world) {
        super(VillageModule.OBSIDIAN_ARROW_ENTITY, world);
    }

    public ObsidianArrowEntity(LivingEntity shooter, Level world) {
        super(VillageModule.OBSIDIAN_ARROW_ENTITY, shooter, world);
    }

    public ObsidianArrowEntity(Level world, double x, double y, double z) {
        super(VillageModule.OBSIDIAN_ARROW_ENTITY, x, y, z, world);
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(VillageModule.OBSIDIAN_ARROW);
    }

    @Override
    protected void onHitEntity(EntityHitResult res) {
        double base = this.getBaseDamage();
        this.setBaseDamage(base * 1.2F);
        super.onHitEntity(res);
        this.setBaseDamage(base);
    }

}
