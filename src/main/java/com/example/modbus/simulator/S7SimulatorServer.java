package com.example.modbus.simulator;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S7 协议模拟服务器
 * 提供基础的 S7 通信模拟功能
 *
 * 注意：这是一个简化的 S7 模拟器，主要用于测试目的
 */
@Slf4j
public class S7SimulatorServer {

    private final String name;
    private final int port;
    private final int rack;
    private final int slot;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    // 模拟数据存储 - S7 数据块
    private final byte[] dbData = new byte[65536];      // 数据块
    private final byte[] inputData = new byte[65536];   // 输入区
    private final byte[] outputData = new byte[65536];  // 输出区
    private final byte[] merkerData = new byte[65536];  // 标志位区

    private final AtomicBoolean running = new AtomicBoolean(false);

    public S7SimulatorServer(String name, int port, int rack, int slot) {
        this.name = name;
        this.port = port;
        this.rack = rack;
        this.slot = slot;
    }

    /**
     * 启动 S7 模拟服务器
     */
    public void start() throws Exception {
        if (running.get()) {
            log.warn("S7 模拟服务器 {} 已经在运行", name);
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new S7MessageDecoder());
                            ch.pipeline().addLast(new S7MessageHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind("0.0.0.0", port).sync();
            serverChannel = future.channel();
            running.set(true);

            log.info("S7 模拟服务器 {} 已启动，监听端口: {}, Rack: {}, Slot: {}", name, port, rack, slot);

        } catch (Exception e) {
            log.error("S7 模拟服务器 {} 启动失败", name, e);
            stop();
            throw e;
        }
    }

    /**
     * 停止 S7 模拟服务器
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (serverChannel != null) {
            serverChannel.close();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        log.info("S7 模拟服务器 {} 已停止", name);
    }

    /**
     * S7 消息解码器（简化版）
     */
    private class S7MessageDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            // S7 TPKT 协议: 版本(1) + 保留(1) + 长度(2) + 数据
            if (in.readableBytes() < 4) {
                return;
            }

            in.markReaderIndex();

            byte version = in.readByte();
            byte reserved = in.readByte();
            int length = in.readUnsignedShort();

            if (in.readableBytes() < length - 4) {
                in.resetReaderIndex();
                return;
            }

            ByteBuf frame = in.readBytes(length - 4);
            out.add(frame);
        }
    }

    /**
     * S7 消息处理器（简化版）
     */
    private class S7MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            try {
                // 简化的 S7 协议处理
                // 这里实现基本的连接和数据交换逻辑

                if (msg.readableBytes() < 2) {
                    return;
                }

                // 读取 COTP 头部
                int headerLength = msg.readUnsignedByte();
                int pduType = msg.readUnsignedByte();

                // 处理 COTP 连接请求 (0xE0 = CR)
                if (pduType == 0xE0) {
                    handleConnectionRequest(ctx, msg);
                }
                // 处理 COTP 数据传输 (0xF0 = DT)
                else if (pduType == 0xF0) {
                    handleDataTransfer(ctx, msg);
                }
                else {
                    log.warn("{} - 未知的 PDU 类型: 0x{}", name, Integer.toHexString(pduType));
                }

            } catch (Exception e) {
                log.error("{} - 处理 S7 消息失败", name, e);
            }
        }

        /**
         * 处理连接请求
         */
        private void handleConnectionRequest(ChannelHandlerContext ctx, ByteBuf msg) {
            log.info("{} - 收到 S7 连接请求", name);

            // 构造 COTP 连接确认响应 (CC)
            ByteBuf response = Unpooled.buffer();

            // TPKT 头
            response.writeByte(0x03);  // 版本
            response.writeByte(0x00);  // 保留
            response.writeShort(0x0B); // 长度

            // COTP CC
            response.writeByte(0x06);  // 长度
            response.writeByte(0xD0);  // CC (Connection Confirm)
            response.writeShort(0x0000); // 目标引用
            response.writeShort(0x0001); // 源引用
            response.writeByte(0x00);    // 类和选项

            ctx.writeAndFlush(response);
            log.debug("{} - 发送连接确认", name);
        }

        /**
         * 处理数据传输
         */
        private void handleDataTransfer(ChannelHandlerContext ctx, ByteBuf msg) {
            if (msg.readableBytes() < 1) {
                return;
            }

            int eot = msg.readUnsignedByte(); // EOT flag

            if (msg.readableBytes() < 10) {
                return;
            }

            // S7 头部
            int protocolId = msg.readUnsignedByte();
            int rosctr = msg.readUnsignedByte(); // 消息类型
            int redundancy = msg.readUnsignedShort();
            int pduRef = msg.readUnsignedShort();
            int paramLength = msg.readUnsignedShort();
            int dataLength = msg.readUnsignedShort();

            log.debug("{} - S7 数据传输, 消息类型: 0x{}", name, Integer.toHexString(rosctr));

            // 处理不同类型的请求
            if (rosctr == 0x01) { // Job request
                handleJobRequest(ctx, msg, pduRef, paramLength, dataLength);
            } else if (rosctr == 0x07) { // Userdata
                handleUserData(ctx, msg, pduRef);
            }
        }

        /**
         * 处理作业请求（读/写）
         */
        private void handleJobRequest(ChannelHandlerContext ctx, ByteBuf msg, int pduRef, int paramLength, int dataLength) {
            if (paramLength < 2) {
                return;
            }

            int function = msg.readUnsignedByte();
            int itemCount = msg.readUnsignedByte();

            log.debug("{} - 作业请求, 功能: 0x{}, 项数: {}", name, Integer.toHexString(function), itemCount);

            // 创建响应
            ByteBuf response = Unpooled.buffer();

            // TPKT 头
            response.writeByte(0x03);
            response.writeByte(0x00);
            int lengthIndex = response.writerIndex();
            response.writeShort(0); // 占位符

            // COTP DT
            response.writeByte(0x02);
            response.writeByte(0xF0);
            response.writeByte(0x80);

            // S7 头
            response.writeByte(0x32); // 协议 ID
            response.writeByte(0x03); // Ack_Data
            response.writeShort(0x0000);
            response.writeShort(pduRef);

            int paramLengthIndex = response.writerIndex();
            response.writeShort(0); // 参数长度占位符
            int dataLengthIndex = response.writerIndex();
            response.writeShort(0); // 数据长度占位符

            int paramStart = response.writerIndex();
            response.writeByte(function);
            response.writeByte(itemCount);

            // 模拟成功响应
            for (int i = 0; i < itemCount; i++) {
                response.writeByte(0xFF); // 返回码：成功
            }

            int paramEnd = response.writerIndex();
            int dataStart = response.writerIndex();

            // 如果是读请求，添加数据
            if (function == 0x04) {
                for (int i = 0; i < itemCount; i++) {
                    response.writeByte(0xFF); // 返回码
                    response.writeByte(0x04); // 传输大小（位）
                    response.writeShort(8);   // 数据长度
                    response.writeLong(0);    // 模拟数据
                }
            }

            int dataEnd = response.writerIndex();

            // 填充长度字段
            int totalLength = response.writerIndex();
            response.setShort(lengthIndex, totalLength);
            response.setShort(paramLengthIndex, paramEnd - paramStart);
            response.setShort(dataLengthIndex, dataEnd - dataStart);

            ctx.writeAndFlush(response);
            log.debug("{} - 发送作业响应", name);
        }

        /**
         * 处理用户数据
         */
        private void handleUserData(ChannelHandlerContext ctx, ByteBuf msg, int pduRef) {
            // 简化的用户数据处理，返回基本响应
            ByteBuf response = Unpooled.buffer();

            // TPKT + COTP + S7 Ack
            response.writeByte(0x03);
            response.writeByte(0x00);
            response.writeShort(0x1B);
            response.writeByte(0x02);
            response.writeByte(0xF0);
            response.writeByte(0x80);
            response.writeByte(0x32);
            response.writeByte(0x07); // Ack Userdata
            response.writeShort(0x0000);
            response.writeShort(pduRef);
            response.writeShort(0x0008);
            response.writeShort(0x0000);

            // 基本参数
            response.writeByte(0x00);
            response.writeByte(0x01);
            response.writeByte(0x12);
            response.writeByte(0x04);
            response.writeByte(0x11);
            response.writeByte(0x44);
            response.writeByte(0x01);
            response.writeByte(0x00);

            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("{} - 连接异常", name, cause);
            ctx.close();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            log.info("{} - 客户端已连接: {}", name, ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("{} - 客户端已断开: {}", name, ctx.channel().remoteAddress());
        }
    }

    /**
     * 设置数据块中的位
     */
    public void setDBBit(int dbNumber, int byteOffset, int bitOffset, boolean value) {
        int address = byteOffset;
        if (value) {
            dbData[address] |= (1 << bitOffset);
        } else {
            dbData[address] &= ~(1 << bitOffset);
        }
        log.debug("{} - 设置 DB{}.DBX{}.{} = {}", name, dbNumber, byteOffset, bitOffset, value);
    }

    /**
     * 获取数据块中的位
     */
    public boolean getDBBit(int dbNumber, int byteOffset, int bitOffset) {
        int address = byteOffset;
        return (dbData[address] & (1 << bitOffset)) != 0;
    }

    /**
     * 设置标志位
     */
    public void setMerker(int byteOffset, int bitOffset, boolean value) {
        if (value) {
            merkerData[byteOffset] |= (1 << bitOffset);
        } else {
            merkerData[byteOffset] &= ~(1 << bitOffset);
        }
        log.debug("{} - 设置 M{}.{} = {}", name, byteOffset, bitOffset, value);
    }

    /**
     * 获取标志位
     */
    public boolean getMerker(int byteOffset, int bitOffset) {
        return (merkerData[byteOffset] & (1 << bitOffset)) != 0;
    }

    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
