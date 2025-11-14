package com.example.modbus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Modbus配置属性类
 * 从application.yml中读取配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "modbus")
public class ModbusProperties {

    private DeviceConfig deviceA;
    private DeviceConfig deviceB;
    private MonitorConfig monitor;

    @Data
    public static class DeviceConfig {
        /**
         * Modbus设备主机地址
         */
        private String host;

        /**
         * Modbus端口（通常为502）
         */
        private int port = 502;

        /**
         * Modbus单元ID（从站地址）
         */
        private int unitId = 1;

        /**
         * 标志位地址，格式：coil:地址 或 holding-register:地址
         * 例如：coil:0, holding-register:100
         */
        private String flagAddress;

        /**
         * 生成Modbus连接字符串
         */
        public String getConnectionString() {
            return String.format("modbus-tcp://%s:%d", host, port);
        }
    }

    @Data
    public static class MonitorConfig {
        /**
         * 轮询间隔（毫秒）
         */
        private long pollIntervalMs = 1000;

        /**
         * 连接超时（毫秒）
         */
        private long connectionTimeoutMs = 5000;

        /**
         * 请求超时（毫秒）
         */
        private long requestTimeoutMs = 3000;
    }
}
