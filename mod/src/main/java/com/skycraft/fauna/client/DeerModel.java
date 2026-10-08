package com.skycraft.fauna.client;

import com.skycraft.fauna.entity.DeerEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Deer and elk: slender body, long neck carrying the head, big ears, short tail, long legs. Males carry antlers
 * (branching beams; the elk's are much larger). Texture 64x64; UVs mirrored in {@code tools/textures/fauna.py}.
 */
public class DeerModel<T extends DeerEntity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart antlerLeft;
    private final ModelPart antlerRight;
    private final ModelPart tail;
    private final ModelPart legFrontRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legBackRight;
    private final ModelPart legBackLeft;

    public DeerModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.antlerLeft = head.getChild("antler_left");
        this.antlerRight = head.getChild("antler_right");
        this.tail = root.getChild("tail");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legBackRight = root.getChild("leg_back_right");
        this.legBackLeft = root.getChild("leg_back_left");
    }

    public static LayerDefinition createLayer(boolean elk) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -4.0f, -8.0f, 7, 8, 16),
                PartPose.offset(0.0f, 9.0f, 0.0f));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(46, 0).addBox(-1.5f, -9.0f, -2.0f, 3, 10, 4)       // neck
                        .texOffs(0, 24).addBox(-2.5f, -13.0f, -4.0f, 5, 5, 6)       // skull
                        .texOffs(22, 24).addBox(-1.5f, -12.0f, -8.0f, 3, 3, 4)      // snout
                        .texOffs(36, 24).addBox(-4.5f, -14.0f, -1.0f, 2, 3, 1)      // ears
                        .texOffs(36, 24).mirror().addBox(2.5f, -14.0f, -1.0f, 2, 3, 1),
                PartPose.offset(0.0f, 7.0f, -6.5f));
        float tilt = elk ? 0.5f : 0.35f;
        head.addOrReplaceChild("antler_left", antlers(elk, false), PartPose.offsetAndRotation(1.5f, -13.0f, -1.0f, 0.0f, 0.0f, tilt));
        head.addOrReplaceChild("antler_right", antlers(elk, true), PartPose.offsetAndRotation(-1.5f, -13.0f, -1.0f, 0.0f, 0.0f, -tilt));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(56, 24).addBox(-1.0f, 0.0f, 0.0f, 2, 3, 1),
                PartPose.offsetAndRotation(0.0f, 5.5f, 8.0f, 0.5f, 0.0f, 0.0f));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(0, 36).addBox(-1.0f, 0.0f, -1.0f, 2, 12, 2),
                PartPose.offset(-2.0f, 12.0f, -6.0f));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(0, 36).mirror().addBox(-1.0f, 0.0f, -1.0f, 2, 12, 2),
                PartPose.offset(2.0f, 12.0f, -6.0f));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(8, 36).addBox(-1.5f, 0.0f, -1.5f, 3, 12, 3),
                PartPose.offset(-2.0f, 12.0f, 6.0f));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(8, 36).mirror().addBox(-1.5f, 0.0f, -1.5f, 3, 12, 3),
                PartPose.offset(2.0f, 12.0f, 6.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One antler: a main beam with forward tines (all UVs inside the bone-colored block at 24..48 x 36..48). */
    private static CubeListBuilder antlers(boolean elk, boolean mirror) {
        CubeListBuilder b = CubeListBuilder.create().mirror(mirror);
        if (elk) {
            b.texOffs(24, 36).addBox(-0.5f, -9.0f, -0.5f, 1, 9, 1)
                    .texOffs(32, 36).addBox(-0.5f, -2.0f, -3.5f, 1, 1, 3)
                    .texOffs(32, 40).addBox(-0.5f, -5.0f, -3.0f, 1, 1, 3)
                    .texOffs(32, 44).addBox(-0.5f, -8.0f, -2.5f, 1, 1, 2)
                    .texOffs(40, 36).addBox(-0.5f, -10.0f, -0.5f, 1, 1, 3)
                    .texOffs(40, 40).addBox(mirror ? -2.5f : 0.5f, -7.0f, -0.5f, 2, 1, 1);
        } else {
            b.texOffs(24, 36).addBox(-0.5f, -6.0f, -0.5f, 1, 6, 1)
                    .texOffs(32, 36).addBox(-0.5f, -3.0f, -2.5f, 1, 1, 2)
                    .texOffs(32, 40).addBox(-0.5f, -6.0f, -2.5f, 1, 1, 2);
        }
        return b;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD + 0.1f;
        boolean antlers = entity.isMale();
        this.antlerLeft.visible = antlers;
        this.antlerRight.visible = antlers;
        float swing = limbSwing * 0.9f;
        float amount = Math.min(1.0f, limbSwingAmount) * 1.2f;
        this.legFrontRight.xRot = Mth.cos(swing) * amount;
        this.legBackLeft.xRot = Mth.cos(swing) * amount;
        this.legFrontLeft.xRot = Mth.cos(swing + Mth.PI) * amount;
        this.legBackRight.xRot = Mth.cos(swing + Mth.PI) * amount;
        this.tail.xRot = 0.5f + Mth.sin(ageInTicks * 0.2f) * 0.12f + amount * 0.3f; // flagging tail when running
    }
}
