package com.example.modbus.service;

import com.example.modbus.config.ModbusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.stereotype.Service;

/**
 * 设备服务
 * 提供读取和写入Modbus设备的通用方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final ModbusConnectionManager connectionManager;
    private final ModbusProperties modbusProperties;

    /**
     * 读取设备A的标志位
     */
    public boolean readDeviceAFlag() {
        return readDeviceFlagWithRetry("deviceA", modbusProperties.getDeviceA(),
                                       () -> connectionManager.getDeviceAConnection());
    }

    /**
     * 读取设备标志位，支持失败重试
     */
    private boolean readDeviceFlagWithRetry(String deviceName,
                                           ModbusProperties.DeviceConfig config,
                                           ConnectionSupplier connectionSupplier) {
        int maxRetries = 2; // 最多尝试2次
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                PlcConnection connection = connectionSupplier.get();
                String address = buildAddress(config);
                String addressType = getAddressType(config.getFlagAddress());
                boolean result = readFlagValue(connection, address, addressType, deviceName);

                // 如果不是第一次尝试，说明重试成功
                if (attempt > 1) {
                    log.info("{} 重试成功 (第{}次尝试)", deviceName, attempt);
                }

                return result;
            } catch (Exception e) {
                lastException = e;

                if (attempt < maxRetries) {
                    log.warn("{} 读取失败 (第{}次尝试): {}，准备重试...",
                            deviceName, attempt, e.getMessage());
                    // 强制重新连接
                    connectionManager.reconnect(deviceName);

                    // 短暂延迟后重试
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("{} 读取失败，已重试{}次", deviceName, maxRetries, e);
                }
            }
        }

        return false;
    }

    /**
     * 连接供应商函数式接口
     */
    @FunctionalInterface
    private interface ConnectionSupplier {
        PlcConnection get() throws Exception;
    }

    /**
     * 写入设备B的标志位
     */
    public boolean writeDeviceBFlag(boolean value) {
        return writeDeviceFlagWithRetry("deviceB", modbusProperties.getDeviceB(),
                                        () -> connectionManager.getDeviceBConnection(), value);
    }

    /**
     * 写入设备标志位，支持失败重试
     */
    private boolean writeDeviceFlagWithRetry(String deviceName,
                                            ModbusProperties.DeviceConfig config,
                                            ConnectionSupplier connectionSupplier,
                                            boolean value) {
        int maxRetries = 2; // 最多尝试2次
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                PlcConnection connection = connectionSupplier.get();
                String address = buildAddress(config);
                String addressType = getAddressType(config.getFlagAddress());
                boolean result = writeFlagValue(connection, address, addressType, value, deviceName);

                // 如果不是第一次尝试，说明重试成功
                if (attempt > 1) {
                    log.info("{} 写入重试成功 (第{}次尝试)", deviceName, attempt);
                }

                return result;
            } catch (Exception e) {
                lastException = e;

                if (attempt < maxRetries) {
                    log.warn("{} 写入失败 (第{}次尝试): {}，准备重试...",
                            deviceName, attempt, e.getMessage());
                    // 强制重新连接
                    connectionManager.reconnect(deviceName);

                    // 短暂延迟后重试
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("{} 写入失败，已重试{}次", deviceName, maxRetries, e);
                }
            }
        }

        return false;
    }

    /**
     * 读取标志位值（支持线圈和寄存器）
     */
    private boolean readFlagValue(PlcConnection connection, String address, String addressType, String deviceName) throws Exception {
        PlcReadRequest.Builder builder = connection.readRequestBuilder();
        PlcReadRequest readRequest = builder.addTagAddress("flag", address).build();

        PlcReadResponse response = readRequest.execute().get();

        if (response.getResponseCode("flag") != PlcResponseCode.OK) {
            log.error("{}读取响应错误: {}", deviceName, response.getResponseCode("flag"));
            throw new RuntimeException(deviceName + "读取失败: " + response.getResponseCode("flag"));
        }

        boolean value;
        // 根据地址类型选择读取方式
        if (isRegisterType(addressType)) {
            // 寄存器类型：读取整数值，非0为true
            int intValue = response.getShort("flag");
            value = intValue != 0;
            log.debug("{}标志位读取成功: {} (寄存器值: {})", deviceName, value, intValue);
        } else {
            // 线圈/离散输入类型：直接读取布尔值
            value = response.getBoolean("flag");
            log.debug("{}标志位读取成功: {}", deviceName, value);
        }

        return value;
    }

    /**
     * 写入标志位值（支持线圈和寄存器）
     */
    private boolean writeFlagValue(PlcConnection connection, String address, String addressType, boolean value, String deviceName) throws Exception {
        PlcWriteRequest.Builder builder = connection.writeRequestBuilder();
        PlcWriteRequest writeRequest;

        // 根据地址类型选择写入方式
        if (isRegisterType(addressType)) {
            // 寄存器类型：写入整数值（0或1）
            short intValue = (short) (value ? 1 : 0);
            writeRequest = builder.addTagAddress("flag", address, intValue).build();
        } else {
            // 线圈类型：直接写入布尔值
            writeRequest = builder.addTagAddress("flag", address, value).build();
        }

        PlcWriteResponse response = writeRequest.execute().get();

        if (response.getResponseCode("flag") != PlcResponseCode.OK) {
            log.error("{}写入响应错误: {}", deviceName, response.getResponseCode("flag"));
            return false;
        }

        log.info("{}标志位写入成功: {}", deviceName, value);
        return true;
    }

    /**
     * 获取地址类型
     */
    private String getAddressType(String flagAddress) {
        String[] parts = flagAddress.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("地址格式错误");
        }
        return parts[0].trim().toLowerCase();
    }

    /**
     * 判断是否为寄存器类型
     */
    private boolean isRegisterType(String addressType) {
        return "input-register".equals(addressType) || "holding-register".equals(addressType);
    }

    /**
     * 构建Modbus地址
     * 根据配置的地址格式构建PLC4X地址字符串
     */
    private String buildAddress(ModbusProperties.DeviceConfig config) {
        String flagAddress = config.getFlagAddress();

        // 解析地址格式：coil:0 或 holding-register:0
        String[] parts = flagAddress.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("地址格式错误，应为 'type:address'，例如 'coil:0' 或 'holding-register:0'");
        }

        String type = parts[0].trim();
        String address = parts[1].trim();

        // 构建PLC4X Modbus地址
        // PLC4X 格式：type:address (单元ID在连接字符串中指定)
        // 例如：coil:0 表示线圈0
        //      input-register:10002 表示输入寄存器10002
        return switch (type.toLowerCase()) {
            case "coil" -> String.format("coil:%s", address);
            case "discrete-input" -> String.format("discrete-input:%s", address);
            case "input-register" -> String.format("input-register:%s", address);
            case "holding-register" -> String.format("holding-register:%s", address);
            default -> throw new IllegalArgumentException("不支持的地址类型: " + type);
        };
    }
}
