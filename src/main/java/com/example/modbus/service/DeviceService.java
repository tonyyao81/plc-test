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
            return readBooleanValue(connection, address, "设备A");
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
            return writeBooleanValue(connection, address, value, "设备B");
        } catch (Exception e) {
            log.error("写入设备B标志位失败", e);
            // 尝试重新连接
            connectionManager.reconnect("deviceB");
            return false;
        }
    }

    /**
     * 读取布尔值
     */
    private boolean readBooleanValue(PlcConnection connection, String address, String deviceName) throws Exception {
        PlcReadRequest.Builder builder = connection.readRequestBuilder();
        PlcReadRequest readRequest = builder.addTagAddress("flag", address).build();

        PlcReadResponse response = readRequest.execute().get();

        if (response.getResponseCode("flag") != PlcResponseCode.OK) {
            log.error("{}读取响应错误: {}", deviceName, response.getResponseCode("flag"));
            throw new RuntimeException(deviceName + "读取失败: " + response.getResponseCode("flag"));
        }

        boolean value = response.getBoolean("flag");
        log.debug("{}标志位读取成功: {}", deviceName, value);
        return value;
    }

    /**
     * 写入布尔值
     */
    private boolean writeBooleanValue(PlcConnection connection, String address, boolean value, String deviceName) throws Exception {
        PlcWriteRequest.Builder builder = connection.writeRequestBuilder();
        PlcWriteRequest writeRequest = builder.addTagAddress("flag", address, value).build();

        PlcWriteResponse response = writeRequest.execute().get();

        if (response.getResponseCode("flag") != PlcResponseCode.OK) {
            log.error("{}写入响应错误: {}", deviceName, response.getResponseCode("flag"));
            return false;
        }

        log.info("{}标志位写入成功: {}", deviceName, value);
        return true;
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
        // 格式：modbus:unit-id:type:address
        // 例如：coil:1:0 表示单元ID 1的线圈0
        //      holding-register:1:100 表示单元ID 1的保持寄存器100
        return switch (type.toLowerCase()) {
            case "coil" -> String.format("%s:%d", address, unitId);
            case "discrete-input" -> String.format("%s:%d", address, unitId);
            case "input-register" -> String.format("%s:%d", address, unitId);
            case "holding-register" -> String.format("%s:%d", address, unitId);
            default -> throw new IllegalArgumentException("不支持的地址类型: " + type);
        };
    }
}
