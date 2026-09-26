package com.example.retinavision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PromptEvaluationRunMapper extends BaseMapper<PromptEvaluationRunEntity> {
    @Update("UPDATE prompt_evaluation_run SET review_decision = #{decision}, review_score = #{score}, "
            + "review_note = #{note}, reviewed_by = #{reviewedBy}, reviewed_at = #{reviewedAt} "
            + "WHERE id = #{id} AND review_decision IS NULL AND status = 'COMPLETED'")
    int saveReviewIfPending(@Param("id") Long id,
                            @Param("decision") String decision,
                            @Param("score") Integer score,
                            @Param("note") String note,
                            @Param("reviewedBy") Integer reviewedBy,
                            @Param("reviewedAt") LocalDateTime reviewedAt);
}
