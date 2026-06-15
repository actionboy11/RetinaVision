package com.example.retinavision.constant;

/**
 * 业务错误码常量定义
 */
public class ErrorMessageSignal {
    /** 成功 */
    public static final int SUCCESS = 0;
    
    /** 请求参数错误 */
    public static final int PARAM_ERROR = 40000;
    
    /** 用户名或密码错误 */
    public static final int LOGIN_ERROR = 40001;
    
    /** 未登录或 Token 失效 */
    public static final int UNAUTHORIZED = 40100;
    
    /** 无权限访问 */
    public static final int FORBIDDEN = 40300;
    
    /** 资源不存在 */
    public static final int NOT_FOUND = 40400;
    
    /** 重复提交或状态冲突 */
    public static final int CONFLICT = 40900;
    
    /** 服务端内部错误 */
    public static final int SERVER_ERROR = 50000;
    
    /** 文件存储异常 */
    public static final int FILE_STORAGE_ERROR = 50001;
    
    /** MQ 消息投递异常 */
    public static final int MQ_DELIVERY_ERROR = 50002;
    
    /** AI 分析任务执行异常 */
    public static final int AI_TASK_ERROR = 50003;
}
