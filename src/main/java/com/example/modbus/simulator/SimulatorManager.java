package com.example.modbus.simulator;

import com.example.modbus.config.SimulatorProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟器管理器
 * 负责启动和管理所有模拟服务器
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "simulator", name = "enabled", havingValue = "true")
public class SimulatorManager {

    private final SimulatorProperties simulatorProperties;

    private final List<ModbusSimulatorServer> modbusServers = new ArrayList<>();
    private final List<S7SimulatorServer> s7Servers = new ArrayList<>();

    @PostConstruct
    public void startSimulators() {
        log.info("===== 启动模拟器服务 =====");

        // 启动 Modbus 模拟服务器
        for (SimulatorProperties.ModbusServerConfig config : simulatorProperties.getModbusServers()) {
            if (!config.isEnabled()) {
                log.info("跳过禁用的 Modbus 服务器: {}", config.getName());
                continue;
            }

            try {
                ModbusSimulatorServer server = new ModbusSimulatorServer(
                        config.getName(),
                        config.getPort(),
                        config.getUnitId()
                );
                server.start();
                modbusServers.add(server);
                log.info("✓ Modbus 服务器 '{}' 启动成功", config.getName());
            } catch (Exception e) {
                log.error("✗ 启动 Modbus 服务器 '{}' 失败", config.getName(), e);
            }
        }

        // 启动 S7 模拟服务器
        for (SimulatorProperties.S7ServerConfig config : simulatorProperties.getS7Servers()) {
            if (!config.isEnabled()) {
                log.info("跳过禁用的 S7 服务器: {}", config.getName());
                continue;
            }

            try {
                S7SimulatorServer server = new S7SimulatorServer(
                        config.getName(),
                        config.getPort(),
                        config.getRack(),
                        config.getSlot()
                );
                server.start();
                s7Servers.add(server);
                log.info("✓ S7 服务器 '{}' 启动成功", config.getName());
            } catch (Exception e) {
                log.error("✗ 启动 S7 服务器 '{}' 失败", config.getName(), e);
            }
        }

        log.info("===== 模拟器服务启动完成 =====");
        log.info("运行中的 Modbus 服务器: {}", modbusServers.size());
        log.info("运行中的 S7 服务器: {}", s7Servers.size());
    }

    @PreDestroy
    public void stopSimulators() {
        log.info("正在停止所有模拟器...");

        for (ModbusSimulatorServer server : modbusServers) {
            try {
                server.stop();
            } catch (Exception e) {
                log.error("停止 Modbus 服务器失败", e);
            }
        }
        modbusServers.clear();

        for (S7SimulatorServer server : s7Servers) {
            try {
                server.stop();
            } catch (Exception e) {
                log.error("停止 S7 服务器失败", e);
            }
        }
        s7Servers.clear();

        log.info("所有模拟器已停止");
    }

    /**
     * 获取 Modbus 服务器
     */
    public ModbusSimulatorServer getModbusServer(String name) {
        return modbusServers.stream()
                .filter(s -> s.isRunning())
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取 S7 服务器
     */
    public S7SimulatorServer getS7Server(String name) {
        return s7Servers.stream()
                .filter(s -> s.isRunning())
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有 Modbus 服务器
     */
    public List<ModbusSimulatorServer> getAllModbusServers() {
        return new ArrayList<>(modbusServers);
    }

    /**
     * 获取所有 S7 服务器
     */
    public List<S7SimulatorServer> getAllS7Servers() {
        return new ArrayList<>(s7Servers);
    }
}
