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

/** Slaughterfish: deep-bodied predatory fish with an underbite full of teeth, spiny dorsal fin, forked tail. 64x32. */
public class SlaughterfishModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart jaw;
    private final ModelPart tail;
    private final ModelPart finLeft;
    private final ModelPart finRight;

    public SlaughterfishModel(ModelPart root) {
        this.root = root;
        this.jaw = root.getChild("head").getChild("jaw");
        this.tail = root.getChild("tail");
        this.finLeft = root.getChild("fin_left");
        this.finRight = root.getChild("fin_right");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -2.5f, -4.0f, 3, 5, 8),
                PartPose.offset(0.0f, 21.5f, 0.0f));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(22, 0).addBox(-1.5f, -2.5f, -4.0f, 3, 3, 4),
                PartPose.offset(0.0f, 21.5f, -4.0f));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5f, 0.0f, -4.0f, 3, 2, 4),
                PartPose.offset(0.0f, 0.5f, 0.0f));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 13).addBox(0.0f, -2.5f, 0.0f, 0, 5, 5),
                PartPose.offset(0.0f, 21.5f, 4.0f));
        root.addOrReplaceChild("dorsal", CubeListBuilder.create().texOffs(10, 13).addBox(0.0f, -2.0f, -3.0f, 0, 2, 6),
                PartPose.offset(0.0f, 19.0f, 0.0f));
        root.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(22, 13).addBox(0.0f, 0.0f, 0.0f, 3, 0, 2),
                PartPose.offsetAndRotation(1.5f, 23.0f, -2.0f, 0.0f, 0.0f, 0.4f));
        root.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(22, 13).mirror().addBox(-3.0f, 0.0f, 0.0f, 3, 0, 2),
                PartPose.offsetAndRotation(-1.5f, 23.0f, -2.0f, 0.0f, 0.0f, -0.4f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float speed = entity.isInWater() ? 1.0f : 1.6f;
        this.tail.yRot = -speed * 0.45f * Mth.sin(0.6f * ageInTicks * speed);
        this.finLeft.zRot = 0.4f + Mth.sin(ageInTicks * 0.3f) * 0.2f;
        this.finRight.zRot = -0.4f - Mth.sin(ageInTicks * 0.3f) * 0.2f;
        // the jaw gnashes, wide open when biting
        float gnash = 0.15f + Mth.sin(ageInTicks * 0.25f) * 0.1f;
        if (this.attackTime > 0) gnash = 0.2f + Mth.sin(this.attackTime * Mth.PI) * 0.7f;
        this.jaw.xRot = gnash;
    }
}
