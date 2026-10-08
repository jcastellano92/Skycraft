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

/** A giant rat: long body, pointed snout, round ears, two-segment tail, four short legs. Texture 64x32. */
public class SkeeverModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart legFrontRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legBackRight;
    private final ModelPart legBackLeft;

    public SkeeverModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.head = root.getChild("head");
        this.tail1 = body.getChild("tail1");
        this.tail2 = tail1.getChild("tail2");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legBackRight = root.getChild("leg_back_right");
        this.legBackLeft = root.getChild("leg_back_left");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -3.0f, -6.0f, 6, 6, 12),
                PartPose.offset(0.0f, 18.0f, 0.0f));
        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(36, 0).addBox(-2.5f, -2.5f, -5.0f, 5, 5, 5)
                        .texOffs(36, 10).addBox(-1.5f, -0.5f, -8.0f, 3, 3, 3)
                        .texOffs(48, 10).addBox(-3.0f, -4.5f, -2.0f, 2, 2, 1)
                        .texOffs(48, 10).mirror().addBox(1.0f, -4.5f, -2.0f, 2, 2, 1),
                PartPose.offset(0.0f, 17.0f, -6.0f));
        PartDefinition tail1 = body.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(0, 18).addBox(-1.0f, -1.0f, 0.0f, 2, 2, 8),
                PartPose.offsetAndRotation(0.0f, -1.0f, 6.0f, 0.25f, 0.0f, 0.0f));
        tail1.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(20, 18).addBox(-0.5f, -0.5f, 0.0f, 1, 1, 8),
                PartPose.offsetAndRotation(0.0f, 0.0f, 7.5f, -0.45f, 0.0f, 0.0f));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0f, 0.0f, -1.0f, 2, 4, 2),
                PartPose.offset(-2.0f, 20.0f, -4.0f));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0f, 0.0f, -1.0f, 2, 4, 2),
                PartPose.offset(2.0f, 20.0f, -4.0f));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0f, 0.0f, -1.0f, 2, 4, 2),
                PartPose.offset(-2.0f, 20.0f, 4.0f));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0f, 0.0f, -1.0f, 2, 4, 2),
                PartPose.offset(2.0f, 20.0f, 4.0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD + Mth.sin(ageInTicks * 0.3f) * 0.03f; // sniffing
        float swing = limbSwing * 1.1f;
        float amount = Math.min(1.0f, limbSwingAmount) * 1.3f;
        this.legFrontRight.xRot = Mth.cos(swing) * amount;
        this.legBackLeft.xRot = Mth.cos(swing) * amount;
        this.legFrontLeft.xRot = Mth.cos(swing + Mth.PI) * amount;
        this.legBackRight.xRot = Mth.cos(swing + Mth.PI) * amount;
        this.tail1.yRot = Mth.sin(ageInTicks * 0.15f) * 0.25f + Mth.cos(swing) * amount * 0.25f;
        this.tail2.yRot = Mth.sin(ageInTicks * 0.15f - 1.0f) * 0.35f;
    }
}
