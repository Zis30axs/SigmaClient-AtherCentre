package net.minecraft.network;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mentalfrostbyte.jello.event.impl.game.network.EventGlobalReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.util.game.network.ServerConnectionErrorLogger;
import com.mentalfrostbyte.jello.util.game.network.ViaNetworkDiagnostics;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.ProtocolPipeline;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.exception.InformativeException;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ClientboundPackets1_19;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.vialoadingbase.netty.event.CompressionReorderEvent;
import de.florianmichael.viamcp.MCPVLBPipeline;
import de.florianmichael.viamcp.ViaMCP;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.crypto.Cipher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.login.ServerLoginNetHandler;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CClickWindowPacket;
import net.minecraft.network.play.client.CCreativeInventoryActionPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPickItemPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.network.play.server.SDisconnectPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.LazyValue;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import team.sdhq.eventBus.EventBus;

public class NetworkManager extends SimpleChannelInboundHandler<IPacket<?>> {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final Marker NETWORK_MARKER = MarkerManager.getMarker("NETWORK");
    public static final Marker NETWORK_PACKETS_MARKER = MarkerManager.getMarker("NETWORK_PACKETS", NETWORK_MARKER);
    public static final AttributeKey<ProtocolType> PROTOCOL_ATTRIBUTE_KEY = AttributeKey.valueOf("protocol");
    private static final int CLIENT_IO_THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    public static final LazyValue<NioEventLoopGroup> CLIENT_NIO_EVENTLOOP = new LazyValue<>(() -> {
        return new NioEventLoopGroup(CLIENT_IO_THREADS,
                (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
    });
    public static final LazyValue<EpollEventLoopGroup> CLIENT_EPOLL_EVENTLOOP = new LazyValue<>(() -> {
        return new EpollEventLoopGroup(0,
                (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
    });
    public static final LazyValue<DefaultEventLoopGroup> CLIENT_LOCAL_EVENTLOOP = new LazyValue<>(() -> {
        return new DefaultEventLoopGroup(0,
                (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
    });
    public static final LazyValue<OioEventLoopGroup> CLIENT_OIO_EVENTLOOP = new LazyValue<>(() -> {
        return new OioEventLoopGroup(0,
                (new ThreadFactoryBuilder()).setNameFormat("Netty OIO Client IO #%d").setDaemon(true).build());
    });
    private final PacketDirection direction;
    private final Queue<NetworkManager.QueuedPacket> outboundPacketsQueue = Queues.newConcurrentLinkedQueue();

    /**
     * The active channel
     */
    private Channel channel;

    /**
     * The address of the remote party
     */
    private SocketAddress socketAddress;

    /**
     * The INetHandler instance responsible for processing received packets
     */
    public INetHandler packetListener;

    /**
     * A String indicating why the network has shutdown.
     */
    private ITextComponent terminationReason;
    private boolean isEncrypted;
    private boolean disconnected;
    private int field_211394_q;
    private int field_211395_r;
    private float field_211396_s;
    private float field_211397_t;
    private int ticks;
    private boolean field_211399_v;

    public NetworkManager(PacketDirection packetDirection) {
        this.direction = packetDirection;
    }

    public void channelActive(ChannelHandlerContext p_channelActive_1_) throws Exception {
        super.channelActive(p_channelActive_1_);
        this.channel = p_channelActive_1_.channel();
        this.socketAddress = this.channel.remoteAddress();

        try {
            this.setConnectionState(ProtocolType.HANDSHAKING);
        } catch (Throwable throwable) {
            LOGGER.fatal(throwable);
        }
    }

    /**
     * Sets the new connection state and registers which packets this channel may
     * send and receive
     */
    public void setConnectionState(ProtocolType newState) {
        this.channel.attr(PROTOCOL_ATTRIBUTE_KEY).set(newState);
        this.channel.config().setAutoRead(true);
        LOGGER.debug("Enabled auto read");
    }

    public void channelInactive(ChannelHandlerContext p_channelInactive_1_) throws Exception {
        ViaNetworkDiagnostics.detach(this);
        this.closeChannel(new TranslationTextComponent("disconnect.endOfStream"));
    }

    public void exceptionCaught(ChannelHandlerContext p_exceptionCaught_1_, Throwable p_exceptionCaught_2_) {
        if (p_exceptionCaught_2_ instanceof SkipableEncoderException) {
            LOGGER.debug("Skipping packet due to errors", p_exceptionCaught_2_.getCause());
        } else {
            boolean flag = !this.field_211399_v;
            this.field_211399_v = true;

            if (this.channel.isOpen()) {
                if (p_exceptionCaught_2_ instanceof TimeoutException) {
                    LOGGER.debug("Timeout", p_exceptionCaught_2_);
                    ServerConnectionErrorLogger.logConnectionException("NetworkManager.Timeout", p_exceptionCaught_2_);
                    this.closeChannel(new TranslationTextComponent("disconnect.timeout"));
                } else {
                    ITextComponent itextcomponent = new TranslationTextComponent("disconnect.genericReason",
                            "Internal Exception: " + p_exceptionCaught_2_);
                    ServerConnectionErrorLogger.logConnectionException("NetworkManager.Exception", p_exceptionCaught_2_);
                    ServerConnectionErrorLogger.logDisconnect("NetworkManager.Exception", new TranslationTextComponent("disconnect.lost"), itextcomponent);

                    if (flag) {
                        LOGGER.debug("Failed to sent packet", p_exceptionCaught_2_);
                        this.sendPacket(new SDisconnectPacket(itextcomponent), (p_211391_2_) -> {
                            this.closeChannel(itextcomponent);
                        });
                        this.disableAutoRead();
                    } else {
                        LOGGER.debug("Double fault", p_exceptionCaught_2_);
                        this.closeChannel(itextcomponent);
                    }
                }
            }
        }
    }

    protected void channelRead0(ChannelHandlerContext context, IPacket<?> packet) {
        if (this.channel.isOpen()) {
            long viaDiagStart = ViaNetworkDiagnostics.startTiming();
            try {
                IPacket<?> packetToProcess = packet;

                // Only the client-side connection may fire module events. In singleplayer the
                // integrated server's connection reuses this class; letting modules intercept,
                // cancel or replay server-side packets corrupts the local connection (e.g. a
                // replayed S-packet reaching ServerPlayNetHandler causes a ClassCastException,
                // which kills keep-alive handling and kicks the player with "Timed out").
                if (this.direction == PacketDirection.CLIENTBOUND) {
                    EventGlobalReceivePacket globalpacketEvent = new EventGlobalReceivePacket(packet);
                    EventBus.call(globalpacketEvent);
                    if (globalpacketEvent.cancelled) {
                        return;
                    }

                    EventReceivePacket packetEvent = new EventReceivePacket(packet);
                    EventBus.call(packetEvent);

                    if (packetEvent.cancelled) {
                        return;
                    }

                    packetToProcess = packetEvent.packet;
                }

                processPacket(packetToProcess, this.packetListener);
            } catch (ThreadQuickExitException ignored) {
            }

            ViaNetworkDiagnostics.rawS2C(packet, viaDiagStart);
            ++this.field_211394_q;
        }
    }

    public static <T extends INetHandler> void processPacket(IPacket<T> p_197664_0_, INetHandler p_197664_1_) {
        p_197664_0_.processPacket((T) p_197664_1_);
    }

    /**
     * Sets the NetHandler for this NetworkManager, no checks are made if this
     * handler is suitable for the particular
     * connection state (protocol)
     */
    public void setNetHandler(INetHandler handler) {
        Validate.notNull(handler, "packetListener");
        this.packetListener = handler;
    }

    public void sendPacket(IPacket<?> packetIn) {
        this.sendPacket(packetIn, null);
    }

    public static void setCount1_19(int count) {
        // Per-connection reset: a fresh UserConnection starts at 0 anyway, but
        // after a world switch (JoinGame / dimension-change Respawn) the client
        // must restart at 1, matching Grim's BadPacketsH.onWorldChange reset.
        for (UserConnection connection : Via.getManager().getConnectionManager().getConnections()) {
            InteractionSequenceStorage storage = connection.get(InteractionSequenceStorage.class);
            if (storage != null) {
                storage.set(count);
            }
        }
    }

    public void sendPacket(IPacket<?> packetIn, @Nullable GenericFutureListener<? extends Future<? super Void>> p_201058_2_) {
        IPacket<?> packet = packetIn;
        ViaNetworkDiagnostics.rawC2S();

        // See channelRead0: never expose the integrated server's outbound packets to module
        // events, otherwise modules like Blink/FakeLag capture clientbound packets and later
        // replay them into the client channel, breaking the singleplayer connection.
        if (this.direction == PacketDirection.CLIENTBOUND) {
            EventSendPacket event = new EventSendPacket(packetIn);
            EventBus.call(event);

            if (event.cancelled) {
                ViaNetworkDiagnostics.cancelledC2S();
                return;
            }
            packet = event.packet;
        }

        if (ViaNetworkDiagnostics.shouldDropPlayPacketDuringConfiguration(this, packet)) {
            return;
        }

        if (this.trySendDirectInteraction(packet)) {
            return;
        }

        if (this.isChannelOpen()) {
            this.flushOutboundQueue();
            this.dispatchPacket(packet, p_201058_2_);
        } else {
            this.outboundPacketsQueue.add(new NetworkManager.QueuedPacket(packet, p_201058_2_));
        }
    }

    public void sendNoEventPacket(IPacket<?> packetIn) {
        sendNoEventPacket(packetIn, null);
    }

    public void sendNoEventPacket(IPacket<?> packetIn, @Nullable GenericFutureListener<? extends Future<? super Void>> p_201058_2_) {
        ViaNetworkDiagnostics.rawC2S();

        if (ViaNetworkDiagnostics.shouldDropPlayPacketDuringConfiguration(this, packetIn)) {
            return;
        }

        if (this.trySendDirectInteraction(packetIn)) {
            return;
        }

        if (this.isChannelOpen()) {
            this.flushOutboundQueue();
            this.dispatchPacket(packetIn, p_201058_2_);
        } else {
            this.outboundPacketsQueue.add(new NetworkManager.QueuedPacket(packetIn, p_201058_2_));
        }
    }

    @Nullable
    public UserConnection getViaUserConnection() {
        if (this.channel != null) {
            MCPVLBPipeline pipeline = this.channel.pipeline().get(MCPVLBPipeline.class);
            if (pipeline != null) {
                return pipeline.getUser();
            }

            return null;
        }

        Iterator<UserConnection> iterator = Via.getManager().getConnectionManager().getConnections().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Will commit the packet to the channel. If the current thread 'owns' the
     * channel it will write and flush the
     * packet, otherwise it will add a task for the channel eventloop thread to do
     * that.
     */
    private void dispatchPacket(IPacket<?> inPacket,
            @Nullable GenericFutureListener<? extends Future<? super Void>> futureListeners) {
        ProtocolType protocoltype = ProtocolType.getFromPacket(inPacket);
        ProtocolType protocoltype1 = this.channel.attr(PROTOCOL_ATTRIBUTE_KEY).get();
        ++this.field_211395_r;
        ViaNetworkDiagnostics.writtenC2S();

        if (protocoltype1 != protocoltype) {
            LOGGER.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
        }

        if (this.channel.eventLoop().inEventLoop()) {
            if (protocoltype != protocoltype1) {
                this.setConnectionState(protocoltype);
            }

            ChannelFuture channelfuture = this.channel.writeAndFlush(inPacket);

            if (futureListeners != null) {
                channelfuture.addListener(futureListeners);
            }

            channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            this.channel.eventLoop().execute(() -> {
                if (protocoltype != protocoltype1) {
                    this.setConnectionState(protocoltype);
                }

                ChannelFuture channelfuture1 = this.channel.writeAndFlush(inPacket);

                if (futureListeners != null) {
                    channelfuture1.addListener(futureListeners);
                }

                channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            });
        }
    }

    /**
     * Will iterate through the outboundPacketQueue and dispatch all Packets
     */
    private void flushOutboundQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            synchronized (this.outboundPacketsQueue) {
                NetworkManager.QueuedPacket networkmanager$queuedpacket;

                while ((networkmanager$queuedpacket = this.outboundPacketsQueue.poll()) != null) {
                    this.dispatchPacket(networkmanager$queuedpacket.packet, networkmanager$queuedpacket.field_201049_b);
                }
            }
        }
    }

    /**
     * Checks timeouts and processes all packets received
     */
    public void tick() {
        ViaNetworkDiagnostics.onConnectionTick(this);
        this.flushOutboundQueue();

        if (this.packetListener instanceof ServerLoginNetHandler) {
            ((ServerLoginNetHandler) this.packetListener).tick();
        }

        if (this.packetListener instanceof ServerPlayNetHandler) {
            ((ServerPlayNetHandler) this.packetListener).tick();
        }

        if (this.channel != null) {
            this.channel.flush();
        }

        if (this.ticks++ % 20 == 0) {
            this.func_241877_b();
        }
    }

    protected void func_241877_b() {
        this.field_211397_t = MathHelper.lerp(0.75F, (float) this.field_211395_r, this.field_211397_t);
        this.field_211396_s = MathHelper.lerp(0.75F, (float) this.field_211394_q, this.field_211396_s);
        this.field_211395_r = 0;
        this.field_211394_q = 0;
    }

    /**
     * Returns the socket address of the remote side. Server-only.
     */
    public SocketAddress getRemoteAddress() {
        return this.socketAddress;
    }

    /**
     * Closes the channel, the parameter can be used for an exit message (not
     * certain how it gets sent)
     */
    public void closeChannel(ITextComponent message) {
        if (this.channel.isOpen()) {
            this.channel.close().awaitUninterruptibly();
            this.terminationReason = message;
        }
    }

    /**
     * True if this NetworkManager uses a memory connection (single player game).
     * False may imply both an active TCP
     * connection or simply no active connection at all
     */
    public boolean isLocalChannel() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    /**
     * Create a new NetworkManager from the server host and connect it to the server
     */
    public static NetworkManager createNetworkManagerAndConnect(InetAddress address, int serverPort,
            boolean useNativeTransport) {
        final NetworkManager networkmanager = new NetworkManager(PacketDirection.CLIENTBOUND);
        Class<? extends Channel> oclass;
        LazyValue<? extends EventLoopGroup> lazyvalue;

        if (Epoll.isAvailable() && useNativeTransport) {
            oclass = EpollSocketChannel.class;
            lazyvalue = CLIENT_EPOLL_EVENTLOOP;
        } else {
            oclass = NioSocketChannel.class;
            lazyvalue = CLIENT_NIO_EVENTLOOP;
        }

        try {
            createClientBootstrap(networkmanager, lazyvalue.getValue(), oclass)
                    .connect(address, serverPort)
                    .syncUninterruptibly();
        } catch (RuntimeException exception) {
            if (oclass != NioSocketChannel.class || !isNioSelectorStartupFailure(exception)) {
                throw exception;
            }

            LOGGER.warn("Failed to initialize Netty NIO selector, retrying with OIO transport", exception);
            createClientBootstrap(networkmanager, CLIENT_OIO_EVENTLOOP.getValue(), OioSocketChannel.class)
                    .connect(address, serverPort)
                    .syncUninterruptibly();
        }

        return networkmanager;
    }

    private static Bootstrap createClientBootstrap(final NetworkManager networkmanager, EventLoopGroup eventLoopGroup,
            Class<? extends Channel> channelClass) {
        return (new Bootstrap()).group(eventLoopGroup).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel p_initChannel_1_) throws Exception {
                try {
                    p_initChannel_1_.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                }

                p_initChannel_1_.pipeline().addLast("timeout", new ReadTimeoutHandler(30))
                        .addLast("splitter", new NettyVarint21FrameDecoder())
                        .addLast("decoder", new NettyPacketDecoder(PacketDirection.CLIENTBOUND))
                        .addLast("prepender", new NettyVarint21FrameEncoder())
                        .addLast("encoder", new NettyPacketEncoder(PacketDirection.SERVERBOUND))
                        .addLast("packet_handler", networkmanager);
                if (p_initChannel_1_ instanceof SocketChannel
                        && ViaLoadingBase.getInstance().getTargetVersion().getVersion() != ViaMCP.NATIVE_VERSION) {
                    final UserConnection user = new UserConnectionImpl(p_initChannel_1_, true);
                    new ProtocolPipelineImpl(user);

                    p_initChannel_1_.pipeline().addLast(new MCPVLBPipeline(user));
                }
            }
        }).channel(channelClass);
    }

    private static boolean isNioSelectorStartupFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("failed to create a child event loop")
                    || message.contains("failed to open a new selector")
                    || message.contains("Unable to establish loopback connection"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Prepares a clientside NetworkManager: establishes a connection to the socket
     * supplied and configures the channel
     * pipeline. Returns the newly created instance.
     */
    public static NetworkManager provideLocalClient(SocketAddress address) {
        final NetworkManager networkmanager = new NetworkManager(PacketDirection.CLIENTBOUND);
        (new Bootstrap()).group(CLIENT_LOCAL_EVENTLOOP.getValue()).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel p_initChannel_1_) throws Exception {
                p_initChannel_1_.pipeline().addLast("packet_handler", networkmanager);
            }
        }).channel(LocalChannel.class).connect(address).syncUninterruptibly();
        return networkmanager;
    }

    public void func_244777_a(Cipher p_244777_1_, Cipher p_244777_2_) {
        this.isEncrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new NettyEncryptingDecoder(p_244777_1_));
        this.channel.pipeline().addBefore("prepender", "encrypt", new NettyEncryptingEncoder(p_244777_2_));
    }

    public boolean isEncrypted() {
        return this.isEncrypted;
    }

    /**
     * Returns true if this NetworkManager has an active channel, false otherwise
     */
    public boolean isChannelOpen() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean hasNoChannel() {
        return this.channel == null;
    }

    /**
     * Gets the current handler for processing packets
     */
    public INetHandler getNetHandler() {
        return this.packetListener;
    }

    @Nullable

    /**
     * If this channel is closed, returns the exit message, null otherwise.
     */
    public ITextComponent getExitMessage() {
        return this.terminationReason;
    }

    /**
     * Switches the channel to manual reading modus
     */
    public void disableAutoRead() {
        this.channel.config().setAutoRead(false);
    }

    public void setCompressionThreshold(int threshold) {
        if (threshold >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
                ((NettyCompressionDecoder) this.channel.pipeline().get("decompress"))
                        .setCompressionThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new NettyCompressionDecoder(threshold));
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
                ((NettyCompressionEncoder) this.channel.pipeline().get("compress")).setCompressionThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new NettyCompressionEncoder(threshold));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }
        this.channel.pipeline().fireUserEventTriggered(new CompressionReorderEvent());
    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnected) {
                LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.disconnected = true;

                if (this.getExitMessage() != null) {
                    this.getNetHandler().onDisconnect(this.getExitMessage());
                } else if (this.getNetHandler() != null) {
                    this.getNetHandler().onDisconnect(new TranslationTextComponent("multiplayer.disconnect.generic"));
                }
            }
        }
    }

    public float getPacketsReceived() {
        return this.field_211396_s;
    }

    public float getPacketsSent() {
        return this.field_211397_t;
    }

    static class QueuedPacket {
        private final IPacket<?> packet;
        @Nullable
        private final GenericFutureListener<? extends Future<? super Void>> field_201049_b;

        public QueuedPacket(IPacket<?> p_i48604_1_,
                @Nullable GenericFutureListener<? extends Future<? super Void>> p_i48604_2_) {
            this.packet = p_i48604_1_;
            this.field_201049_b = p_i48604_2_;
        }
    }

    // ====================== Via interaction compatibility ======================
    //
    // The sections below were consolidated from
    // de.florianmichael.viamcp.fixes.compat (ServerboundInteractionAdapter,
    // LocalInteractionState, LocalItemTranslator, InteractionSequenceStorage,
    // ServerboundInteractionSequenceProtocol). They own the serverbound
    // interaction path this network manager dispatches:
    //   - drop/adapt packets the target protocol cannot express,
    //   - on 1.19-1.21.1 targets send modern interaction packets directly
    //     through the ViaBackwards 1.19 rung instead of the 1.16 wire format,
    //   - remember the locally used item so the 1.8 HandItemProvider can hand
    //     the real stack to ViaVersion,
    //   - assign the modern per-connection block-interaction sequence.

    private static final Logger INTERACTION_LOGGER = LogManager.getLogger("ViaMCP-Interactions");
    private static final Logger ITEM_TRANSLATOR_LOGGER = LogManager.getLogger("ViaMCP-ItemTranslator");
    private static final Logger SEQUENCE_LOGGER = LogManager.getLogger("ViaSequence");
    private static final int MAX_PENDING_USES = 64;
    private static final long ITEM_ERROR_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final AtomicLong NEXT_ITEM_ERROR_LOG_NANOS = new AtomicLong();
    private static final String SEQUENCE_DEBUG_PROPERTY = "sigma.via.sequenceDebug";
    private static final long SEQUENCE_DEBUG_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static volatile boolean sequenceDebugChecked;
    private static volatile boolean sequenceDebugEnabled;
    private static volatile long lastSequenceDebugNanos;

    /**
     * Version gates for the interaction adapter. All reduce to the Via target
     * version; {@code null} (no Via target) falls back to the native path.
     */
    private static boolean atOrOlderThan1_8() {
        ProtocolVersion target = JelloPortal.getVersion();
        return target != null && target.olderThanOrEqualTo(ProtocolVersion.v1_8);
    }

    private static boolean supportsPickItemPacket() {
        return !atOrOlderThan1_8();
    }

    private static boolean between1_19And1_21_1() {
        ProtocolVersion target = JelloPortal.getVersion();
        return target != null
                && target.newerThanOrEqualTo(ProtocolVersion.v1_19)
                && target.olderThan(ProtocolVersion.v1_21_2);
    }

    /**
     * Handles the serverbound interaction packets that must bypass the normal
     * 1.16.4 wire format for the active Via target.
     *
     * @return {@code true} when the packet was consumed here and must not be
     *         dispatched normally.
     */
    private boolean trySendDirectInteraction(IPacket<?> packet) {
        if (shouldDropUnsupportedInteraction(packet)) {
            return true;
        }

        if (!rememberLocalUse(packet)) {
            return true;
        }

        if (!between1_19And1_21_1()) {
            return false;
        }

        UserConnection viaConnection = this.getViaUserConnection();
        if (viaConnection == null) {
            return false;
        }

        try {
            if (packet instanceof CHeldItemChangePacket heldItemChangePacket) {
                sendHeldItemChange(viaConnection, heldItemChangePacket);
                return true;
            }

            if (packet instanceof CPlayerTryUseItemPacket useItemPacket) {
                sendUseItem(viaConnection, useItemPacket);
                return true;
            }

            if (packet instanceof CPlayerTryUseItemOnBlockPacket useItemOnBlockPacket) {
                sendUseItemOn(viaConnection, useItemOnBlockPacket);
                return true;
            }

            if (packet instanceof CPlayerDiggingPacket diggingPacket) {
                sendPlayerAction(viaConnection, diggingPacket);
                return true;
            }
        } catch (Exception e) {
            INTERACTION_LOGGER.warn("Failed to send direct interaction packet {}, falling back to normal Via path",
                    packet.getClass().getSimpleName(), e);
        }

        return false;
    }

    private boolean rememberLocalUse(IPacket<?> packet) {
        if (!atOrOlderThan1_8()) {
            return true;
        }

        UserConnection connection = this.getViaUserConnection();
        if (packet instanceof CPlayerTryUseItemPacket useItemPacket) {
            return enqueueCurrentHand(connection, useItemPacket.getHand());
        } else if (packet instanceof CPlayerTryUseItemOnBlockPacket useItemOnBlockPacket) {
            return enqueueCurrentHand(connection, useItemOnBlockPacket.getHand());
        }
        return true;
    }

    private static boolean shouldDropUnsupportedInteraction(IPacket<?> packet) {
        if (packet instanceof CPlayerTryUseItemPacket useItemPacket) {
            return !PlayerController.isSupportedHand(useItemPacket.getHand());
        }

        if (packet instanceof CPlayerTryUseItemOnBlockPacket useItemOnBlockPacket) {
            return !PlayerController.isSupportedHand(useItemOnBlockPacket.getHand());
        }

        if (packet instanceof CAnimateHandPacket animateHandPacket) {
            return !PlayerController.isSupportedHand(animateHandPacket.getHand());
        }

        if (packet instanceof CPlayerDiggingPacket diggingPacket) {
            return atOrOlderThan1_8()
                    && diggingPacket.getAction() == CPlayerDiggingPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        }

        if (packet instanceof CPickItemPacket) {
            return !supportsPickItemPacket();
        }

        if (packet instanceof CClickWindowPacket clickWindowPacket) {
            ClickType clickType = clickWindowPacket.getClickType();
            return !PlayerController.isInventoryActionSupported(
                    clickWindowPacket.getSlotId(),
                    clickWindowPacket.getUsedButton(),
                    clickType);
        }

        return false;
    }

    private static void sendHeldItemChange(UserConnection connection, CHeldItemChangePacket packet) throws Exception {
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.SET_CARRIED_ITEM, connection);
        wrapper.write(Types.SHORT, (short) packet.getSlotId());
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }

    private static void sendUseItem(UserConnection connection, CPlayerTryUseItemPacket packet) throws Exception {
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM, connection);
        wrapper.write(Types.VAR_INT, packet.getHand().ordinal());
        // Placeholder: InteractionSequenceProtocol assigns the real
        // per-connection sequence right after the 1.19 rung. Writing it here
        // would double-increment because that protocol also runs on this path.
        wrapper.write(Types.VAR_INT, 0);
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }

    private static void sendUseItemOn(UserConnection connection, CPlayerTryUseItemOnBlockPacket packet) throws Exception {
        BlockRayTraceResult hit = packet.func_218794_c();
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM_ON, connection);
        wrapper.write(Types.VAR_INT, packet.getHand().ordinal());
        wrapper.write(Types.BLOCK_POSITION1_14,
                new BlockPosition(hit.getPos().getX(), hit.getPos().getY(), hit.getPos().getZ()));
        wrapper.write(Types.VAR_INT, hit.getFace().getIndex());
        wrapper.write(Types.FLOAT, (float) (hit.getHitVec().x - hit.getPos().getX()));
        wrapper.write(Types.FLOAT, (float) (hit.getHitVec().y - hit.getPos().getY()));
        wrapper.write(Types.FLOAT, (float) (hit.getHitVec().z - hit.getPos().getZ()));
        wrapper.write(Types.BOOLEAN, hit.isInside());
        // Placeholder, see sendUseItem.
        wrapper.write(Types.VAR_INT, 0);
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }

    private static void sendPlayerAction(UserConnection connection, CPlayerDiggingPacket packet) throws Exception {
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.PLAYER_ACTION, connection);
        wrapper.write(Types.VAR_INT, packet.getAction().ordinal());
        wrapper.write(Types.BLOCK_POSITION1_14,
                new BlockPosition(packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ()));
        wrapper.write(Types.UNSIGNED_BYTE, (short) packet.getFacing().getIndex());
        // Placeholder, see sendUseItem. InteractionSequenceProtocol also applies
        // the BadPacketsL rules (pos=0,0,0 / face=DOWN / sequence=0 for
        // non-digging actions) on the exact same packet.
        wrapper.write(Types.VAR_INT, 0);
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }

    /**
     * Captures the item for one packet that will actually enter ViaVersion.
     * The packet may be enqueued onto Netty after the render thread returns,
     * so a single global "last item" slot is not a safe hand-off: each packet
     * claims one queue slot and the HandItemProvider polls them in order.
     */
    public static boolean enqueueCurrentHand(UserConnection connection, Hand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (connection == null || mc.player == null || hand == null) {
            return false;
        }

        ItemStack snapshot = mc.player.getHeldItem(hand).copy();
        PendingUsedItems pending = pendingItems(connection);
        if (!pending.items.offer(snapshot)) {
            // The oldest item belongs to the oldest packet already queued on
            // Netty. Reject the newest packet instead of breaking FIFO pairing.
            return false;
        }

        return true;
    }

    public static Item pollViaItem(UserConnection connection) {
        if (connection == null) {
            return null;
        }

        PendingUsedItems pending = connection.get(PendingUsedItems.class);
        if (pending == null) {
            return null;
        }

        ItemStack snapshot = pending.items.poll();
        return toViaItem(snapshot);
    }

    private static PendingUsedItems pendingItems(UserConnection connection) {
        if (connection == null) {
            return null;
        }

        PendingUsedItems pending = connection.get(PendingUsedItems.class);
        if (pending != null) {
            return pending;
        }

        synchronized (connection) {
            pending = connection.get(PendingUsedItems.class);
            if (pending == null) {
                pending = new PendingUsedItems();
                connection.put(pending);
            }
            return pending;
        }
    }

    private static final class PendingUsedItems implements StorableObject {
        private final ArrayBlockingQueue<ItemStack> items = new ArrayBlockingQueue<>(MAX_PENDING_USES);
    }

    /**
     * Converts a native 1.16.4 stack to the item representation expected by a
     * 1.8 server. Numeric Minecraft registry ids are version-specific, so they
     * must never be written directly as {@link Types#ITEM1_8}.
     *
     * <p>This mirrors ViaFabricPlus' item translator: serialize a harmless
     * creative-slot packet, run it through a fresh dummy Via protocol pipeline,
     * then read the target-version item back out.</p>
     */
    private static Item toViaItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        // ViaBackwards replaces unknown 1.16 items with generic legacy
        // placeholders (for example a netherite sword becomes item id 1).
        // Such a stack cannot originate from a real 1.8 server, so do not
        // misrepresent it as another item in the legacy use packet.
        if (stack.getItem() instanceof SwordItem && !SwordItem.isLegacyBlockingSword(stack)) {
            return null;
        }

        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf rawPacket = Unpooled.buffer();
        try {
            UserConnection connection = createDummyConnection(channel, ProtocolVersion.v1_8);
            PacketBuffer packetBuffer = new PacketBuffer(rawPacket);
            new CCreativeInventoryActionPacket(0, stack).writePacketData(packetBuffer);

            PacketWrapper wrapper = PacketWrapper.create(
                    ServerboundPackets1_16_2.SET_CREATIVE_MODE_SLOT,
                    rawPacket,
                    connection);
            connection.getProtocolInfo().getPipeline().transform(Direction.SERVERBOUND, State.PLAY, wrapper);

            wrapper.read(Types.SHORT);
            Item translated = wrapper.read(Types.ITEM1_8);
            // ITEM1_8 serializes null as absent (-1), while a non-null id 0 is
            // present AIR and is explicitly rejected by Grim BadPacketsU.
            return translated == null || translated.identifier() <= 0 ? null : translated.copy();
        } catch (Throwable throwable) {
            // Null is the safe failure mode: emitting a native registry id as a
            // legacy item becomes AIR on the server and triggers Grim BadPacketsU.
            logItemTranslationFailure(throwable);
            return null;
        } finally {
            if (rawPacket.refCnt() > 0) {
                rawPacket.release();
            }
            channel.finishAndReleaseAll();
        }
    }

    private static void logItemTranslationFailure(Throwable throwable) {
        long now = System.nanoTime();
        long next = NEXT_ITEM_ERROR_LOG_NANOS.get();
        if (now >= next && NEXT_ITEM_ERROR_LOG_NANOS.compareAndSet(next, now + ITEM_ERROR_LOG_INTERVAL_NANOS)) {
            ITEM_TRANSLATOR_LOGGER.error("Failed to translate native item stack to a 1.8 ViaVersion item", throwable);
        }
    }

    private static UserConnection createDummyConnection(EmbeddedChannel channel, ProtocolVersion targetVersion) {
        UserConnection user = new UserConnectionImpl(channel, true);
        ProtocolPipeline pipeline = new ProtocolPipelineImpl(user);
        List<com.viaversion.viaversion.api.protocol.ProtocolPathEntry> path = Via.getManager()
                .getProtocolManager()
                .getProtocolPath(ProtocolVersion.v1_16_4, targetVersion);

        if (path == null) {
            throw new IllegalStateException("No ViaVersion protocol path from 1.16.4 to " + targetVersion);
        }

        for (com.viaversion.viaversion.api.protocol.ProtocolPathEntry entry : path) {
            pipeline.add(entry.protocol());
            entry.protocol().init(user);
        }

        ProtocolInfo info = user.getProtocolInfo();
        info.setState(State.PLAY);
        info.setProtocolVersion(ProtocolVersion.v1_16_4);
        info.setServerProtocolVersion(targetVersion);
        return user;
    }

    /**
     * Per-{@link UserConnection} storage for the modern (1.19+) block-interaction
     * sequence counter.
     *
     * <p>One counter is shared by USE_ITEM, USE_ITEM_ON and the digging
     * START/STOP actions, exactly like the vanilla 1.19+ client. Non-digging
     * PLAYER_ACTION packets must carry sequence 0 and must never consume the
     * counter. A fresh {@link UserConnection} starts at 0, so the first
     * incrementing packet after connect / world switch is sequence 1.
     *
     * <p>All mutation happens on the connection's Netty event loop, except the
     * world-switch reset which is issued from the Minecraft main thread
     * ({@code Minecraft.loadWorld}). The methods are therefore synchronized; the
     * frequency is a few calls per second per connection, so the lock is
     * uncontended and effectively free.
     */
    public static final class InteractionSequenceStorage implements StorableObject {
        private int sequence;

        public synchronized int next() {
            if (sequence == Integer.MAX_VALUE) {
                sequence = 0;
            }
            return ++sequence;
        }

        public synchronized int current() {
            return sequence;
        }

        public synchronized void set(int value) {
            sequence = Math.max(0, value);
        }

        public synchronized void reset() {
            sequence = 0;
        }

        public static InteractionSequenceStorage of(UserConnection connection) {
            InteractionSequenceStorage storage = connection.get(InteractionSequenceStorage.class);
            if (storage != null) {
                return storage;
            }

            synchronized (connection) {
                storage = connection.get(InteractionSequenceStorage.class);
                if (storage == null) {
                    storage = new InteractionSequenceStorage();
                    connection.put(storage);
                }
                return storage;
            }
        }
    }

    /**
     * Assigns the modern block-interaction sequence to every serverbound
     * USE_ITEM / USE_ITEM_ON / PLAYER_ACTION packet exactly once, after the
     * ViaBackwards 1.18.2 -> 1.19 layer has created the sequence field (it always
     * writes 0, which makes Grim flag {@code BadPacketsH expected=1, id=0}).
     *
     * <p>The protocol is inserted into the connection's protocol pipeline
     * immediately after {@link Protocol1_19To1_18_2}, i.e. it sees the fixed
     * 1.19 wire format for every target >= 1.19. All later rungs (1.19.1 ... 1.21.11)
     * preserve the value.
     *
     * <p>It is also the single place that decides the per-action rules:
     * <ul>
     *   <li>START_DESTROY_BLOCK (0) and STOP_DESTROY_BLOCK (2): next(), keep pos/face</li>
     *   <li>ABORT_DESTROY_BLOCK (1): 0, keep pos/face (Grim requires CANCELLED sequence 0)</li>
     *   <li>RELEASE_USE_ITEM / DROP_ITEM / DROP_ALL_ITEMS / SWAP_ITEM_WITH_OFFHAND:
     *       0 and pos=0,0,0 face=DOWN (BadPacketsL)</li>
     * </ul>
     */
    public static final class InteractionSequenceProtocol
            extends AbstractProtocol<ClientboundPackets1_19, ClientboundPackets1_19,
                    ServerboundPackets1_19, ServerboundPackets1_19> {

        /** PlayerAction ids, identical from 1.16 through 1.21.11 (PacketEvents DiggingAction). */
        private static final int ACTION_START_DESTROY_BLOCK = 0;
        private static final int ACTION_ABORT_DESTROY_BLOCK = 1;
        private static final int ACTION_STOP_DESTROY_BLOCK = 2;
        private static final int ACTION_DROP_ALL_ITEMS = 3;
        private static final int ACTION_DROP_ITEM = 4;
        private static final int ACTION_RELEASE_USE_ITEM = 5;
        private static final int ACTION_SWAP_ITEM_WITH_OFFHAND = 6;

        private static final BlockPosition ZERO = new BlockPosition(0, 0, 0);

        public InteractionSequenceProtocol() {
            super(ClientboundPackets1_19.class, ClientboundPackets1_19.class,
                    ServerboundPackets1_19.class, ServerboundPackets1_19.class);
        }

        @Override
        public void init(UserConnection connection) {
            if (!connection.has(InteractionSequenceStorage.class)) {
                connection.put(new InteractionSequenceStorage());
            }
        }

        @Override
        protected void registerPackets() {
            registerServerbound(ServerboundPackets1_19.USE_ITEM, this::handleUseItem);
            registerServerbound(ServerboundPackets1_19.USE_ITEM_ON, this::handleUseItemOn);
            registerServerbound(ServerboundPackets1_19.PLAYER_ACTION, this::handlePlayerAction);
        }

        private void handleUseItem(PacketWrapper wrapper) throws InformativeException {
            InteractionSequenceStorage storage = InteractionSequenceStorage.of(wrapper.user());
            int before = storage.current();
            int hand = wrapper.read(Types.VAR_INT);
            wrapper.read(Types.VAR_INT); // ViaBackwards placeholder (always 0)
            int sequence = storage.next();
            wrapper.write(Types.VAR_INT, hand);
            wrapper.write(Types.VAR_INT, sequence);
            debug(wrapper, "USE_ITEM", -1, null, (short) -1, sequence, before, "translated");
        }

        private void handleUseItemOn(PacketWrapper wrapper) throws InformativeException {
            InteractionSequenceStorage storage = InteractionSequenceStorage.of(wrapper.user());
            int before = storage.current();
            int hand = wrapper.read(Types.VAR_INT);
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            int face = wrapper.read(Types.VAR_INT);
            float cursorX = wrapper.read(Types.FLOAT);
            float cursorY = wrapper.read(Types.FLOAT);
            float cursorZ = wrapper.read(Types.FLOAT);
            boolean inside = wrapper.read(Types.BOOLEAN);
            wrapper.read(Types.VAR_INT); // ViaBackwards placeholder (always 0)
            int sequence = storage.next();
            wrapper.write(Types.VAR_INT, hand);
            wrapper.write(Types.BLOCK_POSITION1_14, pos);
            wrapper.write(Types.VAR_INT, face);
            wrapper.write(Types.FLOAT, cursorX);
            wrapper.write(Types.FLOAT, cursorY);
            wrapper.write(Types.FLOAT, cursorZ);
            wrapper.write(Types.BOOLEAN, inside);
            wrapper.write(Types.VAR_INT, sequence);
            debug(wrapper, "USE_ITEM_ON", -1, pos, (short) face, sequence, before, "translated");
        }

        private void handlePlayerAction(PacketWrapper wrapper) throws InformativeException {
            InteractionSequenceStorage storage = InteractionSequenceStorage.of(wrapper.user());
            int before = storage.current();
            int action = wrapper.read(Types.VAR_INT);
            BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
            short face = wrapper.read(Types.UNSIGNED_BYTE);
            wrapper.read(Types.VAR_INT); // ViaBackwards placeholder (always 0)

            int sequence;
            BlockPosition outPos;
            short outFace;

            switch (action) {
                case ACTION_START_DESTROY_BLOCK, ACTION_STOP_DESTROY_BLOCK -> {
                    sequence = storage.next();
                    outPos = pos;
                    outFace = face;
                }
                case ACTION_ABORT_DESTROY_BLOCK -> {
                    // CANCELLED_DIGGING keeps the real position/face but MUST be sequence 0.
                    sequence = 0;
                    outPos = pos;
                    outFace = face;
                }
                default -> {
                    // RELEASE_USE_ITEM / DROP_ITEM / DROP_ALL_ITEMS / SWAP_ITEM_WITH_OFFHAND
                    // (and any future non-digging action): BadPacketsL requires
                    // pos=0,0,0, face=DOWN, sequence=0. Never touches the counter.
                    sequence = 0;
                    outPos = ZERO;
                    outFace = 0;
                }
            }

            wrapper.write(Types.VAR_INT, action);
            wrapper.write(Types.BLOCK_POSITION1_14, outPos);
            wrapper.write(Types.UNSIGNED_BYTE, outFace);
            wrapper.write(Types.VAR_INT, sequence);
            debug(wrapper, "PLAYER_ACTION", action, outPos, outFace, sequence, before, "translated");
        }

        private static void debug(PacketWrapper wrapper, String packet, int action,
                                  BlockPosition pos, short face, int sequence, int before, String origin) {
            if (!isDebugEnabled() || !rateLimited()) {
                return;
            }
            SEQUENCE_LOGGER.info("[ViaSequence] connection={} packet={} action={} pos={} face={} "
                            + "sequence={} counterBefore={} counterAfter={} stage={} thread={} origin={}",
                    wrapper.user().getId(), packet,
                    action < 0 ? "-" : action,
                    pos == null ? "-" : pos.x() + "," + pos.y() + "," + pos.z(),
                    face < 0 ? "-" : face,
                    sequence, before, sequence,
                    InteractionSequenceProtocol.class.getSimpleName(),
                    Thread.currentThread().getName(), origin);
        }

        private static boolean isDebugEnabled() {
            if (!sequenceDebugChecked) {
                sequenceDebugEnabled = Boolean.parseBoolean(System.getProperty(SEQUENCE_DEBUG_PROPERTY, "false"));
                sequenceDebugChecked = true;
            }
            return sequenceDebugEnabled;
        }

        private static synchronized boolean rateLimited() {
            long now = System.nanoTime();
            if (now - lastSequenceDebugNanos < SEQUENCE_DEBUG_INTERVAL_NANOS) {
                return false;
            }
            lastSequenceDebugNanos = now;
            return true;
        }

        /**
         * Installs this protocol into the connection pipeline right after the
         * ViaBackwards 1.19 rung. Must be called on the connection's event loop.
         */
        public static void ensureInstalled(UserConnection connection) {
            if (connection == null || connection.getProtocolInfo() == null) {
                return;
            }
            ProtocolPipeline pipeline = connection.getProtocolInfo().getPipeline();
            if (pipeline == null) {
                return;
            }
            if (pipeline.contains(InteractionSequenceProtocol.class)) {
                return;
            }
            // Targets below 1.19 have no sequence field; nothing to fix.
            if (!pipeline.contains(Protocol1_19To1_18_2.class)) {
                return;
            }

            try {
                InteractionSequenceProtocol protocol = new InteractionSequenceProtocol();
                protocol.setClientVersion(ProtocolVersion.v1_19);
                protocol.setServerVersion(ProtocolVersion.v1_19);
                protocol.initialize();
                pipeline.add(protocol);
                moveAfter19Rung(pipeline, protocol);
            } catch (Exception e) {
                SEQUENCE_LOGGER.warn("Failed to install interaction sequence fix", e);
            }
        }

        /**
         * {@link ProtocolPipeline#add} always appends non-base protocols at the end
         * of the serverbound list; the sequence handler needs to run right after
         * the 1.19 rung so it sees the fixed 1.19 wire format. The list is only
         * touched once, on the event loop, before any further packet transform.
         */
        private static void moveAfter19Rung(ProtocolPipeline pipeline, Protocol protocol) {
            if (!(pipeline instanceof ProtocolPipelineImpl impl)) {
                return;
            }
            try {
                Field field = ProtocolPipelineImpl.class.getDeclaredField("protocolList");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Protocol> list = (List<Protocol>) field.get(impl);
                int anchor = -1;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getClass() == Protocol1_19To1_18_2.class) {
                        anchor = i;
                        break;
                    }
                }
                if (anchor < 0) {
                    return;
                }
                list.remove(protocol);
                list.add(anchor + 1, protocol);
            } catch (ReflectiveOperationException e) {
                SEQUENCE_LOGGER.warn("Failed to reposition interaction sequence protocol", e);
            }
        }
    }
}
