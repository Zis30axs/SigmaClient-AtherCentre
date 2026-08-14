/*
 * This file is part of ViaMCP - https://github.com/FlorianMichael/ViaMCP
 * Copyright (C) 2020-2024 FlorianMichael/EnZaXD <florian.michael07@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.florianmichael.viamcp;

import com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.platform.providers.ViaProviders;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.provider.PlayerAbilitiesProvider;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ClientboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ClientboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ServerboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.provider.AckSequenceProvider;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.SharedConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class ViaMCP {
    public final static int NATIVE_VERSION = SharedConstants.getNativeVersion();
    public static ViaMCP INSTANCE;

    public static void create() {
        INSTANCE = new ViaMCP();
    }

    public ViaMCP() {
        ViaLoadingBase.ViaLoadingBaseBuilder.create()
                .runDirectory(new File("ViaMCP"))
                .nativeVersion(NATIVE_VERSION)
                .providers(ViaMCP::registerProviders)
                .onProtocolReload(protocolVersion -> {})
                .build();
        fixTransactions();
    }

    private void fixTransactions() {
        final Protocol1_17To1_16_4 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_17To1_16_4.class);
        protocol.registerClientbound(ClientboundPackets1_17.PING, ClientboundPackets1_16_2.CONTAINER_ACK, wrapper -> {}, true);
        protocol.registerServerbound(ServerboundPackets1_16_2.CONTAINER_ACK, ServerboundPackets1_17.PONG, wrapper -> {}, true);
    }

    /**
     * Registers the client-side providers ViaVersion needs to translate the
     * local player's state. Previously grouped as
     * {@code de.florianmichael.viamcp.fixes.compat.InteractionProviders}; the
     * four providers now live here as nested classes.
     */
    private static void registerProviders(ViaProviders providers) {
        providers.use(AckSequenceProvider.class, new LocalAckSequenceProvider());
        providers.use(HandItemProvider.class, new LocalHandItemProvider());
        providers.use(PlayerAbilitiesProvider.class, new LocalPlayerAbilitiesProvider());
        providers.use(PickItemProvider.class, new LocalPickItemProvider());
    }

    /**
     * Mirrors the server's sequence into the per-connection
     * {@link NetworkManager.InteractionSequenceStorage} so locally sent and
     * remotely acknowledged sequences stay in sync.
     */
    private static final class LocalAckSequenceProvider extends AckSequenceProvider {
        @Override
        public void handleSequence(UserConnection connection, int sequence) {
            NetworkManager.InteractionSequenceStorage storage =
                    NetworkManager.InteractionSequenceStorage.of(connection);
            storage.set(Math.max(storage.current(), sequence));
        }
    }

    /**
     * Hands the locally used item to the 1.8 protocol layer. The item was
     * captured by {@link NetworkManager#enqueueCurrentHand} when the matching
     * use packet entered the network manager.
     */
    private static final class LocalHandItemProvider extends HandItemProvider {
        @Override
        public Item getHandItem(UserConnection connection) {
            return NetworkManager.pollViaItem(connection);
        }
    }

    private static final class LocalPlayerAbilitiesProvider extends PlayerAbilitiesProvider {
        @Override
        public float getFlyingSpeed(UserConnection connection) {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            return player == null ? super.getFlyingSpeed(connection) : player.abilities.getFlySpeed();
        }

        @Override
        public float getWalkingSpeed(UserConnection connection) {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            return player == null ? super.getWalkingSpeed(connection) : player.abilities.getWalkSpeed();
        }
    }

    private static final class LocalPickItemProvider extends PickItemProvider {
        private static final Logger LOGGER = LogManager.getLogger("ViaMCP-PickItem");
        private static int warnedBlockPick;
        private static int warnedEntityPick;

        @Override
        public void pickItemFromBlock(UserConnection connection, BlockPosition position, boolean includeData) {
            if (++warnedBlockPick == 1) {
                LOGGER.warn("Ignoring unsupported 1.21.2+ pick-item-from-block fallback at {}", position);
            }
        }

        @Override
        public void pickItemFromEntity(UserConnection connection, int entityId, boolean includeData) {
            if (++warnedEntityPick == 1) {
                LOGGER.warn("Ignoring unsupported 1.21.2+ pick-item-from-entity fallback for entity {}", entityId);
            }
        }
    }
}
