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
    private final Map<String, String> deviceNameToEndpoint = new ConcurrentHashMap<>(); // 设备名到endpoint的映射
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
        // 使用 endpoint 作为连接键，实现连接复用
        String endpoint = getEndpoint(modbusProperties.getDeviceA());
        deviceNameToEndpoint.put("deviceA", endpoint);
        return getOrCreateConnection(endpoint, modbusProperties.getDeviceA());
    }

    /**
     * 获取或创建设备B的连接
     */
    public PlcConnection getDeviceBConnection() throws Exception {
        // 使用 endpoint 作为连接键，实现连接复用
        String endpoint = getEndpoint(modbusProperties.getDeviceB());
        deviceNameToEndpoint.put("deviceB", endpoint);
        return getOrCreateConnection(endpoint, modbusProperties.getDeviceB());
    }

    /**
     * 获取设备的 endpoint 标识（host:port:unit-id）
     * 用于判断两个设备是否指向同一个物理连接
     */
    private String getEndpoint(ModbusProperties.DeviceConfig config) {
        return String.format("%s:%d:%d", config.getHost(), config.getPort(), config.getUnitId());
    }

    /**
     * 获取或创建连接
     * @param endpoint 连接端点标识 (host:port:unit-id)
     * @param config 设备配置
     */
    private PlcConnection getOrCreateConnection(String endpoint, ModbusProperties.DeviceConfig config) throws Exception {
        PlcConnection connection = connections.get(endpoint);

        // 检查连接是否存在且有效
        if (connection != null) {
            try {
                // 更严格的连接验证：不仅检查 isConnected()，还要确保连接可用
                if (connection.isConnected()) {
                    // 尝试获取连接元数据来验证连接真正可用
                    // 如果内部线程池已终止，这里会抛出 RejectedExecutionException
                    connection.getMetadata();
                    return connection;
                }
            } catch (Exception e) {
                // 连接已失效（例如线程池已终止），需要移除并重建
                log.warn("检测到 {} 的连接已失效: {}", endpoint, e.getMessage());
                connections.remove(endpoint);
                // 尝试关闭失效的连接
                try {
                    connection.close();
                } catch (Exception closeEx) {
                    log.debug("关闭失效连接时出错: {}", endpoint, closeEx);
                }
                connection = null;
            }
        }

        // 创建新连接
        synchronized (this) {
            // 双重检查
            connection = connections.get(endpoint);
            if (connection != null) {
                try {
                    if (connection.isConnected()) {
                        connection.getMetadata();
                        return connection;
                    }
                } catch (Exception e) {
                    log.debug("双重检查时发现连接失效: {}", endpoint);
                    connections.remove(endpoint);
                    connection = null;
                }
            }

            // 关闭旧连接（如果存在）
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.warn("关闭旧连接时出错: {}", endpoint, e);
                }
            }

            // 创建新连接
            String connectionString = config.getConnectionString();
            log.info("正在连接到 {}: {}", endpoint, connectionString);

            try {
                connection = driverManager.getConnectionManager().getConnection(connectionString);

                if (!connection.isConnected()) {
                    throw new RuntimeException("连接创建成功但状态为未连接");
                }

                // 验证连接真正可用
                connection.getMetadata();

                connections.put(endpoint, connection);
                log.info("成功连接到 {} (此连接可能被多个设备复用)", endpoint);

                return connection;
            } catch (Exception e) {
                log.error("创建到 {} 的连接失败: {}", endpoint, e.getMessage());
                // 确保失败的连接不会被缓存
                connections.remove(endpoint);
                throw new RuntimeException("无法连接到: " + endpoint + " - " + e.getMessage(), e);
            }
        }
    }

    /**
     * 重新连接指定设备
     */
    public void reconnect(String deviceName) {
        // 通过设备名找到对应的 endpoint
        String endpoint = deviceNameToEndpoint.get(deviceName);
        if (endpoint != null) {
            PlcConnection connection = connections.remove(endpoint);
            if (connection != null) {
                try {
                    connection.close();
                    log.info("已关闭 {} ({}) 的连接，准备重新连接", deviceName, endpoint);
                } catch (Exception e) {
                    log.warn("关闭连接时出错: {} ({})", deviceName, endpoint, e);
                }
            }
        } else {
            log.debug("设备 {} 没有活跃的连接", deviceName);
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
