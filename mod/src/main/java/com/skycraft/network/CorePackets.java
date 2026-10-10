package com.skycraft.network;

import com.skycraft.client.ClientPacketHandlers;
import com.skycraft.core.PlayerData;
import com.skycraft.core.Race;
import com.skycraft.core.SkyData;
import com.skycraft.perk.Perk;
import com.skycraft.perk.Perks;
import com.skycraft.skills.Progression;
import com.skycraft.vitals.ActionHandler;
import com.skycraft.vitals.Vitals;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Packets owned by the core (player data sync, notifications, level-up choices, perks, race, key actions). */
public final class CorePackets {
    private CorePackets() {}

    public static void register() {
        SkyNetwork.register(SyncData.class, NetworkDirection.PLAY_TO_CLIENT, SyncData::encode, SyncData::decode, SyncData::handle);
        SkyNetwork.register(SyncVitals.class, NetworkDirection.PLAY_TO_CLIENT, SyncVitals::encode, SyncVitals::decode, SyncVitals::handle);
        SkyNetwork.register(Notify.class, NetworkDirection.PLAY_TO_CLIENT, Notify::encode, Notify::decode, Notify::handle);
        SkyNetwork.register(OpenScreen.class, NetworkDirection.PLAY_TO_CLIENT, OpenScreen::encode, OpenScreen::decode, OpenScreen::handle);
        SkyNetwork.register(ChooseAttribute.class, NetworkDirection.PLAY_TO_SERVER, ChooseAttribute::encode, ChooseAttribute::decode, ChooseAttribute::handle);
        SkyNetwork.register(UnlockPerk.class, NetworkDirection.PLAY_TO_SERVER, UnlockPerk::encode, UnlockPerk::decode, UnlockPerk::handle);
        SkyNetwork.register(ChooseRace.class, NetworkDirection.PLAY_TO_SERVER, ChooseRace::encode, ChooseRace::decode, ChooseRace::handle);
        SkyNetwork.register(Action.class, NetworkDirection.PLAY_TO_SERVER, Action::encode, Action::decode, Action::handle);
        SkyNetwork.register(HarvestBlock.class, NetworkDirection.PLAY_TO_SERVER, HarvestBlock::encode, HarvestBlock::decode, HarvestBlock::handle);
    }

    // ------------------------------------------------------------------ S2C

    /** Full player data sync. */
    public record SyncData(CompoundTag tag) {
        static void encode(SyncData m, FriendlyByteBuf buf) {
            buf.writeNbt(m.tag);
        }

        static SyncData decode(FriendlyByteBuf buf) {
            return new SyncData(buf.readNbt());
        }

        static void handle(SyncData m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.syncData(m.tag));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Frequent, small update of the current magicka/stamina pools and stealth/combat flags. */
    public record SyncVitals(float magicka, float stamina, byte flags) {
        public static final byte DETECTED = 1;
        public static final byte IN_COMBAT = 2;
        public static final byte SNEAK_HIDDEN = 4;
        public static final byte EXHAUSTED = 8;

        static void encode(SyncVitals m, FriendlyByteBuf buf) {
            buf.writeFloat(m.magicka);
            buf.writeFloat(m.stamina);
            buf.writeByte(m.flags);
        }

        static SyncVitals decode(FriendlyByteBuf buf) {
            return new SyncVitals(buf.readFloat(), buf.readFloat(), buf.readByte());
        }

        static void handle(SyncVitals m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.syncVitals(m.magicka, m.stamina, m.flags));
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * A HUD notification. {@code kind} is a {@link NotifyKind}; {@code value} carries a skill ordinal for
     * skill popups and {@code progress} the 0..1 bar fill.
     */
    public record Notify(NotifyKind kind, Component title, Component subtitle, int value, float progress) {
        static void encode(Notify m, FriendlyByteBuf buf) {
            buf.writeEnum(m.kind);
            buf.writeComponent(m.title);
            buf.writeComponent(m.subtitle);
            buf.writeVarInt(m.value);
            buf.writeFloat(m.progress);
        }

        static Notify decode(FriendlyByteBuf buf) {
            return new Notify(buf.readEnum(NotifyKind.class), buf.readComponent(), buf.readComponent(), buf.readVarInt(), buf.readFloat());
        }

        static void handle(Notify m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.notify(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Asks the client to open one of the core screens. */
    public record OpenScreen(int screen) {
        public static final int RACE = 0;
        public static final int LEVEL_UP = 1;
        public static final int SKILLS = 2;

        static void encode(OpenScreen m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.screen);
        }

        static OpenScreen decode(FriendlyByteBuf buf) {
            return new OpenScreen(buf.readVarInt());
        }

        static void handle(OpenScreen m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.openScreen(m.screen));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C2S

    /** Level-up choice: 0 health, 1 magicka, 2 stamina. */
    public record ChooseAttribute(int which) {
        static void encode(ChooseAttribute m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.which);
        }

        static ChooseAttribute decode(FriendlyByteBuf buf) {
            return new ChooseAttribute(buf.readVarInt());
        }

        static void handle(ChooseAttribute m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) Progression.applyLevelUpChoice(player, m.which);
            ctx.get().setPacketHandled(true);
        }
    }

    public record UnlockPerk(String perkId) {
        static void encode(UnlockPerk m, FriendlyByteBuf buf) {
            buf.writeUtf(m.perkId, 128);
        }

        static UnlockPerk decode(FriendlyByteBuf buf) {
            return new UnlockPerk(buf.readUtf(128));
        }

        static void handle(UnlockPerk m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Perk perk = Perks.get(m.perkId);
                PlayerData data = SkyData.get(player);
                if (perk != null && data.getPerkPoints() > 0 && Perks.canUnlock(data, perk)) {
                    data.setPerkRank(perk.id(), data.getPerkRank(perk.id()) + 1);
                    data.setPerkPoints(data.getPerkPoints() - 1);
                    Vitals.refreshAttributes(player);
                }
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record ChooseRace(String raceId) {
        static void encode(ChooseRace m, FriendlyByteBuf buf) {
            buf.writeUtf(m.raceId, 64);
        }

        static ChooseRace decode(FriendlyByteBuf buf) {
            return new ChooseRace(buf.readUtf(64));
        }

        static void handle(ChooseRace m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            Race race = Race.byId(m.raceId);
            if (player != null && race != null) Progression.chooseRace(player, race);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Generic keybind actions handled by the core. See {@link ActionHandler}. */
    public record Action(int action, int arg) {
        public static final int POWER_ATTACK = 0;
        public static final int BLOCK_START = 1;
        public static final int BLOCK_STOP = 2;
        public static final int USE_POWER = 3;
        public static final int MAKE_LEGENDARY = 4;
        public static final int SPRINT_EXHAUSTED = 5;
        public static final int SHEATHE_TOGGLE = 6;
        public static final int DODGE_ROLL = 7;
        public static final int CLIMB_TICK = 8;
        public static final int UNSTUCK = 9;
        public static final int TAKE_WORLD_ITEM = 10;
        public static final int SHIELD_BASH = 11;

        static void encode(Action m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.action);
            buf.writeVarInt(m.arg);
        }

        static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Action m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ActionHandler.handle(player, m.action, m.arg);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Harvest wild plants, tall grass, flora and mature crops directly into bags. */
    public record HarvestBlock(net.minecraft.core.BlockPos pos) {
        static void encode(HarvestBlock m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.pos);
        }

        static HarvestBlock decode(FriendlyByteBuf buf) {
            return new HarvestBlock(buf.readBlockPos());
        }

        static void handle(HarvestBlock m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) com.skycraft.survival.Harvesting.harvest(player, m.pos);
            ctx.get().setPacketHandled(true);
        }
    }
}
