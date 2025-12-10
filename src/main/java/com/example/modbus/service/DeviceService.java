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
        try {
            PlcConnection connection = connectionManager.getDeviceAConnection();
            String address = buildAddress(modbusProperties.getDeviceA());
            String addressType = getAddressType(modbusProperties.getDeviceA().getFlagAddress());
            return readFlagValue(connection, address, addressType, "设备A");
        } catch (Exception e) {
            log.error("读取设备A标志位失败", e);
            // 尝试重新连接
            connectionManager.reconnect("deviceA");
            return false;
        }
    }

    /**
     * 写入设备B的标志位
     */
    public boolean writeDeviceBFlag(boolean value) {
        try {
            PlcConnection connection = connectionManager.getDeviceBConnection();
            String address = buildAddress(modbusProperties.getDeviceB());
            String addressType = getAddressType(modbusProperties.getDeviceB().getFlagAddress());
            return writeFlagValue(connection, address, addressType, value, "设备B");
        } catch (Exception e) {
            log.error("写入设备B标志位失败", e);
            // 尝试重新连接
            connectionManager.reconnect("deviceB");
            return false;
        }
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
        int unitId = config.getUnitId();

        // 解析地址格式：coil:0 或 holding-register:0
        String[] parts = flagAddress.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("地址格式错误，应为 'type:address'，例如 'coil:0' 或 'holding-register:0'");
        }

        String type = parts[0].trim();
        String address = parts[1].trim();

        // 构建PLC4X Modbus地址
        // PLC4X 格式：type:address:unitId
        // 例如：coil:0:1 表示单元ID 1的线圈0
        //      input-register:100:1 表示单元ID 1的输入寄存器100
        return switch (type.toLowerCase()) {
            case "coil" -> String.format("coil:%s:%d", address, unitId);
            case "discrete-input" -> String.format("discrete-input:%s:%d", address, unitId);
            case "input-register" -> String.format("input-register:%s:%d", address, unitId);
            case "holding-register" -> String.format("holding-register:%s:%d", address, unitId);
            default -> throw new IllegalArgumentException("不支持的地址类型: " + type);
        };
    }
}
