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

/** Mudcrab: flat shell with a ridged top, eye stalks, two big claws and six splayed legs. Texture 64x32. */
public class MudcrabModel<T extends Entity> extends HierarchicalModel<T> {
    private static final float[] LEG_Z = {-1.5f, 0.5f, 2.5f};
    private final ModelPart root;
    private final ModelPart clawLeft;
    private final ModelPart clawRight;
    private final ModelPart[] legsLeft = new ModelPart[3];
    private final ModelPart[] legsRight = new ModelPart[3];

    public MudcrabModel(ModelPart root) {
        this.root = root;
        this.clawLeft = root.getChild("claw_left");
        this.clawRight = root.getChild("claw_right");
        for (int i = 0; i < 3; i++) {
            legsLeft[i] = root.getChild("leg_left_" + i);
            legsRight[i] = root.getChild("leg_right_" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0f, -3.0f, -4.0f, 10, 4, 8)      // shell
                        .texOffs(0, 12).addBox(-4.0f, -4.0f, -3.0f, 8, 1, 6)      // ridged top
                        .texOffs(36, 0).addBox(-2.5f, -6.0f, -4.0f, 1, 3, 1)      // eye stalks
                        .texOffs(36, 0).mirror().addBox(1.5f, -6.0f, -4.0f, 1, 3, 1),
                PartPose.offset(0.0f, 20.0f, 0.0f));
        root.addOrReplaceChild("claw_left", CubeListBuilder.create()
                        .texOffs(28, 12).addBox(0.0f, -1.5f, -4.0f, 3, 3, 4)
                        .texOffs(42, 0).addBox(0.5f, -1.0f, -7.0f, 2, 2, 3),
                PartPose.offsetAndRotation(4.0f, 20.0f, -3.5f, 0.0f, 0.35f, 0.0f));
        root.addOrReplaceChild("claw_right", CubeListBuilder.create().mirror()
                        .texOffs(28, 12).addBox(-3.0f, -1.5f, -4.0f, 3, 3, 4)
                        .texOffs(42, 0).addBox(-2.5f, -1.0f, -7.0f, 2, 2, 3),
                PartPose.offsetAndRotation(-4.0f, 20.0f, -3.5f, 0.0f, -0.35f, 0.0f));
        for (int i = 0; i < 3; i++) {
            root.addOrReplaceChild("leg_left_" + i, CubeListBuilder.create().texOffs(0, 19).addBox(0.0f, -0.5f, -0.5f, 6, 1, 1),
                    PartPose.offsetAndRotation(4.5f, 20.0f, LEG_Z[i], 0.0f, 0.0f, 0.6f));
            root.addOrReplaceChild("leg_right_" + i, CubeListBuilder.create().texOffs(0, 19).mirror().addBox(-6.0f, -0.5f, -0.5f, 6, 1, 1),
                    PartPose.offsetAndRotation(-4.5f, 20.0f, LEG_Z[i], 0.0f, 0.0f, -0.6f));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float amount = Math.min(1.0f, limbSwingAmount);
        for (int i = 0; i < 3; i++) {
            float phase = limbSwing * 1.6f + i * 2.1f;
            this.legsLeft[i].yRot = Mth.cos(phase) * 0.5f * amount;
            this.legsLeft[i].zRot = 0.6f + Math.max(0f, Mth.sin(phase)) * 0.4f * amount;
            this.legsRight[i].yRot = Mth.cos(phase + Mth.PI) * 0.5f * amount;
            this.legsRight[i].zRot = -0.6f - Math.max(0f, Mth.sin(phase + Mth.PI)) * 0.4f * amount;
        }
        // claws snap idly, and hard when attacking
        float snap = Mth.sin(ageInTicks * 0.15f) * 0.08f;
        if (this.attackTime > 0) snap = Mth.sin(this.attackTime * Mth.PI) * 0.9f;
        this.clawLeft.xRot = -snap;
        this.clawRight.xRot = -snap;
        this.clawLeft.yRot = 0.35f;
        this.clawRight.yRot = -0.35f;
    }
}
