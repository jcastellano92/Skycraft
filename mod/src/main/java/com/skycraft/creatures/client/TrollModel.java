package com.skycraft.creatures.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** A hunched, long-armed ape-like troll with a three-eyed head. Texture 128x64. */
public class TrollModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public TrollModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0f, -22.0f, -6.0f, 18, 22, 12),
                PartPose.offsetAndRotation(0.0f, 8.0f, 3.0f, 0.35f, 0.0f, 0.0f));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(60, 0).addBox(-5.0f, -8.0f, -7.0f, 10, 10, 8),
                PartPose.offset(0.0f, -7.0f, -8.0f));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(60, 18).addBox(-4.0f, -2.0f, -3.0f, 6, 26, 6),
                PartPose.offset(-11.0f, -7.0f, -3.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 18).mirror().addBox(-2.0f, -2.0f, -3.0f, 6, 26, 6),
                PartPose.offset(11.0f, -7.0f, -3.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(84, 18).addBox(-3.0f, 0.0f, -3.0f, 6, 16, 6),
                PartPose.offset(-5.0f, 8.0f, 4.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(84, 18).mirror().addBox(-3.0f, 0.0f, -3.0f, 6, 16, 6),
                PartPose.offset(5.0f, 8.0f, 4.0f));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        float swing = limbSwing * 0.6662f;
        float amount = Math.min(1.0f, limbSwingAmount);
        this.rightLeg.xRot = Mth.cos(swing) * 1.1f * amount;
        this.leftLeg.xRot = Mth.cos(swing + Mth.PI) * 1.1f * amount;
        // knuckle-walking arms swing opposite to the legs
        this.rightArm.xRot = -0.2f + Mth.cos(swing + Mth.PI) * 0.9f * amount;
        this.leftArm.xRot = -0.2f + Mth.cos(swing) * 0.9f * amount;
        this.rightArm.zRot = 0.12f + Mth.sin(ageInTicks * 0.067f) * 0.04f;
        this.leftArm.zRot = -0.12f - Mth.sin(ageInTicks * 0.067f) * 0.04f;
        this.body.xRot = 0.35f + Mth.sin(ageInTicks * 0.05f) * 0.02f;
        if (this.attackTime > 0.0f) {
            // two-handed overhead smash
            float smash = Mth.sin(this.attackTime * Mth.PI);
            this.rightArm.xRot -= smash * 2.4f;
            this.leftArm.xRot -= smash * 2.4f;
            this.body.xRot += smash * 0.25f;
        }
    }
}
