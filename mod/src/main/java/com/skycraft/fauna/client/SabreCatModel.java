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

/** Sabre cat: long feline body, broad head with two long sabre fangs, round ears, two-part tail. Texture 64x64. */
public class SabreCatModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart legFrontRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legBackRight;
    private final ModelPart legBackLeft;

    public SabreCatModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.tail1 = root.getChild("tail1");
        this.tail2 = tail1.getChild("tail2");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legBackRight = root.getChild("leg_back_right");
        this.legBackLeft = root.getChild("leg_back_left");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -4.0f, -9.0f, 8, 8, 18),
                PartPose.offset(0.0f, 11.0f, 1.0f));
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 26).addBox(-3.5f, -3.5f, -5.0f, 7, 6, 6)       // skull
                        .texOffs(26, 26).addBox(-2.0f, -0.5f, -8.0f, 4, 3, 3)      // muzzle
                        .texOffs(26, 32).addBox(-1.5f, 2.5f, -7.0f, 3, 1, 3)       // jaw
                        .texOffs(46, 26).addBox(-1.5f, 2.5f, -7.5f, 1, 4, 1)       // sabre fangs
                        .texOffs(46, 26).addBox(0.5f, 2.5f, -7.5f, 1, 4, 1)
                        .texOffs(40, 26).addBox(-3.5f, -5.0f, -2.0f, 2, 2, 1)      // ears
                        .texOffs(40, 26).mirror().addBox(1.5f, -5.0f, -2.0f, 2, 2, 1),
                PartPose.offset(0.0f, 9.0f, -8.0f));
        PartDefinition tail1 = root.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 38).addBox(-1.0f, 0.0f, 0.0f, 2, 2, 8),
                PartPose.offsetAndRotation(0.0f, 8.0f, 9.5f, -0.6f, 0.0f, 0.0f));
        tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(20, 38).addBox(-1.0f, 0.0f, 0.0f, 2, 2, 7),
                PartPose.offsetAndRotation(0.0f, 0.0f, 7.5f, 0.4f, 0.0f, 0.0f));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(0, 48).addBox(-1.5f, 0.0f, -1.5f, 3, 9, 3),
                PartPose.offset(-2.5f, 15.0f, -5.0f));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(0, 48).mirror().addBox(-1.5f, 0.0f, -1.5f, 3, 9, 3),
                PartPose.offset(2.5f, 15.0f, -5.0f));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(12, 48).addBox(-1.5f, 0.0f, -1.5f, 3, 9, 3),
                PartPose.offset(-2.5f, 15.0f, 8.0f));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(12, 48).mirror().addBox(-1.5f, 0.0f, -1.5f, 3, 9, 3),
                PartPose.offset(2.5f, 15.0f, 8.0f));
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
        float swing = limbSwing * 0.8f;
        float amount = Math.min(1.0f, limbSwingAmount) * 1.3f;
        // bounding gallop: front and back pairs move together
        this.legFrontRight.xRot = Mth.cos(swing) * amount;
        this.legFrontLeft.xRot = Mth.cos(swing + 0.4f) * amount;
        this.legBackRight.xRot = Mth.cos(swing + Mth.PI) * amount;
        this.legBackLeft.xRot = Mth.cos(swing + Mth.PI + 0.4f) * amount;
        this.body.xRot = Mth.sin(swing) * amount * 0.06f;
        this.tail1.xRot = -0.6f + Mth.cos(swing) * amount * 0.3f;
        this.tail1.yRot = Mth.sin(ageInTicks * 0.08f) * 0.2f;
        this.tail2.yRot = Mth.sin(ageInTicks * 0.08f - 1.0f) * 0.3f;
        // crouch before the pounce / in the air
        if (!entity.onGround()) {
            this.legFrontRight.xRot = this.legFrontLeft.xRot = -0.9f;
            this.legBackRight.xRot = this.legBackLeft.xRot = 0.9f;
        }
    }
}
