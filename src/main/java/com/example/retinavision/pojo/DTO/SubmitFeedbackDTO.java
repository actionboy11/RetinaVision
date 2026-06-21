package com.example.retinavision.pojo.DTO;
import com.example.retinavision.enumeration.FeedbackVerdict;
import lombok.Data;
import java.util.List;
@Data public class SubmitFeedbackDTO { private FeedbackVerdict verdict; private List<String> issueCodes; private String comment; }
