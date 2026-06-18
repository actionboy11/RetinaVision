package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.StatisticsMapper;
import com.example.retinavision.mq.RabbitMqProperties;
import com.example.retinavision.pojo.VO.QueueStatisticsVO;
import com.example.retinavision.pojo.VO.TaskStatisticsVO;
import com.example.retinavision.pojo.VO.TaskTrendItemVO;
import com.example.retinavision.service.StatisticsService;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

@Service
public class StatisticsServiceImpl implements StatisticsService {
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int MIN_TREND_DAYS = 1;
    private static final int MAX_TREND_DAYS = 30;

    private final StatisticsMapper statisticsMapper;
    private final RabbitAdmin rabbitAdmin;
    private final RabbitMqProperties rabbitMqProperties;

    public StatisticsServiceImpl(
            StatisticsMapper statisticsMapper,
            RabbitAdmin rabbitAdmin,
            RabbitMqProperties rabbitMqProperties) {
        this.statisticsMapper = statisticsMapper;
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitMqProperties = rabbitMqProperties;
    }

    @Override
    public TaskStatisticsVO getTaskStatistics() {
        TaskStatisticsVO taskStatistics = statisticsMapper.selectTaskStatistics();
        if (taskStatistics == null) {
            return TaskStatisticsVO.builder()
                    .todaySubmittedCount(0L)
                    .todaySuccessCount(0L)
                    .todayFailedCount(0L)
                    .waitingCount(0L)
                    .runningCount(0L)
                    .totalTaskCount(0L)
                    .successRate(0D)
                    .averageProcessingTimeMs(0L)
                    .build();
        }
        return taskStatistics;
    }

    @Override
    public QueueStatisticsVO getQueueStatistics() {
        Properties mainQueueProperties = getRequiredQueueProperties(rabbitMqProperties.getAnalysisQueue());
        Properties deadLetterQueueProperties = getRequiredQueueProperties(rabbitMqProperties.getAnalysisDeadLetterQueue());

        return QueueStatisticsVO.builder()
                .queueName(rabbitMqProperties.getAnalysisQueue())
                .messageReadyCount(getLongProperty(mainQueueProperties, RabbitAdmin.QUEUE_MESSAGE_COUNT))
                // AMQP passive declare does not expose unacked count; later use RabbitMQ Management HTTP API if exact unacked is required.
                .messageUnackedCount(0L)
                .consumerCount(getLongProperty(mainQueueProperties, RabbitAdmin.QUEUE_CONSUMER_COUNT))
                .deadLetterCount(getLongProperty(deadLetterQueueProperties, RabbitAdmin.QUEUE_MESSAGE_COUNT))
                .build();
    }

    @Override
    public List<TaskTrendItemVO> getTaskTrend(Integer days) {
        int safeDays = normalizeTrendDays(days);
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);
        return statisticsMapper.selectTaskTrend(startDate);
    }

    private Properties getRequiredQueueProperties(String queueName) {
        try {
            Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);
            if (queueProperties == null) {
                throw new BaseException(ErrorMessageSignal.NOT_FOUND, "RabbitMQ 队列不存在：" + queueName);
            }
            return queueProperties;
        } catch (BaseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.SERVER_ERROR, ErrorMessageContant.SERVER_ERROR_MSG);
        }
    }

    private Long getLongProperty(Properties properties, Object key) {
        Object value = properties.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        return 0L;
    }

    private int normalizeTrendDays(Integer days) {
        if (days == null) {
            return DEFAULT_TREND_DAYS;
        }
        if (days < MIN_TREND_DAYS) {
            return MIN_TREND_DAYS;
        }
        return Math.min(days, MAX_TREND_DAYS);
    }
}
