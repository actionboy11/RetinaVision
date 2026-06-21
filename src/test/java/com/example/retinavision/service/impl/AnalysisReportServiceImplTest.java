package com.example.retinavision.service.impl;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.*;
import com.example.retinavision.pojo.Entity.*;
import com.example.retinavision.service.ReportPdfRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalysisReportServiceImplTest {
 @TempDir Path root;
 private final AnalysisResultMapper results=mock(AnalysisResultMapper.class);private final AnalysisReportMapper reports=mock(AnalysisReportMapper.class);
 private final AnalysisReviewMapper reviews=mock(AnalysisReviewMapper.class);private final UserRegisterMapper users=mock(UserRegisterMapper.class);
 @Test void unapprovedResultCannotBeSigned(){when(results.selectById(1L)).thenReturn(new AnalysisResultEntity());AnalysisReviewEntity r=new AnalysisReviewEntity();r.setStatus(ReviewStatus.PENDING);when(reviews.selectOne(any())).thenReturn(r);var s=service();assertThatThrownBy(()->s.sign(1L,2)).isInstanceOf(BaseException.class);}
 @Test void signingStoresImmutableFileHash() throws Exception {when(results.selectById(1L)).thenReturn(new AnalysisResultEntity());when(reports.selectList(any())).thenReturn(List.of());when(reports.updateById(any(AnalysisReportEntity.class))).thenReturn(1);AnalysisReviewEntity r=new AnalysisReviewEntity();r.setStatus(ReviewStatus.APPROVED);when(reviews.selectOne(any())).thenReturn(r);UserEntity d=new UserEntity();d.setRealName("医生");d.setProfessionalNo("DOC-1");when(users.selectById(2)).thenReturn(d);AnalysisReportEntity signed=service().sign(1L,2);assertThat(signed.getReportSha256()).hasSize(64);assertThat(Files.readAllBytes(root.resolve(signed.getReportObjectKey()))).isEqualTo("pdf".getBytes());}
 private AnalysisReportServiceImpl service(){ReportPdfRenderer renderer=(document,path)->{try{Files.createDirectories(path.getParent());Files.writeString(path,"pdf");}catch(Exception e){throw new RuntimeException(e);}};return new AnalysisReportServiceImpl(results,reports,reviews,users,renderer,root.toString());}
}
