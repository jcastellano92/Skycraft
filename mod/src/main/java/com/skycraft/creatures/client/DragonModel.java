package com.skycraft.creatures.client;

import com.skycraft.creatures.entity.DragonEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Hand-written dragon: body with dorsal spikes, four-segment neck, horned head with a hinged jaw, two wings of two
 * segments each with membranes, a six-segment tail ending in a spade, and four two-part legs. Built at half size
 * (the renderer scales it by 2). Texture 256x128 (see tools/textures/creatures.py).
 */
public class DragonModel extends HierarchicalModel<DragonEntity> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart[] neck = new ModelPart[4];
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart wingRight;
    private final ModelPart wingRightTip;
    private final ModelPart wingLeft;
    private final ModelPart wingLeftTip;
    private final ModelPart[] tail = new ModelPart[6];
    private final ModelPart[] thighs = new ModelPart[4];
    private final ModelPart[] shins = new ModelPart[4];
    private static final String[] LEGS = {"leg_front_right", "leg_front_left", "leg_back_right", "leg_back_left"};

    public DragonModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        ModelPart n = body;
        for (int i = 0; i < 4; i++) {
            n = n.getChild("neck" + (i + 1));
            neck[i] = n;
        }
        this.head = neck[3].getChild("head");
        this.jaw = head.getChild("jaw");
        this.wingRight = body.getChild("wing_right");
        this.wingRightTip = wingRight.getChild("wing_right_tip");
        this.wingLeft = body.getChild("wing_left");
        this.wingLeftTip = wingLeft.getChild("wing_left_tip");
        ModelPart t = body;
        for (int i = 0; i < 6; i++) {
            t = t.getChild("tail" + (i + 1));
            tail[i] = t;
        }
        for (int i = 0; i < 4; i++) {
            thighs[i] = body.getChild(LEGS[i]);
            shins[i] = thighs[i].getChild(LEGS[i] + "_lower");
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder bodyCubes = CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, -6.0f, -14.0f, 14, 12, 28);
        for (int z : new int[]{-11, -5, 1, 7}) bodyCubes.texOffs(208, 0).addBox(-1.0f, -9.0f, z, 2, 3, 2);
        PartDefinition body = root.addOrReplaceChild("body", bodyCubes, PartPose.offset(0.0f, 10.0f, 0.0f));

        // neck: four segments chained forward from the chest
        PartDefinition parent = body;
        for (int i = 0; i < 4; i++) {
            PartPose pose = i == 0 ? PartPose.offset(0.0f, -2.0f, -13.0f) : PartPose.offset(0.0f, 0.0f, -6.0f);
            parent = parent.addOrReplaceChild("neck" + (i + 1), CubeListBuilder.create().texOffs(84, 0)
                    .addBox(-3.0f, -3.0f, -7.0f, 6, 6, 7)
                    .texOffs(208, 0).addBox(-1.0f, -5.0f, -5.0f, 2, 2, 2), pose);
        }
        PartDefinition head = parent.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(110, 0).addBox(-4.0f, -4.0f, -10.0f, 8, 6, 10)
                        .texOffs(146, 0).addBox(-3.0f, -2.0f, -18.0f, 6, 4, 8),
                PartPose.offset(0.0f, 0.0f, -6.0f));
        head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(174, 0).addBox(-1.0f, -1.0f, 0.0f, 2, 2, 8),
                PartPose.offsetAndRotation(-3.0f, -3.0f, -2.0f, 0.6f, -0.25f, 0.0f));
        head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(174, 0).mirror().addBox(-1.0f, -1.0f, 0.0f, 2, 2, 8),
                PartPose.offsetAndRotation(3.0f, -3.0f, -2.0f, 0.6f, 0.25f, 0.0f));
        head.addOrReplaceChild("horn_right_small", CubeListBuilder.create().texOffs(194, 0).addBox(-0.5f, -0.5f, 0.0f, 1, 1, 6),
                PartPose.offsetAndRotation(-3.5f, -1.0f, -4.0f, 0.3f, -0.5f, 0.0f));
        head.addOrReplaceChild("horn_left_small", CubeListBuilder.create().texOffs(194, 0).mirror().addBox(-0.5f, -0.5f, 0.0f, 1, 1, 6),
                PartPose.offsetAndRotation(3.5f, -1.0f, -4.0f, 0.3f, 0.5f, 0.0f));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(84, 16).addBox(-3.0f, 0.0f, -16.0f, 6, 2, 16),
                PartPose.offset(0.0f, 2.0f, -2.0f));

        // wings: arm + membrane, then finger + membrane
        PartDefinition wingRight = body.addOrReplaceChild("wing_right", CubeListBuilder.create()
                        .texOffs(128, 16).addBox(-20.0f, -1.0f, -2.0f, 20, 3, 4)
                        .texOffs(0, 40).addBox(-20.0f, 0.0f, 2.0f, 20, 0, 24),
                PartPose.offset(-6.0f, -4.0f, -8.0f));
        wingRight.addOrReplaceChild("wing_right_tip", CubeListBuilder.create()
                        .texOffs(128, 24).addBox(-22.0f, -1.0f, -1.0f, 22, 2, 2)
                        .texOffs(88, 40).addBox(-22.0f, 0.0f, 1.0f, 22, 0, 26),
                PartPose.offset(-20.0f, 0.0f, 0.0f));
        PartDefinition wingLeft = body.addOrReplaceChild("wing_left", CubeListBuilder.create().mirror()
                        .texOffs(128, 16).addBox(0.0f, -1.0f, -2.0f, 20, 3, 4)
                        .texOffs(0, 40).addBox(0.0f, 0.0f, 2.0f, 20, 0, 24),
                PartPose.offset(6.0f, -4.0f, -8.0f));
        wingLeft.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().mirror()
                        .texOffs(128, 24).addBox(0.0f, -1.0f, -1.0f, 22, 2, 2)
                        .texOffs(88, 40).addBox(0.0f, 0.0f, 1.0f, 22, 0, 26),
                PartPose.offset(20.0f, 0.0f, 0.0f));

        // tail: six tapering segments, a spade fin at the end
        float[][] seg = {{8, 6}, {7, 5}, {6, 4}, {5, 4}, {4, 3}, {3, 2}};
        int[] u = {0, 32, 62, 90, 116, 140};
        parent = body;
        for (int i = 0; i < 6; i++) {
            float w = seg[i][0];
            float h = seg[i][1];
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(u[i], 66).addBox(-w / 2, -h / 2, 0.0f, w, h, 8);
            if (i < 4) cubes.texOffs(208, 0).addBox(-1.0f, -h / 2 - 2, 3.0f, 2, 2, 2);
            if (i == 5) cubes.texOffs(162, 66).addBox(-4.0f, 0.0f, 3.0f, 8, 0, 6);
            PartPose pose = i == 0 ? PartPose.offset(0.0f, -2.0f, 13.0f) : PartPose.offset(0.0f, 0.0f, 7.5f);
            parent = parent.addOrReplaceChild("tail" + (i + 1), cubes, pose);
        }

        // legs: thigh + shin with claws
        float[][] legPos = {{-6, 4, -9}, {6, 4, -9}, {-6, 4, 9}, {6, 4, 9}};
        for (int i = 0; i < 4; i++) {
            boolean left = legPos[i][0] > 0;
            CubeListBuilder thigh = CubeListBuilder.create().mirror(left).texOffs(0, 80).addBox(-2.5f, -1.0f, -3.0f, 5, 7, 6);
            PartDefinition leg = body.addOrReplaceChild(LEGS[i], thigh, PartPose.offset(legPos[i][0], legPos[i][1], legPos[i][2]));
            leg.addOrReplaceChild(LEGS[i] + "_lower", CubeListBuilder.create().mirror(left)
                            .texOffs(22, 80).addBox(-2.0f, 0.0f, -2.0f, 4, 4, 4)
                            .texOffs(38, 80).addBox(-2.0f, 3.0f, -5.0f, 4, 1, 3),
                    PartPose.offset(0.0f, 6.0f, 0.0f));
        }
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(DragonEntity dragon, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean corpse = dragon.isCorpsePose();
        boolean dead = corpse || dragon.deathTime > 0;
        boolean flying = dragon.isFlying() && !dead;
        float t = ageInTicks;
        float yaw = Mth.clamp(netHeadYaw, -70f, 70f) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(headPitch, -40f, 40f) * Mth.DEG_TO_RAD;

        body.y = dead ? 16.0f : 10.0f;
        body.xRot = 0.0f;
        body.zRot = 0.0f;

        // ---- neck & head
        float[] neckPitch;
        float headX;
        if (flying) {
            neckPitch = new float[]{-0.15f, 0.04f, 0.04f, 0.04f};
            headX = 0.08f;
        } else if (dead) {
            neckPitch = new float[]{0.25f, 0.2f, 0.15f, 0.05f};
            headX = 0.15f;
            yaw = 0.35f;
            pitch = 0.0f;
        } else {
            float breathe = Mth.sin(t * 0.05f) * 0.04f;
            neckPitch = new float[]{-0.8f + breathe + pitch * 0.15f, -0.3f + pitch * 0.15f, 0.3f + pitch * 0.2f, 0.45f + pitch * 0.2f};
            headX = 0.35f + pitch * 0.3f - breathe;
        }
        for (int i = 0; i < 4; i++) {
            neck[i].xRot = neckPitch[i];
            neck[i].yRot = yaw * 0.2f;
        }
        head.xRot = headX;
        head.yRot = yaw * 0.2f;

        float open = dragon.isBreathing() ? 0.55f + Mth.sin(t * 0.8f) * 0.05f : 0.04f + Math.max(0.0f, Mth.sin(t * 0.04f)) * 0.06f;
        if (this.attackTime > 0.0f) open = Math.max(open, Mth.sin(this.attackTime * Mth.PI) * 0.75f);
        if (dead) open = 0.45f;
        jaw.xRot = open;

        // ---- wings
        if (flying) {
            float flap = Mth.sin(t * 0.3f);
            float glide = dragon.getXRot() > 15.0f ? 0.4f : 1.0f; // diving: wings half-tucked
            wingRight.xRot = 0.0f;
            wingRight.yRot = 0.0f;
            wingRight.zRot = 0.15f + flap * 0.6f * glide;
            wingRightTip.yRot = 0.0f;
            wingRightTip.zRot = Mth.sin(t * 0.3f - 0.8f) * 0.4f * glide;
        } else if (dead) {
            wingRight.xRot = 0.0f;
            wingRight.yRot = 0.35f;
            wingRight.zRot = -0.15f;
            wingRightTip.yRot = 0.1f;
            wingRightTip.zRot = 0.12f;
        } else {
            wingRight.xRot = 0.0f;
            wingRight.yRot = 0.75f;
            wingRight.zRot = 0.45f + Mth.sin(t * 0.05f) * 0.03f;
            wingRightTip.yRot = 0.5f;
            wingRightTip.zRot = -1.9f;
        }
        wingLeft.xRot = wingRight.xRot;
        wingLeft.yRot = -wingRight.yRot;
        wingLeft.zRot = -wingRight.zRot;
        wingLeftTip.yRot = -wingRightTip.yRot;
        wingLeftTip.zRot = -wingRightTip.zRot;

        // ---- tail
        for (int i = 0; i < 6; i++) {
            float sway = Mth.sin(t * 0.08f - i * 0.6f) * (flying ? 0.07f : 0.12f);
            tail[i].yRot = dead ? 0.15f : sway;
            if (flying) tail[i].xRot = Mth.sin(t * 0.3f - i * 0.5f) * 0.04f;
            else if (dead) tail[i].xRot = i == 0 ? -0.1f : 0.02f;
            else tail[i].xRot = switch (i) {
                case 0 -> -0.35f;
                case 1 -> -0.2f;
                case 2 -> 0.15f;
                case 3 -> 0.2f;
                default -> 0.1f;
            };
        }

        // ---- legs
        float swing = limbSwing * 0.5f;
        float amount = Math.min(1.0f, limbSwingAmount);
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            boolean right = i % 2 == 0;
            thighs[i].zRot = 0.0f;
            if (flying) {
                thighs[i].xRot = front ? 0.6f : 1.0f;
                shins[i].xRot = 0.6f;
            } else if (dead) {
                thighs[i].xRot = 0.0f;
                thighs[i].zRot = right ? 1.1f : -1.1f;
                shins[i].xRot = 0.0f;
            } else {
                float phase = (i == 0 || i == 3) ? 0.0f : Mth.PI;
                thighs[i].xRot = Mth.cos(swing + phase) * 0.7f * amount;
                shins[i].xRot = Math.max(0.0f, -thighs[i].xRot) * 0.6f;
            }
        }
    }
}
