package com.example.retinavision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 医生审核提醒配置。
 *
 * <p>这里的配置只控制“多久没处理算超时”，不会创建后台定时任务。
 * 当前提醒采用实时计算：前端请求提醒接口时，后端根据任务、审核和报告状态现场统计。</p>
 */
@Component
@ConfigurationProperties(prefix = "retina.doctor-review")
public class DoctorReviewReminderProperties {

    /**
     * AI 血管分割任务完成后，超过多少分钟还没完成审核/签发，就算超时待办。
     * 默认 120 分钟，也就是 2 小时。
     */
    private long overdueThresholdMinutes = 120;

    public long getOverdueThresholdMinutes() {
        return overdueThresholdMinutes;
    }

    public void setOverdueThresholdMinutes(long overdueThresholdMinutes) {
        this.overdueThresholdMinutes = overdueThresholdMinutes;
    }
}
