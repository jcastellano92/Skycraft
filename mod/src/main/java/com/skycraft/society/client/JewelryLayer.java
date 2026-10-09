package com.skycraft.society.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skycraft.Skycraft;
import com.skycraft.core.SkyData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 3D Render Layer for Jewelry (Circlets, Necklaces, Amulets, Pendants, Rings) and Layered Clothing.
 * Renders gleaming jewelry and authentic layered garments over or under armor.
 */
public class JewelryLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = new ResourceLocation(Skycraft.MODID, "textures/entity/society/jewelry.png");

    private final ModelPart circlet;
    private final ModelPart torso;
    private final ModelPart necklaceChain;
    private final ModelPart medallion;
    private final ModelPart clothingTrim;
    private final ModelPart ringRight;
    private final ModelPart ringLeft;

    public JewelryLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(SocietyClient.JEWELRY_LAYER);
        this.circlet = root.getChild("circlet");
        this.torso = root.getChild("torso");
        this.necklaceChain = torso.getChild("necklace_chain");
        this.medallion = torso.getChild("medallion");
        this.clothingTrim = torso.getChild("clothing_trim");
        this.ringRight = root.getChild("ring_right");
        this.ringLeft = root.getChild("ring_left");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 1. Circlet: sits on forehead
        root.addOrReplaceChild("circlet", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.2f, -6.5f, -4.2f, 8.4f, 1.6f, 8.4f, new CubeDeformation(0.35f))
                .texOffs(14, 0).addBox(-0.75f, -7.0f, -4.6f, 1.5f, 1.5f, 0.8f), PartPose.ZERO);

        // 2. Torso with necklace and layered clothing trim
        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create(), PartPose.ZERO);
        torso.addOrReplaceChild("necklace_chain", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.1f, 0.0f, -2.2f, 8.2f, 4.0f, 4.4f, new CubeDeformation(0.2f)), PartPose.ZERO);
        torso.addOrReplaceChild("medallion", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-1.0f, 3.0f, -2.45f, 2.0f, 2.5f, 0.5f), PartPose.ZERO);
        torso.addOrReplaceChild("clothing_trim", CubeListBuilder.create().texOffs(0, 40)
                .addBox(-4.05f, 0.0f, -2.05f, 8.1f, 12.0f, 4.1f, new CubeDeformation(0.12f)), PartPose.ZERO);

        // 3. Rings on hands/wrists
        root.addOrReplaceChild("ring_right", CubeListBuilder.create().texOffs(36, 0)
                .addBox(-3.1f, 8.0f, -2.1f, 4.2f, 1.4f, 4.2f, new CubeDeformation(0.15f)), PartPose.ZERO);
        root.addOrReplaceChild("ring_left", CubeListBuilder.create().texOffs(36, 10)
                .addBox(-1.1f, 8.0f, -2.1f, 4.2f, 1.4f, 4.2f, new CubeDeformation(0.15f)), PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static boolean matchKeyword(ItemStack stack, String keyword) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().contains(keyword);
    }

    private static boolean isCirclet(ItemStack stack) {
        return matchKeyword(stack, "circlet");
    }

    private static boolean isNecklace(ItemStack stack) {
        return matchKeyword(stack, "necklace") || matchKeyword(stack, "amulet") || matchKeyword(stack, "pendant");
    }

    private static boolean isRing(ItemStack stack) {
        return matchKeyword(stack, "ring");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;

        ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack offhandStack = player.getItemBySlot(EquipmentSlot.OFFHAND);
        ItemStack mainhandStack = player.getItemBySlot(EquipmentSlot.MAINHAND);

        CompoundTag apparel = SkyData.get(player).module("apparel");
        String appCirclet = apparel.getString("circlet");
        String appNecklace = apparel.getString("necklace");
        String appRing = apparel.getString("ring");

        boolean hasCirclet = isCirclet(headStack) || !appCirclet.isEmpty();
        boolean hasNecklace = isNecklace(chestStack) || isNecklace(offhandStack) || isNecklace(mainhandStack) || !appNecklace.isEmpty();
        boolean hasRing = isRing(offhandStack) || isRing(mainhandStack) || !appRing.isEmpty();
        boolean hasClothing = true; // Layered clothing trim under/over armor

        // Also check player inventory if not in explicit slots
        if (!hasCirclet || !hasNecklace || !hasRing) {
            for (ItemStack invStack : player.getInventory().items) {
                if (invStack.isEmpty()) continue;
                if (!hasCirclet && isCirclet(invStack)) hasCirclet = true;
                if (!hasNecklace && isNecklace(invStack)) hasNecklace = true;
                if (!hasRing && isRing(invStack)) hasRing = true;
            }
        }

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        circlet.copyFrom(model.head);
        torso.copyFrom(model.body);
        ringRight.copyFrom(model.rightArm);
        ringLeft.copyFrom(model.leftArm);

        circlet.visible = hasCirclet;
        necklaceChain.visible = hasNecklace;
        medallion.visible = hasNecklace;
        clothingTrim.visible = hasClothing;
        ringRight.visible = hasRing;
        ringLeft.visible = hasRing;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = OverlayTexture.NO_OVERLAY;

        if (hasCirclet) {
            circlet.render(pose, vc, packedLight, overlay);
        }

        if (hasNecklace || hasClothing) {
            torso.render(pose, vc, packedLight, overlay);
        }

        if (hasRing) {
            ringRight.render(pose, vc, packedLight, overlay);
            ringLeft.render(pose, vc, packedLight, overlay);
        }
    }
}

