package com.example.modbus.service;

import com.example.modbus.config.ModbusProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 监控服务
 * 负责监控设备A的标志位变化，并触发设备B的控制逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final DeviceService deviceService;
    private final ModbusProperties modbusProperties;

    // 保存设备A的上一次状态
    private final AtomicBoolean previousStateA = new AtomicBoolean(false);

    // 服务是否已启动
    private volatile boolean started = false;

    @PostConstruct
    public void init() {
        log.info("监控服务初始化完成");
        log.info("设备A: {}:{}",
            modbusProperties.getDeviceA().getHost(),
            modbusProperties.getDeviceA().getPort());
        log.info("设备B: {}:{}",
            modbusProperties.getDeviceB().getHost(),
            modbusProperties.getDeviceB().getPort());
        log.info("轮询间隔: {}ms", modbusProperties.getMonitor().getPollIntervalMs());
    }

    /**
     * 定期轮询设备A的标志位
     * 使用fixedDelayString从配置文件读取轮询间隔
     */
    @Scheduled(fixedDelayString = "${modbus.monitor.poll-interval-ms:1000}")
    public void pollDeviceA() {
        if (!started) {
            started = true;
            log.info("监控服务已启动，开始轮询设备A...");
        }

        try {
            // 读取设备A当前状态
            boolean currentState = deviceService.readDeviceAFlag();

            // 获取上一次的状态
            boolean previousState = previousStateA.get();

            // 检测状态变化：从0到1
            if (!previousState && currentState) {
                onStateChangedFrom0To1();
            }
            // 检测状态变化：从1到0
            else if (previousState && !currentState) {
                onStateChangedFrom1To0();
            }

            // 更新状态
            previousStateA.set(currentState);

            // 记录状态（可选，仅在状态变化时记录）
            if (previousState != currentState) {
                log.info("设备A标志位状态变化: {} -> {}", previousState ? 1 : 0, currentState ? 1 : 0);
            }

        } catch (Exception e) {
            log.error("轮询设备A时发生错误", e);
        }
    }

    /**
     * 当设备A标志位从0变成1时触发
     * 将设备B的标志位设置为1
     */
    private void onStateChangedFrom0To1() {
        log.warn("检测到设备A标志位从 0 变为 1，正在触发设备B...");

        try {
            boolean success = deviceService.writeDeviceBFlag(true);

            if (success) {
                log.info("成功将设备B标志位设置为 1");
            } else {
                log.error("设置设备B标志位失败");
            }

        } catch (Exception e) {
            log.error("触发设备B时发生错误", e);
        }
    }

    /**
     * 当设备A标志位从1变成0时触发
     * 将设备B的标志位设置为0
     */
    private void onStateChangedFrom1To0() {
        log.warn("检测到设备A标志位从 1 变为 0，正在触发设备B...");

        try {
            boolean success = deviceService.writeDeviceBFlag(false);

            if (success) {
                log.info("成功将设备B标志位设置为 0");
            } else {
                log.error("设置设备B标志位失败");
            }

        } catch (Exception e) {
            log.error("触发设备B时发生错误", e);
        }
    }

    /**
     * 手动触发一次检查（用于测试）
     */
    public void manualTrigger() {
        log.info("手动触发检查...");
        pollDeviceA();
    }

    /**
     * 重置监控状态（用于测试）
     */
    public void reset() {
        log.info("重置监控状态");
        previousStateA.set(false);
    }

    /**
     * 获取当前设备A的状态
     */
    public boolean getCurrentDeviceAState() {
        return previousStateA.get();
    }
}
