package com.example.retinavision.pojo.VO;

import java.util.ArrayList;
import java.util.List;

/**
 * 医生审核提醒摘要。
 *
 * <p>这个 VO 给前端侧边栏红点和在线弹窗使用，不改变任何业务状态。</p>
 */
public class DoctorReviewReminderVO {

    /** AI 血管分割完成但医生还没审核的数量。 */
    private int pendingReviewCount;

    /** 待审核任务中，超过阈值仍未处理的数量。 */
    private int overdueReviewCount;

    /** 医生已审核通过但还没签发 PDF 报告的数量。 */
    private int pendingReportCount;

    /** 待签发报告中，超过阈值仍未处理的数量。 */
    private int overdueReportCount;

    /** 超时阈值，单位分钟。 */
    private long overdueThresholdMinutes;

    /** 最近几条超时待办，用于弹窗里提示医生优先处理。 */
    private List<DoctorReviewReminderItemVO> latestOverdueItems = new ArrayList<>();

    public int getPendingReviewCount() {
        return pendingReviewCount;
    }

    public void setPendingReviewCount(int pendingReviewCount) {
        this.pendingReviewCount = pendingReviewCount;
    }

    public int getOverdueReviewCount() {
        return overdueReviewCount;
    }

    public void setOverdueReviewCount(int overdueReviewCount) {
        this.overdueReviewCount = overdueReviewCount;
    }

    public int getPendingReportCount() {
        return pendingReportCount;
    }

    public void setPendingReportCount(int pendingReportCount) {
        this.pendingReportCount = pendingReportCount;
    }

    public int getOverdueReportCount() {
        return overdueReportCount;
    }

    public void setOverdueReportCount(int overdueReportCount) {
        this.overdueReportCount = overdueReportCount;
    }

    public long getOverdueThresholdMinutes() {
        return overdueThresholdMinutes;
    }

    public void setOverdueThresholdMinutes(long overdueThresholdMinutes) {
        this.overdueThresholdMinutes = overdueThresholdMinutes;
    }

    public List<DoctorReviewReminderItemVO> getLatestOverdueItems() {
        return latestOverdueItems;
    }

    public void setLatestOverdueItems(List<DoctorReviewReminderItemVO> latestOverdueItems) {
        this.latestOverdueItems = latestOverdueItems;
    }
}
