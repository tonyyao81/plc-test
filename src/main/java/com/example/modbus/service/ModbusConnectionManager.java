package com.example.modbus.service;

import com.example.modbus.config.ModbusProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modbus连接管理器
 * 负责管理设备A和设备B的PLC4X连接
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModbusConnectionManager {

    private final ModbusProperties modbusProperties;
    private final Map<String, PlcConnection> connections = new ConcurrentHashMap<>();
    private PlcDriverManager driverManager;

    @PostConstruct
    public void init() {
        try {
            driverManager = PlcDriverManager.getDefault();
            log.info("Modbus连接管理器初始化成功");
        } catch (Exception e) {
            log.error("初始化PlcDriverManager失败", e);
            throw new RuntimeException("无法初始化Modbus连接管理器", e);
        }
    }

    /**
     * 获取或创建设备A的连接
     */
    public PlcConnection getDeviceAConnection() throws Exception {
        return getOrCreateConnection("deviceA", modbusProperties.getDeviceA());
    }

    /**
     * 获取或创建设备B的连接
     */
    public PlcConnection getDeviceBConnection() throws Exception {
        return getOrCreateConnection("deviceB", modbusProperties.getDeviceB());
    }

    /**
     * 获取或创建连接
     */
    private PlcConnection getOrCreateConnection(String deviceName, ModbusProperties.DeviceConfig config) throws Exception {
        PlcConnection connection = connections.get(deviceName);

        // 检查连接是否存在且有效
        if (connection != null && connection.isConnected()) {
            return connection;
        }

        // 创建新连接
        synchronized (this) {
            // 双重检查
            connection = connections.get(deviceName);
            if (connection != null && connection.isConnected()) {
                return connection;
            }

            // 关闭旧连接（如果存在）
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.warn("关闭旧连接时出错: {}", deviceName, e);
                }
            }

            // 创建新连接
            String connectionString = config.getConnectionString();
            log.info("正在连接到设备 {}: {}", deviceName, connectionString);

            connection = driverManager.getConnectionManager().getConnection(connectionString);

            if (!connection.isConnected()) {
                throw new RuntimeException("无法连接到设备: " + deviceName);
            }

            connections.put(deviceName, connection);
            log.info("成功连接到设备 {}", deviceName);

            return connection;
        }
    }

    /**
     * 重新连接指定设备
     */
    public void reconnect(String deviceName) {
        PlcConnection connection = connections.remove(deviceName);
        if (connection != null) {
            try {
                connection.close();
                log.info("已关闭设备 {} 的连接，准备重新连接", deviceName);
            } catch (Exception e) {
                log.warn("关闭连接时出错: {}", deviceName, e);
            }
        }
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup() {
        log.info("正在关闭所有Modbus连接...");
        for (Map.Entry<String, PlcConnection> entry : connections.entrySet()) {
            try {
                if (entry.getValue() != null) {
                    entry.getValue().close();
                    log.info("已关闭设备 {} 的连接", entry.getKey());
                }
            } catch (Exception e) {
                log.error("关闭连接时出错: {}", entry.getKey(), e);
            }
        }
        connections.clear();
    }
}
