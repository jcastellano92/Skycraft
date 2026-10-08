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

/** Bear: heavy body with a shoulder hump, broad head and muzzle, thick legs; swipes with a forepaw. Texture 64x64. */
public class BearModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart legFrontRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legBackRight;
    private final ModelPart legBackLeft;

    public BearModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legBackRight = root.getChild("leg_back_right");
        this.legBackLeft = root.getChild("leg_back_left");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0f, -5.0f, -9.0f, 10, 10, 18)
                        .texOffs(0, 28).addBox(-4.0f, -7.0f, -8.0f, 8, 3, 7),     // shoulder hump
                PartPose.offset(0.0f, 10.0f, 1.0f));
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(30, 28).addBox(-3.5f, -3.5f, -5.0f, 7, 7, 5)     // skull
                        .texOffs(0, 38).addBox(-2.0f, 0.0f, -8.0f, 4, 3, 3)       // muzzle
                        .texOffs(14, 38).addBox(-4.0f, -5.0f, -2.0f, 2, 2, 1)     // ears
                        .texOffs(14, 38).mirror().addBox(2.0f, -5.0f, -2.0f, 2, 2, 1),
                PartPose.offset(0.0f, 9.0f, -8.0f));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(32, 44).addBox(-1.0f, 0.0f, 0.0f, 2, 2, 2),
                PartPose.offset(0.0f, 7.0f, 9.5f));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(0, 44).addBox(-2.0f, 0.0f, -2.0f, 4, 9, 4),
                PartPose.offset(-3.0f, 15.0f, -5.0f));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(0, 44).mirror().addBox(-2.0f, 0.0f, -2.0f, 4, 9, 4),
                PartPose.offset(3.0f, 15.0f, -5.0f));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(16, 44).addBox(-2.0f, 0.0f, -2.0f, 4, 9, 4),
                PartPose.offset(-3.0f, 15.0f, 7.0f));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(16, 44).mirror().addBox(-2.0f, 0.0f, -2.0f, 4, 9, 4),
                PartPose.offset(3.0f, 15.0f, 7.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        float swing = limbSwing * 0.7f;
        float amount = Math.min(1.0f, limbSwingAmount) * 1.1f;
        this.legFrontRight.xRot = Mth.cos(swing) * amount;
        this.legBackLeft.xRot = Mth.cos(swing) * amount;
        this.legFrontLeft.xRot = Mth.cos(swing + Mth.PI) * amount;
        this.legBackRight.xRot = Mth.cos(swing + Mth.PI) * amount;
        if (this.attackTime > 0) {
            // forepaw swipe
            float t = Mth.sin(this.attackTime * Mth.PI);
            this.legFrontRight.xRot = -1.6f * t;
            this.legFrontRight.zRot = 0.3f * t;
            this.head.xRot -= 0.3f * t;
        } else {
            this.legFrontRight.zRot = 0.0f;
        }
    }
}
