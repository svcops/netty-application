package io.intellij.netty.tcpfrp.client.service;

import io.intellij.netty.tcpfrp.commons.Listeners;
import io.intellij.netty.tcpfrp.protocol.channel.DispatchManager;
import io.intellij.netty.tcpfrp.protocol.channel.DispatchPacket;
import io.intellij.netty.tcpfrp.protocol.channel.FrpChannel;
import io.intellij.netty.tcpfrp.protocol.client.ServiceState;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * ServiceChannelHandler
 *
 * @author tech@intellij.io
 */
@RequiredArgsConstructor
@Slf4j
public class ServiceChannelHandler extends ChannelInboundHandlerAdapter {
    private final String serviceName;
    private final String dispatchId;
    private final FrpChannel frpChannel;
    private final DispatchManager dispatchManager;

    /**
     * 服务连接成功
     */
    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
        log.info("[SERVICE] 建立服务端连接 |dispatchId={}|serviceName={}", dispatchId, serviceName);
        dispatchManager.addChannel(dispatchId, ctx.channel());
        // BootStrap set AUTO_READ=false
        // 等待frp-server 发送 UserConnState(READY)
    }

    /**
     * 读取到服务的数据
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            log.debug("接收到服务端的数据 |dispatchId={}|serviceName={}|len={}", dispatchId, serviceName, byteBuf.readableBytes());
            frpChannel.write(DispatchPacket.create(dispatchId, byteBuf))
                    .addListeners(Listeners.read(frpChannel));
            return;
        }
        log.error("ServiceHandler channelRead error, msg: {}", msg);
        throw new IllegalArgumentException("msg is not ByteBuf");
    }

    @Override
    public void channelReadComplete(@NotNull ChannelHandlerContext ctx) throws Exception {
        ctx.read();
        frpChannel.flush();
    }

    /**
     * 服务连接断开
     * <p>
     * 1. 通知 frp-server，服务连接断开
     * 2. 关闭服务的 channel
     */
    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
        log.warn("[SERVICE] 丢失服务端连接 |dispatchId={}|serviceName{}", dispatchId, serviceName);
        // frp-client -x-> mysql:3306
        frpChannel.writeAndFlush(ServiceState.broken(dispatchId), Listeners.read(frpChannel));
        ctx.close();
    }

    @Override
    public void exceptionCaught(@NotNull ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exception caught|dispatchId={}", dispatchId, cause);
        ctx.close();
    }

}
