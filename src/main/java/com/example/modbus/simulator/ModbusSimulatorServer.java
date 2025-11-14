package com.example.modbus.simulator;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.requests.*;
import com.digitalpetri.modbus.responses.*;
import com.digitalpetri.modbus.slave.ModbusTcpSlave;
import com.digitalpetri.modbus.slave.ModbusTcpSlaveConfig;
import com.digitalpetri.modbus.slave.ServiceRequestHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modbus TCP 模拟服务器
 * 模拟一个支持读写线圈和寄存器的 Modbus 设备
 */
@Slf4j
public class ModbusSimulatorServer {

    private final String name;
    private final int port;
    private final int unitId;
    private ModbusTcpSlave slave;

    // 模拟数据存储
    private final boolean[] coils = new boolean[65536];           // 线圈
    private final boolean[] discreteInputs = new boolean[65536];  // 离散输入
    private final short[] holdingRegisters = new short[65536];    // 保持寄存器
    private final short[] inputRegisters = new short[65536];      // 输入寄存器

    private final AtomicBoolean running = new AtomicBoolean(false);

    public ModbusSimulatorServer(String name, int port, int unitId) {
        this.name = name;
        this.port = port;
        this.unitId = unitId;
    }

    /**
     * 启动模拟服务器
     */
    public void start() throws Exception {
        if (running.get()) {
            log.warn("Modbus 模拟服务器 {} 已经在运行", name);
            return;
        }

        ModbusTcpSlaveConfig config = new ModbusTcpSlaveConfig.Builder().build();
        slave = new ModbusTcpSlave(config);

        // 设置请求处理器
        slave.setRequestHandler(new ServiceRequestHandler() {
            @Override
            public void onReadHoldingRegisters(ServiceRequest<ReadHoldingRegistersRequest, ReadHoldingRegistersResponse> service) {
                ReadHoldingRegistersRequest request = service.getRequest();
                int address = request.getAddress();
                int quantity = request.getQuantity();

                try {
                    ByteBuf registers = Unpooled.buffer(quantity * 2);
                    for (int i = 0; i < quantity; i++) {
                        registers.writeShort(holdingRegisters[address + i]);
                    }
                    service.sendResponse(new ReadHoldingRegistersResponse(registers));
                    log.debug("{} - 读取保持寄存器: 地址={}, 数量={}", name, address, quantity);
                } catch (Exception e) {
                    log.error("{} - 读取保持寄存器失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }

            @Override
            public void onReadInputRegisters(ServiceRequest<ReadInputRegistersRequest, ReadInputRegistersResponse> service) {
                ReadInputRegistersRequest request = service.getRequest();
                int address = request.getAddress();
                int quantity = request.getQuantity();

                try {
                    ByteBuf registers = Unpooled.buffer(quantity * 2);
                    for (int i = 0; i < quantity; i++) {
                        registers.writeShort(inputRegisters[address + i]);
                    }
                    service.sendResponse(new ReadInputRegistersResponse(registers));
                    log.debug("{} - 读取输入寄存器: 地址={}, 数量={}", name, address, quantity);
                } catch (Exception e) {
                    log.error("{} - 读取输入寄存器失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }

            @Override
            public void onReadCoils(ServiceRequest<ReadCoilsRequest, ReadCoilsResponse> service) {
                ReadCoilsRequest request = service.getRequest();
                int address = request.getAddress();
                int quantity = request.getQuantity();

                try {
                    ByteBuf coilStatus = Unpooled.buffer((quantity + 7) / 8);
                    int byteCount = (quantity + 7) / 8;
                    for (int i = 0; i < byteCount; i++) {
                        int b = 0;
                        for (int bit = 0; bit < 8 && (i * 8 + bit) < quantity; bit++) {
                            if (coils[address + i * 8 + bit]) {
                                b |= (1 << bit);
                            }
                        }
                        coilStatus.writeByte(b);
                    }
                    service.sendResponse(new ReadCoilsResponse(coilStatus));
                    log.debug("{} - 读取线圈: 地址={}, 数量={}", name, address, quantity);
                } catch (Exception e) {
                    log.error("{} - 读取线圈失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }

            @Override
            public void onReadDiscreteInputs(ServiceRequest<ReadDiscreteInputsRequest, ReadDiscreteInputsResponse> service) {
                ReadDiscreteInputsRequest request = service.getRequest();
                int address = request.getAddress();
                int quantity = request.getQuantity();

                try {
                    ByteBuf inputStatus = Unpooled.buffer((quantity + 7) / 8);
                    int byteCount = (quantity + 7) / 8;
                    for (int i = 0; i < byteCount; i++) {
                        int b = 0;
                        for (int bit = 0; bit < 8 && (i * 8 + bit) < quantity; bit++) {
                            if (discreteInputs[address + i * 8 + bit]) {
                                b |= (1 << bit);
                            }
                        }
                        inputStatus.writeByte(b);
                    }
                    service.sendResponse(new ReadDiscreteInputsResponse(inputStatus));
                    log.debug("{} - 读取离散输入: 地址={}, 数量={}", name, address, quantity);
                } catch (Exception e) {
                    log.error("{} - 读取离散输入失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }

            @Override
            public void onWriteSingleCoil(ServiceRequest<WriteSingleCoilRequest, WriteSingleCoilResponse> service) {
                WriteSingleCoilRequest request = service.getRequest();
                int address = request.getAddress();
                boolean value = request.getValue() != 0;

                try {
                    coils[address] = value;
                    service.sendResponse(new WriteSingleCoilResponse(address, value ? 0xFF00 : 0x0000));
                    log.info("{} - 写入单个线圈: 地址={}, 值={}", name, address, value);
                } catch (Exception e) {
                    log.error("{} - 写入单个线圈失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }

            @Override
            public void onWriteSingleRegister(ServiceRequest<WriteSingleRegisterRequest, WriteSingleRegisterResponse> service) {
                WriteSingleRegisterRequest request = service.getRequest();
                int address = request.getAddress();
                int value = request.getValue();

                try {
                    holdingRegisters[address] = (short) value;
                    service.sendResponse(new WriteSingleRegisterResponse(address, value));
                    log.info("{} - 写入单个寄存器: 地址={}, 值={}", name, address, value);
                } catch (Exception e) {
                    log.error("{} - 写入单个寄存器失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }

            @Override
            public void onWriteMultipleCoils(ServiceRequest<WriteMultipleCoilsRequest, WriteMultipleCoilsResponse> service) {
                WriteMultipleCoilsRequest request = service.getRequest();
                int address = request.getAddress();
                int quantity = request.getQuantity();

                try {
                    ByteBuf values = request.getValues();
                    for (int i = 0; i < quantity; i++) {
                        int byteIndex = i / 8;
                        int bitIndex = i % 8;
                        byte b = values.getByte(byteIndex);
                        coils[address + i] = ((b >> bitIndex) & 0x01) == 1;
                    }
                    ReferenceCountUtil.release(values);
                    service.sendResponse(new WriteMultipleCoilsResponse(address, quantity));
                    log.info("{} - 写入多个线圈: 地址={}, 数量={}", name, address, quantity);
                } catch (Exception e) {
                    log.error("{} - 写入多个线圈失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }

            @Override
            public void onWriteMultipleRegisters(ServiceRequest<WriteMultipleRegistersRequest, WriteMultipleRegistersResponse> service) {
                WriteMultipleRegistersRequest request = service.getRequest();
                int address = request.getAddress();
                int quantity = request.getQuantity();

                try {
                    ByteBuf values = request.getValues();
                    for (int i = 0; i < quantity; i++) {
                        holdingRegisters[address + i] = values.readShort();
                    }
                    ReferenceCountUtil.release(values);
                    service.sendResponse(new WriteMultipleRegistersResponse(address, quantity));
                    log.info("{} - 写入多个寄存器: 地址={}, 数量={}", name, address, quantity);
                } catch (Exception e) {
                    log.error("{} - 写入多个寄存器失败", name, e);
                    service.sendException(ExceptionCode.IllegalDataAddress);
                }
            }
        });

        // 绑定到指定端口
        slave.bind(port).await();
        running.set(true);
        log.info("Modbus 模拟服务器 {} 已启动，监听端口: {}, 单元ID: {}", name, port, unitId);
    }

    /**
     * 停止模拟服务器
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        if (slave != null) {
            slave.shutdown();
            slave = null;
        }
        running.set(false);
        log.info("Modbus 模拟服务器 {} 已停止", name);
    }

    /**
     * 获取线圈值
     */
    public boolean getCoil(int address) {
        return coils[address];
    }

    /**
     * 设置线圈值
     */
    public void setCoil(int address, boolean value) {
        coils[address] = value;
        log.debug("{} - 手动设置线圈: 地址={}, 值={}", name, address, value);
    }

    /**
     * 获取保持寄存器值
     */
    public short getHoldingRegister(int address) {
        return holdingRegisters[address];
    }

    /**
     * 设置保持寄存器值
     */
    public void setHoldingRegister(int address, short value) {
        holdingRegisters[address] = value;
        log.debug("{} - 手动设置保持寄存器: 地址={}, 值={}", name, address, value);
    }

    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
