package com.example.retinavision.constant;

/**
 * 业务错误提示信息常量定义
 */
public class ErrorMessageContant {
    // ==================== 用户相关 ====================
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String USER_ALREADY_EXISTS = "用户已存在";
    public static final String PASSWORD_LENGTH_ERROR = "密码长度必须在6-20个字符之间";
    public static final String USERNAME_LENGTH_ERROR = "用户名长度必须在4-20个字符之间";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String USER_NOT_AUTHORIZED = "用户未授权";
    public static final String USER_PASSWORD_ERROR = "用户名或者密码错误";
    public static final String USER_NOT_ACTIVE = "该用户账号不可使用";
    
    // ==================== 病例相关 ====================
    public static final String CASE_ALREADY_EXISTS = "患者编号已存在";
    public static final String CASE_CODE_REQUIRED = "患者编号不能为空";
    public static final String CASE_CODELENGTH_ERROR = "患者编号长度在3-64 个字符";
    public static final String CASE_REQUIRED_FIELD_ERROR = "患者性别和眼别不能为空";
    public static final String CASE_AGE_ERROR = "患者年龄在0-120岁";
    public static final String CASE_DELETED_MSG = "该病例已删除";
    public static final String CASE_NOT_EXISTS = "病例不存在";
    public static final String CASE_UPDATE_EMPTY = "至少需要传入一个要更新的病例字段";
    public static final String CASE_STATUS_DELETE_FORBIDDEN = "更新病例接口不能直接删除病例";

    // ==================== 图像相关 ====================
    public static final String IMAGE_NOT_EXISTS = "图像不存在";
    public static final String IMAGE_DELETED_MSG = "该图像已删除";
    public static final String IMAGE_EMPTY_FILE = "上传文件不能为空";
    public static final String IMAGE_TYPE_ERROR = "仅支持 png、jpg、jpeg、tif、tiff 格式图像";
    public static final String IMAGE_SIZE_ERROR = "图像文件不能超过20MB";
    public static final String IMAGE_STORAGE_PATH_ERROR = "图像文件不存在或存储路径无效";
    // ==================== 任务相关 ====================
    public static final String TASK_NOT_EXISTS = "任务不存在";

    // ==================== 通用错误提示 ====================
    public static final String SUCCESS_MSG = "成功";
    public static final String PARAM_ERROR_MSG = "请求参数错误";
    public static final String LOGIN_ERROR_MSG = "用户名或密码错误";
    public static final String UNAUTHORIZED_MSG = "未登录或 Token 失效";
    public static final String FORBIDDEN_MSG = "无权限访问";
    public static final String NOT_FOUND_MSG = "资源不存在";
    public static final String CONFLICT_MSG = "重复提交或状态冲突";
    public static final String SERVER_ERROR_MSG = "服务端内部错误";
    public static final String FILE_STORAGE_ERROR_MSG = "文件存储异常";
    public static final String MQ_DELIVERY_ERROR_MSG = "MQ 消息投递异常";
    public static final String AI_TASK_ERROR_MSG = "AI 分析任务执行异常";
}
