package com.example.retinavision.pojo.DTO;
import com.example.retinavision.enumeration.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data @NoArgsConstructor @AllArgsConstructor
public class SubmitReviewDTO {
    private Integer correctionVersion;
    private ReviewStatus status;
    private String findings;
    private String conclusion;
    private String recommendation;
    private Integer expectedVersion;
}
