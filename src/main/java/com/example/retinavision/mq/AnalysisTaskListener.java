package com.example.retinavision.mq;

import com.example.retinavision.enumeration.TaskStatus;
import com.example.retinavision.mapper.LogMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.pojo.Entity.LogEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Component
public class AnalysisTaskListener {

    //CONSUMABLE_STATUS 是允许被消费的任务状态集合
    private static final Set<TaskStatus> CONSUMABLE_STATUS =
            EnumSet.of(TaskStatus.WAITING, TaskStatus.RETRYING);

    private final TaskMapper taskMapper;
    private final LogMapper logMapper;

    public AnalysisTaskListener(TaskMapper taskMapper, LogMapper logMapper) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
    }

    @Transactional
    @RabbitListener(queues = "${retina.mq.analysis-queue}")
    public void handleAnalysisTask(AnalysisTaskMessage message, Channel channel, Message rawMessage) throws Exception {
        //RabbitMQ 在当前 channel 上给这次消息投递分配的编号，用来告诉 RabbitMQ处理的是哪一条消息。
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        try {
            TaskEntity taskEntity = taskMapper.selectById(message.getTaskId());
            if (taskEntity == null) {
                // Codex: 生产者事务尚未提交时，Worker 可能先收到 MQ 消息；重新入队，稍后再查数据库。
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            if (taskEntity.getStatus() == TaskStatus.FAILED) {
                // Codex: 重试任务在事务提交前可能仍读到旧的 FAILED 状态；重新入队，等待状态提交为 WAITING。
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            if (!CONSUMABLE_STATUS.contains(taskEntity.getStatus())) {
                // Codex: 消息可能重复投递；状态不再可消费时直接 ack，避免把已完成/已取消任务改回 RUNNING。
                channel.basicAck(deliveryTag, false);
                return;
            }

            TaskStatus fromStatus = taskEntity.getStatus();
            LocalDateTime now = LocalDateTime.now();
            taskEntity.setStatus(TaskStatus.RUNNING);
            taskEntity.setStartedAt(now);
            taskEntity.setUpdatedAt(now);
            taskMapper.updateById(taskEntity);

            logMapper.insert(LogEntity.builder()
                    .taskId(taskEntity.getId())
                    .fromStatus(fromStatus)
                    .toStatus(TaskStatus.RUNNING)
                    .message("Worker 已接收任务，开始处理")
                    .operatorType("WORKER")
                    .operatorId(null)
                    .createdAt(now)
                    .build());

            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            // Codex: requeue=false 会把失败消息交给死信队列，便于后续人工排查或补偿处理。
            channel.basicNack(deliveryTag, false, false);
            throw exception;
        }
    }
}
