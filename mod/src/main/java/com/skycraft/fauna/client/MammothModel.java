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

/**
 * Woolly mammoth: shaggy body with a shoulder hump, domed head, flapping ears, a three-segment trunk and two long
 * curving tusks, pillar legs. Built at half size and rendered at 2x. Texture 128x128.
 */
public class MammothModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart trunk1;
    private final ModelPart trunk2;
    private final ModelPart trunk3;
    private final ModelPart tail;
    private final ModelPart legFrontRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legBackRight;
    private final ModelPart legBackLeft;

    public MammothModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.trunk1 = head.getChild("trunk1");
        this.trunk2 = trunk1.getChild("trunk2");
        this.trunk3 = trunk2.getChild("trunk3");
        this.tail = root.getChild("tail");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legBackRight = root.getChild("leg_back_right");
        this.legBackLeft = root.getChild("leg_back_left");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8.0f, -8.0f, -12.0f, 16, 16, 26)
                        .texOffs(0, 42).addBox(-6.0f, -11.0f, -10.0f, 12, 3, 12),   // shoulder hump
                PartPose.offset(0.0f, 6.0f, 0.0f));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(48, 42).addBox(-5.0f, -6.0f, -7.0f, 10, 11, 8)
                        .texOffs(84, 0).addBox(-9.0f, -4.0f, -3.0f, 4, 7, 1)       // ears
                        .texOffs(84, 0).mirror().addBox(5.0f, -4.0f, -3.0f, 4, 7, 1),
                PartPose.offset(0.0f, 2.0f, -12.0f));
        PartDefinition trunk1 = head.addOrReplaceChild("trunk1", CubeListBuilder.create().texOffs(84, 8).addBox(-2.0f, 0.0f, -2.0f, 4, 6, 4),
                PartPose.offsetAndRotation(0.0f, 4.0f, -5.5f, 0.15f, 0.0f, 0.0f));
        PartDefinition trunk2 = trunk1.addOrReplaceChild("trunk2", CubeListBuilder.create().texOffs(100, 8).addBox(-1.5f, 0.0f, -1.5f, 3, 6, 3),
                PartPose.offsetAndRotation(0.0f, 5.5f, 0.0f, 0.1f, 0.0f, 0.0f));
        trunk2.addOrReplaceChild("trunk3", CubeListBuilder.create().texOffs(112, 8).addBox(-1.0f, 0.0f, -1.0f, 2, 5, 2),
                PartPose.offsetAndRotation(0.0f, 5.5f, 0.0f, -0.3f, 0.0f, 0.0f));
        tusk(head, "tusk_left", 3.5f, false);
        tusk(head, "tusk_right", -3.5f, true);
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 61).addBox(-0.5f, 0.0f, 0.0f, 1, 6, 1),
                PartPose.offsetAndRotation(0.0f, 0.0f, 13.5f, 0.3f, 0.0f, 0.0f));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(0, 57).addBox(-3.0f, 0.0f, -3.0f, 6, 11, 6),
                PartPose.offset(-4.5f, 13.0f, -8.0f));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(0, 57).mirror().addBox(-3.0f, 0.0f, -3.0f, 6, 11, 6),
                PartPose.offset(4.5f, 13.0f, -8.0f));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(24, 57).addBox(-3.0f, 0.0f, -3.0f, 6, 11, 6),
                PartPose.offset(-4.5f, 13.0f, 9.0f));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(24, 57).mirror().addBox(-3.0f, 0.0f, -3.0f, 6, 11, 6),
                PartPose.offset(4.5f, 13.0f, 9.0f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** A tusk in two segments: down and forward, then sweeping up. */
    private static void tusk(PartDefinition head, String name, float x, boolean mirror) {
        float out = mirror ? 0.18f : -0.18f;
        PartDefinition seg1 = head.addOrReplaceChild(name, CubeListBuilder.create().mirror(mirror).texOffs(84, 18).addBox(-1.0f, 0.0f, -1.0f, 2, 8, 2),
                PartPose.offsetAndRotation(x, 3.0f, -5.0f, -0.5f, 0.0f, out));
        seg1.addOrReplaceChild(name + "_tip", CubeListBuilder.create().mirror(mirror).texOffs(92, 18).addBox(-1.0f, 0.0f, -1.0f, 2, 7, 2),
                PartPose.offsetAndRotation(0.0f, 7.5f, 0.0f, -1.3f, 0.0f, 0.0f));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5f;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5f;
        float swing = limbSwing * 0.5f;
        float amount = Math.min(1.0f, limbSwingAmount) * 0.8f;
        this.legFrontRight.xRot = Mth.cos(swing) * amount;
        this.legBackLeft.xRot = Mth.cos(swing) * amount;
        this.legFrontLeft.xRot = Mth.cos(swing + Mth.PI) * amount;
        this.legBackRight.xRot = Mth.cos(swing + Mth.PI) * amount;
        // the trunk sways
        float sway = Mth.sin(ageInTicks * 0.06f);
        this.trunk1.xRot = 0.15f + sway * 0.06f;
        this.trunk1.zRot = Mth.cos(ageInTicks * 0.045f) * 0.08f;
        this.trunk2.xRot = 0.1f + sway * 0.1f;
        this.trunk3.xRot = -0.3f + sway * 0.18f;
        this.tail.zRot = Mth.sin(ageInTicks * 0.1f) * 0.2f;
        if (this.attackTime > 0) {
            // tusk sweep: head down then up
            this.head.xRot += Mth.sin(this.attackTime * Mth.PI) * 0.7f;
            this.trunk3.xRot -= 0.8f * Mth.sin(this.attackTime * Mth.PI);
        }
    }
}
