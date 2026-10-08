package com.skycraft.fauna.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Horker: a walrus. Huge blubbery body lying on the ground, whiskered snout with two tusks, four flippers. 128x64. */
public class HorkerModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart flipperLeft;
    private final ModelPart flipperRight;
    private final ModelPart rearLeft;
    private final ModelPart rearRight;

    public HorkerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.flipperLeft = root.getChild("flipper_left");
        this.flipperRight = root.getChild("flipper_right");
        this.rearLeft = root.getChild("rear_left");
        this.rearRight = root.getChild("rear_right");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, -6.0f, -10.0f, 14, 12, 22),
                PartPose.offset(0.0f, 18.0f, 0.0f));
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(72, 0).addBox(-5.0f, -5.0f, -7.0f, 10, 9, 8)     // head and thick neck
                        .texOffs(72, 17).addBox(-4.0f, -1.0f, -10.0f, 8, 5, 3)    // whiskered snout
                        .texOffs(94, 17).addBox(-3.0f, 3.0f, -9.0f, 1, 6, 1)      // tusks
                        .texOffs(94, 17).mirror().addBox(2.0f, 3.0f, -9.0f, 1, 6, 1),
                PartPose.offset(0.0f, 15.0f, -10.0f));
        root.addOrReplaceChild("flipper_left", CubeListBuilder.create().texOffs(0, 34).addBox(0.0f, 0.0f, -2.0f, 6, 2, 4),
                PartPose.offset(6.0f, 22.0f, -6.0f));
        root.addOrReplaceChild("flipper_right", CubeListBuilder.create().texOffs(0, 34).mirror().addBox(-6.0f, 0.0f, -2.0f, 6, 2, 4),
                PartPose.offset(-6.0f, 22.0f, -6.0f));
        root.addOrReplaceChild("rear_left", CubeListBuilder.create().texOffs(20, 34).addBox(-2.0f, 0.0f, 0.0f, 4, 2, 6),
                PartPose.offsetAndRotation(3.0f, 22.0f, 11.0f, 0.0f, 0.3f, 0.0f));
        root.addOrReplaceChild("rear_right", CubeListBuilder.create().texOffs(20, 34).mirror().addBox(-2.0f, 0.0f, 0.0f, 4, 2, 6),
                PartPose.offsetAndRotation(-3.0f, 22.0f, 11.0f, 0.0f, -0.3f, 0.0f));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6f;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5f;
        float amount = Math.min(1.0f, limbSwingAmount);
        float hump = Mth.sin(limbSwing * 0.6f) * amount;
        // galumphing: the body heaves while the flippers row
        this.body.y = 18.0f - Math.abs(hump) * 1.5f + Mth.sin(ageInTicks * 0.05f) * 0.3f; // breathing
        this.flipperLeft.yRot = -0.2f + hump * 0.6f;
        this.flipperRight.yRot = 0.2f - hump * 0.6f;
        this.rearLeft.yRot = 0.3f + Mth.cos(limbSwing * 0.6f) * amount * 0.4f;
        this.rearRight.yRot = -0.3f - Mth.cos(limbSwing * 0.6f) * amount * 0.4f;
        if (this.attackTime > 0) this.head.xRot += Mth.sin(this.attackTime * Mth.PI) * 0.8f; // tusk stab
    }
}
