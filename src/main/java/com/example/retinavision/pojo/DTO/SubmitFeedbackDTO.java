package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.FeedbackVerdict;
import lombok.Data;

import java.util.List;

/**
 * 提交人工反馈的请求 DTO。
 *
 * <p>使用场景：普通用户、研究员或医生在结果页表达“AI 结果是否可接受”。
 * 反馈只做追加记录，不会覆盖 AI 原始结果，也不会直接改变医生审核结论。</p>
 */
@Data
public class SubmitFeedbackDTO {

    /**
     * 用户对 AI 结果的总体判断：接受、部分正确或错误。
     */
    private FeedbackVerdict verdict;

    /**
     * 问题代码列表，例如模糊、血管断裂、误分割等。
     * 当前数据库用 JSON 字符串保存，Service 层会把 List 序列化为 JSON。
     */
    private List<String> issueCodes;

    /**
     * 人工说明文本，用来补充 issueCodes 无法表达的细节。
     */
    private String comment;
}
